import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The menu music ships as small .ogg files (a sixth of the size of .wav) but Java can only
 * play plain .wav. So each track is turned back into a .wav once, in the background, and
 * kept in ~/.scaccomatto/music; after that it plays exactly as before.
 *
 * <p>Decoding uses JOrbis (LGPL, from JCraft).
 */
final class OggMusic {
    private OggMusic() { }

    private static final File DIR = new File(new File(System.getProperty("user.home"), ".scaccomatto"), "music");
    /** Track name ("Theme 1.ogg") -> its ready .wav. */
    private static final Map<String, File> READY = new ConcurrentHashMap<>();
    private static volatile boolean started;

    /** Starts preparing every track in the background, the first ones first. */
    static void prepareAll(java.util.List<String> tracks) {
        if (started) return;
        started = true;
        Thread t = new Thread(() -> {
            for (String name : tracks) {
                if (!name.toLowerCase().endsWith(".ogg")) continue;
                try {
                    prepare(name);
                    // a track that wasn't ready when the music wanted to start can start now
                    Runnable r = onReady;
                    if (r != null) r.run();
                } catch (Exception | LinkageError e) {
                    // that track won't play; saved to app.log (Settings > About > Problem reports)
                    System.err.println("Music: couldn't prepare " + name + ": " + e);
                }
            }
        }, "music-prepare");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static volatile Runnable onReady;

    /** Runs (on the preparing thread) each time another track becomes playable. */
    static void onReady(Runnable r) { onReady = r; }

    /** The playable .wav for a track, or null if it isn't ready (yet). */
    static File ready(String name) {
        File f = READY.get(name);
        return f != null && f.isFile() ? f : null;
    }

    private static void prepare(String name) throws IOException {
        long size = resourceSize(name);
        if (size <= 0) return;
        // the file name carries the .ogg's size, so a changed track is made again
        String base = name.substring(0, name.length() - 4);
        File wav = new File(DIR, base + "." + size + ".wav");
        if (!wav.isFile()) {
            DIR.mkdirs();
            File tmp = new File(DIR, base + "." + size + ".part");
            try (InputStream in = openResource("/assets/music/" + name)) {
                if (in == null) return;
                decode(in, tmp);
            }
            try {
                Files.move(tmp.toPath(), wav.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveFailed) {
                // (Windows: a virus scanner can hold the new file for a moment) copy it instead
                Files.copy(tmp.toPath(), wav.toPath(), StandardCopyOption.REPLACE_EXISTING);
                tmp.delete();
            }
        }
        READY.put(name, wav);
    }

    private static long resourceSize(String name) throws IOException {
        try (InputStream in = openResource("/assets/music/" + name)) {
            if (in == null) return -1;
            long n = 0;
            byte[] buf = new byte[1 << 16];
            for (int r; (r = in.read(buf)) > 0; ) n += r;
            return n;
        }
    }

    /** Ogg Vorbis in, 16-bit PCM .wav out. */
    static void decode(InputStream in, File out) throws IOException {
        SyncState oy = new SyncState();
        StreamState os = new StreamState();
        Page og = new Page();
        Packet op = new Packet();
        Info vi = new Info();
        Comment vc = new Comment();
        DspState vd = new DspState();
        Block vb = new Block(vd);
        oy.init();

        try (RandomAccessFile wav = new RandomAccessFile(out, "rw")) {
            wav.setLength(0);
            wav.write(new byte[44]);                 // header, filled in at the end

            // the three header packets
            if (!feed(oy, in) || oy.pageout(og) != 1) throw new IOException("not an Ogg file");
            os.init(og.serialno());
            vi.init();
            vc.init();
            if (os.pagein(og) < 0 || os.packetout(op) != 1 || vi.synthesis_headerin(vc, op) < 0) {
                throw new IOException("not Vorbis audio");
            }
            int headers = 1;
            while (headers < 3) {
                int r = oy.pageout(og);
                if (r == 1) {
                    os.pagein(og);
                    while (headers < 3) {
                        r = os.packetout(op);
                        if (r == 0) break;
                        if (r < 0) throw new IOException("broken Vorbis header");
                        vi.synthesis_headerin(vc, op);
                        headers++;
                    }
                } else if (r == 0 && !feed(oy, in)) {
                    throw new IOException("file ended in the header");
                }
            }

            vd.synthesis_init(vi);
            vb.init(vd);
            int channels = vi.channels;
            float[][][] pcmHolder = new float[1][][];
            int[] index = new int[channels];
            byte[] out16 = new byte[8192 * 2 * channels];
            long dataBytes = 0;

            boolean eos = false;
            while (!eos) {
                int r = oy.pageout(og);
                if (r == 0) {
                    if (!feed(oy, in)) break;
                    continue;
                }
                if (r < 0) continue;                  // a gap in the data: skip it
                os.pagein(og);
                while (true) {
                    r = os.packetout(op);
                    if (r == 0) break;
                    if (r < 0) continue;
                    if (vb.synthesis(op) == 0) vd.synthesis_blockin(vb);
                    int samples;
                    while ((samples = vd.synthesis_pcmout(pcmHolder, index)) > 0) {
                        float[][] pcm = pcmHolder[0];
                        int n = Math.min(samples, out16.length / (2 * channels));
                        int p = 0;
                        for (int s = 0; s < n; s++) {
                            for (int c = 0; c < channels; c++) {
                                int v = Math.round(pcm[c][index[c] + s] * 32767f);
                                if (v > 32767) v = 32767;
                                if (v < -32768) v = -32768;
                                out16[p++] = (byte) v;
                                out16[p++] = (byte) (v >> 8);
                            }
                        }
                        wav.write(out16, 0, p);
                        dataBytes += p;
                        vd.synthesis_read(n);
                    }
                }
                if (og.eos() != 0) eos = true;
            }

            writeHeader(wav, vi.rate, channels, dataBytes);
        } finally {
            os.clear();
            vb.clear();
            vd.clear();
            vi.clear();
            oy.clear();
        }
    }

    private static volatile boolean held;

    /** While held, making the music files waits (so a busy animation gets the computer to itself). */
    static void hold(boolean on) { held = on; }

    /** Reads the next chunk of the file into the Ogg reader. False at the end of the file. */
    private static boolean feed(SyncState oy, InputStream in) throws IOException {
        for (int waited = 0; held && waited < 5000; waited += 20) {
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        int at = oy.buffer(4096);
        int n = in.read(oy.data, at, 4096);
        if (n <= 0) {
            oy.wrote(0);
            return false;
        }
        oy.wrote(n);
        return true;
    }

    private static void writeHeader(RandomAccessFile wav, int rate, int channels, long dataBytes) throws IOException {
        wav.seek(0);
        int byteRate = rate * channels * 2;
        wav.writeBytes("RIFF");
        wav.writeInt(Integer.reverseBytes((int) (36 + dataBytes)));
        wav.writeBytes("WAVEfmt ");
        wav.writeInt(Integer.reverseBytes(16));
        wav.writeShort(Short.reverseBytes((short) 1));            // plain PCM
        wav.writeShort(Short.reverseBytes((short) channels));
        wav.writeInt(Integer.reverseBytes(rate));
        wav.writeInt(Integer.reverseBytes(byteRate));
        wav.writeShort(Short.reverseBytes((short) (channels * 2)));
        wav.writeShort(Short.reverseBytes((short) 16));
        wav.writeBytes("data");
        wav.writeInt(Integer.reverseBytes((int) dataBytes));
    }

    /** Finds a bundled file, inside the app or (when run from the project) on disk. */
    private static InputStream openResource(String path) throws IOException {
        InputStream in = OggMusic.class.getResourceAsStream(path);
        if (in != null) return in;
        for (String root : new String[] {"src/main", "Scaccomatto/src/main", "src"}) {
            File f = new File(root + path);
            if (f.isFile()) return Files.newInputStream(f.toPath());
        }
        return null;
    }
}
