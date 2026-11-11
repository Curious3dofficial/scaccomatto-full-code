import javax.sound.sampled.*;
import java.io.File;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SoundManager {
    // Mute state is remembered across app restarts (user preferences),
    // so the game opens muted if it was muted when it was closed.
    private static final java.util.prefs.Preferences PREFS =
            java.util.prefs.Preferences.userNodeForPackage(SoundManager.class);
    private static final String PREF_MUTED = "globalMuted";
    private static boolean soundEnabled = !PREFS.getBoolean(PREF_MUTED, false);

    private static void saveMuted() {
        try {
            PREFS.putBoolean(PREF_MUTED, !soundEnabled);
            PREFS.flush();
        } catch (Exception ignored) {
            // can't save preferences -- just keep it for this session
        }
    }
    private static final Set<SoundHandle> activeSounds = ConcurrentHashMap.newKeySet();
    // looping music, so a volume change in Settings reaches it while it plays
    private static final Set<SoundHandle> musicHandles = ConcurrentHashMap.newKeySet();

    // ── volume levels from Settings (master x music / master x effects) ──────
    static float musicGain() {
        return (AppSettings.masterVolume() / 100f) * (AppSettings.musicVolume() / 100f);
    }

    static float effectsGain() {
        return (AppSettings.masterVolume() / 100f) * (AppSettings.effectsVolume() / 100f);
    }

    /** Re-applies the music volume to tracks already playing (after a Settings change). */
    public static void refreshVolumes() {
        for (SoundHandle h : musicHandles) {
            h.targetVolume = Math.max(0f, Math.min(1f, h.baseVolume * musicGain()));
            if (h.clip != null && !h.silencedByGlobalMute && soundEnabled) setClipVolume(h.clip, h.targetVolume);
        }
    }

    // ── Handle returned by playSound() so callers can stop playback ───────
    public static class SoundHandle {
        private Clip          clip;    // non-null for WAV (music swaps it when the output device changes)
        /** Music only: plays it on again from the same spot on the current output device. */
        private Runnable      reopen;
        private final Process proc;    // non-null for MP3
        // Target gain for a Clip, captured when it was started, so the "M"
        // mute toggle can restore it exactly after silencing it in place
        // (see applyGlobalMute()). Defaults to full volume for the paths
        // that start a Clip without ever calling setClipVolume themselves.
        private float targetVolume = 1f;
        private float baseVolume = 1f;      // music: the track's own level before Settings' volume
        private boolean silencedByGlobalMute = false;
        // For instant mute: where the clip was paused, and whether it loops.
        private boolean looping = false;
        private int pausedFrame = 0;
        private volatile boolean stopped = false;   // stop() was called (vs. the track simply ending)

        void setLooping(boolean looping) { this.looping = looping; }

        SoundHandle(Clip clip) {
            this.clip = clip;
            this.proc = null;
            activeSounds.add(this);
        }

        SoundHandle(Process proc) {
            this.proc = proc;
            this.clip = null;
            activeSounds.add(this);
            Thread cleanup = new Thread(() -> {
                try {
                    proc.waitFor();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    activeSounds.remove(this);
                }
            }, "sound-process-cleanup");
            cleanup.setDaemon(true);
            cleanup.start();
        }

        void setTargetVolume(float v) { this.targetVolume = v; }

        /** Immediately stop and release the sound. Safe to call more than once. */
        public void stop() {
            stopped = true;
            musicHandles.remove(this);
            if (clip != null) {
                try { clip.stop(); clip.close(); } catch (Exception ignored) {}
            }
            if (proc != null) {
                try { proc.destroy(); } catch (Exception ignored) {}
            }
            activeSounds.remove(this);
        }

        /**
         * Used by the global "M" mute toggle (see SoundManager.toggleMuted()).
         * A Clip -- including a LOOP_CONTINUOUSLY background track like the
         * menu music -- is silenced in place (gain to minimum) rather than
         * stopped/closed, so unmuting can simply restore its gain and it
         * picks back up exactly where the loop already is. stop()/close()
         * would be permanent: nothing re-starts a looping track afterwards,
         * which is why muting used to leave the menu music dead for good.
         * A Process-backed sound (an MP3 one-shot; nothing loops through
         * this path -- see playMusic()'s own note) has no live volume
         * control once launched, so it's just stopped outright; that's a
         * short, non-looping effect anyway, not something that needs to
         * resume.
         */
        void applyGlobalMute(boolean muted) {
            if (clip != null) {
                // Instant: lowering the gain only took effect after the
                // audio already queued in the sound card's buffer had
                // played out (the noticeable delay). Pausing the clip and
                // flushing that buffer silences it right away; unmuting
                // resumes from the exact same spot.
                try {
                    if (muted) {
                        if (silencedByGlobalMute) return;
                        silencedByGlobalMute = true;
                        pausedFrame = clip.getFramePosition();
                        clip.stop();
                        clip.flush();
                    } else if (silencedByGlobalMute) {
                        silencedByGlobalMute = false;
                        setClipVolume(clip, targetVolume);
                        if (looping) {
                            int len = Math.max(1, clip.getFrameLength());
                            clip.setFramePosition(pausedFrame % len);
                            clip.loop(Clip.LOOP_CONTINUOUSLY);
                        } else if (pausedFrame < clip.getFrameLength()) {
                            clip.setFramePosition(pausedFrame);
                            clip.start();
                        }
                    }
                } catch (Exception ignored) {
                    // fall back to gain only
                    setClipVolume(clip, muted ? 0f : targetVolume);
                }
            } else if (proc != null && muted) {
                stop();
            }
        }
    }

    public enum SoundType {
        MOVE_SELF("move-self.wav"),
        CAPTURE("capture.wav"),
        CASTLE("castle.wav"),
        PROMOTE("promote.wav"),
        CHECK("move-check.wav"),
        GAME_START("after pressing start game.wav"),
        GAME_END("game-end.wav"),
        ILLEGAL("illegal.wav"),
        TEN_SECONDS("tensecondsleftonclock.wav"),
        ROOK_CHECK("rookcheck.wav"),
        CHECKMATE_WHITE("Checkmateyay1.mp3"),
        CHECKMATE_BLACK("Checkmateyay2.mp3");

        private final String fileName;
        SoundType(String fileName) { this.fileName = fileName; }
        public String getFileName() { return fileName; }
    }

    /**
     * Play a sound and return a SoundHandle that can stop it.
     * Returns null if the sound file is not found or sound is disabled.
     */
    public static SoundHandle playSound(SoundType soundType) {
        if (!soundEnabled) return null;

        // We need to return the handle synchronously, so resolve the file
        // on the calling thread, then start playback on a background thread.
        String fileName = soundType.getFileName();
        // the board sounds follow the piece set (wooden for Classic, soft for Minimal)
        String family = boardSoundFamily(soundType);
        File   soundFile = family == null ? null : resolveFile(family + fileName);
        if (soundFile == null) soundFile = resolveFile(fileName);
        if (soundFile == null) return null;

        float fx = effectsGain();
        if (fx <= 0.001f) return null;
        if (fileName.toLowerCase().endsWith(".mp3")) {
            // Start the native process and return its handle immediately
            Process proc = startMP3Process(soundFile, fx);
            return (proc != null) ? new SoundHandle(proc) : null;
        } else {
            // WAV: open the Clip, start it, return the handle
            try {
                AudioInputStream ain = AudioSystem.getAudioInputStream(soundFile.toURI().toURL());
                Clip clip = newClip();
                clip.open(ain);
                setClipVolume(clip, fx);
                clip.start();
                SoundHandle handle = new SoundHandle(clip);
                handle.setTargetVolume(fx);
                clip.addLineListener(e -> {
                    if (e.getType() == LineEvent.Type.STOP) {
                        clip.close();
                        activeSounds.remove(handle);
                    }
                });
                return handle;
            } catch (Exception ignored) { return null; }
        }
    }

    /** Every ordinary button press. */
    static final String TAP = "tap.wav";
    /** Back buttons (and Esc going back). */
    static final String BACK = "back.wav";
    /** A screen sliding down into view (Settings, Bot Battle...). */
    static final String SCROLL_DOWN = "scroll_down.wav";
    /** A screen lifting back up out of view. */
    static final String SCROLL_UP = "scroll_up.wav";

    private static volatile long lastSlideNanos = Long.MIN_VALUE;

    /** A screen slides down into view (true) or lifts back up (false). */
    static void playScreenSlide(boolean down) {
        lastSlideNanos = System.nanoTime();
        playExtraSound(down ? SCROLL_DOWN : SCROLL_UP);
    }

    /** Play a sound from src/assets/extras (or /assets/extras on classpath). */
    public static SoundHandle playExtraSound(String fileName) {
        if (!soundEnabled || fileName == null || fileName.trim().isEmpty()) return null;
        String name = fileName.trim();
        if ((TAP.equals(name) || BACK.equals(name)) && javax.swing.SwingUtilities.isEventDispatchThread()) {
            // The button click waits a moment: if that same click opens a popup, the popup
            // plays its own sound instead and the click is dropped (see PopupSound).
            java.util.Set<java.awt.Window> before = PopupSound.showingDialogs();
            long pressedAt = System.nanoTime();
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (PopupSound.newDialogSince(before)) return;
                // a back press that makes a screen slide away: only the slide's whoosh plays
                if (BACK.equals(name) && lastSlideNanos - pressedAt >= 0) return;
                File f = resolveFileFromExtra(name);
                if (f != null && soundEnabled) playResolvedFile(name, f);
            });
            return null;
        }
        File soundFile = resolveFileFromExtra(fileName.trim());
        if (soundFile == null) return null;

        return playResolvedFile(fileName, soundFile);
    }

    /** Play a sound from src/assets/spell chess sounds (or /assets/spell chess sounds on classpath). */
    public static SoundHandle playSpellChessSound(String fileName) {
        return playSpellChessSound(fileName, 1.0f);
    }

    public static SoundHandle playSpellChessSound(String fileName, float volume) {
        return playSpellChessSound(fileName, volume, 0);
    }

    public static SoundHandle playSpellChessSound(String fileName, float volume, int fadeInMs) {
        if (!soundEnabled || fileName == null || fileName.trim().isEmpty()) return null;
        File soundFile = resolveFileFromSpellChessSounds(fileName.trim());
        if (soundFile == null) return null;

        return playResolvedFile(fileName, soundFile, volume, fadeInMs);
    }

    private static SoundHandle playResolvedFile(String fileName, File soundFile) {
        return playResolvedFile(fileName, soundFile, 1.0f);
    }

    private static SoundHandle playResolvedFile(String fileName, File soundFile, float volume) {
        return playResolvedFile(fileName, soundFile, volume, 0);
    }

    private static SoundHandle playResolvedFile(String fileName, File soundFile, float volume, int fadeInMs) {
        String lower = fileName.toLowerCase();
        float vol = Math.max(0f, Math.min(1f, volume * effectsGain()));
        if (vol <= 0.001f) return null;
        if (lower.endsWith(".mp3")) {
            Process proc = startMP3Process(soundFile, vol, fadeInMs);
            return (proc != null) ? new SoundHandle(proc) : null;
        } else {
            try {
                AudioInputStream ain = AudioSystem.getAudioInputStream(soundFile.toURI().toURL());
                Clip clip = newClip();
                clip.open(ain);
                if (fadeInMs > 0) {
                    fadeClipIn(clip, vol, fadeInMs);
                } else {
                    setClipVolume(clip, vol);
                }
                clip.start();
                SoundHandle handle = new SoundHandle(clip);
                handle.setTargetVolume(vol);
                clip.addLineListener(e -> {
                    if (e.getType() == LineEvent.Type.STOP) {
                        clip.close();
                        activeSounds.remove(handle);
                    }
                });
                return handle;
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    /** Play a looping background track from assets/music (or /assets/music on classpath). */
    public static SoundHandle playMusic(String fileName) {
        return playMusic(fileName, 1.0f, 0);
    }

    public static SoundHandle playMusic(String fileName, float volume) {
        return playMusic(fileName, volume, 0);
    }

    /**
     * Loops a track seamlessly via javax.sound.sampled's Clip API. Tracks in
     * assets/music are .ogg (see OggMusic) or plain .wav. Returns a SoundHandle
     * whose stop() ends the loop.
     */
    public static SoundHandle playMusic(String fileName, float volume, int fadeInMs) {
        return playMusic(fileName, volume, fadeInMs, null);
    }

    /**
     * Plays a music track once (no loop) and runs {@code onEnd} on the Swing thread when it
     * reaches its end by itself -- not when it's stopped or paused by the M mute. Used to
     * play the themes one after another.
     */
    public static SoundHandle playMusicOnce(String fileName, float volume, int fadeInMs, Runnable onEnd) {
        return playMusic(fileName, volume, fadeInMs, onEnd == null ? () -> { } : onEnd);
    }

    private static SoundHandle playMusic(String fileName, float volume, int fadeInMs, Runnable onEndOrLoop) {
        if (!soundEnabled || fileName == null || fileName.trim().isEmpty()) return null;
        String name = fileName.trim();
        // the bundled themes are .ogg: played from the .wav made of them in the background
        File musicFile = name.toLowerCase().endsWith(".ogg") ? OggMusic.ready(name) : resolveFileFromMusic(name);
        if (musicFile == null) return null;

        try {
            AudioInputStream ain = AudioSystem.getAudioInputStream(musicFile.toURI().toURL());
            Clip clip = newClip();
            clip.open(ain);
            float base = volume;
            volume = Math.max(0f, Math.min(1f, base * musicGain()));
            if (fadeInMs > 0) {
                fadeClipIn(clip, volume, fadeInMs);
            } else {
                setClipVolume(clip, volume);
            }
            clip.setFramePosition(0);
            boolean loop = onEndOrLoop == null;
            SoundHandle handle = new SoundHandle(clip);
            if (loop) {
                clip.loop(Clip.LOOP_CONTINUOUSLY);
            } else {
                watchForEnd(clip, handle, onEndOrLoop);
                clip.start();
            }
            handle.reopen = () -> moveMusic(handle, musicFile, onEndOrLoop);
            handle.setLooping(loop);
            handle.setTargetVolume(volume);
            handle.baseVolume = base;
            musicHandles.add(handle);
            return handle;
        } catch (Exception e) {
            System.err.println("Music: couldn't play " + name + ": " + e);   // saved to app.log
            return null;
        }
    }

    /** A track played once: when it reaches its end by itself, the next one starts. */
    private static void watchForEnd(Clip clip, SoundHandle handle, Runnable onEnd) {
        clip.addLineListener(ev -> {
            if (ev.getType() != LineEvent.Type.STOP || handle.stopped || handle.silencedByGlobalMute) return;
            if (handle.clip != clip) return;                                    // moved to another device
            if (clip.getFramePosition() < clip.getFrameLength() - 1) return;   // not at the end
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (handle.stopped) return;
                handle.stop();
                onEnd.run();
            });
        });
    }

    /** Plays a music track on, from the same spot, on the output device chosen now. */
    private static void moveMusic(SoundHandle h, File musicFile, Runnable onEndOrLoop) {
        Clip old = h.clip;
        if (old == null || h.stopped) return;
        try {
            Clip clip = newClip();
            try (AudioInputStream ain = AudioSystem.getAudioInputStream(musicFile.toURI().toURL())) {
                clip.open(ain);
            }
            int at = h.silencedByGlobalMute ? h.pausedFrame : old.getFramePosition();
            setClipVolume(clip, h.targetVolume);
            clip.setFramePosition(Math.max(0, Math.min(at, clip.getFrameLength() - 1)));
            h.clip = clip;
            try { old.stop(); old.close(); } catch (Exception ignored) { }
            if (!h.looping) watchForEnd(clip, h, onEndOrLoop);
            if (!h.silencedByGlobalMute) {
                if (h.looping) clip.loop(Clip.LOOP_CONTINUOUSLY);
                else clip.start();
            }
        } catch (Exception e) {
            // the new device wouldn't open: the music stays where it was
        }
    }

    // ── output device (Settings > Audio) ─────────────────────────────────────

    static final String SYSTEM_DEFAULT = "System default";

    /** The output devices sound can go to, "System default" first. */
    static java.util.List<String> outputDevices() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(SYSTEM_DEFAULT);
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                if (mi.getName().startsWith("Port ")) continue;      // volume controls, not outputs
                if (!AudioSystem.getMixer(mi).isLineSupported(new Line.Info(Clip.class))) continue;
                if (!out.contains(mi.getName())) out.add(mi.getName());
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** The device picked in Settings (System default if it's gone). */
    static String outputDevice() {
        String name = AppSettings.str("audioOutput", SYSTEM_DEFAULT);
        return mixerNamed(name) == null ? SYSTEM_DEFAULT : name;
    }

    /** Sends all sound to {@code name} from now on; the music moves over straight away. */
    static void setOutputDevice(String name) {
        AppSettings.setStr("audioOutput", name == null ? SYSTEM_DEFAULT : name);
        reconnectOutput();
    }

    /** Opens the music again on the chosen device -- e.g. after connecting earphones. */
    static void reconnectOutput() {
        for (SoundHandle h : musicHandles.toArray(new SoundHandle[0])) {
            if (h.reopen != null) h.reopen.run();
        }
    }

    private static Mixer.Info mixerNamed(String name) {
        if (name == null || SYSTEM_DEFAULT.equals(name)) return null;
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) if (mi.getName().equals(name)) return mi;
        return null;
    }

    /**
     * A Clip on the chosen output device. "System default" means whatever the computer
     * is using right now: on Linux that's the "default" device (the sound server), which
     * follows Bluetooth earphones; Java's own pick there can be the speakers' hardware.
     */
    private static Clip newClip() throws LineUnavailableException {
        String name = AppSettings.str("audioOutput", SYSTEM_DEFAULT);
        Mixer.Info chosen = mixerNamed(name);
        if (chosen == null) {
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                if (mi.getName().startsWith("default")) { chosen = mi; break; }
            }
        }
        if (chosen != null) {
            try {
                return AudioSystem.getClip(chosen);
            } catch (Exception ignored) {
                // that device won't open: fall back to Java's own choice
            }
        }
        return AudioSystem.getClip();
    }

    /** Convenience stop — null-safe. */
    public static void stopSound(SoundHandle handle) {
        if (handle != null) handle.stop();
    }

    /** Stops every sound effect that's playing (the background music carries on). */
    public static void stopAllSounds() {
        for (SoundHandle handle : activeSounds.toArray(new SoundHandle[0])) {
            if (musicHandles.contains(handle)) continue;
            handle.stop();
        }
        activeSounds.removeIf(h -> !musicHandles.contains(h));
    }

    // ── sounds per piece set ───────────────────────────────────────────────
    // Classic: assets/sounds/wood/, Minimal: assets/sounds/soft/, Neo: the original
    // sounds. Only the board's own sounds change (move, capture, castle, check,
    // promotion, premove); game start/end, clock and illegal-move sounds are shared.

    private static String boardSoundFamily(SoundType t) {
        switch (t) {
            case MOVE_SELF: case CAPTURE: case CASTLE: case PROMOTE: case CHECK:
                return familyFolder();
            default:
                return null;
        }
    }

    private static String familyFolder() {
        String set;
        try { set = Piece.setName(); } catch (Throwable e) { set = "Neo"; }
        if ("Classic".equals(set)) return "wood/";
        if ("Minimal".equals(set)) return "soft/";
        return null;
    }

    /** The quiet "queued" click for a premove, in the piece set's own sound. */
    public static SoundHandle playPremove() {
        String family = familyFolder();
        if (family != null && soundEnabled && resolveFile(family + "premove.wav") != null) {
            File f = resolveFile(family + "premove.wav");
            return playResolvedFile("premove.wav", f);
        }
        return playExtraSound("premove.wav");
    }

    // ── File resolver ──────────────────────────────────────────────────────
    private static File resolveFile(String fileName) {
        String[] candidates = {
            "src/assets/sounds/" + fileName,
            "Scaccomatto/src/assets/sounds/" + fileName,
            "assets/sounds/" + fileName
        };
        for (String p : candidates) {
            File f = new File(p);
            if (f.exists()) return f;
        }
        try {
            URL res = SoundManager.class.getResource("/assets/sounds/" + fileName);
            if (res != null) {
                if (res.getProtocol().equals("file")) return new File(res.toURI());
                String ext = fileName.substring(fileName.lastIndexOf('.'));
                File tmp = File.createTempFile("chess_snd_", ext);
                tmp.deleteOnExit();
                try (java.io.InputStream in  = res.openStream();
                     java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
                    in.transferTo(out);
                }
                return tmp;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static File resolveFileFromExtra(String fileName) {
        String[] candidates = {
            "src/assets/extras/" + fileName,
            "Scaccomatto/src/assets/extras/" + fileName,
            "assets/extras/" + fileName
        };
        for (String p : candidates) {
            File f = new File(p);
            if (f.exists()) return f;
        }
        try {
            URL res = SoundManager.class.getResource("/assets/extras/" + fileName);
            if (res != null) {
                if (res.getProtocol().equals("file")) return new File(res.toURI());
                String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : ".tmp";
                File tmp = File.createTempFile("chess_extra_", ext);
                tmp.deleteOnExit();
                try (java.io.InputStream in  = res.openStream();
                     java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
                    in.transferTo(out);
                }
                return tmp;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** The music tracks shipped in assets/music (file names), for the Settings dropdown. */
    public static java.util.List<String> musicTracks() {
        java.util.TreeSet<String> out = new java.util.TreeSet<>();
        String[] dirs = {
            "src/main/assets/music", "Scaccomatto/src/main/assets/music",
            "src/assets/music", "assets/music"
        };
        for (String d : dirs) {
            File[] files = new File(d).listFiles((dir, n) -> isTrack(n));
            if (files != null) for (File f : files) out.add(f.getName());
        }
        try {
            URL res = SoundManager.class.getResource("/assets/music/");
            if (res != null && "file".equals(res.getProtocol())) {
                File[] files = new File(res.toURI()).listFiles((dir, n) -> isTrack(n));
                if (files != null) for (File f : files) out.add(f.getName());
            }
        } catch (Exception ignored) {
        }
        // Inside the installed app the tracks are packed in the .jar, where there's no folder to
        // list, so look for "Theme 1.ogg", "Theme 2.ogg", ... by name until one is missing.
        for (int n = 1; n <= 50; n++) {
            String name = "Theme " + n + ".ogg";
            if (SoundManager.class.getResource("/assets/music/" + name) == null) {
                name = "Theme " + n + ".wav";
                if (SoundManager.class.getResource("/assets/music/" + name) == null) break;
            }
            out.add(name);
        }
        if (out.isEmpty()) out.add("Theme 1.ogg");
        return new java.util.ArrayList<>(out);
    }

    private static boolean isTrack(String fileName) {
        String n = fileName.toLowerCase();
        return n.endsWith(".ogg") || n.endsWith(".wav");
    }

    private static File resolveFileFromMusic(String fileName) {
        String[] candidates = {
            "src/main/assets/music/" + fileName,
            "Scaccomatto/src/main/assets/music/" + fileName,
            "src/assets/music/" + fileName,
            "Scaccomatto/src/assets/music/" + fileName,
            "assets/music/" + fileName
        };
        for (String p : candidates) {
            File f = new File(p);
            if (f.exists()) return f;
        }
        try {
            URL res = SoundManager.class.getResource("/assets/music/" + fileName);
            if (res != null) {
                if (res.getProtocol().equals("file")) return new File(res.toURI());
                String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : ".tmp";
                File tmp = File.createTempFile("chess_music_", ext);
                tmp.deleteOnExit();
                try (java.io.InputStream in  = res.openStream();
                     java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
                    in.transferTo(out);
                }
                return tmp;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static File resolveFileFromSpellChessSounds(String fileName) {
        String[] candidates = {
            "src/assets/spell chess sounds/" + fileName,
            "Scaccomatto/src/assets/spell chess sounds/" + fileName,
            "assets/spell chess sounds/" + fileName
        };
        for (String p : candidates) {
            File f = new File(p);
            if (f.exists()) return f;
        }
        try {
            URL res = SoundManager.class.getResource("/assets/spell chess sounds/" + fileName);
            if (res != null) {
                if (res.getProtocol().equals("file")) return new File(res.toURI());
                String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : ".tmp";
                File tmp = File.createTempFile("chess_spell_snd_", ext);
                tmp.deleteOnExit();
                try (java.io.InputStream in  = res.openStream();
                     java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
                    in.transferTo(out);
                }
                return tmp;
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ── MP3 via OS native player ───────────────────────────────────────────
    private static void setClipVolume(Clip clip, float volume) {
        if (clip == null) return;
        float vol = Math.max(0f, Math.min(1f, volume));
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float db = (vol <= 0f) ? gain.getMinimum() : (float) (20.0 * Math.log10(vol));
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
            } else if (clip.isControlSupported(FloatControl.Type.VOLUME)) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.VOLUME);
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), vol)));
            }
        } catch (Exception ignored) {
        }
    }

    private static void fadeClipIn(Clip clip, float targetVolume, int fadeInMs) {
        if (clip == null) return;
        float target = Math.max(0f, Math.min(1f, targetVolume));
        int duration = Math.max(1, fadeInMs);
        setClipVolume(clip, 0.01f);
        javax.swing.Timer fadeTimer = AnimationTiming.createUiTimer(null);
        long startedAtNanos = System.nanoTime();
        fadeTimer.addActionListener(e -> {
            float t = AnimationTiming.progress(startedAtNanos, duration);
            float eased = (float) (0.5 - 0.5 * Math.cos(Math.PI * t));
            setClipVolume(clip, Math.max(0.01f, target * eased));
            if (t >= 1f) {
                setClipVolume(clip, target);
                fadeTimer.stop();
            }
        });
        fadeTimer.start();
    }

    private static Process startMP3Process(File file) {
        return startMP3Process(file, 1.0f);
    }

    private static Process startMP3Process(File file, float volume) {
        return startMP3Process(file, volume, 0);
    }

    private static Process startMP3Process(File file, float volume, int fadeInMs) {
        String abs = file.getAbsolutePath();
        String os  = System.getProperty("os.name", "").toLowerCase();
        String[] cmd;
        float vol = Math.max(0f, Math.min(1f, volume));
        float fadeSeconds = Math.max(0f, fadeInMs / 1000f);

        try {
            if (os.contains("win")) {
                String ps = String.format(
                    "Add-Type -AssemblyName presentationCore;" +
                    "$m=New-Object System.Windows.Media.MediaPlayer;" +
                    "$m.Open([uri]'%s');" +
                    "$target=%s;" +
                    "$fadeMs=%d;" +
                    "$m.Volume=if($fadeMs -gt 0){0.0}else{$target};" +
                    "$m.Play();" +
                    "if($fadeMs -gt 0){" +
                    "  $steps=16;" +
                    "  for($i=1;$i -le $steps;$i++){" +
                    "    $t=$i/$steps;" +
                    "    $m.Volume=$target*(0.5-0.5*[Math]::Cos([Math]::PI*$t));" +
                    "    Start-Sleep -Milliseconds ([int]($fadeMs/$steps));" +
                    "  }" +
                    "  $m.Volume=$target;" +
                    "}" +
                    "Start-Sleep 1;" +
                    "$d=$m.NaturalDuration;" +
                    "$secs=if($d.HasTimeSpan){[int]$d.TimeSpan.TotalSeconds+1}else{10};" +
                    "Start-Sleep $secs;" +
                    "$m.Close()",
                    abs.replace("\\", "/").replace("'", "\\'"),
                    String.format(java.util.Locale.US, "%.3f", vol),
                    Math.max(0, fadeInMs)
                );
                cmd = new String[]{"powershell", "-WindowStyle", "Hidden", "-Command", ps};
            } else if (os.contains("mac")) {
                cmd = new String[]{"afplay", "-v", String.format(java.util.Locale.US, "%.3f", vol), abs};
            } else {
                if (fadeInMs > 0 && cmdExists("ffplay")) {
                    cmd = new String[]{
                        "ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet",
                        "-volume", String.valueOf(Math.round(100 * vol)),
                        "-af", "afade=t=in:st=0:d=" + String.format(java.util.Locale.US, "%.3f", fadeSeconds),
                        abs
                    };
                }
                else if (cmdExists("mpg123"))  cmd = new String[]{"mpg123", "-q", "-f", String.valueOf(Math.round(32768 * vol)), abs};
                else if (cmdExists("ffplay"))  cmd = new String[]{"ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet", "-volume", String.valueOf(Math.round(100 * vol)), abs};
                else if (cmdExists("cvlc"))    cmd = new String[]{"cvlc", "--intf", "dummy", "--play-and-exit", "--gain", String.format(java.util.Locale.US, "%.3f", vol), abs};
                else return null;
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            // drain stdout so the process never blocks on a full pipe
            new Thread(() -> {
                try { proc.getInputStream().transferTo(java.io.OutputStream.nullOutputStream()); }
                catch (Exception ignored) {}
            }).start();
            return proc;

        } catch (Exception ignored) { return null; }
    }

    private static boolean cmdExists(String cmd) {
        try { return new ProcessBuilder("which", cmd).start().waitFor() == 0; }
        catch (Exception e) { return false; }
    }

    // ── Misc ───────────────────────────────────────────────────────────────
    // Same as the M key: also silences / restores sounds already playing.
    public static void setSoundEnabled(boolean enabled) {
        if (enabled == soundEnabled) return;
        toggleMuted();
    }
    public static boolean isSoundEnabled()              { return soundEnabled; }

    /**
     * Global mute toggle, wired to the "M" keyboard shortcut (see
     * Main.java's global key dispatcher) and reflected by MuteIndicator's
     * on-screen badge. Returns true if sound is now MUTED.
     *
     * Previously this called SoundHandle.stop() on every active sound when
     * muting, which for a one-shot effect is fine but for a looping Clip
     * (the menu's background music, played via playMusic()'s
     * LOOP_CONTINUOUSLY) is permanent -- stop() closes the Clip outright,
     * and nothing was listening for the mute key to start it again, so
     * pressing M to mute killed the menu music for the rest of the
     * session: the next M press flipped soundEnabled back to true (so new
     * sounds like button clicks worked fine) but the music itself never
     * came back, which is what "mute works once, then never unmutes" was
     * describing. Now every active sound's applyGlobalMute() is called on
     * BOTH mute and unmute: a Clip is silenced in place (gain to minimum)
     * rather than closed, so a looping one keeps looping inaudibly and
     * simply becomes audible again -- still in sync -- when gain is
     * restored on unmute; a Process-backed one-shot (nothing loops through
     * that path) is still just stopped on mute, since it isn't meant to
     * resume anyway.
     */
    public static boolean toggleMuted() {
        soundEnabled = !soundEnabled;
        saveMuted();
        boolean nowMuted = !soundEnabled;
        for (SoundHandle h : activeSounds) {
            h.applyGlobalMute(nowMuted);
        }
        // e.g. start the menu music if the app was opened muted (it never
        // started, so there was nothing to un-silence).
        Runnable hook = onUnmuted;
        if (!nowMuted && hook != null) hook.run();
        return nowMuted;
    }

    private static volatile Runnable onUnmuted;

    public static void setOnUnmuted(Runnable hook) { onUnmuted = hook; }

    public static boolean isMuted() { return !soundEnabled; }

    public static void playMoveSound(boolean isCapture, boolean isCastle,
                                     boolean isPromotion, boolean isCheck) {
        if      (isCheck)      playSound(SoundType.CHECK);
        else if (isPromotion)  playSound(SoundType.PROMOTE);
        else if (isCastle)     playSound(SoundType.CASTLE);
        else if (isCapture)    playSound(SoundType.CAPTURE);
        else                   playSound(SoundType.MOVE_SELF);
    }
}
