import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RescaleOp;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Board extends JPanel implements MouseListener, MouseMotionListener {
    
    // ===== Chess.com style arrows (square -> square) =====
private boolean drawingArrow = false;
private int arrowFromRow = -1, arrowFromCol = -1;
private int arrowToRow = -1, arrowToCol = -1;

// ===== Red square highlights =====
private static class MarkedSquare {
    int row, col;
    boolean green;
    MarkedSquare(int r, int c) {
        row = r;
        col = c;
    }
}

    /** The arrows and marked squares drawn right now, as an opaque snapshot (analysis keeps one per move). */
    public Object getDrawings() {
        java.util.List<Object> out = new java.util.ArrayList<>();
        for (Arrow a : arrows) out.add(new int[]{0, a.fromRow, a.fromCol, a.toRow, a.toCol, a.green ? 1 : 0});
        for (MarkedSquare m : markedSquares) out.add(new int[]{1, m.row, m.col, 0, 0, m.green ? 1 : 0});
        return out;
    }

    /** Put back drawings from {@link #getDrawings()}; null clears them. */
    @SuppressWarnings("unchecked")
    public void setDrawings(Object snapshot) {
        arrows.clear();
        markedSquares.clear();
        drawingArrow = false;
        if (snapshot instanceof java.util.List) {
            for (Object o : (java.util.List<Object>) snapshot) {
                int[] d = (int[]) o;
                if (d[0] == 0) arrows.add(new Arrow(d[1], d[2], d[3], d[4], d[5] == 1));
                else { MarkedSquare m = new MarkedSquare(d[1], d[2]); m.green = d[5] == 1; markedSquares.add(m); }
            }
        }
        repaint();
    }

    public boolean hasDrawings() { return !arrows.isEmpty() || !markedSquares.isEmpty(); }

private ArrayList<MarkedSquare> markedSquares = new ArrayList<>();


	    private static class Arrow {
	    int fromRow, fromCol;
	    int toRow, toCol;
        boolean green;

	    Arrow(int fr, int fc, int tr, int tc, boolean green) {
	        fromRow = fr;
	        fromCol = fc;
	        toRow = tr;
	        toCol = tc;
            this.green = green;
	    }
	}

private ArrayList<Arrow> arrows = new ArrayList<>();

    private static class AtomicExplosionFx {
        int centerRow, centerCol;
        long startMs;
        long durationMs;
        AtomicExplosionFx(int row, int col, long now, long durationMs) {
            this.centerRow = row;
            this.centerCol = col;
            this.startMs = now;
            this.durationMs = durationMs;
        }
    }

    private static class FireballImpactFx {
        int row, col;
        long startMs;
        long durationMs;
        Piece victimPiece;
        float releaseT;
        boolean shadowTopToBottom;
        boolean cloudOnly;
        float cloudScale = 1f;
        FireballImpactFx(int row, int col, long now, long durationMs, Piece victimPiece, boolean shadowTopToBottom) {
            this.row = row;
            this.col = col;
            this.startMs = now;
            this.durationMs = durationMs;
            this.victimPiece = victimPiece;
            this.releaseT = FIREBALL_DROP_START_T;
            this.shadowTopToBottom = shadowTopToBottom;
        }
    }

    private static class BomberCaptureFx {
        Piece rook;
        int fromRow, fromCol;
        int toRow, toCol;
        long startMs;
        boolean rushing;
        float rushProgress;
    }

    private static class ShieldVisualFx {
        Piece piece;
        int anchorRow, anchorCol;
        long startMs;
        long durationMs;
        boolean fadeIn;
        ShieldVisualFx(Piece piece, int row, int col, long now, long durationMs, boolean fadeIn) {
            this.piece = piece;
            this.anchorRow = row;
            this.anchorCol = col;
            this.startMs = now;
            this.durationMs = durationMs;
            this.fadeIn = fadeIn;
        }
    }

    private static class FreezeVisualFx {
        Piece piece;
        int anchorRow, anchorCol;
        long startMs;
        long durationMs;
        boolean fadeIn;
        FreezeVisualFx(Piece piece, int row, int col, long now, long durationMs, boolean fadeIn) {
            this.piece = piece;
            this.anchorRow = row;
            this.anchorCol = col;
            this.startMs = now;
            this.durationMs = durationMs;
            this.fadeIn = fadeIn;
        }
    }

    private static class FreezeBurstFx {
        Piece piece;
        int row, col;
        long startMs;

        FreezeBurstFx(Piece piece, int row, int col, long startMs) {
            this.piece = piece;
            this.row = row;
            this.col = col;
            this.startMs = startMs;
        }
    }

    private static class SpellCastCardFx {
        String spellId;
        String spellName;
        int targetRow, targetCol;
        long startMs;
        BufferedImage art;

        SpellCastCardFx(String spellId, String spellName, int row, int col, long now, BufferedImage art) {
            this.spellId = spellId;
            this.spellName = spellName;
            this.targetRow = row;
            this.targetCol = col;
            this.startMs = now;
            this.art = art;
        }
    }

    private static class PieceExplosionShard {
        int sx, sy, sw, sh;
        double vx, vy;
        double spin;
        double angleOffset;
        int[] px;
        int[] py;
        int pcount;
    }

    private static class PieceExplosionFx {
        Piece piece;
        int row, col;
        long startMs;
        long shatterStartMs;
        long preDurationMs;
        long shatterDurationMs;
        boolean shatterStarted;
        BufferedImage pieceSprite;
        BufferedImage shatterSprite;
        long shakeSeed;
        String shatterSoundFile;
        ArrayList<PieceExplosionShard> shards = new ArrayList<>();
    }

    private static class UrielResurrectionFx {
        Piece piece;
        int row, col;
        long startMs;
        long durationMs;
        UrielResurrectionFx(Piece piece, int row, int col, long now, long durationMs) {
            this.piece = piece;
            this.row = row;
            this.col = col;
            this.startMs = now;
            this.durationMs = durationMs;
        }
    }

    private static class PendingFireballVictim {
        Piece piece;
        int row, col;
        PendingFireballVictim(Piece piece, int row, int col) {
            this.piece = piece;
            this.row = row;
            this.col = col;
        }
    }

    private final ArrayList<AtomicExplosionFx> atomicExplosionFx = new ArrayList<>();
    private final ArrayList<FireballImpactFx> fireballImpactFx = new ArrayList<>();
    private final ArrayList<ShieldVisualFx> shieldVisualFx = new ArrayList<>();
    private final ArrayList<FreezeVisualFx> freezeVisualFx = new ArrayList<>();
    private final ArrayList<FreezeBurstFx> freezeBurstFx = new ArrayList<>();
    private final ArrayList<PieceExplosionFx> pieceExplosionFx = new ArrayList<>();
    private final ArrayList<UrielResurrectionFx> urielResurrectionFx = new ArrayList<>();
    private final ArrayList<BufferedImage> explosionOverlayFrames = new ArrayList<>();
    private boolean explosionOverlayFramesLoaded = false;
    private double explosionOverlayMaxAspectRatio = 1.0;
    private Timer atomicExplosionTimer;
    private volatile SoundManager.SoundHandle spellChessCrashSoundHandle;
    private final ArrayList<Timer> scheduledSpellSoundTimers = new ArrayList<>();
    private static final int FREEZE_STREAM_BEATS = 5;
    private static final long FREEZE_STREAM_MS = 1450L;
    private static final long FREEZE_STREAM_PARTICLE_INTERVAL_MS = 52L;
    private static final long FREEZE_STREAM_TRAVEL_MS = 420L;
    private static final long FREEZE_CRYSTAL_GROW_MS = 620L;
    private static final long FREEZE_BURST_MS =
            FREEZE_STREAM_MS + FREEZE_STREAM_TRAVEL_MS + FREEZE_CRYSTAL_GROW_MS;
    
    // Cache for fireball impact overlay to avoid per-frame allocations
    private BufferedImage cachedImpactOverlay = null;
    private BufferedImage cachedImpactBlurOverlay = null;
    private int cachedOverlayWidth = -1, cachedOverlayHeight = -1;
    private ConvolveOp cachedImpactBlurOp = null;
    
    // Cache for blurred explosion overlay frames
    private final HashMap<Integer, BufferedImage> blurredExplosionFrameCache = new HashMap<>();
    private static final float EXPLOSION_OVERLAY_BLUR_RADIUS = 5.0f;
    
    private Timer threeCheckRingTimer;
    private Timer globalFxRepaintTimer;
    private long lastCheckEvalMs = 0L;
    private boolean cachedWhiteInCheck = false;
    private boolean cachedBlackInCheck = false;
    private final Color[][] renderedSquareColors = new Color[8][8];
    private final Color[][] squareColorStarts = new Color[8][8];
    private final Color[][] squareColorTargets = new Color[8][8];
    private final long[][] squareColorStartedAt = new long[8][8];
    private Timer squareColorTransitionTimer;
    private static final int SQUARE_COLOR_TRANSITION_MS = 140;
    private int glowingSelectedRow = -1;
    private int glowingSelectedCol = -1;
    private long selectedGlowStartedAt = 0L;
    private static final Color CHECK_SQUARE_COLOR = new Color(224, 58, 64);
    private static final long CHECK_EVAL_THROTTLE_MS = 120L;
    private Rectangle lastExplosionDirtyBounds = null;
    private static final int FX_TIMER_DELAY_MS = AnimationTiming.FRAME_DELAY_MS;
    private static final int GLOBAL_FX_REPAINT_DELAY_MS = AnimationTiming.FRAME_DELAY_MS;
    private static final int THREE_CHECK_RING_TIMER_DELAY_MS = AnimationTiming.FRAME_DELAY_MS;
    private static final long EXPLOSION_FRAME_MS = AnimationTiming.FRAME_DELAY_MS;
    private static final int MAX_EXPLOSION_OVERLAY_FRAMES = 45;
    private static final int MAX_EXPLOSION_FRAME_EDGE = 480;
    private static final long EXPLOSION_FADE_TAIL_MS = 500L;
    private static final int ATOMIC_FX_TILE_SPAN = 3;
    private static final float FIREBALL_GLOBAL_SPEED_SCALE = 0.80f; // 0.8x overall nuke speed
    private static final float FIREBALL_BASE_TRAVEL_SPEED_SCALE = 0.75f; // original pace baseline
    private static final long FIREBALL_OVERLAY_DURATION_MS = 3200L;
    private static final float FIREBALL_TRAVEL_SPEED_SCALE = FIREBALL_BASE_TRAVEL_SPEED_SCALE * FIREBALL_GLOBAL_SPEED_SCALE; // scales travel with global speed
    private static final float FIREBALL_OVERLAY_SCALE = 0.577f; // ~1/3 area
    private static final float FIREBALL_DROP_START_T = 0.35f;
    private static final float FIREBALL_IMPACT_T = 0.62f;
    private static final int FIREBALL_START_DELAY_MS = Math.max(1, Math.round(1000f / FIREBALL_GLOBAL_SPEED_SCALE));
    private static final long FIREBALL_IMPACT_FLASH_FADE_IN_MS = 100L;
    private static final long FIREBALL_IMPACT_FLASH_HOLD_MS = 0L;
    private static final long FIREBALL_IMPACT_FLASH_OUT_MS = 400L;
    private static final long FIREBALL_IMPACT_FLASH_DURATION_MS =
        FIREBALL_IMPACT_FLASH_FADE_IN_MS + FIREBALL_IMPACT_FLASH_HOLD_MS + FIREBALL_IMPACT_FLASH_OUT_MS;
    private static final long FIREBALL_LOCAL_FLASH_DURATION_MS = 180L;
    private static final long FIREBALL_LINE_FADE_START_MS = 1200L; // after flash core ends
    private static final long FIREBALL_LINE_FADE_DURATION_MS = 550L; // faster CRT-style close
    private static final long FIREBALL_FLASH_SHAKE_BRIEF_MS = 280L;
    private static final float FIREBALL_FLASH_LINE_MAX_THICKNESS_SQ = 0.115f;
    private static final float FIREBALL_FLASH_LINE_MIN_THICKNESS_PX = 0.22f;
    private static final float FIREBALL_FLASH_START_SCALE = 0.60f;
    private static final float FIREBALL_FLASH_START_ALPHA = 0.70f;
    private static final float FIREBALL_CORNER_VIGNETTE_MAX_ALPHA = 0.62f;
    private static final long FIREBALL_VIGNETTE_FADE_IN_MS = 500L;
    
    private static final long FIREBALL_VIGNETTE_FADE_OUT_MS = 1500L;
    private static final float[] FIREBALL_YELLOW_TINT_SCALES = new float[] {1.00f, 0.88f, 0.26f, 1.0f};
    private static final float[] FIREBALL_YELLOW_TINT_OFFSETS = new float[] {10f, 8f, 0f, 0f};
    private static final float[] FIREBALL_RED_TINT_SCALES = new float[] {1.00f, 0.30f, 0.22f, 1.0f};
    private static final float[] FIREBALL_RED_TINT_OFFSETS = new float[] {4f, 0f, 0f, 0f};
    private static final float[] FIREBALL_YELLOW_LAYER_SCALES = new float[] {1.18f, 1.30f, 1.46f};
    private static final float[] FIREBALL_YELLOW_LAYER_ALPHAS = new float[] {0.34f, 0.24f, 0.15f};
    private static final float[] FIREBALL_RED_LAYER_SCALES = new float[] {1.58f, 1.76f, 1.94f};
    private static final float[] FIREBALL_RED_LAYER_ALPHAS = new float[] {0.20f, 0.13f, 0.08f};
    private static final RescaleOp FIREBALL_YELLOW_TINT = new RescaleOp(
        FIREBALL_YELLOW_TINT_SCALES, FIREBALL_YELLOW_TINT_OFFSETS, null
    );
    private static final RescaleOp FIREBALL_RED_TINT = new RescaleOp(
        FIREBALL_RED_TINT_SCALES, FIREBALL_RED_TINT_OFFSETS, null
    );
    private static final RescaleOp FIREBALL_SMOKE_WHITE_TINT = new RescaleOp(
        new float[] {0.0f, 0.0f, 0.0f, 1.0f},
        new float[] {255f, 255f, 255f, 0f},
        null
    );
    private static final RescaleOp FIREBALL_SMOKE_YELLOW_SILHOUETTE_TINT = new RescaleOp(
        new float[] {0.0f, 0.0f, 0.0f, 1.0f},
        new float[] {255f, 214f, 46f, 0f},
        null
    );
    private static final RescaleOp FIREBALL_SMOKE_WARM_TINT = new RescaleOp(
        new float[] {1.06f, 0.92f, 0.78f, 1.0f},
        new float[] {14f, 6f, 0f, 0f},
        null
    );
    private static final RescaleOp FIREBALL_SMOKE_GRAY_TINT = new RescaleOp(
        new float[] {0.62f, 0.62f, 0.62f, 1.0f},
        new float[] {6f, 6f, 6f, 0f},
        null
    );
    private static final RescaleOp FIREBALL_SMOKE_BLACK_TINT = new RescaleOp(
        new float[] {0f, 0f, 0f, 1.0f},
        new float[] {0f, 0f, 0f, 0f},
        null
    );
    private static final RescaleOp FIREBALL_SMALL_SMOKE_DARK_TINT = new RescaleOp(
        new float[] {0.48f, 0.46f, 0.46f, 1.0f},
        new float[] {3f, 3f, 3f, 0f},
        null
    );

    private static final int SMOKE_TINT_CACHE_STEPS = 48;
    private static final Map<String, RescaleOp> SMOKE_TINT_CACHE = new HashMap<>();

    private static RescaleOp interpolateTint(String cachePrefix, RescaleOp a, RescaleOp b, float t) {
        int step = Math.max(0, Math.min(SMOKE_TINT_CACHE_STEPS, Math.round(t * SMOKE_TINT_CACHE_STEPS)));
        String key = cachePrefix + ":" + step;
        RescaleOp cached = SMOKE_TINT_CACHE.get(key);
        if (cached != null) return cached;

        RescaleOp created = createInterpolatedTint(a, b, step / (float) SMOKE_TINT_CACHE_STEPS);
        SMOKE_TINT_CACHE.put(key, created);
        return created;
    }

    private static void warmSmokeTintCache() {
        for (int step = 0; step <= SMOKE_TINT_CACHE_STEPS; step++) {
            float t = step / (float) SMOKE_TINT_CACHE_STEPS;
            interpolateTint("small-white-warm", FIREBALL_SMOKE_WHITE_TINT, FIREBALL_SMOKE_WARM_TINT, t);
            interpolateTint("small-warm-gray", FIREBALL_SMOKE_WARM_TINT, FIREBALL_SMOKE_GRAY_TINT, t);
            interpolateTint("main-white-warm", FIREBALL_SMOKE_WHITE_TINT, FIREBALL_SMOKE_WARM_TINT, t);
            interpolateTint("main-warm-gray", FIREBALL_SMOKE_WARM_TINT, FIREBALL_SMOKE_GRAY_TINT, t);
        }
    }

    private static RescaleOp createInterpolatedTint(RescaleOp a, RescaleOp b, float t) {
        float[] scalesA = a.getScaleFactors(new float[4]);
        float[] offsetsA = a.getOffsets(new float[4]);
        float[] scalesB = b.getScaleFactors(new float[4]);
        float[] offsetsB = b.getOffsets(new float[4]);
        float[] scales = new float[4];
        float[] offsets = new float[4];
        for (int i = 0; i < 4; i++) {
            scales[i] = scalesA[i] + t * (scalesB[i] - scalesA[i]);
            offsets[i] = offsetsA[i] + t * (offsetsB[i] - offsetsA[i]);
        }
        return new RescaleOp(scales, offsets, null);
    }
    private static final float FIREBALL_SHAKE_MAX_SQUARE_FACTOR = 0.08f;
    private static final float FIREBALL_NUKE_TRAVEL_BOOST = 2.24f; // 2x faster than previous tweak.
    private static final float FIREBALL_BOMBER_SPEED_MULT = 1.65f; // Tiny extra speed bump for bomber silhouette.
    private static final float FIREBALL_FLASH_WHITE_CORE_SCALE = 1.02f; // Tiny reduction to white core size.
    private static final boolean FIREBALL_USE_FULL_SCREEN_IMPACT_BLUR = false; // Full-screen blur is expensive and can cause visible frame stepping.
    private static final long FIREBALL_SMOKE_DURATION_MS = 3200L;
    private static final long FIREBALL_SMALL_SMOKE_DURATION_MS = 10600L; // +0.6s linger for bottom black cloud
    private static final long FIREBALL_SMOKE_DELAY_MS = 100L;
    private static final long FIREBALL_SMALL_SMOKE_DELAY_MS = 90L;
    private static final float FIREBALL_SMOKE_FADE_IN_WHITE_END_T = 0.15625f; // 500ms / 3200ms
    private static final float FIREBALL_SMOKE_HOLD_WHITE_END_T = 0.3125f; // 1000ms / 3200ms
    private static final float FIREBALL_SMOKE_TO_WARM_END_T = 0.71875f; // 2300ms / 3200ms
    private static final float FIREBALL_SMOKE_TO_GRAY_END_T = 1.03125f; // 3300ms / 3200ms
    private static final float FIREBALL_SMOKE_FADE_START_T = 0.875f; // 2800ms / 3200ms
    private static final float FIREBALL_SMALL_SMOKE_FADE_IN_WHITE_END_T = 0.05f; // 500ms / 10000ms
    private static final float FIREBALL_SMALL_SMOKE_HOLD_WHITE_END_T = 0.10f; // 1000ms / 10000ms
    private static final float FIREBALL_SMALL_SMOKE_TO_WARM_END_T = 0.23f; // 2300ms / 10000ms
    private static final float FIREBALL_SMALL_SMOKE_TO_GRAY_END_T = 0.33f; // 3300ms / 10000ms
    private static final float FIREBALL_SMALL_SMOKE_FADE_START_T = 0.22f; // start fading earlier so black cloud tails off sooner
    private static final float FIREBALL_CLOUD_SPRITE_SCALE_MULT = 1.30f; // 130% cloud sprite size
    private static final float FIREBALL_CLOUD_MOTION_RATE_MULT = 1.30f; // keep motion proportional to larger sprites
    private static final float FIREBALL_YELLOW_CLOUD_SCALE_MULT = 0.80f; // 80% of current yellow cloud size
    private static final float FIREBALL_BOTTOM_CLOUD_SCALE_MULT = 2.25f; // 150% of current bottom cloud size (1.50x previous 1.50x)
    private static final float FIREBALL_SMOKE_RISE_SQ = 0.72f;
    private static final float FIREBALL_SMOKE_BASE_SCALE_SQ = 0.28f;
    private static final int MAX_NUKE_SERIES_FRAMES = 120;
    private static final long SHIELD_FADE_MS = 200L;
    private static final float SHIELD_BASE_ALPHA = 1.0f;
    private static final long FREEZE_FADE_MS = 200L;
    private static final float FREEZE_BASE_ALPHA = 1f;
    private static final long PIECE_PRE_EXPLOSION_MS = 2300L;
    private static final long PIECE_SHATTER_MS = 900L;
    private static final long PIECE_POST_DARK_HOLD_MS = 1000L;
    private static final long PIECE_POST_DARK_FADE_MS = 2200L;
    private static final long URIEL_RESURRECTION_MS = 1250L;
    private static final String URIEL_RESURRECTION_SOUND = "heaven.wav";
    private static final int THREE_CHECK_RESULT_EXTRA_DELAY_MS = 260;
    private static final String DEFAULT_PIECE_EXPLOSION_SOUND = "explosion.mp3";
    private static final String SPELL_CHESS_PLANE_SOUND = "plane.mp3";
    private static final String SPELL_CHESS_CRASH_SOUND = "crash1_iQ0TDVZ9.mp3";
    private static final String SPELL_CHESS_NUKE_SOUND = "nuked.mp3";
    private static final int SPELL_CHESS_PLANE_SOUND_DELAY_MS = 1000;
    private static final int SPELL_CHESS_CRASH_SOUND_DELAY_MS = 2000;
    private static final int SPELL_CHESS_SOUND_FADE_IN_MS = 550;
    private static final float SPELL_CHESS_CRASH_SOUND_VOLUME = 0.20f;
    private static final int SPELL_CHESS_CRASH_STOP_AFTER_NUKE_MS = 100;
    private static final int SPELL_CHESS_NUKE_VISUAL_DELAY_MS = 500;
    private static final float SPELL_CHESS_NUKE_APPROACH_START_BELOW_BOARD_SQ = 3.4f;
    private boolean fireballPlaneSoundPrestarted = false;
    private static final String THREE_CHECK_PRE_EXPLOSION_SOUND = "fuse.wav";
    private static final String THREE_CHECK_KING_EXPLOSION_SOUND = "king_shatter.wav";
    private static final int THREE_CHECK_EXPLOSION_SOUND_DELAY_MS = 2250;   // the crash lands as the king shatters (PIECE_PRE_EXPLOSION_MS)
    private static final float THREE_CHECK_RING_MAX_RADIUS_SQ = 1.5f;
    private int spellSimulationDepth = 0;

    // Settings toggles
    private boolean showCoordinates = true;
    private boolean animationsEnabled = true;
    private int moveAnimationSteps = 9;
    private int moveAnimationDelayMs = 8;
    private boolean autoQueenEnabled = false;
    private boolean premovingEnabled = false;
    private boolean autoFlipEnabled = false;
    private boolean fogOfWarEnabled = false;
    private BufferedImage fogCloudTexture;
    private BufferedImage shieldOverlayIcon;
    private BufferedImage freezeSnowflakeIcon;
    private final ArrayList<BufferedImage> fireballMissileFrames = new ArrayList<>();
    private boolean fireballMissileFramesLoaded = false;
    private BufferedImage fireballMissileSprite;
    private BufferedImage fireballImpactFlashSprite;
    private BufferedImage fireballImpactFlashSoftSprite;
    private BufferedImage fireballSmokeSprite;
    private BufferedImage fireballSmallSmokeSprite;
    private BufferedImage bomberShadowSilhouetteSprite;
    private Timer fogAnimationTimer;
    private double fogPhaseX = 0.0;
    private double fogPhaseY = 0.0;
    private long fogLastFrameNanos = 0L;
    private float fogVisualAlpha = 0.0f;
    private float fogTargetAlpha = 0.0f;
    private static final float FOG_FADE_STEP = 0.08f;
    private static final int FOG_RIPPLE_CHARGE_MS = 2000;
    private static final int FOG_RIPPLE_DURATION_MS = 850;
    private boolean fogRippleActive = false;
    private long fogRippleStartedAtMs = 0L;
    private boolean fogRippleViewerIsWhite = true;
    
    /**
     * One queued premove. {@code piece} is the piece expected on the from-square when
     * it is played; {@code promo}/{@code promoPiece} are chosen when the premove is
     * made (so no picker ever pops up in the middle of the opponent's move).
     */
    private static final class Premove {
        final int fr, fc, tr, tc;
        final Piece piece;
        final String promo;
        final Piece promoPiece;
        Premove(int fr, int fc, int tr, int tc, Piece piece, String promo, Piece promoPiece) {
            this.fr = fr; this.fc = fc; this.tr = tr; this.tc = tc;
            this.piece = piece; this.promo = promo; this.promoPiece = promoPiece;
        }
    }
    private final ArrayList<Premove> premoveQueue = new ArrayList<>();
    private static final int MAX_PREMOVES = 16;
    /** While a premove is being played: the promotion it asked for (handlePromotion reads it). */
    private String premovePromotionSymbol;
    /** Counts every committed move, so a premove can tell whether it actually went through. */
    private int committedMoveCount;
    /** Set once the game has ended; premoves stay off until a new game/position. */
    private boolean premovesBlockedGameEnded;
    private int premoveRetryCount;

    public String getCurrentFEN() {
    return generateFEN();
    }

    public java.util.List<String> getMoveHistoryFEN() {
        return new java.util.ArrayList<>(moveHistoryFEN);
    }

    public java.util.List<String> getFensBeforeEachMove() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (moveHistoryFEN.size() <= 1) return out;
        for (int i = 0; i < moveHistoryFEN.size() - 1; i++) {
            out.add(moveHistoryFEN.get(i));
        }
        return out;
    }

    public java.util.List<String> getUciMoveHistoryFromStates() {
        java.util.ArrayList<String> moves = new java.util.ArrayList<>();
        if (boardStates.size() <= 1) return moves;
        for (int i = 0; i < boardStates.size() - 1; i++) {
            Piece[][] fromState = boardStates.get(i);
            Piece[][] toState = boardStates.get(i + 1);
            int[] m = inferTransitionMove(fromState, toState);
            if (m == null) continue;
            int sr = m[0], sc = m[1], tr = m[2], tc = m[3];
            Piece moving = fromState[sr][sc];
            if (moving == null) continue;

            String uci = toUciSquare(sc, sr) + toUciSquare(tc, tr);
            if (moving instanceof Pawn && (tr == 0 || tr == 7)) {
                Piece promoted = toState[tr][tc];
                char promo = promotionSuffix(promoted);
                if (promo != 0) uci += promo;
            }
            moves.add(uci);
        }
        return moves;
    }
    
    public Piece[][] getBoardArray() {
        return board;
    }

    private String toUciSquare(int col, int row) {
        char file = (char) ('a' + col);
        char rank = (char) ('8' - row);
        return "" + file + rank;
    }

    private char promotionSuffix(Piece p) {
        if (p instanceof Queen) return 'q';
        if (p instanceof Rook) return 'r';
        if (p instanceof Bishop) return 'b';
        if (p instanceof Knight) return 'n';
        return 0;
    }

    private void saveBoardState() {
        // Deep copy the current board state
        Piece[][] stateCopy = new Piece[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                stateCopy[r][c] = board[r][c]; // Note: This is a shallow copy
                // For full functionality, you'd need deep copy of pieces
            }
        }
        boardStates.add(stateCopy);
        threeCheckStates.add(new int[] { whiteChecksDelivered, blackChecksDelivered });
        moveHistoryFEN.add(generateFEN());
        currentStateIndex = boardStates.size() - 1;
        // New saved state means we're at latest position
        viewingPast = false;
        if (gameController != null) gameController.onGameBoardStateSaved();
        }
            public void setPositionAtMove(int moveIndex) {
        if (moveIndex < 0 || moveIndex >= boardStates.size()) 
            return;
        clearPremoves();
        
        currentStateIndex = moveIndex;
        restoreTurnStateAt(moveIndex);
        
        // When viewing history (not at latest), clear last move highlight
        if (moveIndex < boardStates.size() - 1) {
            lastMoveFromRow = -1;
            lastMoveFromCol = -1;
            lastMoveToRow = -1;
            lastMoveToCol = -1;
        }
        
        // Restore the board state at the given move index
        Piece[][] stateToRestore = boardStates.get(moveIndex);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                board[r][c] = stateToRestore[r][c];
            }
        }
        if (moveIndex < threeCheckStates.size()) {
            int[] counts = threeCheckStates.get(moveIndex);
            whiteChecksDelivered = counts[0];
            blackChecksDelivered = counts[1];
        } else {
            whiteChecksDelivered = 0;
            blackChecksDelivered = 0;
        }
        
        // Update turn based on move index (even = white's turn, odd = black's turn)
        whiteTurn = (moveIndex % 2 == 0);
        
        repaint();
    }
        public String generateFEN() {
        StringBuilder fen = new StringBuilder();
        
        // 1. Piece placement (from rank 8 to rank 1)
        for (int r = 0; r < 8; r++) {
            int emptyCount = 0;
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == null) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;   // the run has been written; start counting again
                    }
                    fen.append(getPieceFENChar(board[r][c]));
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (r < 7) {
                fen.append('/');
            }
        }
        
        // 2. Active color
        fen.append(' ').append(whiteTurn ? 'w' : 'b');
        
        // 3. Castling availability
        fen.append(' ').append(getCastlingAvailability());
        
        // 4. En passant target square
        fen.append(' ').append(getEnPassantTargetSquare());
        
        // 5. Halfmove clock
        fen.append(' ').append(halfmoveClock);
        
        // 6. Fullmove number
        fen.append(' ').append(fullmoveNumber);
        
        return fen.toString();
    }

    private char getPieceFENChar(Piece piece) {
        String className = piece.getClass().getSimpleName();
        char c = ' ';
        
        switch (className) {
            case "King": c = 'k'; break;
            case "Queen": c = 'q'; break;
            case "Rook": c = 'r'; break;
            case "Bishop": c = 'b'; break;
            case "Knight": c = 'n'; break;
            case "Pawn": c = 'p'; break;
            case "Zoglin": c = 'z'; break;
        }
        
        return piece.isWhite() ? Character.toUpperCase(c) : c;
    }

    private King findKing(boolean white) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] instanceof King && board[r][c].isWhite() == white) {
                    return (King) board[r][c];
                }
            }
        }
        return null;
    }

    private int[] findKingPosition(boolean white) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] instanceof King && board[r][c].isWhite() == white) {
                    return new int[] { r, c };
                }
            }
        }
        return null;
    }
    private Piece[][] board = new Piece[8][8];
    private Piece selectedPiece;
    private int selectedRow, selectedCol;
    private boolean whiteTurn = true;
    private ChessGame gameController;
    
    // When false, user cannot interact with the board (useful before "Start Game")
    private boolean inputEnabled = true;
    private boolean showInputDisabledOverlay = true;
    /** How much of the light "board is locked" wash shows (0..1); eased, never snapped. */
    private float lockWash = 0f;
    private float lockWashFrom, lockWashTarget = -1f;
    private long lockWashStart;
    private Timer lockWashTimer;
    private static final int LOCK_WASH_MS = 520;

    private void animateLockWash(float target) {
        if (target == lockWashTarget) return;
        lockWashTarget = target;
        if (!isShowing()) {                 // not on screen yet: no point animating
            if (lockWashTimer != null) lockWashTimer.stop();
            lockWash = target;
            return;
        }
        lockWashFrom = lockWash;
        lockWashStart = System.nanoTime();
        if (lockWashTimer == null) {
            lockWashTimer = new Timer(12, e -> {
                double t = Math.min(1.0, (System.nanoTime() - lockWashStart) / 1_000_000.0 / LOCK_WASH_MS);
                double eased = (1.0 - Math.cos(Math.PI * t)) / 2.0;
                lockWash = (float) (lockWashFrom + (lockWashTarget - lockWashFrom) * eased);
                repaint();
                if (t >= 1.0) {
                    lockWash = lockWashTarget;
                    ((Timer) e.getSource()).stop();
                }
            });
            lockWashTimer.setCoalesce(true);
        }
        lockWashTimer.restart();
    }

    public void setInputEnabled(boolean enabled) {
        setInputEnabled(enabled, true);
    }

    public void setInputEnabled(boolean enabled, boolean showDisabledOverlay) {
        this.inputEnabled = enabled;
        this.showInputDisabledOverlay = showDisabledOverlay;
        animateLockWash(!enabled && showDisabledOverlay ? 1f : 0f);
        if (!enabled) {
            premoveQueue.clear();
            selectedPiece = null;
            dragging = false;
            draggedPiece = null;
            duckDragging = false;
            duckSelected = false;
            duckPressedOnDuck = false;
            possibleMoves.clear();
        }
        updateThreeCheckRingTimer();
        repaint();
    }

    private final int SQUARE_SIZE = 70;
    private final int BOARD_SIZE = SQUARE_SIZE * 8;

    // Animation
    private Piece animPiece;
    private int animX, animY;
    private boolean animating = false;
    private double animProgress = 0.0; // Track animation progress (0.0 to 1.0)
    // Viewing past position (not at latest)
    private boolean viewingPast = false;
    
    // Drag and drop
    private boolean dragging = false;
    private int dragX, dragY;
    private Piece draggedPiece;

    // Click-vs-drag discrimination
    private boolean wasDragged = false;
    private int pressX, pressY;
    // True only when a press started on the already-selected square.
    // Used for click-to-deselect without breaking single-click selection.
    private boolean clickedSelectedOnPress = false;
    private static final int DRAG_THRESHOLD = 5;

    
    // Castling rook animation
    private Piece animRook;
    private int animRookX, animRookY;
    
    // Animation source/destination for visual effects
    private int animFromRow = -1, animFromCol = -1;
    private int animToRow = -1, animToCol = -1;
    
    // Illegal move vibration
    private Piece vibratePiece;
    private int vibrateRow = -1, vibrateCol = -1;
    private int vibrateOffsetX = 0, vibrateOffsetY = 0;
    private int vibrateCount = 0;
    
    // King check blinking
    private int blinkKingRow = -1, blinkKingCol = -1;
    private boolean blinkVisible = false;
    private int blinkCount = 0;
    private int checkFlashKingRow = -1, checkFlashKingCol = -1;
    private int checkFlashStep = -1;
    private Color checkFlashPrimaryColor = null;
    private Color checkFlashFinalColor = null;
    private Timer checkFlashTimer = null;
    
    // Checkmated king rotation
    private int rotatingKingRow = -1, rotatingKingCol = -1;
    private double kingRotationAngle = 0.0;
    private boolean kingRotating = false;

    // Confetti / poop particle system
    private static class Particle {
        double x, y, vx, vy, gravity;
        float alpha;
        Color color;
        int size;
        double angle, spin;
        boolean isCircle;
        boolean isPoop;
        Particle(double x, double y, double vx, double vy,
                 Color color, int size, double spin, boolean isCircle, boolean isPoop) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.color = color; this.size = size;
            this.angle = 0; this.spin = spin;
            this.alpha = 1.0f;
            this.gravity = 0.18 + Math.random() * 0.12;
            this.isCircle = isCircle;
            this.isPoop = isPoop;
        }
    }
    private final java.util.List<Particle> particles =
            java.util.Collections.synchronizedList(new ArrayList<>());
    private Timer confettiTimer = null;
    private Timer rebursterTimer = null;
    private Timer poopSpawnTimer  = null;
    private static final Color[] CONFETTI_COLORS = {
        new Color(255, 80,  80),  new Color(255, 200, 50),
        new Color(80,  200, 80),  new Color(80,  160, 255),
        new Color(220, 80,  220), new Color(255, 140, 0),
        new Color(0,   220, 220)
    };

    // Legal move indicators
    private ArrayList<int[]> possibleMoves = new ArrayList<>();
    private int tabFocusedLegalMoveIndex = -1;
    private int tabFocusedPieceRow = -1;
    private int tabFocusedPieceCol = -1;
    private boolean tabPieceSelectionConfirmed = false;
    
    // Board flip
    private boolean boardFlipped = false;
    // Online play flags
    private boolean onlineMode = false;
    private boolean localIsWhite = true;
    // Bot game flag
    private boolean isBotGame = false;
    private boolean playerIsWhite = true; // In bot games, player is always white
    // Analysis mode
    private boolean analysisMode = false;
    private AnalysisGame analysisGame = null;
    private final Map<Review.MoveClass, BufferedImage> reviewBadgeImages = new java.util.EnumMap<>(Review.MoveClass.class);
    private Review.MoveClass reviewBadgeClass = null;
    
    // Show legal moves toggle
    private boolean showLegalMovesEnabled = true;
    private VariantMode variantMode = VariantMode.NORMAL;
    private int duckRow = -1;
    private int duckCol = -1;
    private boolean pendingDuckPlacement = false;
    private boolean pendingDuckPlacementSideWhite = false;
    private BufferedImage duckImage;
    private boolean duckSelected = false;
    private boolean duckDragging = false;
    private int duckDragX = -1;
    private int duckDragY = -1;
    private boolean duckPressedOnDuck = false;
    private boolean duckMoveAnimating = false;
    private int duckAnimFromRow = -1;
    private int duckAnimFromCol = -1;
    private int duckAnimToRow = -1;
    private int duckAnimToCol = -1;
    private long duckAnimStartMs = 0L;
    private static final long DUCK_MOVE_ANIM_MS = 180L;
    private int whiteChecksDelivered = 0;
    private int blackChecksDelivered = 0;
    private long whiteKingRingPhaseStartMs = 0L;
    private long blackKingRingPhaseStartMs = 0L;
    private int whiteKingRingStage = -1;
    private int blackKingRingStage = -1;
    private final PlayerState whiteState = new PlayerState();
    private final PlayerState blackState = new PlayerState();
    private final SpellManager spellManager = new SpellManager();
    private boolean deferSpellVisualEffects = false;
    private final ArrayList<Runnable> deferredSpellVisualEffects = new ArrayList<>();
    private final java.util.Set<Piece> pendingFreezeVisualPieces =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final ArrayList<PendingFireballVictim> pendingFireballVictims = new ArrayList<>();
    private final java.util.Set<Piece> pendingUrielVisualPieces =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private boolean spellShieldBreaksOnMove = true;
    private boolean refundLastSpellElixir = false;
    private boolean preserveLastSpellCard = false;
    public static final String SPELL_TARGETING_PENDING = "__SPELL_TARGETING_PENDING__";
    public static final String SPELL_PHASE_BOMBER_PRIME = "BOMBER_PRIME";
    public static final String SPELL_PHASE_ENDERMAN = "ENDERMAN_PHASE";
    private String pendingSpellTargetingId = null;
    private boolean pendingSpellTargetingSideWhite = false;
    private final ArrayList<SpellTarget> pendingSpellLegalTargets = new ArrayList<>();
    private final ArrayList<int[]> pendingSpellHighlightSquares = new ArrayList<>();
    private SpellCastCardFx spellCastCardFx;
    private Timer spellCastCardTimer;
    private final Map<String, BufferedImage> spellCastCardArtCache = new HashMap<>();
    private static final long SPELL_CAST_CARD_FLIP_MS = 520L;
    private static final long SPELL_CAST_CARD_PULSE_MS = 430L;
    private static final long SPELL_CAST_CARD_IMPACT_MS = 270L;
    private static final long SPELL_CAST_CARD_TOTAL_MS =
            SPELL_CAST_CARD_FLIP_MS + SPELL_CAST_CARD_PULSE_MS + SPELL_CAST_CARD_IMPACT_MS;
    private int tabSelectedSpellTargetRow = -1;
    private int tabSelectedSpellTargetCol = -1;
    private boolean pendingUrielPlacement = false;
    private boolean pendingUrielSideWhite = false;
    private String pendingUrielPieceType = null;
    private int urielHoverRow = -1;
    private int urielHoverCol = -1;
    private boolean urielChooserActive = false;
    private boolean urielChooserSideWhite = false;
    private final ArrayList<String> urielChooserLabels = new ArrayList<>();
    private final ArrayList<String> urielChooserTypes = new ArrayList<>();
    private final ArrayList<Integer> urielChooserCounts = new ArrayList<>();
    private int urielChooserHoverIndex = -1;
    private int urielChooserSelectedIndex = -1;
    private long urielChooserOpenedAtMs = 0L;
    private long urielChooserSelectedAtMs = 0L;
    private Timer urielChooserTimer;
    private static final long URIEL_CHOOSER_TRANSITION_MS = 600L;
    private static final long URIEL_CHOOSER_HANDOFF_DELAY_MS = 16L;
    private boolean pendingBomberRookPick = false;
    private boolean pendingBomberSideWhite = false;
    private int bomberPrimedRookRow = -1;
    private int bomberPrimedRookCol = -1;
    private boolean bomberPrimedSideWhite = false;
    private boolean bomberConsumePending = false;
    private boolean bomberConsumeSideWhite = false;
    private static final int BOMBER_COST = 8;
    private static final long BOMBER_CHARGE_MS = 4000L;
    private static final long BOMBER_RUSH_MS = 170L;
    private static final float BOMBER_ROOK_MAX_SCALE = 1.16f;
    private BomberCaptureFx bomberCaptureFx;
    private Timer bomberCaptureTimer;
    private boolean completingBomberCapture = false;
    private boolean pendingEndermanPiecePick = false;
    private boolean pendingEndermanSideWhite = false;
    private boolean endermanPhaseActive = false;
    private boolean endermanPhaseSideWhite = false;
    private int endermanPhaseRow = -1;
    private int endermanPhaseCol = -1;
    private boolean endermanConsumePending = false;
    private boolean endermanConsumeSideWhite = false;
    private int endermanCardTargetRow = -1;
    private int endermanCardTargetCol = -1;
    private int pendingEndermanTeleportFromRow = -1;
    private int pendingEndermanTeleportFromCol = -1;
    private int pendingEndermanTeleportToRow = -1;
    private int pendingEndermanTeleportToCol = -1;
    private boolean completingEndermanTeleport = false;
    private static final int ENDERMAN_PICKUP_FX_MS = 1200;
    private static final int ENDERMAN_POP_OUT_MS = 145;
    private static final int ENDERMAN_POP_IN_MS = 290;
    private static final int ENDERMAN_SOUND_EARLY_MS = 300;
    private long endermanPickupFxStartedAtMs = -1L;
    private int endermanPickupFxRow = -1;
    private int endermanPickupFxCol = -1;
    private int endermanPickupFxDestRow = -1;
    private int endermanPickupFxDestCol = -1;
    private Timer endermanPickupFxTimer;
    private Piece endermanTeleportFxPiece;
    private boolean endermanTeleportFxMoved;
    private boolean endermanTeleportSoundPlayed;
    private final ArrayList<int[]> endermanChangedSources = new ArrayList<>();
    private static final int ENDERMAN_PHASE_POOF_MS = 2700;
    private static final int ENDERMAN_PHASE_UNPOOF_MS = 520;
    private long endermanPhasePoofStartedAtMs = -1L;
    private Piece endermanPhasePoofPiece;
    private int endermanPhasePoofRow = -1;
    private int endermanPhasePoofCol = -1;
    private boolean endermanPhasePoofReversing = false;
    private static final int ENDERMAN_OVERLAY_FADE_MS = 180;
    private long endermanOverlayFadeStartedAtMs = -1L;
    private Piece endermanOverlayFadePiece;
    private int endermanOverlayFadeRow = -1;
    private int endermanOverlayFadeCol = -1;
    private static final int ENDERMAN_COST = 5;

    private ArrayList<String> moveHistoryFEN = new ArrayList<>();
    private ArrayList<Piece[][]> boardStates = new ArrayList<>();
    private ArrayList<int[]> threeCheckStates = new ArrayList<>();
    private Map<String, Integer> positionCounts = new HashMap<>();
    private int halfmoveClock = 0;
    private int fullmoveNumber = 1;
    // Index of the current state when navigating through history
    private int currentStateIndex = 0;
    // Latest requested target index for history navigation; consumed step-by-step.
    private int pendingHistoryTargetIndex = -1;
    
    // Last move highlighting
    private int lastMoveFromRow = -1;
    private int lastMoveFromCol = -1;
    private int lastMoveToRow = -1;
    private int lastMoveToCol = -1;
    
    // Color scheme
    private Color lightSquare = new Color(222, 230, 242);
    private Color darkSquare = new Color(82, 121, 169);
    private final Color SELECTED_SQUARE = new Color(246, 246, 130);
    private final Color TAB_FOCUSED_SQUARE = new Color(176, 255, 106);
    private final Color PREMOVE_FIRST_COLOR = new Color(244, 110, 110, 120);
    private final Color PREMOVE_CHAIN_COLOR = new Color(190, 38, 42, 145);

    public Board(ChessGame controller) {
        this.gameController = controller;
        setPreferredSize(new Dimension(BOARD_SIZE, BOARD_SIZE));
        setMinimumSize(new Dimension(BOARD_SIZE, BOARD_SIZE));
        setMaximumSize(new Dimension(BOARD_SIZE, BOARD_SIZE));
        setOpaque(true);
        setBackground(new Color(48, 46, 43));
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addMouseListener(this);
        addMouseMotionListener(this);
        installLegalIndicatorTabNavigation();
        setupPieces();
        resetSpellState();
        resetThreeCheckState();
        resetDuckState();
        boardStates.clear();
        threeCheckStates.clear();
        moveHistoryFEN.clear();
        positionCounts.clear();
        halfmoveClock = 0;
        fullmoveNumber = 1;
        loadReviewBadgeImages();
        saveBoardState();
        recordCurrentPosition();
        ensureGlobalFxRepaintTimerRunning();
    }

    public Color getLightSquareColor() { return lightSquare; }
    public Color getDarkSquareColor() { return darkSquare; }

    public void setDisplaySize(int boardPixels) {
        int size = Math.max(1, boardPixels);
        Dimension d = new Dimension(size, size);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
        revalidate();
        repaint();
    }

    private double renderScale() {
        return Math.max(0.01, Math.min(getWidth(), getHeight()) / (double) BOARD_SIZE);
    }

    private int logicalMouseX(MouseEvent e) {
        return (int) Math.floor(e.getX() / renderScale());
    }

    private int logicalMouseY(MouseEvent e) {
        return (int) Math.floor(e.getY() / renderScale());
    }

    private int mouseCol(MouseEvent e) {
        return logicalMouseX(e) / SQUARE_SIZE;
    }

    private int mouseRow(MouseEvent e) {
        return logicalMouseY(e) / SQUARE_SIZE;
    }

    private void ensureGlobalFxRepaintTimerRunning() {
        if (globalFxRepaintTimer == null) {
            globalFxRepaintTimer = new Timer(GLOBAL_FX_REPAINT_DELAY_MS, e -> {
                if (isShowing() && hasActiveVisualEffects()) repaint();
            });
        }
        if (!globalFxRepaintTimer.isRunning()) {
            globalFxRepaintTimer.start();
        }
    }

    private boolean hasActiveVisualEffects() {
        return !atomicExplosionFx.isEmpty()
            || !fireballImpactFx.isEmpty()
            || !shieldVisualFx.isEmpty()
            || !freezeVisualFx.isEmpty()
            || !pieceExplosionFx.isEmpty()
            || endermanPickupFxStartedAtMs >= 0L
            || endermanPhasePoofStartedAtMs >= 0L
            || endermanOverlayFadeStartedAtMs >= 0L
            || pendingEndermanPiecePick
            || !particles.isEmpty()
            || animPiece != null
            || animRook != null
            || selectedPiece != null
            || dragging
            || duckDragging
            || isDuckPlacementAnimating()
            || (fogAnimationTimer != null && fogAnimationTimer.isRunning())
            || (threeCheckRingTimer != null && threeCheckRingTimer.isRunning())
            || (checkFlashTimer != null && checkFlashTimer.isRunning())
            || (confettiTimer != null && confettiTimer.isRunning())
            || (poopSpawnTimer != null && poopSpawnTimer.isRunning())
            || (rebursterTimer != null && rebursterTimer.isRunning());
    }

    private void refreshCheckStatusCached() {
        if (fogOfWarEnabled || isDuckChessMode()) return;
        long now = System.currentTimeMillis();
        if (now - lastCheckEvalMs < CHECK_EVAL_THROTTLE_MS) return;
        IsLegal legal = new IsLegal(board);
        boolean whiteInCheck = legal.isInCheck(true);
        boolean blackInCheck = legal.isInCheck(false);
        boolean checkStarted = (!cachedWhiteInCheck && whiteInCheck)
                || (!cachedBlackInCheck && blackInCheck);
        cachedWhiteInCheck = whiteInCheck;
        cachedBlackInCheck = blackInCheck;
        if (checkStarted) ensureSquareColorTransitionTimer();
        lastCheckEvalMs = now;
    }

    private void ensureSquareColorTransitionTimer() {
        if (squareColorTransitionTimer == null) {
            squareColorTransitionTimer = new Timer(AnimationTiming.FRAME_DELAY_MS, e -> {
                repaint();
                if (!hasActiveSquareColorTransition()) {
                    ((Timer) e.getSource()).stop();
                }
            });
        }
        if (!squareColorTransitionTimer.isRunning()) {
            squareColorTransitionTimer.start();
        }
    }

    private boolean hasActiveSquareColorTransition() {
        long now = System.currentTimeMillis();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Color start = squareColorStarts[row][col];
                Color target = squareColorTargets[row][col];
                if (start != null && target != null && !start.equals(target)
                        && now - squareColorStartedAt[row][col] < SQUARE_COLOR_TRANSITION_MS) {
                    return true;
                }
            }
        }
        return false;
    }

    private Color animateSquareColor(int row, int col, Color target, boolean immediate) {
        long now = System.currentTimeMillis();
        Color rendered = renderedSquareColors[row][col];
        Color oldTarget = squareColorTargets[row][col];

        if (rendered == null || immediate) {
            renderedSquareColors[row][col] = target;
            squareColorStarts[row][col] = target;
            squareColorTargets[row][col] = target;
            squareColorStartedAt[row][col] = now;
            return target;
        }

        if (oldTarget == null || !oldTarget.equals(target)) {
            Color quickStart = blendColor(rendered, target, 0.25f);
            squareColorStarts[row][col] = quickStart;
            squareColorTargets[row][col] = target;
            squareColorStartedAt[row][col] = now;
            renderedSquareColors[row][col] = quickStart;
            ensureSquareColorTransitionTimer();
            return quickStart;
        }

        float t = (now - squareColorStartedAt[row][col]) / (float) SQUARE_COLOR_TRANSITION_MS;
        if (t >= 1f) {
            renderedSquareColors[row][col] = target;
            return target;
        }
        float eased = 1f - (float) Math.pow(1f - Math.max(0f, t), 3);
        Color color = blendColor(squareColorStarts[row][col], target, eased);
        renderedSquareColors[row][col] = color;
        return color;
    }

    private static Color blendColor(Color from, Color to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int red = Math.round(from.getRed() + (to.getRed() - from.getRed()) * t);
        int green = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t);
        int blue = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t);
        return new Color(red, green, blue);
    }

    private static Color overlayColor(Color base, Color overlay) {
        float alpha = overlay.getAlpha() / 255f;
        return blendColor(base, new Color(overlay.getRed(), overlay.getGreen(), overlay.getBlue()), alpha);
    }

    private void drawSelectedSquareGlow(Graphics2D g2, int x, int y) {
        long now = System.currentTimeMillis();
        float fade = Math.min(1f, (now - selectedGlowStartedAt) / 160f);
        fade = 1f - (float) Math.pow(1f - fade, 3);
        float pulse = 0.5f + 0.5f * (float) Math.sin(now * (2.0 * Math.PI / 1450.0));
        int centerAlpha = Math.round((78 + pulse * 18) * fade);
        int midAlpha = Math.round((35 + pulse * 10) * fade);
        float centerX = x + SQUARE_SIZE / 2f;
        float centerY = y + SQUARE_SIZE / 2f;
        float radius = SQUARE_SIZE * 0.72f;

        RadialGradientPaint glow = new RadialGradientPaint(
            centerX,
            centerY,
            radius,
            new float[] {0f, 0.48f, 1f},
            new Color[] {
                new Color(255, 252, 180, centerAlpha),
                new Color(255, 232, 92, midAlpha),
                new Color(255, 218, 45, 0)
            }
        );
        Paint oldPaint = g2.getPaint();
        g2.setPaint(glow);
        g2.fillRect(x, y, SQUARE_SIZE, SQUARE_SIZE);
        g2.setPaint(oldPaint);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ensureGlobalFxRepaintTimerRunning();
    }

    private void installLegalIndicatorTabNavigation() {
        InputMap im = getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "legalMoveTabNext");
        am.put("legalMoveTabNext", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                focusNextLegalIndicator(1);
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK), "legalMoveTabPrev");
        am.put("legalMoveTabPrev", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                focusNextLegalIndicator(-1);
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "legalMoveEnterActivate");
        am.put("legalMoveEnterActivate", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                activateTabFocusedLegalMove();
            }
        });
    }

    private ArrayList<int[]> getTabNavigableLegalMoves() {
        ArrayList<int[]> moves = new ArrayList<>();
        if (!inputEnabled || animating) return moves;
        if (pendingSpellTargetingId != null) {
            for (int[] sq : pendingSpellHighlightSquares) {
                if (sq == null || sq.length < 2) continue;
                int row = sq[0], col = sq[1];
                if (!inBounds(row, col)) continue;
                moves.add(new int[]{-1, -1, row, col});
            }
            moves.addAll(possibleMoves);
        } else if (pendingDuckPlacement || pendingUrielPlacement) {
            moves.addAll(possibleMoves);
        } else if (showLegalMovesEnabled && selectedPiece != null) {
            moves.addAll(possibleMoves);
        }
        moves.sort(this::compareMovesForTabOrder);
        return moves;
    }

    private boolean isDirectDestructiveSpell(String spellId) {
        return SpellManager.FIREBALL.equals(spellId)
                || SpellManager.FREEZE.equals(spellId);
    }

    private boolean isTabSelectedSpellTargetSquare(int row, int col) {
        return tabSelectedSpellTargetRow == row && tabSelectedSpellTargetCol == col;
    }

    private void clearTabSelectedSpellTarget() {
        tabSelectedSpellTargetRow = -1;
        tabSelectedSpellTargetCol = -1;
    }

    private void setTabSelectedSpellTargetFromMove(int[] move) {
        if (move == null || move.length < 4) {
            clearTabSelectedSpellTarget();
            return;
        }
        tabSelectedSpellTargetRow = move[2];
        tabSelectedSpellTargetCol = move[3];
    }

    private int compareMovesForTabOrder(int[] a, int[] b) {
        int ar = boardFlipped ? (7 - a[2]) : a[2];
        int ac = boardFlipped ? (7 - a[3]) : a[3];
        int br = boardFlipped ? (7 - b[2]) : b[2];
        int bc = boardFlipped ? (7 - b[3]) : b[3];
        if (ar != br) return Integer.compare(ar, br);
        if (ac != bc) return Integer.compare(ac, bc);

        // If multiple pieces can move to the same square, prefer the visually-left source piece first.
        int aFromDisplayCol = boardFlipped ? (7 - a[1]) : a[1];
        int bFromDisplayCol = boardFlipped ? (7 - b[1]) : b[1];
        if (aFromDisplayCol != bFromDisplayCol) return Integer.compare(aFromDisplayCol, bFromDisplayCol);

        int aFromDisplayRow = boardFlipped ? (7 - a[0]) : a[0];
        int bFromDisplayRow = boardFlipped ? (7 - b[0]) : b[0];
        if (aFromDisplayRow != bFromDisplayRow) return Integer.compare(aFromDisplayRow, bFromDisplayRow);
        return 0;
    }

    private void focusNextLegalIndicator(int step) {
        if (selectedPiece == null
                && !pendingDuckPlacement
                && !pendingUrielPlacement
                && pendingSpellTargetingId == null) {
            ArrayList<int[]> pieceSources = getTabNavigablePieceSources();
            if (pieceSources.isEmpty()) {
                tabFocusedPieceRow = -1;
                tabFocusedPieceCol = -1;
                tabFocusedLegalMoveIndex = -1;
                repaint();
                return;
            }
            int current = -1;
            for (int i = 0; i < pieceSources.size(); i++) {
                int[] s = pieceSources.get(i);
                if (s[0] == tabFocusedPieceRow && s[1] == tabFocusedPieceCol) {
                    current = i;
                    break;
                }
            }
            int next = (current < 0)
                    ? ((step < 0) ? pieceSources.size() - 1 : 0)
                    : Math.floorMod(current + step, pieceSources.size());
            int[] src = pieceSources.get(next);
            tabFocusedPieceRow = src[0];
            tabFocusedPieceCol = src[1];
            tabFocusedLegalMoveIndex = -1;
            repaint();
            return;
        }

        tabFocusedPieceRow = -1;
        tabFocusedPieceCol = -1;
        ArrayList<int[]> orderedMoves = getTabNavigableLegalMoves();
        if (orderedMoves.isEmpty()) {
            tabFocusedLegalMoveIndex = -1;
            clearTabSelectedSpellTarget();
            repaint();
            return;
        }
        if (tabFocusedLegalMoveIndex < 0 || tabFocusedLegalMoveIndex >= orderedMoves.size()) {
            tabFocusedLegalMoveIndex = (step < 0) ? (orderedMoves.size() - 1) : 0;
        } else {
            tabFocusedLegalMoveIndex = Math.floorMod(tabFocusedLegalMoveIndex + step, orderedMoves.size());
        }
        if (isDirectDestructiveSpell(pendingSpellTargetingId)) {
            setTabSelectedSpellTargetFromMove(orderedMoves.get(tabFocusedLegalMoveIndex));
        } else {
            clearTabSelectedSpellTarget();
        }
        repaint();
    }

    private ArrayList<int[]> getTabNavigablePieceSources() {
        ArrayList<int[]> sources = new ArrayList<>();
        if (!inputEnabled || animating || !isLocalPlayerTurn()) return sources;

        ArrayList<int[]> legalMoves = getActiveLegalMoves(whiteTurn);
        for (int[] m : legalMoves) {
            boolean exists = false;
            for (int[] s : sources) {
                if (s[0] == m[0] && s[1] == m[1]) {
                    exists = true;
                    break;
                }
            }
            if (!exists) sources.add(new int[]{m[0], m[1]});
        }

        sources.sort((a, b) -> {
            int ar = boardFlipped ? (7 - a[0]) : a[0];
            int ac = boardFlipped ? (7 - a[1]) : a[1];
            int br = boardFlipped ? (7 - b[0]) : b[0];
            int bc = boardFlipped ? (7 - b[1]) : b[1];
            if (ar != br) return Integer.compare(ar, br);
            return Integer.compare(ac, bc);
        });
        return sources;
    }

    private boolean confirmTabFocusedPieceSelection() {
        if (selectedPiece != null) return false;
        if (tabFocusedPieceRow < 0 || tabFocusedPieceCol < 0) return false;
        if (!inputEnabled || animating || !isLocalPlayerTurn()) return false;
        Piece p = board[tabFocusedPieceRow][tabFocusedPieceCol];
        if (p == null || p.isWhite() != whiteTurn) return false;

        selectedPiece = p;
        selectedRow = tabFocusedPieceRow;
        selectedCol = tabFocusedPieceCol;
        draggedPiece = p;
        tabPieceSelectionConfirmed = true;
        possibleMoves.clear();
        for (int[] m : getActiveLegalMoves(whiteTurn)) {
            if (m[0] == selectedRow && m[1] == selectedCol) possibleMoves.add(m);
        }
        tabFocusedPieceRow = -1;
        tabFocusedPieceCol = -1;
        tabFocusedLegalMoveIndex = -1;
        return !possibleMoves.isEmpty();
    }

    private void activateTabFocusedLegalMove() {
        boolean targetingModeActive = pendingSpellTargetingId != null || pendingDuckPlacement || pendingUrielPlacement;
        if (targetingModeActive) {
            tabFocusedPieceRow = -1;
            tabFocusedPieceCol = -1;
        }
        if (!targetingModeActive && confirmTabFocusedPieceSelection()) {
            repaint();
            return;
        }
        ArrayList<int[]> orderedMoves = getTabNavigableLegalMoves();
        if (orderedMoves.isEmpty()) return;
        if (tabFocusedLegalMoveIndex < 0 || tabFocusedLegalMoveIndex >= orderedMoves.size()) return;
        int[] focused = orderedMoves.get(tabFocusedLegalMoveIndex);
        int fromRow = focused[0];
        int fromCol = focused[1];
        int toRow = focused[2];
        int toCol = focused[3];

        if (pendingSpellTargetingId != null) {
            tryResolvePendingSpellTargeting(toRow, toCol);
            return;
        }

        if (!inputEnabled || animating) return;
        Piece focusedPiece = board[fromRow][fromCol];
        if (focusedPiece == null) return;

        boolean moveLegal = false;
        for (int[] m : getActiveLegalMoves(whiteTurn)) {
            if (m[0] == fromRow && m[1] == fromCol && m[2] == toRow && m[3] == toCol) {
                moveLegal = true;
                break;
            }
        }
        if (!moveLegal) return;

        selectedRow = fromRow;
        selectedCol = fromCol;
        selectedPiece = focusedPiece;

        Piece movePiece = focusedPiece;
        if (endermanPhaseActive && whiteTurn == endermanPhaseSideWhite) {
            endermanConsumePending = true;
            endermanConsumeSideWhite = whiteTurn;
        }
        boolean bomberMove = movePiece instanceof Rook && isBomberPrimedForSquare(fromRow, fromCol, whiteTurn);
        if (bomberMove) {
            Piece dest = board[toRow][toCol];
            boolean capture = dest != null && dest.isWhite() != whiteTurn
                    && !(dest instanceof King)
                    && dest.getShieldedTurnsRemaining() <= 0;
            if (capture) {
                movePiece.setBombRookTurnsRemaining(Math.max(1, movePiece.getBombRookTurnsRemaining()));
                bomberConsumePending = true;
                bomberConsumeSideWhite = whiteTurn;
            } else {
                movePiece.setBombRookTurnsRemaining(0);
                bomberConsumePending = false;
                bomberConsumeSideWhite = false;
            }
            bomberPrimedRookRow = -1;
            bomberPrimedRookCol = -1;
            bomberPrimedSideWhite = false;
        } else if (bomberPrimedRookRow != -1) {
            clearBomberState(false);
        }
        boolean endermanTeleportMove = endermanPhaseActive && whiteTurn == endermanPhaseSideWhite;
        boolean animate = animationsEnabled && !endermanTeleportMove;
        tabPieceSelectionConfirmed = false;
        selectedPiece = null;
        possibleMoves.clear();
        tabFocusedLegalMoveIndex = -1;

        if (endermanTeleportMove) {
            queueEndermanTeleportAfterCard(fromRow, fromCol, toRow, toCol);
        } else if (animate) {
            executeAnimated(fromRow, fromCol, toRow, toCol);
        } else {
            executeInstant(fromRow, fromCol, toRow, toCol);
        }
    }

    private void resetSpellState() {
        if (bomberCaptureTimer != null) {
            bomberCaptureTimer.stop();
            bomberCaptureTimer = null;
        }
        bomberCaptureFx = null;
        completingBomberCapture = false;
        cancelDeferredSpellVisualEffects();
        freezeBurstFx.clear();
        freezeVisualFx.clear();
        if (spellCastCardTimer != null) {
            spellCastCardTimer.stop();
            spellCastCardTimer = null;
        }
        spellCastCardFx = null;
        whiteState.copyFrom(new PlayerState());
        blackState.copyFrom(new PlayerState());
        clearUrielChooser();
        clearPendingSpellTargeting();
        clearPendingUrielPlacement();
        clearBomberState(false);
        clearEndermanState(false);
        endermanPhasePoofStartedAtMs = -1L;
        endermanPhasePoofPiece = null;
        endermanPhasePoofRow = -1;
        endermanPhasePoofCol = -1;
        endermanPhasePoofReversing = false;
        endermanOverlayFadeStartedAtMs = -1L;
        endermanOverlayFadePiece = null;
        endermanOverlayFadeRow = -1;
        endermanOverlayFadeCol = -1;
    }

    @Override
    public void removeNotify() {
        if (ringOverlay != null && ringOverlay.getParent() != null) {
            Container lp = ringOverlay.getParent();
            lp.remove(ringOverlay);
            lp.repaint();
        }
        stopFogAnimation();
        if (squareColorTransitionTimer != null) {
            squareColorTransitionTimer.stop();
        }
        if (atomicExplosionTimer != null) {
            atomicExplosionTimer.stop();
        }
        if (globalFxRepaintTimer != null) {
            globalFxRepaintTimer.stop();
        }
        if (urielChooserTimer != null) {
            urielChooserTimer.stop();
            urielChooserTimer = null;
        }
        if (spellCastCardTimer != null) {
            spellCastCardTimer.stop();
            spellCastCardTimer = null;
        }
        spellCastCardFx = null;
        stopThreeCheckRingTimer();
        stopCheckFlashTimer();
        super.removeNotify();
    }
    
    // Settings methods
    public void setShowCoordinates(boolean show) {
        this.showCoordinates = show;
        repaint();
    }

    /** How long the board takes to change colour when a new theme is picked. */
    private static final int THEME_FADE_MS = 320;
    private javax.swing.Timer themeFade;

    public void setBoardTheme(String theme) {
        Color[] to = themeSquares(theme);
        if (gameController != null) gameController.setUiTheme(theme);
        fadeSquaresTo(to[0], to[1]);
    }

    private static Color[] themeSquares(String theme) {
        switch (theme == null ? "" : theme) {
            case "Green":  return new Color[]{new Color(238, 238, 210), new Color(118, 150, 86)};
            case "Gray":   return new Color[]{new Color(216, 220, 222), new Color(120, 130, 138)};
            case "Purple": return new Color[]{new Color(226, 216, 238), new Color(121, 86, 156)};
            case "Red":    return new Color[]{new Color(238, 218, 214), new Color(166, 76, 72)};
            case "Orange": return new Color[]{new Color(236, 206, 158), new Color(171, 111, 57)};
            case "Yellow": return new Color[]{new Color(255, 246, 153), new Color(226, 170, 22)};
            case "Pink":   return new Color[]{new Color(242, 218, 231), new Color(181, 91, 139)};
            default:       return new Color[]{new Color(222, 230, 242), new Color(82, 121, 169)};
        }
    }

    /** The squares blend smoothly into their new colours (straight away if the board isn't on screen). */
    private void fadeSquaresTo(Color light, Color dark) {
        if (themeFade != null) themeFade.stop();
        themeFade = null;
        if (!isShowing() || (light.equals(lightSquare) && dark.equals(darkSquare))) {
            lightSquare = light;
            darkSquare = dark;
            repaint();
            return;
        }
        Color fromLight = lightSquare, fromDark = darkSquare;
        long start = System.nanoTime();
        javax.swing.Timer t = AnimationTiming.createUiTimer(null);
        t.addActionListener(e -> {
            float p = AnimationTiming.progress(start, THEME_FADE_MS);
            float k = (float) (0.5 - 0.5 * Math.cos(Math.PI * p));
            lightSquare = TimeControlPicker.mix(fromLight, light, k);
            darkSquare = TimeControlPicker.mix(fromDark, dark, k);
            repaint();
            if (p >= 1f) {
                t.stop();
                if (themeFade == t) themeFade = null;
            }
        });
        themeFade = t;
        t.start();
    }
    
    // History navigation speed (Analysis): one step takes navStepMs; a multi-step
    // jump runs each step faster so a long jump never drags. 0 = use the move speed.
    private int navStepMs = 0;
    private int overrideAnimMs = 0;

    public void setNavigationAnimationMs(int ms) { navStepMs = Math.max(0, ms); }

    public boolean isAnimatingMove() { return animating; }

    public void setAnimationSpeed(String speed) {
        animationsEnabled = AppSettings.animateMoves();   // Settings > Board & pieces > Animate moves
        if ("Slow".equals(speed)) {
            moveAnimationSteps = 14;
            moveAnimationDelayMs = 12;
        } else if ("Fast".equals(speed)) {
            moveAnimationSteps = 7;
            moveAnimationDelayMs = 4;
        } else {
            moveAnimationSteps = 9;
            moveAnimationDelayMs = 8;
        }
    }

    public void setAutoQueenEnabled(boolean enabled) {
        this.autoQueenEnabled = enabled;
    }

    public void setOpeningNamesEnabled(boolean enabled) {
        if (gameController != null) {
            gameController.setOpeningNamesEnabled(enabled);
        }
    }
    
    public void setPremovingEnabled(boolean enabled) {
        if (!enabled && isPremoveSelection()) dropSelection();
        this.premovingEnabled = enabled;
        if (!enabled) {
            clearPremove();
        }
    }

    public void setAutoFlipEnabled(boolean enabled) {
        this.autoFlipEnabled = enabled;
    }

    public void setFogOfWarEnabled(boolean enabled) {
        this.fogOfWarEnabled = enabled;
        updateFogTransitionTarget(getFogViewerIsWhite());
        repaint();
    }

    public boolean isFogOfWarEnabled() {
        return fogOfWarEnabled;
    }

    public void setVariantMode(VariantMode mode) {
        this.variantMode = (mode == null) ? VariantMode.NORMAL : mode;
        resetSpellState();
        resetThreeCheckState();
        resetDuckState();
        updateThreeCheckRingTimer();
        repaint();
    }

    public boolean isSpellChessMode() {
        return variantMode == VariantMode.SPELL_CHESS;
    }

    public boolean isAtomicMode() {
        return variantMode == VariantMode.ATOMIC;
    }

    public boolean isThreeCheckMode() {
        return variantMode == VariantMode.THREE_CHECK;
    }

    public boolean isKingOfTheHillMode() {
        return variantMode == VariantMode.KING_OF_THE_HILL;
    }

    public boolean isDuckChessMode() {
        return variantMode == VariantMode.DUCK_CHESS;
    }

    // ── Chess960 ─────────────────────────────────────────────────────────────
    // The back rank comes from Chess960.backRank(position). Castling follows the
    // Fischer rules: the king always ends on g (O-O) or c (O-O-O) and the rook beside
    // it on f or d, whatever squares they started on. Every square either piece crosses
    // must be empty (apart from the two of them), and the king may not be in check,
    // cross an attacked square or end in check. To castle you put the king on its own
    // rook (or on the g/c square when that isn't an ordinary king move).

    private int chess960Position = -1;          // -1 = normal chess
    private int castleRookColQueenside = 0;     // the castling rooks' starting files
    private int castleRookColKingside = 7;

    public boolean isChess960() { return chess960Position >= 0; }
    public int getChess960Position() { return chess960Position; }

    /** Switch this board to Chess960 position {@code n} (0-959), or back to normal chess (-1). */
    public void setChess960Position(int n) {
        chess960Position = (n >= 0 && n < 960) ? n : -1;
        resetToStartPosition();
    }

    /** A Chess960 castle, written as the king moving onto its own castling rook. */
    private boolean isCastle960(int fromRow, int fromCol, int toRow, int toCol) {
        if (!isChess960() || fromRow != toRow) return false;
        Piece k = board[fromRow][fromCol];
        Piece r = board[toRow][toCol];
        return k instanceof King && r instanceof Rook && k.isWhite() == r.isWhite();
    }

    /**
     * Castling moves that are legal right now for {@code sideWhite}: normal chess as
     * king-two-squares, Chess960 as king-onto-rook.
     */
    private java.util.List<int[]> legalCastles(boolean sideWhite, Piece[][] b) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        int row = sideWhite ? 7 : 0;
        int kingCol = -1;
        for (int c = 0; c < 8; c++) {
            if (b[row][c] instanceof King && b[row][c].isWhite() == sideWhite) { kingCol = c; break; }
        }
        if (kingCol < 0 || ((King) b[row][kingCol]).hasMoved()) return out;
        if (new IsLegal(copyOf(b)).isInCheck(sideWhite)) return out;      // never castle out of check
        for (int side = 0; side < 2; side++) {
            boolean kingside = side == 1;
            int rookCol = isChess960() ? (kingside ? castleRookColKingside : castleRookColQueenside) : (kingside ? 7 : 0);
            if (isChess960() && (kingside ? rookCol < kingCol : rookCol > kingCol)) continue;
            Piece rook = b[row][rookCol];
            if (!(rook instanceof Rook) || rook.isWhite() != sideWhite || ((Rook) rook).hasMoved()) continue;
            int kingTo = kingside ? 6 : 2, rookTo = kingside ? 5 : 3;
            int lo = Math.min(Math.min(kingCol, kingTo), Math.min(rookCol, rookTo));
            int hi = Math.max(Math.max(kingCol, kingTo), Math.max(rookCol, rookTo));
            boolean clear = true;
            for (int c = lo; c <= hi && clear; c++) {
                if (c == kingCol || c == rookCol) continue;
                if (b[row][c] != null) clear = false;
            }
            if (!clear) continue;
            // the king may not pass through or land on an attacked square
            boolean safe = true;
            int step = Integer.compare(kingTo, kingCol);
            for (int c = kingCol; safe; c += step) {
                Piece[][] t = copyOf(b);
                t[row][kingCol] = null;
                if (c == kingTo) {               // final square: with the rook in its new place
                    t[row][rookCol] = null;
                    t[row][rookTo] = rook;
                }
                t[row][c] = b[row][kingCol];
                if (new IsLegal(t).isInCheck(sideWhite)) safe = false;
                if (c == kingTo || step == 0) break;
            }
            if (!safe) continue;
            out.add(new int[]{row, kingCol, row, isChess960() ? rookCol : kingTo});
        }
        return out;
    }

    private static Piece[][] copyOf(Piece[][] b) {
        Piece[][] t = new Piece[8][8];
        for (int r = 0; r < 8; r++) t[r] = b[r].clone();
        return t;
    }

    /**
     * Replace the piece logic's own castling moves (which only know normal chess and
     * don't look at attacked squares) with the properly checked ones.
     */
    private ArrayList<int[]> withProperCastling(ArrayList<int[]> moves, boolean sideWhite) {
        // the variants without check rules keep their own behaviour
        if (isSpellChessMode() || isDuckChessMode() || fogOfWarEnabled || isAtomicMode()) return moves;
        ArrayList<int[]> out = new ArrayList<>(moves.size() + 2);
        for (int[] m : moves) {
            Piece p = board[m[0]][m[1]];
            if (p instanceof King && m[0] == m[2] && Math.abs(m[3] - m[1]) >= 2) continue;   // a castle
            out.add(m);
        }
        out.addAll(legalCastles(sideWhite, board));
        return out;
    }

    /**
     * Plays a Chess960 castle: king to g/c, rook to f/d, done safely even when the
     * squares overlap (e.g. king f1 + rook g1 becomes king g1 + rook f1).
     */
    private void executeCastle960(int row, int kingCol, int rookCol) {
        Piece king = board[row][kingCol];
        Piece rook = board[row][rookCol];
        Piece[][] boardBeforeMove = copyOf(board);
        boolean kingside = rookCol > kingCol;
        board[row][kingCol] = null;
        board[row][rookCol] = null;
        board[row][kingside ? 6 : 2] = king;
        board[row][kingside ? 5 : 3] = rook;
        ((King) king).setMoved(true);
        ((Rook) rook).setMoved(true);
        selectedPiece = null;
        possibleMoves.clear();
        commitMove(king, row, kingCol, row, rookCol, boardBeforeMove, null, true, false, "");
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    /** A drop of the king on its castling square (g/c) means "castle" when that's not a normal king move. */
    private int castleDropTarget(int fromRow, int fromCol, int toRow, int toCol) {
        if (!isChess960() || fromRow != toRow) return toCol;
        Piece k = board[fromRow][fromCol];
        if (!(k instanceof King) || (toCol != 6 && toCol != 2)) return toCol;
        boolean plain = false;
        java.util.List<int[]> castles = new java.util.ArrayList<>();
        for (int[] m : getActiveLegalMoves(whiteTurn)) {
            if (m[0] != fromRow || m[1] != fromCol) continue;
            if (m[2] == toRow && m[3] == toCol) plain = true;
            if (isCastle960(m[0], m[1], m[2], m[3])) castles.add(m);
        }
        if (plain) return toCol;
        for (int[] m : castles) {
            boolean kingside = m[3] > fromCol;
            if ((kingside ? 6 : 2) == toCol) return m[3];
        }
        return toCol;
    }

    private boolean isCenterSquareForKingHill(int row, int col) {
        return (row == 3 || row == 4) && (col == 3 || col == 4);
    }

    private void resetDuckState() {
        duckRow = -1;
        duckCol = -1;
        pendingDuckPlacement = false;
        pendingDuckPlacementSideWhite = false;
        duckSelected = false;
        duckDragging = false;
        duckDragX = -1;
        duckDragY = -1;
        duckPressedOnDuck = false;
        duckMoveAnimating = false;
        duckAnimFromRow = -1;
        duckAnimFromCol = -1;
        duckAnimToRow = -1;
        duckAnimToCol = -1;
        duckAnimStartMs = 0L;
    }

    private void ensureDuckImageLoaded() {
        if (duckImage != null) return;
        try {
            java.net.URL url = getClass().getResource("/assets/multiplayer/duck.png");
            if (url != null) {
                duckImage = ImageIO.read(url);
            }
        } catch (Exception ignored) {
        }
        if (duckImage != null) return;
        String[] paths = {
            "src/assets/multiplayer/duck.png",
            "assets/multiplayer/duck.png"
        };
        for (String p : paths) {
            try {
                File f = new File(p);
                if (!f.exists()) continue;
                duckImage = ImageIO.read(f);
                if (duckImage != null) break;
            } catch (Exception ignored) {
            }
        }
    }

    private boolean hasThreeCheckWinner() {
        return whiteChecksDelivered >= 3 || blackChecksDelivered >= 3;
    }

    private void updateThreeCheckRingTimer() {
        if (isThreeCheckMode() && inputEnabled && !hasThreeCheckWinner()) {
            startThreeCheckRingTimer();
        } else {
            stopThreeCheckRingTimer();
        }
    }

    private void startThreeCheckRingTimer() {
        if (threeCheckRingTimer == null) {
            threeCheckRingTimer = new Timer(THREE_CHECK_RING_TIMER_DELAY_MS, e -> {
                repaint();
                if (ensureRingOverlay()) { placeRingOverlay(); ringOverlay.repaint(); }
            });
        }
        if (!threeCheckRingTimer.isRunning()) {
            threeCheckRingTimer.start();
        }
    }

    private void stopThreeCheckRingTimer() {
        if (threeCheckRingTimer != null) {
            threeCheckRingTimer.stop();
        }
        if (ringOverlay != null) ringOverlay.repaint();
    }

    // ── three-check rings, on a layer above the board ─────────────────────────
    // A ring around a king on the edge reaches past the board; drawn on the board itself
    // it got cut off at the border. This see-through layer is a little bigger than the
    // board and sits just above it, so the whole ring shows. Clicks go straight through.
    private JComponent ringOverlay;

    private final class RingOverlay extends JComponent {
        RingOverlay() { setOpaque(false); }
        @Override public boolean contains(int x, int y) { return false; }   // never takes the mouse
        @Override protected void paintComponent(Graphics g) {
            if (!Board.this.isShowing() || !isThreeCheckMode()) return;
            Point origin = SwingUtilities.convertPoint(Board.this, 0, 0, this);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(origin.x, origin.y);
            g2.scale(renderScale(), renderScale());
            drawThreeCheckAmbientRings(g2);
            g2.dispose();
        }
    }

    /** Puts the ring layer in place when the board is in a window; false if it can't be (then rings draw on the board). */
    private boolean ensureRingOverlay() {
        if (!isThreeCheckMode()) return false;
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root == null || !isShowing()) return ringOverlay != null && ringOverlay.getParent() != null;
        JLayeredPane layers = root.getLayeredPane();
        if (ringOverlay == null) ringOverlay = new RingOverlay();
        if (ringOverlay.getParent() != layers) {
            if (ringOverlay.getParent() != null) ringOverlay.getParent().remove(ringOverlay);
            layers.add(ringOverlay, JLayeredPane.PALETTE_LAYER);
            placeRingOverlay();
        }
        return true;
    }

    /** The board's area plus room for a ring around an edge king. */
    private void placeRingOverlay() {
        if (ringOverlay == null || ringOverlay.getParent() == null) return;
        Container layers = ringOverlay.getParent();
        if (!isShowing()) { ringOverlay.setVisible(false); return; }
        Rectangle r = SwingUtilities.convertRectangle(getParent(), getBounds(), layers);
        int margin = (int) Math.ceil(SQUARE_SIZE * renderScale() * (THREE_CHECK_RING_MAX_RADIUS_SQ + 0.3f));
        r.grow(margin, margin);
        r = r.intersection(new Rectangle(0, 0, layers.getWidth(), layers.getHeight()));
        if (!r.equals(ringOverlay.getBounds())) ringOverlay.setBounds(r);
        if (!ringOverlay.isVisible()) ringOverlay.setVisible(true);
    }

    private void resetThreeCheckState() {
        whiteChecksDelivered = 0;
        blackChecksDelivered = 0;
        long now = System.currentTimeMillis();
        whiteKingRingPhaseStartMs = now;
        blackKingRingPhaseStartMs = now;
        whiteKingRingStage = 0;
        blackKingRingStage = 0;
        clearCheckFlashState();
        updateThreeCheckRingTimer();
    }

    private void clearCheckFlashState() {
        checkFlashKingRow = -1;
        checkFlashKingCol = -1;
        checkFlashStep = -1;
        checkFlashPrimaryColor = null;
        checkFlashFinalColor = null;
    }

    private void stopCheckFlashTimer() {
        if (checkFlashTimer != null) {
            checkFlashTimer.stop();
            checkFlashTimer = null;
        }
    }

    private void startCheckFlashSequence(int kingRow, int kingCol, Color primary, Color fin) {
        stopCheckFlashTimer();
        checkFlashKingRow = kingRow;
        checkFlashKingCol = kingCol;
        checkFlashPrimaryColor = primary;
        checkFlashFinalColor = fin;
        checkFlashStep = 0; // primary -> off -> primary -> final
        repaint();

        checkFlashTimer = new Timer(160, null);
        checkFlashTimer.addActionListener(e -> {
            checkFlashStep++;
            if (checkFlashStep > 3) {
                stopCheckFlashTimer();
                clearCheckFlashState();
            }
            repaint();
        });
        checkFlashTimer.start();
    }

    private Color getCheckFlashColor() {
        if (checkFlashStep == 0 || checkFlashStep == 2) return checkFlashPrimaryColor;
        if (checkFlashStep == 3) return checkFlashFinalColor;
        return null; // off-step
    }

    private void startFogAnimation() {
        ensureFogTexture();
        if (fogAnimationTimer == null) {
            fogAnimationTimer = new Timer(AnimationTiming.FRAME_DELAY_MS, e -> {
                long nowNanos = System.nanoTime();
                double elapsedSeconds = fogLastFrameNanos == 0L
                        ? AnimationTiming.FRAME_DELAY_MS / 1000.0
                        : Math.min(0.05, (nowNanos - fogLastFrameNanos) / 1_000_000_000.0);
                fogLastFrameNanos = nowNanos;
                fogPhaseX += 20.0 * elapsedSeconds;
                fogPhaseY += 8.75 * elapsedSeconds;
                if (fogPhaseX > 100000) fogPhaseX = 0.0;
                if (fogPhaseY > 100000) fogPhaseY = 0.0;
                if (fogRippleActive
                        && System.currentTimeMillis() - fogRippleStartedAtMs
                                >= FOG_RIPPLE_CHARGE_MS + FOG_RIPPLE_DURATION_MS) {
                    fogRippleActive = false;
                }
                advanceFogTransition();
                repaint();
                if (!fogRippleActive && fogTargetAlpha <= 0.0f && fogVisualAlpha <= 0.001f) {
                    stopFogAnimation();
                }
            });
            fogAnimationTimer.setCoalesce(true);
            fogAnimationTimer.setInitialDelay(0);
        }
        if (!fogAnimationTimer.isRunning()) {
            fogLastFrameNanos = System.nanoTime();
            fogAnimationTimer.start();
        }
    }

    private void stopFogAnimation() {
        if (fogAnimationTimer != null) {
            fogAnimationTimer.stop();
        }
        fogLastFrameNanos = 0L;
    }

    private void advanceFogTransition() {
        if (Math.abs(fogVisualAlpha - fogTargetAlpha) < 0.001f) {
            fogVisualAlpha = fogTargetAlpha;
            return;
        }
        if (fogVisualAlpha < fogTargetAlpha) {
            fogVisualAlpha = Math.min(fogTargetAlpha, fogVisualAlpha + FOG_FADE_STEP);
        } else {
            fogVisualAlpha = Math.max(fogTargetAlpha, fogVisualAlpha - FOG_FADE_STEP);
        }
    }

    private void updateFogTransitionTarget(boolean viewerIsWhite) {
        // Fog of War: once the game is over the fog fades away and both sides see everything
        boolean fogRulesActive = fogOfWarLive() || isSpellFogActiveForViewer(viewerIsWhite);
        fogTargetAlpha = fogRulesActive ? 1.0f : 0.0f;
        if (fogTargetAlpha > 0.0f || fogVisualAlpha > 0.001f) {
            startFogAnimation();
        } else {
            stopFogAnimation();
        }
    }
    
    // ── premoves ───────────────────────────────────────────────────────────
    //
    // While it is the opponent's turn (bot or online games) you can queue moves.
    // They show on the board as if already played (pieces sit on their new squares,
    // from/to squares tinted red) and you can keep chaining from that picture. When
    // the opponent's move has fully landed and the turn is yours, the first one is
    // played (if it is legal right then), otherwise the whole queue is dropped.
    // Right-click, or a click on an empty square / enemy piece, cancels them all.

    /** Drops every queued premove. */
    public void clearPremoves() {
        if (premoveQueue.isEmpty()) return;
        premoveQueue.clear();
        repaint();
    }

    private void clearPremove() {
        clearPremoves();
    }

    /** Games where premoves exist at all: real games (bot, online or two players), plain-turn variants. */
    private boolean premoveGameSupported() {
        return gameController != null && !analysisMode
                && !isSpellChessMode() && !isDuckChessMode();
    }

    /** Two players sharing this board: whoever is waiting may premove. */
    private boolean isPassAndPlay() {
        return !onlineMode && !isBotGame;
    }

    /** In a two-player game: the side whose premoves are queued (only meaningful while some are). */
    private boolean premoveOwnerWhite;

    /** The side that premoves: you (bot/online), or in a two-player game the side waiting for its turn. */
    private boolean premoverWhite() {
        if (isBotGame) return playerIsWhite;
        if (onlineMode) return localIsWhite;
        return premoveQueue.isEmpty() ? !whiteTurn : premoveOwnerWhite;
    }

    private boolean isPremoverPiece(Piece p) {
        return p != null && p.isWhite() == premoverWhite();
    }

    private boolean isPremoverTurn() {
        return whiteTurn == premoverWhite();
    }

    /** Fog of War is on and the game is still going (the fog lifts when it ends). */
    private boolean fogOfWarLive() {
        return fogOfWarEnabled && !premoveGameEnded();
    }

    private boolean premoveGameEnded() {
        return premovesBlockedGameEnded || (gameController != null && gameController.isGameOver());
    }

    /** True while the local player may queue premoves right now. */
    private boolean isPremoveModeActive() {
        return premovingEnabled && inputEnabled && premoveGameSupported()
                && !premoveGameEnded() && !isPremoverTurn() && !viewingPast;
    }

    /**
     * The position as it will look once every queued premove is played -- what the
     * board shows, and what new premoves are made from. The board itself when none.
     */
    private Piece[][] premoveView() {
        if (premoveQueue.isEmpty() || viewingPast) return board;
        Piece[][] v = copyOf(board);
        for (Premove pm : premoveQueue) {
            Piece p = v[pm.fr][pm.fc];
            if (p == null || !isPremoverPiece(p)) break;   // the chain is broken from here on
            if (p instanceof King && pm.fr == pm.tr && Math.abs(pm.tc - pm.fc) == 2) {
                int rookFrom = pm.tc > pm.fc ? 7 : 0, rookTo = pm.tc > pm.fc ? 5 : 3;
                Piece rook = v[pm.fr][rookFrom];
                if (rook instanceof Rook && rook.isWhite() == p.isWhite()) {
                    v[pm.fr][rookFrom] = null;
                    v[pm.fr][rookTo] = rook;
                }
            }
            v[pm.fr][pm.fc] = null;
            v[pm.tr][pm.tc] = pm.promoPiece != null ? pm.promoPiece : p;
        }
        return v;
    }

    private boolean isMovedByQueuedPremove(Piece p) {
        for (Premove pm : premoveQueue) if (pm.piece == p) return true;
        return false;
    }

    /**
     * Where a piece may be premoved: everywhere it could go once the opponent has
     * moved, so other pieces never block (the opponent may move or trade them away),
     * pawns may always premove a capture, and a square holding your own piece is
     * allowed too (that's a recapture, if the opponent takes there first).
     */
    private ArrayList<int[]> premoveTargets(Piece[][] v, int r, int c) {
        ArrayList<int[]> out = new ArrayList<>();
        Piece p = v[r][c];
        if (p == null) return out;
        boolean white = p.isWhite();
        if (p instanceof Pawn) {
            int dir = white ? -1 : 1;
            addPremoveTarget(out, r, c, r + dir, c);
            if (r == (white ? 6 : 1)) addPremoveTarget(out, r, c, r + 2 * dir, c);
            addPremoveTarget(out, r, c, r + dir, c - 1);
            addPremoveTarget(out, r, c, r + dir, c + 1);
        } else if (p instanceof Knight) {
            int[][] jumps = {{1, 2}, {2, 1}, {-1, 2}, {-2, 1}, {1, -2}, {2, -1}, {-1, -2}, {-2, -1}};
            for (int[] j : jumps) addPremoveTarget(out, r, c, r + j[0], c + j[1]);
        } else if (p instanceof King) {
            for (int dr = -1; dr <= 1; dr++)
                for (int dc = -1; dc <= 1; dc++)
                    if (dr != 0 || dc != 0) addPremoveTarget(out, r, c, r + dr, c + dc);
            int home = white ? 7 : 0;
            if (!isChess960() && r == home && c == 4 && !((King) p).hasMoved() && !isMovedByQueuedPremove(p)) {
                for (int side = 0; side < 2; side++) {
                    int rookCol = side == 0 ? 7 : 0;
                    Piece rook = v[home][rookCol];
                    if (rook instanceof Rook && rook.isWhite() == white && !((Rook) rook).hasMoved()
                            && !isMovedByQueuedPremove(rook)) {
                        addPremoveTarget(out, r, c, home, side == 0 ? 6 : 2);
                    }
                }
            }
        } else if (p instanceof Rook || p instanceof Bishop || p instanceof Queen) {
            boolean straight = !(p instanceof Bishop), diagonal = !(p instanceof Rook);
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
            for (int i = 0; i < dirs.length; i++) {
                if (i < 4 ? !straight : !diagonal) continue;
                for (int k = 1; k < 8; k++) {
                    int tr = r + dirs[i][0] * k, tc = c + dirs[i][1] * k;
                    if (!inBounds(tr, tc)) break;
                    addPremoveTarget(out, r, c, tr, tc);
                }
            }
        } else {
            out.addAll(getPseudoMovesForPieceOnBoard(r, c, v));
        }
        return out;
    }

    private void addPremoveTarget(ArrayList<int[]> out, int r, int c, int tr, int tc) {
        if (!inBounds(tr, tc) || (tr == r && tc == c)) return;
        for (int[] m : out) if (m[2] == tr && m[3] == tc) return;
        out.add(new int[]{r, c, tr, tc});
    }

    private static Piece promotionPiece(String symbol, boolean white) {
        if ("R".equals(symbol)) return new Rook(white);
        if ("B".equals(symbol)) return new Bishop(white);
        if ("N".equals(symbol)) return new Knight(white);
        return new Queen(white);
    }

    private boolean isPremoveSelection() {
        return selectedPiece != null && isPremoveModeActive() && isPremoverPiece(selectedPiece);
    }

    private void dropSelection() {
        selectedPiece = null;
        possibleMoves.clear();
        dragging = false;
        draggedPiece = null;
        wasDragged = false;
        clickedSelectedOnPress = false;
        setCursor(Cursor.getDefaultCursor());
    }

    /** Selects a piece for premoving, from the premove picture of the board. */
    private void selectForPremove(Piece[][] view, int r, int c) {
        selectedPiece = view[r][c];
        selectedRow = r;
        selectedCol = c;
        draggedPiece = selectedPiece;
        tabPieceSelectionConfirmed = false;
        dragX = pressX;
        dragY = pressY;
        possibleMoves.clear();
        possibleMoves.addAll(premoveTargets(view, r, c));
        repaint();
    }

    /** Adds a premove for the selected piece (asking for the promotion piece now, if needed). */
    private void queuePremoveFromSelection(int toRow, int toCol) {
        Piece piece = selectedPiece;
        int fr = selectedRow, fc = selectedCol;
        boolean isTarget = false;
        for (int[] m : possibleMoves) {
            if (m[0] == fr && m[1] == fc && m[2] == toRow && m[3] == toCol) { isTarget = true; break; }
        }
        dropSelection();
        repaint();
        if (!isTarget || piece == null || premoveQueue.size() >= MAX_PREMOVES) return;

        String promo = null;
        Piece promoPiece = null;
        if (piece instanceof Pawn && toRow == (piece.isWhite() ? 0 : 7)) {
            promo = autoQueenEnabled ? "Q" : showPromotionPicker(piece.isWhite(), toRow, toCol);
            if (promo == null || promo.isEmpty()) promo = "Q";
            promoPiece = promotionPiece(promo, piece.isWhite());
            // the picker is a window: the game may have moved on while it was open
            if (!premovingEnabled || !inputEnabled || !premoveGameSupported() || premoveGameEnded()
                    || viewingPast || premoveView()[fr][fc] != piece) {
                repaint();
                return;
            }
        }
        if (premoveQueue.isEmpty()) premoveOwnerWhite = piece.isWhite();
        else if (piece.isWhite() != premoveOwnerWhite) return;      // never mix both sides' premoves
        premoveQueue.add(new Premove(fr, fc, toRow, toCol, piece, promo, promoPiece));
        SoundManager.playPremove();                      // a quiet click: it's queued (in the piece set's sound)
        repaint();
        // already our turn (the opponent moved while the picker was open): play it now
        if (isPremoverTurn()) schedulePremoveRun();
    }

    private void schedulePremoveRun() {
        premoveRetryCount = 0;
        SwingUtilities.invokeLater(this::runQueuedPremove);
    }

    /**
     * Runs once the opponent's move has completely landed and the turn is ours:
     * plays the first queued premove if it is legal right now, else drops them all.
     */
    private void runQueuedPremove() {
        if (premoveQueue.isEmpty()) {
            refreshSelectionAfterPositionChange();
            return;
        }
        if (!premovingEnabled || !inputEnabled || !premoveGameSupported() || premoveGameEnded()) {
            clearPremoves();
            refreshSelectionAfterPositionChange();
            return;
        }
        if (!isPremoverTurn()) {               // not ours (yet): leave the queue for later
            refreshSelectionAfterPositionChange();
            return;
        }
        if (animating || isDuckPlacementAnimating()) {
            if (++premoveRetryCount > 300) { clearPremoves(); refreshSelectionAfterPositionChange(); return; }
            Timer retry = new Timer(15, e -> runQueuedPremove());
            retry.setRepeats(false);
            retry.start();
            return;
        }
        if (viewingPast) snapToLatestPosition();

        Premove pm = premoveQueue.get(0);
        Piece onSquare = board[pm.fr][pm.fc];
        boolean ok = onSquare != null && pm.piece != null
                && onSquare.isWhite() == pm.piece.isWhite()
                && onSquare.getClass() == pm.piece.getClass()
                && isPremoverPiece(onSquare);
        if (ok) {
            ok = false;
            for (int[] m : getActiveLegalMoves(whiteTurn)) {
                if (m[0] == pm.fr && m[1] == pm.fc && m[2] == pm.tr && m[3] == pm.tc) { ok = true; break; }
            }
        }
        if (!ok) {
            clearPremoves();
            refreshSelectionAfterPositionChange();
            return;
        }
        premoveQueue.remove(0);
        int before = committedMoveCount;
        premovePromotionSymbol = pm.promo;
        try {
            executeInstant(pm.fr, pm.fc, pm.tr, pm.tc);
        } finally {
            premovePromotionSymbol = null;
        }
        if (committedMoveCount == before) clearPremoves();   // it didn't go through after all
        refreshSelectionAfterPositionChange();
        repaint();
    }

    /** Keeps a piece you are holding/selected valid after the position changed under it. */
    private void refreshSelectionAfterPositionChange() {
        if (selectedPiece == null) return;
        if (!inBounds(selectedRow, selectedCol)) { dropSelection(); repaint(); return; }
        if (isPremoveModeActive() && isPremoverPiece(selectedPiece)) {
            Piece[][] v = premoveView();
            if (v[selectedRow][selectedCol] == selectedPiece) {
                possibleMoves.clear();
                possibleMoves.addAll(premoveTargets(v, selectedRow, selectedCol));
            } else {
                dropSelection();
            }
        } else if (inputEnabled && isLocalPlayerTurn() && !premoveGameEnded()) {
            if (board[selectedRow][selectedCol] == selectedPiece && selectedPiece.isWhite() == whiteTurn) {
                possibleMoves.clear();
                for (int[] m : getActiveLegalMoves(whiteTurn))
                    if (m[0] == selectedRow && m[1] == selectedCol) possibleMoves.add(m);
            } else {
                dropSelection();
            }
        } else {
            dropSelection();
        }
        repaint();
    }

    /** Puts the live (latest) position back on the board straight away, no animation. */
    private void snapToLatestPosition() {
        pendingHistoryTargetIndex = -1;
        int latest = getLatestStateIndex();
        if (latest < 0 || latest >= boardStates.size()) return;
        if (!viewingPast && currentStateIndex == latest) return;
        Piece[][] s = boardStates.get(latest);
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) board[r][c] = s[r][c];
        currentStateIndex = latest;
        restoreTurnStateAt(latest);
        if (latest < threeCheckStates.size()) {
            int[] counts = threeCheckStates.get(latest);
            whiteChecksDelivered = counts[0];
            blackChecksDelivered = counts[1];
        }
        viewingPast = false;
        if (gameController != null) gameController.onPositionChanged(latest);
        repaint();
    }

    /** Called after every committed move: ends premoves with the game, or plays the next one. */
    private void afterMoveCommitted() {
        if (!premoveGameSupported()) return;
        boolean ended = !inputEnabled || premoveGameEnded()
                || getActiveLegalMoves(whiteTurn).isEmpty() || getAutomaticDrawReason() != null;
        if (ended) {
            premovesBlockedGameEnded = true;
            clearPremoves();
            if (selectedPiece != null) { dropSelection(); repaint(); }
            return;
        }
        schedulePremoveRun();      // plays a premove if it's now the premover's turn; else just tidies the selection
    }

    /** A fresh position (new game, takeback, loaded position): no premoves carried over. */
    private void resetPremoveState() {
        premoveQueue.clear();
        premovesBlockedGameEnded = false;
        premovePromotionSymbol = null;
    }

    private boolean isLocalPlayerTurn() {
        if (isBotGame) return whiteTurn == playerIsWhite;
        if (onlineMode) return whiteTurn == localIsWhite;
        return true;
    }

    private boolean isLocalPlayerPiece(Piece p) {
        if (p == null) return false;
        if (isBotGame) return p.isWhite() == playerIsWhite;
        if (onlineMode) return p.isWhite() == localIsWhite;
        return p.isWhite() == whiteTurn;
    }


    /** Whose eyes the fog is seen through (the player, or in a two-player game the side to move). */
    public boolean isFogViewerWhite() { return getFogViewerIsWhite(); }

    private boolean getFogViewerIsWhite() {
        if (isBotGame) return playerIsWhite;
        if (onlineMode) return localIsWhite;
        return whiteTurn;
    }

    private boolean isSpellFogActiveForViewer(boolean viewerIsWhite) {
        if (!isSpellChessMode()) return false;
        return getPlayerState(viewerIsWhite).getFogTurnsRemaining() > 0;
    }

    private boolean[][] computeVisibilityMap(boolean viewerIsWhite) {
        boolean[][] visible = new boolean[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p == null || p.isWhite() != viewerIsWhite) continue;
                visible[r][c] = true;
                addVisionForPiece(visible, p, r, c);
            }
        }
        return visible;
    }

    private void addVisionForPiece(boolean[][] visible, Piece p, int row, int col) {
        if (p instanceof Pawn) {
            int dir = p.isWhite() ? -1 : 1;
            int oneRow = row + dir;
            if (inBounds(oneRow, col) && board[oneRow][col] == null) {
                visible[oneRow][col] = true;
                int startRow = p.isWhite() ? 6 : 1;
                int twoRow = row + 2 * dir;
                if (row == startRow && inBounds(twoRow, col) && board[twoRow][col] == null) {
                    visible[twoRow][col] = true;
                }
            }
            if (inBounds(oneRow, col - 1)) visible[oneRow][col - 1] = true;
            if (inBounds(oneRow, col + 1)) visible[oneRow][col + 1] = true;
            return;
        }

        if (p instanceof Knight) {
            int[][] deltas = {
                {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
                {1, -2}, {1, 2}, {2, -1}, {2, 1}
            };
            for (int[] d : deltas) {
                int nr = row + d[0], nc = col + d[1];
                if (inBounds(nr, nc)) visible[nr][nc] = true;
            }
            return;
        }

        if (p instanceof King) {
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    int nr = row + dr, nc = col + dc;
                    if (inBounds(nr, nc)) visible[nr][nc] = true;
                }
            }
            return;
        }

        if (p instanceof Bishop || p instanceof Rook || p instanceof Queen) {
            if (p instanceof Bishop || p instanceof Queen) {
                addRayVision(visible, row, col, -1, -1);
                addRayVision(visible, row, col, -1, 1);
                addRayVision(visible, row, col, 1, -1);
                addRayVision(visible, row, col, 1, 1);
            }
            if (p instanceof Rook || p instanceof Queen) {
                addRayVision(visible, row, col, -1, 0);
                addRayVision(visible, row, col, 1, 0);
                addRayVision(visible, row, col, 0, -1);
                addRayVision(visible, row, col, 0, 1);
            }
        }
    }

    private void addRayVision(boolean[][] visible, int row, int col, int dr, int dc) {
        int r = row + dr, c = col + dc;
        while (inBounds(r, c)) {
            visible[r][c] = true;
            if (board[r][c] != null) break;
            r += dr;
            c += dc;
        }
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < 8 && c >= 0 && c < 8;
    }

    private int[] findPiecePosition(Piece target) {
        if (target == null) return null;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == target) return new int[]{r, c};
            }
        }
        return null;
    }

    private boolean isCaptureIndicatorMove(int[] move) {
        if (move == null || move.length < 4) return false;
        int fromRow = move[0], fromCol = move[1], toRow = move[2], toCol = move[3];
        if (!inBounds(toRow, toCol)) return false;
        if (board[toRow][toCol] != null) return true;
        if (fromRow < 0 || fromCol < 0 || fromRow > 7 || fromCol > 7) return false;
        Piece mover = board[fromRow][fromCol];
        if (!(mover instanceof Pawn)) return false;
        return fromCol != toCol;
    }

    private boolean shouldShowFireCaptureIcon(boolean captureMove) {
        if (!captureMove) return false;
        if (isAtomicMode()) return true;
        if (isSpellChessMode()) {
            if (SpellManager.FIREBALL.equals(pendingSpellTargetingId)) return true;
            if (selectedPiece instanceof Rook
                    && selectedPiece.isWhite() == whiteTurn
                    && selectedPiece.getBombRookTurnsRemaining() > 0) return true;
        }
        return false;
    }

    private void drawFireIcon(Graphics2D g2d, int x, int y, int size) {
        Graphics2D gf = (Graphics2D) g2d.create();
        gf.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int outerW = size;
        int outerH = size + 4;
        int outerX = x;
        int outerY = y + 1;
        int[] ox = {outerX + outerW / 2, outerX + outerW - 1, outerX + outerW / 2, outerX + 1};
        int[] oy = {outerY, outerY + outerH / 2, outerY + outerH, outerY + outerH / 2};
        gf.setColor(new Color(214, 56, 25, 235));
        gf.fillPolygon(ox, oy, 4);

        int midW = Math.max(6, size - 4);
        int midH = Math.max(8, size);
        int midX = x + (size - midW) / 2;
        int midY = y + 3;
        int[] mx = {midX + midW / 2, midX + midW - 1, midX + midW / 2, midX + 1};
        int[] my = {midY, midY + midH / 2, midY + midH, midY + midH / 2};
        gf.setColor(new Color(245, 144, 29, 240));
        gf.fillPolygon(mx, my, 4);

        int inW = Math.max(4, size - 8);
        int inH = Math.max(6, size - 4);
        int inX = x + (size - inW) / 2;
        int inY = y + 6;
        int[] ix = {inX + inW / 2, inX + inW - 1, inX + inW / 2, inX + 1};
        int[] iy = {inY, inY + inH / 2, inY + inH, inY + inH / 2};
        gf.setColor(new Color(255, 225, 120, 245));
        gf.fillPolygon(ix, iy, 4);

        // Periodic glaze sweep over the icon (top-left -> bottom-right).
        Polygon outerDiamond = new Polygon(ox, oy, 4);
        Shape oldClip = gf.getClip();
        gf.setClip(outerDiamond);
        float phase = (System.currentTimeMillis() % 2900L) / 2900f;
        float sweep = x - size + phase * (size * 3.0f);
        float bandHalf = Math.max(2.0f, size * 0.18f);
        float cx = x + size * 0.5f;
        float cy = y + (size + 4) * 0.5f;
        gf.rotate(Math.toRadians(45), cx, cy);
        LinearGradientPaint glaze = new LinearGradientPaint(
            sweep - bandHalf, cy, sweep + bandHalf, cy,
            new float[]{0f, 0.5f, 1f},
            new Color[]{
                new Color(255, 255, 255, 0),
                new Color(255, 250, 220, 185),
                new Color(255, 255, 255, 0)
            }
        );
        gf.setPaint(glaze);
        gf.fillRect(x - size * 2, y - size * 2, size * 5, size * 5);
        gf.setClip(oldClip);

        gf.dispose();
    }

    private void startAtomicExplosionFx(int centerRow, int centerCol) {
        if (!inBounds(centerRow, centerCol)) return;
        ensureExplosionOverlayFramesLoaded();
        if (explosionOverlayFrames.isEmpty()) return;
        long durationMs = getExplosionDurationMs();
        atomicExplosionFx.add(new AtomicExplosionFx(centerRow, centerCol, System.currentTimeMillis(), durationMs));
        ensureExplosionFxTimerRunning();
    }

    private long getTotalPieceExplosionDurationMs() {
        return PIECE_PRE_EXPLOSION_MS + PIECE_SHATTER_MS;
    }

    private void startPieceExplosionFx(Piece piece, int row, int col) {
        startPieceExplosionFx(piece, row, col, DEFAULT_PIECE_EXPLOSION_SOUND);
    }

    private void startPieceExplosionFx(Piece piece, int row, int col, String shatterSoundFile) {
        if (piece == null || !inBounds(row, col)) return;
        PieceExplosionFx fx = new PieceExplosionFx();
        fx.piece = piece;
        fx.row = row;
        fx.col = col;
        fx.startMs = System.currentTimeMillis();
        fx.preDurationMs = PIECE_PRE_EXPLOSION_MS;
        fx.shatterDurationMs = PIECE_SHATTER_MS;
        fx.shatterStartMs = fx.startMs + fx.preDurationMs;
        fx.shakeSeed = System.nanoTime() ^ (((long) row) << 16) ^ col;
        fx.shatterSoundFile = (shatterSoundFile == null) ? DEFAULT_PIECE_EXPLOSION_SOUND : shatterSoundFile.trim();
        fx.pieceSprite = createPieceSprite(piece);
        fx.shards = buildPieceShards(fx.pieceSprite);
        pieceExplosionFx.add(fx);
        ensureExplosionFxTimerRunning();
    }

    private void playPieceExplosionSound(PieceExplosionFx fx) {
        SoundManager.SoundHandle handle = SoundManager.playExtraSound(fx.shatterSoundFile);
        if (handle == null && !DEFAULT_PIECE_EXPLOSION_SOUND.equals(fx.shatterSoundFile)) {
            SoundManager.playExtraSound(DEFAULT_PIECE_EXPLOSION_SOUND);
        }
    }

    private void scheduleSpellChessSound(String fileName, int delayMs) {
        scheduleSpellChessSound(fileName, delayMs, 1.0f, 0);
    }

    private void scheduleSpellChessSound(String fileName, int delayMs, float volume, int fadeInMs) {
        Timer timer = new Timer(Math.max(0, delayMs), e -> {
            scheduledSpellSoundTimers.remove((Timer) e.getSource());
            playSpellChessSoundAsync(fileName, volume, fadeInMs);
        });
        timer.setRepeats(false);
        scheduledSpellSoundTimers.add(timer);
        timer.start();
    }

    private void playSpellChessSoundAsync(String fileName, float volume, int fadeInMs) {
        Thread t = new Thread(
            () -> SoundManager.playSpellChessSound(fileName, volume, fadeInMs),
            "spell-chess-sound-" + fileName
        );
        t.setDaemon(true);
        t.start();
    }

    public void prestartFireballPlaneSound() {
        fireballPlaneSoundPrestarted = true;
        playSpellChessSoundAsync(
                SPELL_CHESS_PLANE_SOUND,
                1.0f,
                SPELL_CHESS_SOUND_FADE_IN_MS);
    }

    private void scheduleSpellChessCrashSound(int delayMs) {
        Timer timer = new Timer(Math.max(0, delayMs), e -> {
            scheduledSpellSoundTimers.remove((Timer) e.getSource());
            SoundManager.stopSound(spellChessCrashSoundHandle);
            Thread t = new Thread(
                () -> spellChessCrashSoundHandle = SoundManager.playSpellChessSound(
                    SPELL_CHESS_CRASH_SOUND,
                    SPELL_CHESS_CRASH_SOUND_VOLUME,
                    SPELL_CHESS_SOUND_FADE_IN_MS
                ),
                "spell-chess-sound-" + SPELL_CHESS_CRASH_SOUND
            );
            t.setDaemon(true);
            t.start();
        });
        timer.setRepeats(false);
        scheduledSpellSoundTimers.add(timer);
        timer.start();
    }

    private void scheduleSpellChessNukeSound(int delayMs) {
        Timer timer = new Timer(Math.max(0, delayMs), e -> {
            scheduledSpellSoundTimers.remove((Timer) e.getSource());
            playSpellChessSoundAsync(SPELL_CHESS_NUKE_SOUND, 1.0f, 0);
            Timer stopCrashTimer = new Timer(SPELL_CHESS_CRASH_STOP_AFTER_NUKE_MS, stopEvent -> {
                scheduledSpellSoundTimers.remove((Timer) stopEvent.getSource());
                SoundManager.stopSound(spellChessCrashSoundHandle);
                spellChessCrashSoundHandle = null;
            });
            stopCrashTimer.setRepeats(false);
            scheduledSpellSoundTimers.add(stopCrashTimer);
            stopCrashTimer.start();
        });
        timer.setRepeats(false);
        scheduledSpellSoundTimers.add(timer);
        timer.start();
    }

    private void cancelScheduledSpellSounds() {
        for (Timer timer : new ArrayList<>(scheduledSpellSoundTimers)) {
            timer.stop();
        }
        scheduledSpellSoundTimers.clear();
        SoundManager.stopSound(spellChessCrashSoundHandle);
        spellChessCrashSoundHandle = null;
    }

    public void startFireballImpactFx(int row, int col) {
        startFireballImpactFx(row, col, null);
    }

    public void startFireballImpactFx(int row, int col, Piece victimPiece) {
        boolean casterWhite = (victimPiece != null) ? !victimPiece.isWhite() : whiteTurn;
        startFireballImpactFx(row, col, victimPiece, casterWhite);
    }

    public void startFireballImpactFx(int row, int col, Piece victimPiece, boolean casterWhite) {
        if (deferSpellVisualEffects) {
            PendingFireballVictim pending = new PendingFireballVictim(victimPiece, row, col);
            pendingFireballVictims.add(pending);
            deferSpellVisualEffect(() -> {
                pendingFireballVictims.remove(pending);
                startFireballImpactFx(row, col, victimPiece, casterWhite);
            });
            return;
        }
        if (!inBounds(row, col)) return;
        ensureFireballMissileFrames();
        ensureFireballImpactFlashSprite();
        ensureFireballSmokeSprite();
        ensureBomberShadowSilhouetteSprite();
        warmSmokeTintCache();

        boolean shadowTopToBottom = isSideDisplayedAtTop(casterWhite);
        long scaledFireballDurationMs = Math.max(
            1L,
            Math.round(FIREBALL_OVERLAY_DURATION_MS / Math.max(0.01f, FIREBALL_TRAVEL_SPEED_SCALE))
        );
        fireballImpactFx.add(new FireballImpactFx(
            row,
            col,
            System.currentTimeMillis() + FIREBALL_START_DELAY_MS + SPELL_CHESS_NUKE_VISUAL_DELAY_MS,
            scaledFireballDurationMs,
            victimPiece,
            shadowTopToBottom
        ));
        ensureExplosionFxTimerRunning();

        float nukeImpactT = Math.max(
            0.05f,
            Math.min(FIREBALL_IMPACT_T, FIREBALL_IMPACT_T / Math.max(0.01f, FIREBALL_NUKE_TRAVEL_BOOST))
        );
        int impactDelayMs = FIREBALL_START_DELAY_MS + Math.round(scaledFireballDurationMs * nukeImpactT);
        int cloudDelayMs = impactDelayMs + SPELL_CHESS_NUKE_VISUAL_DELAY_MS + (int) FIREBALL_SMOKE_DELAY_MS;
        if (fireballPlaneSoundPrestarted) {
            fireballPlaneSoundPrestarted = false;
        } else {
            scheduleSpellChessSound(
                    SPELL_CHESS_PLANE_SOUND,
                    SPELL_CHESS_PLANE_SOUND_DELAY_MS,
                    1.0f,
                    SPELL_CHESS_SOUND_FADE_IN_MS);
        }
        scheduleSpellChessCrashSound(SPELL_CHESS_CRASH_SOUND_DELAY_MS);
        scheduleSpellChessNukeSound(cloudDelayMs);
    }

    private void startBomberCloudFx(int row, int col) {
        if (!inBounds(row, col)) return;
        ensureFireballSmokeSprite();
        warmSmokeTintCache();
        FireballImpactFx fx = new FireballImpactFx(
                row,
                col,
                System.currentTimeMillis(),
                1L,
                null,
                false);
        fx.cloudOnly = true;
        fx.cloudScale = 0.72f;
        fireballImpactFx.add(fx);
        ensureExplosionFxTimerRunning();
        playSpellChessSoundAsync(SPELL_CHESS_NUKE_SOUND, 0.75f, 0);
    }

    private long getExplosionDurationMs() {
        // Compensate for 60 FPS frame rate by doubling duration to maintain animation speed
        return Math.max(EXPLOSION_FRAME_MS, (long) explosionOverlayFrames.size() * EXPLOSION_FRAME_MS * 2);
    }

    private void ensureExplosionOverlayFramesLoaded() {
        if (explosionOverlayFramesLoaded) return;
        explosionOverlayFramesLoaded = true;
        explosionOverlayFrames.clear();
        explosionOverlayMaxAspectRatio = 1.0;

        String[] frameDirs = new String[] {
            "src/assets/extras/frames",
            "assets/extras/frames",
            "frames"
        };

        for (String dirPath : frameDirs) {
            try {
                File dir = new File(dirPath);
                if (!dir.exists() || !dir.isDirectory()) continue;
                File[] pngFiles = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));
                if (pngFiles == null || pngFiles.length == 0) continue;
                Arrays.sort(pngFiles, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                int targetCount = Math.min(MAX_EXPLOSION_OVERLAY_FRAMES, pngFiles.length);
                int step = Math.max(1, (int) Math.ceil(pngFiles.length / (double) targetCount));
                for (int i = 0; i < pngFiles.length && explosionOverlayFrames.size() < targetCount; i += step) {
                    File png = pngFiles[i];
                    BufferedImage img = ImageIO.read(png);
                    if (img != null) {
                        img = downscaleExplosionFrameIfNeeded(img);
                        explosionOverlayFrames.add(img);
                        if (img.getHeight() > 0) {
                            double aspect = img.getWidth() / (double) img.getHeight();
                            if (aspect > explosionOverlayMaxAspectRatio) {
                                explosionOverlayMaxAspectRatio = aspect;
                            }
                        }
                    }
                }
                if (!explosionOverlayFrames.isEmpty()) return;
            } catch (Exception ignored) {
            }
        }
    }

    private BufferedImage downscaleExplosionFrameIfNeeded(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int maxEdge = Math.max(w, h);
        if (maxEdge <= MAX_EXPLOSION_FRAME_EDGE) return src;

        double scale = MAX_EXPLOSION_FRAME_EDGE / (double) maxEdge;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resized.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(src, 0, 0, nw, nh, null);
        g2.dispose();
        return resized;
    }

    private BufferedImage applyBlurToFrame(BufferedImage frame, int frameIndex) {
        // Check cache first
        if (blurredExplosionFrameCache.containsKey(frameIndex)) {
            return blurredExplosionFrameCache.get(frameIndex);
        }
        
        // Create blur kernel
        int radius = Math.round(EXPLOSION_OVERLAY_BLUR_RADIUS);
        int size = 2 * radius + 1;
        float[] blurKernel = new float[size * size];
        float blurValue = 1.0f / (size * size);
        for (int i = 0; i < blurKernel.length; i++) {
            blurKernel[i] = blurValue;
        }
        
        // Apply blur
        Kernel kernel = new Kernel(size, size, blurKernel);
        ConvolveOp blurOp = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        BufferedImage blurredFrame = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
        blurOp.filter(frame, blurredFrame);
        
        // Cache the result
        blurredExplosionFrameCache.put(frameIndex, blurredFrame);
        return blurredFrame;
    }

    public void beginSpellSimulation() {
        spellSimulationDepth++;
    }

    public void endSpellSimulation() {
        if (spellSimulationDepth > 0) spellSimulationDepth--;
    }

    public boolean isSpellSimulationActive() {
        return spellSimulationDepth > 0;
    }

    public void onShieldApplied(Piece piece, int row, int col) {
        if (deferSpellVisualEffect(() -> onShieldApplied(piece, row, col))) return;
        if (piece == null || !inBounds(row, col)) return;
        SoundManager.playExtraSound("shield_on.wav");
        addShieldFx(piece, row, col, true);
    }

    public void onFreezeApplied(Piece piece, int row, int col) {
        if (deferSpellVisualEffects) {
            pendingFreezeVisualPieces.add(piece);
            deferSpellVisualEffect(() -> {
                pendingFreezeVisualPieces.remove(piece);
                onFreezeApplied(piece, row, col);
            });
            return;
        }
        if (piece == null || !inBounds(row, col)) return;
        freezeVisualFx.removeIf(fx -> fx.piece == piece);
        freezeBurstFx.removeIf(fx -> fx.piece == piece);
        freezeBurstFx.add(new FreezeBurstFx(piece, row, col, System.currentTimeMillis()));
        ensureExplosionFxTimerRunning();
    }

    private void onShieldRemoved(Piece piece, int row, int col) {
        if (piece == null || !inBounds(row, col)) return;
        addShieldFx(piece, row, col, false);
    }

    private void addShieldFx(Piece piece, int row, int col, boolean fadeIn) {
        shieldVisualFx.removeIf(fx -> fx.piece == piece);
        shieldVisualFx.add(new ShieldVisualFx(piece, row, col, System.currentTimeMillis(), SHIELD_FADE_MS, fadeIn));
        ensureExplosionFxTimerRunning();
    }

    private void onFreezeRemoved(Piece piece, int row, int col) {
        if (piece == null || !inBounds(row, col)) return;
        addFreezeFx(piece, row, col, false);
    }

    private void addFreezeFx(Piece piece, int row, int col, boolean fadeIn) {
        freezeVisualFx.removeIf(fx -> fx.piece == piece);
        freezeVisualFx.add(new FreezeVisualFx(piece, row, col, System.currentTimeMillis(), FREEZE_FADE_MS, fadeIn));
        ensureExplosionFxTimerRunning();
    }

    private void ensureExplosionFxTimerRunning() {
        if (atomicExplosionTimer == null) {
            atomicExplosionTimer = new Timer(FX_TIMER_DELAY_MS, e -> {
                long t = System.currentTimeMillis();
                atomicExplosionFx.removeIf(fx -> t - fx.startMs > fx.durationMs);
                fireballImpactFx.removeIf(fx -> t - fx.startMs > getFireballImpactFxLifetimeMs(fx));
                if (!fireballImpactFx.isEmpty()) {
                    ensureFireballOverlayPanel();
                }
                // Clear cached overlay buffer when all effects are done
                if (fireballImpactFx.isEmpty()) {
                    cachedImpactOverlay = null;
                    cachedImpactBlurOverlay = null;
                    cachedImpactBlurOp = null;
                    cachedOverlayWidth = -1;
                    cachedOverlayHeight = -1;
                }
                shieldVisualFx.removeIf(fx -> t - fx.startMs > fx.durationMs);
                freezeVisualFx.removeIf(fx -> t - fx.startMs > fx.durationMs);
                freezeBurstFx.removeIf(fx -> t - fx.startMs > FREEZE_BURST_MS);
                urielResurrectionFx.removeIf(fx -> t - fx.startMs > fx.durationMs);
                for (PieceExplosionFx fx : pieceExplosionFx) {
                    if (!fx.shatterStarted && t - fx.startMs >= fx.preDurationMs) {
                        fx.shatterStarted = true;
                        fx.shatterStartMs = t;
                        fx.shatterSprite = createGlowingShatterSprite(fx.pieceSprite);
                        fx.shards = buildPieceShards(fx.shatterSprite != null ? fx.shatterSprite : fx.pieceSprite);
                        if (inBounds(fx.row, fx.col) && board[fx.row][fx.col] == fx.piece) {
                            board[fx.row][fx.col] = null;
                        }
                        if (!fx.shatterSoundFile.isEmpty()) {
                            playPieceExplosionSound(fx);
                        }
                    }
                }
                pieceExplosionFx.removeIf(fx -> {
                    if (!fx.shatterStarted) return false;
                    long totalMs = fx.shatterDurationMs + PIECE_POST_DARK_HOLD_MS + PIECE_POST_DARK_FADE_MS;
                    return t - fx.shatterStartMs > totalMs;
                });

                boolean boardRepaintNeeded = !atomicExplosionFx.isEmpty()
                        || !shieldVisualFx.isEmpty()
                        || !freezeVisualFx.isEmpty()
                        || !freezeBurstFx.isEmpty()
                        || !urielResurrectionFx.isEmpty()
                        || !pieceExplosionFx.isEmpty()
                        || hasActiveFireballBoardShake(t);
                Rectangle currentBounds = boardRepaintNeeded ? getCurrentExplosionBounds(false) : null;
                Rectangle dirty = unionRect(lastExplosionDirtyBounds, currentBounds);
                if (boardRepaintNeeded) {
                    if (dirty != null) {
                        repaint(dirty.x, dirty.y, dirty.width, dirty.height);
                    } else {
                        repaint();
                    }
                }
                if (fireballOverlayPanel != null) {
                    JLayeredPane layered = fireballOverlayLayer;
                    if (layered != null) {
                        fireballOverlayPanel.setBounds(0, 0, layered.getWidth(), layered.getHeight());
                    }
                    Point boardOrigin = SwingUtilities.convertPoint(this, new Point(0, 0), fireballOverlayPanel);
                    int boardPixels = Math.max(1, (int) Math.round(BOARD_SIZE * renderScale()));
                    fireballOverlayPanel.repaint(boardOrigin.x, boardOrigin.y, boardPixels, boardPixels);
                }
                lastExplosionDirtyBounds = currentBounds;

                if (atomicExplosionFx.isEmpty() && fireballImpactFx.isEmpty() && shieldVisualFx.isEmpty()
                        && freezeVisualFx.isEmpty() && freezeBurstFx.isEmpty()
                        && urielResurrectionFx.isEmpty() && pieceExplosionFx.isEmpty()) {
                    atomicExplosionTimer.stop();
                    lastExplosionDirtyBounds = null;
                    if (fireballOverlayPanel != null && fireballOverlayLayer != null) {
                        fireballOverlayLayer.remove(fireballOverlayPanel);
                        fireballOverlayPanel = null;
                        fireballOverlayLayer.revalidate();
                        fireballOverlayLayer.repaint();
                        fireballOverlayLayer = null;
                    }
                }
            });
            atomicExplosionTimer.setCoalesce(true);
        }
        if (!atomicExplosionTimer.isRunning()) {
            atomicExplosionTimer.start();
        }
    }

    private long getFireballImpactFxLifetimeMs(FireballImpactFx fx) {
        if (fx.cloudOnly) return getFireballSmokeTotalDurationMs();
        long impactMs = Math.round(fx.durationMs * Math.max(0.05f, Math.min(FIREBALL_IMPACT_T, FIREBALL_IMPACT_T / Math.max(0.01f, FIREBALL_NUKE_TRAVEL_BOOST))));
        long shadowDurationMs = Math.max(
            1L,
            Math.round(FIREBALL_OVERLAY_DURATION_MS / Math.max(0.01f, FIREBALL_BASE_TRAVEL_SPEED_SCALE * FIREBALL_BOMBER_SPEED_MULT))
        );
        long vignetteEndMs = impactMs + getFireballSmokeTotalDurationMs();
        long flashEndMs = impactMs + FIREBALL_IMPACT_FLASH_DURATION_MS;
        return Math.max(Math.max(fx.durationMs, shadowDurationMs), Math.max(vignetteEndMs, flashEndMs));
    }

    private long getFireballSmokeTotalDurationMs() {
        return Math.max(FIREBALL_SMOKE_DURATION_MS, FIREBALL_SMALL_SMOKE_DURATION_MS);
    }

    private Rectangle unionRect(Rectangle a, Rectangle b) {
        if (a == null) return (b == null) ? null : new Rectangle(b);
        if (b == null) return new Rectangle(a);
        return a.union(b);
    }

    private Rectangle getCurrentExplosionBounds(boolean includeFireball) {
        Rectangle out = null;
        for (AtomicExplosionFx fx : atomicExplosionFx) {
            out = unionRect(out, getAtomicFxBounds(fx));
        }
        if (includeFireball) {
            for (FireballImpactFx fx : fireballImpactFx) {
                out = unionRect(out, getFireballFxBounds(fx));
            }
        }
        for (ShieldVisualFx fx : shieldVisualFx) {
            out = unionRect(out, getShieldFxBounds(fx));
        }
        for (FreezeVisualFx fx : freezeVisualFx) {
            out = unionRect(out, getFreezeFxBounds(fx));
        }
        for (UrielResurrectionFx fx : urielResurrectionFx) {
            out = unionRect(out, new Rectangle(0, 0, BOARD_SIZE, BOARD_SIZE));
        }
        for (PieceExplosionFx fx : pieceExplosionFx) {
            out = unionRect(out, getPieceExplosionFxBounds(fx));
        }
        return out;
    }

    private Rectangle getAtomicFxBounds(AtomicExplosionFx fx) {
        int dc = boardFlipped ? (7 - fx.centerCol) : fx.centerCol;
        int dr = boardFlipped ? (7 - fx.centerRow) : fx.centerRow;
        int cx = dc * SQUARE_SIZE + SQUARE_SIZE / 2;
        int cy = dr * SQUARE_SIZE + SQUARE_SIZE / 2;
        int drawH = SQUARE_SIZE * ATOMIC_FX_TILE_SPAN;
        int drawW = getExplosionMaxWidthForHeight(drawH);
        int x = cx - drawW / 2;
        int y = cy - drawH / 2;
        return new Rectangle(x, y, drawW, drawH);
    }

    private Rectangle getFireballFxBounds(FireballImpactFx fx) {
        int boardW = SQUARE_SIZE * 8;
        int boardH = SQUARE_SIZE * 8;
        return new Rectangle(0, 0, boardW, boardH);
    }

    private int getExplosionWidthForHeight(BufferedImage frame, int targetHeight) {
        if (targetHeight <= 0) return 1;
        if (frame == null || frame.getHeight() <= 0) return targetHeight;
        double aspect = frame.getWidth() / (double) frame.getHeight();
        return Math.max(1, (int) Math.round(targetHeight * aspect));
    }

    private int getExplosionMaxWidthForHeight(int targetHeight) {
        if (targetHeight <= 0) return 1;
        return Math.max(1, (int) Math.round(targetHeight * explosionOverlayMaxAspectRatio));
    }

    private Rectangle getShieldFxBounds(ShieldVisualFx fx) {
        int[] pos = findPiecePosition(fx.piece);
        int row = fx.anchorRow;
        int col = fx.anchorCol;
        if (pos != null) {
            row = pos[0];
            col = pos[1];
            fx.anchorRow = row;
            fx.anchorCol = col;
        }
        int dc = boardFlipped ? (7 - col) : col;
        int dr = boardFlipped ? (7 - row) : row;
        int x = dc * SQUARE_SIZE;
        int y = dr * SQUARE_SIZE;
        return new Rectangle(x - 8, y - 8, SQUARE_SIZE + 16, SQUARE_SIZE + 16);
    }

    private Rectangle getFreezeFxBounds(FreezeVisualFx fx) {
        int[] pos = findPiecePosition(fx.piece);
        int row = fx.anchorRow;
        int col = fx.anchorCol;
        if (pos != null) {
            row = pos[0];
            col = pos[1];
            fx.anchorRow = row;
            fx.anchorCol = col;
        }
        int dc = boardFlipped ? (7 - col) : col;
        int dr = boardFlipped ? (7 - row) : row;
        int x = dc * SQUARE_SIZE;
        int y = dr * SQUARE_SIZE;
        return new Rectangle(x - 8, y - 8, SQUARE_SIZE + 16, SQUARE_SIZE + 16);
    }

    private Rectangle getPieceExplosionFxBounds(PieceExplosionFx fx) {
        return new Rectangle(0, 0, BOARD_SIZE, BOARD_SIZE);
    }

    private void drawAtomicExplosionFx(Graphics2D g2d) {
        if (atomicExplosionFx.isEmpty()) return;
        if (explosionOverlayFrames.isEmpty()) return;
        long now = System.currentTimeMillis();
        Graphics2D ge = (Graphics2D) g2d.create();
        ge.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (AtomicExplosionFx fx : atomicExplosionFx) {
            long elapsedMs = now - fx.startMs;
            int frameIdx = (int) (elapsedMs / EXPLOSION_FRAME_MS);
            if (frameIdx < 0 || frameIdx >= explosionOverlayFrames.size()) continue;
            BufferedImage frame = explosionOverlayFrames.get(frameIdx);
            if (frame == null) continue;

            float tailFade = 1.0f;
            long baseFadeWindowMs = Math.min(EXPLOSION_FADE_TAIL_MS, fx.durationMs);
            long fadeWindowMs = Math.min(fx.durationMs, Math.round(baseFadeWindowMs * 1.55f));
            long fadeLeadMs = 900L; // Start fading a bit earlier, but keep a continuous fade to the full duration.
            long fadeStartMs = Math.max(0L, fx.durationMs - fadeWindowMs - fadeLeadMs);
            if (elapsedMs > fadeStartMs) {
                double fadeProgress = Math.max(0.0, Math.min(1.0, (elapsedMs - fadeStartMs) / (double) Math.max(1L, fx.durationMs - fadeStartMs)));
                tailFade = (float) Math.pow(1.0 - fadeProgress, 1.35);
            }
            int dc = boardFlipped ? (7 - fx.centerCol) : fx.centerCol;
            int dr = boardFlipped ? (7 - fx.centerRow) : fx.centerRow;
            int cx = dc * SQUARE_SIZE + SQUARE_SIZE / 2;
            int cy = dr * SQUARE_SIZE + SQUARE_SIZE / 2;
            int drawH = SQUARE_SIZE * ATOMIC_FX_TILE_SPAN;
            int drawW = getExplosionWidthForHeight(frame, drawH);
            int x = cx - drawW / 2;
            int y = cy - drawH / 2;
            ge.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, tailFade));
            
            // Apply blur to the overlay frame
            BufferedImage blurredFrame = applyBlurToFrame(frame, frameIdx);
            ge.drawImage(blurredFrame, x, y, drawW, drawH, null);
        }

        ge.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        ge.dispose();
    }

    private void drawFireballImpactFx(Graphics2D g2d) {
        drawFireballImpactFx(g2d, 0, 0, SQUARE_SIZE * 8, SQUARE_SIZE * 8);
    }

    private void drawFireballImpactFx(Graphics2D g2d, int boardOriginX, int boardOriginY, int boardW, int boardH) {
        if (fireballImpactFx.isEmpty()) return;
        ensureFireballMissileFrames();
        ensureFireballImpactFlashSprite();
        ensureFireballSmokeSprite();
        ensureBomberShadowSilhouetteSprite();
        long now = System.currentTimeMillis();
        Graphics2D ge = (Graphics2D) g2d.create();
        ge.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (FireballImpactFx fx : fireballImpactFx) {
            long elapsedMs = now - fx.startMs;
            if (fx.cloudOnly) {
                int dc = boardFlipped ? (7 - fx.col) : fx.col;
                int dr = boardFlipped ? (7 - fx.row) : fx.row;
                int cx = boardOriginX + dc * SQUARE_SIZE + SQUARE_SIZE / 2;
                int cy = boardOriginY + dr * SQUARE_SIZE + SQUARE_SIZE / 2;
                int span = SQUARE_SIZE * 3;
                Shape oldClip = ge.getClip();
                ge.clipRect(cx - span / 2, cy - span / 2, span, span);
                Graphics2D gc = (Graphics2D) ge.create();
                gc.translate(cx, cy);
                gc.scale(fx.cloudScale, fx.cloudScale);
                gc.translate(-cx, -cy);
                drawFireballSmokePlume(
                        gc,
                        fx,
                        boardOriginX,
                        boardOriginY,
                        0L,
                        Math.max(0L, elapsedMs));
                gc.dispose();
                ge.setClip(oldClip);
                continue;
            }
            float t = (float) (elapsedMs / (double) Math.max(1L, fx.durationMs));
            int dc = boardFlipped ? (7 - fx.col) : fx.col;
            int dr = boardFlipped ? (7 - fx.row) : fx.row;
            int squareX = boardOriginX + dc * SQUARE_SIZE;
            int squareY = boardOriginY + dr * SQUARE_SIZE;

            // Keep the target piece visible while waiting for delayed fireball start.
            if (elapsedMs < 0L) {
                if (fx.victimPiece != null) {
                    ge.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                    fx.victimPiece.draw(ge, squareX, squareY);
                }
                continue;
            }

            if (t > 1f) continue;

            float tailFade = 1.0f;
            long fadeWindowMs = Math.min(EXPLOSION_FADE_TAIL_MS, fx.durationMs);
            long fadeStartMs = fx.durationMs - fadeWindowMs;
            if (elapsedMs > fadeStartMs) {
                long remainingMs = fx.durationMs - elapsedMs;
                tailFade = (float) Math.max(0.0, Math.min(1.0, remainingMs / (double) fadeWindowMs));
            }

            int cx = squareX + SQUARE_SIZE / 2;
            int cy = squareY + SQUARE_SIZE / 2;
            float unit = SQUARE_SIZE * FIREBALL_OVERLAY_SCALE;
            float impactT = FIREBALL_IMPACT_T;
            float nukeImpactT = Math.max(0.05f, Math.min(impactT, impactT / Math.max(0.01f, FIREBALL_NUKE_TRAVEL_BOOST)));
            long impactMs = Math.round(fx.durationMs * nukeImpactT);

            if (fx.victimPiece != null) {
                // Keep victim fully visible until impact flash; then hide instantly.
                if (elapsedMs < impactMs) {
                    ge.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, tailFade));
                    fx.victimPiece.draw(ge, squareX, squareY);
                }
            }

            long shadowDurationMs = Math.max(
                1L,
                Math.round(FIREBALL_OVERLAY_DURATION_MS / Math.max(0.01f, FIREBALL_BASE_TRAVEL_SPEED_SCALE * FIREBALL_BOMBER_SPEED_MULT))
            );
            float shadowTimelineT = Math.max(0f, Math.min(1f, elapsedMs / (float) Math.max(1L, shadowDurationMs)));
            float shadowPlaneT = Math.max(0f, Math.min(1f, shadowTimelineT / Math.max(0.01f, impactT)));
            float offscreenApproach = SQUARE_SIZE * SPELL_CHESS_NUKE_APPROACH_START_BELOW_BOARD_SQ;
            float bomberStartY = fx.shadowTopToBottom ? -offscreenApproach : boardH + offscreenApproach;
            float bomberEndY = fx.shadowTopToBottom ? boardH + SQUARE_SIZE * 1.2f : -SQUARE_SIZE * 1.2f;
            float shadowY = bomberStartY + ((bomberEndY - bomberStartY) * shadowPlaneT);
            float planeX = cx;
            float missileTravelT = Math.max(0f, Math.min(1f, t / Math.max(0.01f, nukeImpactT)));

            long postImpactElapsedMs = Math.max(0L, elapsedMs - impactMs);
            long postImpactCloudElapsedMs = Math.max(0L, postImpactElapsedMs - FIREBALL_SMOKE_DELAY_MS);
            long vignetteEndMs = impactMs + getFireballSmokeTotalDurationMs();
            long effectEndMs = Math.max(Math.max(impactMs + FIREBALL_IMPACT_FLASH_DURATION_MS, shadowDurationMs), vignetteEndMs);
            if (elapsedMs > effectEndMs) {
                continue;
            }

            float shadowPulse = (float) Math.max(0.0, 1.0 - Math.abs(shadowPlaneT - 0.55f) * 1.6);
            float shadowFade = 1f;
            if (shadowTimelineT > 0.92f) {
                shadowFade = (float) Math.max(0.0, (1.0 - shadowTimelineT) / 0.08);
            }
            float approachReveal = Math.max(0f, Math.min(1f, missileTravelT / 0.16f));
            approachReveal = (float) (0.5 - 0.5 * Math.cos(Math.PI * approachReveal));
            float shadowAlpha = 0.70f * shadowPulse * shadowFade * tailFade * approachReveal;
            if (shadowAlpha > 0f) {
                int shadowW = Math.max(8, Math.round(SQUARE_SIZE * (1.3f - 0.25f * shadowPlaneT)));
                int shadowH = Math.max(6, Math.round(SQUARE_SIZE * (0.44f - 0.08f * shadowPlaneT)));
                ge.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, shadowAlpha))));
                int sx = Math.round(planeX - shadowW / 2f);
                int sy = Math.round(shadowY - shadowH / 2f);
                if (bomberShadowSilhouetteSprite != null) {
                    int drawW = Math.max(1, Math.round(shadowW * 2.8f));
                    int drawH = Math.max(1, Math.round(shadowH * 2.8f));
                    Graphics2D gs = (Graphics2D) ge.create();
                    gs.translate(planeX, shadowY);
                    if (fx.shadowTopToBottom) {
                        gs.rotate(Math.PI);
                    }
                    gs.drawImage(bomberShadowSilhouetteSprite, Math.round(-drawW / 2f), Math.round(-drawH / 2f), drawW, drawH, null);
                    gs.dispose();
                } else {
                    ge.setColor(new Color(16, 16, 18, 210));
                    ge.fillOval(sx, sy, shadowW, shadowH);
                }
            }

            float missileX = planeX;
            float missileY = bomberStartY + ((cy - bomberStartY) * missileTravelT);
            double missileHeading = fx.shadowTopToBottom ? Math.PI / 2.0 : -Math.PI / 2.0;

            if (t <= nukeImpactT) {
                Graphics2D gm = (Graphics2D) ge.create();
                gm.translate(missileX, missileY);
                // Sprite has nose-up source orientation, so +90deg aligns it with movement direction.
                gm.rotate(missileHeading + (Math.PI / 2.0));
                gm.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                gm.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                gm.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                gm.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.97f * tailFade * approachReveal));
                BufferedImage missileFrame = null;
                if (!fireballMissileFrames.isEmpty()) {
                    float frameT = missileTravelT;
                    int frameIndex = Math.round(frameT * Math.max(0, fireballMissileFrames.size() - 1));
                    frameIndex = Math.max(0, Math.min(fireballMissileFrames.size() - 1, frameIndex));
                    missileFrame = fireballMissileFrames.get(frameIndex);
                } else if (fireballMissileSprite != null) {
                    missileFrame = fireballMissileSprite;
                }
                if (missileFrame != null) {
                    // Smooth non-linear shrink with softer acceleration near impact.
                    float shrinkT = missileTravelT;
                    float shrinkEase = (float) Math.pow(shrinkT, 1.45);
                    float sizeScale = 1.28f - (0.56f * shrinkEase); // 1.28 -> 0.72
                    float bodyH = Math.max(16f, SQUARE_SIZE * 0.52f) * 2.0f * sizeScale;
                    float spriteAspect = missileFrame.getWidth() / (float) Math.max(1, missileFrame.getHeight());
                    float bodyW = Math.max(10f, bodyH * spriteAspect);
                    float scaleX = bodyW / Math.max(1f, missileFrame.getWidth());
                    float scaleY = bodyH / Math.max(1f, missileFrame.getHeight());
                    gm.scale(scaleX, scaleY);
                    gm.drawImage(
                        missileFrame,
                        Math.round(-missileFrame.getWidth() * 0.5f),
                        Math.round(-missileFrame.getHeight() * 0.58f),
                        null
                    );
                } else {
                    // Fallback vector missile if sprite is unavailable.
                    float bodyW = Math.max(6f, SQUARE_SIZE * 0.135f);
                    float bodyH = Math.max(14f, SQUARE_SIZE * 0.41f);
                    gm.setColor(new Color(214, 218, 226, 240));
                    gm.fillRoundRect(Math.round(-bodyW * 0.5f), Math.round(-bodyH * 0.58f), Math.round(bodyW), Math.round(bodyH), Math.round(bodyW), Math.round(bodyW));
                    gm.setColor(new Color(116, 122, 132, 220));
                    gm.fillRoundRect(Math.round(-bodyW * 0.36f), Math.round(-bodyH * 0.10f), Math.round(bodyW * 0.72f), Math.round(bodyH * 0.22f), Math.round(bodyW * 0.4f), Math.round(bodyW * 0.4f));
                    gm.setColor(new Color(198, 22, 22, 235));
                    gm.fillRoundRect(Math.round(-bodyW * 0.30f), Math.round(-bodyH * 0.34f), Math.round(bodyW * 0.60f), Math.max(2, Math.round(bodyH * 0.12f)), 3, 3);
                    gm.fillRoundRect(Math.round(-bodyW * 0.30f), Math.round(bodyH * 0.20f), Math.round(bodyW * 0.60f), Math.max(2, Math.round(bodyH * 0.11f)), 3, 3);
                    gm.setColor(new Color(244, 244, 248, 248));
                    gm.fillOval(Math.round(-bodyW * 0.44f), Math.round(-bodyH * 0.78f), Math.round(bodyW * 0.88f), Math.max(4, Math.round(bodyW * 0.58f)));
                    Polygon tailFin = new Polygon();
                    tailFin.addPoint(0, Math.round(bodyH * 0.53f));
                    tailFin.addPoint(Math.round(-bodyW * 0.96f), Math.round(bodyH * 0.26f));
                    tailFin.addPoint(Math.round(bodyW * 0.96f), Math.round(bodyH * 0.26f));
                    gm.setColor(new Color(152, 158, 170, 222));
                    gm.fillPolygon(tailFin);
                }
                gm.dispose();
            }

            float vignetteEnvelope = getFireballVignetteEnvelope(elapsedMs, impactMs);
            if (elapsedMs >= impactMs) {
                float fadeAlpha;
                float shrink;
                float flashT = Math.max(0f, Math.min(1f, postImpactElapsedMs / (float) Math.max(1L, FIREBALL_IMPACT_FLASH_DURATION_MS)));
                fadeAlpha = FIREBALL_FLASH_START_ALPHA * (float) Math.pow(Math.max(0f, 1f - flashT), 1.0);
                shrink = FIREBALL_FLASH_START_SCALE * (float) Math.pow(Math.max(0f, 1f - flashT), 1.4);
                boolean hasFlash = fadeAlpha > 0f && shrink > 0f;
                boolean hasVignette = vignetteEnvelope > 0f;
                if (hasFlash || hasVignette) {
                    // Reuse cached impact overlay buffer
                    if (cachedImpactOverlay == null || cachedOverlayWidth != boardW || cachedOverlayHeight != boardH) {
                        cachedImpactOverlay = new BufferedImage(boardW, boardH, BufferedImage.TYPE_INT_ARGB);
                        cachedImpactBlurOverlay = new BufferedImage(boardW, boardH, BufferedImage.TYPE_INT_ARGB);
                        cachedOverlayWidth = boardW;
                        cachedOverlayHeight = boardH;
                    }
                    BufferedImage impactOverlay = cachedImpactOverlay;
                    Graphics2D go = impactOverlay.createGraphics();
                    go.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
                    go.fillRect(0, 0, boardW, boardH);
                    go.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    go.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    go.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                    if (hasVignette) {
                        go.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, tailFade))));
                        drawFireballDirectionalCornerVignette(go, boardW, boardH, fx.row < 4, vignetteEnvelope);
                    }

                    // Draw horizontal impact line first so the boom flash layers sit above it.
                    float lineAppearT = Math.max(0f, Math.min(1f, postImpactElapsedMs / (float) Math.max(1L, FIREBALL_IMPACT_FLASH_FADE_IN_MS)));
                    float lineFadeT = Math.max(0f, Math.min(1f, (postImpactElapsedMs - FIREBALL_LINE_FADE_START_MS) / (float) Math.max(1L, FIREBALL_LINE_FADE_DURATION_MS)));
                    float lineShapeT = Math.max(0f, Math.min(1f, postImpactElapsedMs / (float) Math.max(1L, FIREBALL_LINE_FADE_START_MS + FIREBALL_LINE_FADE_DURATION_MS)));
                    float lineStartScale = FIREBALL_FLASH_START_SCALE + (1f - FIREBALL_FLASH_START_SCALE) * lineAppearT;
                    float lineCoreThickness = (SQUARE_SIZE * FIREBALL_FLASH_LINE_MAX_THICKNESS_SQ) * lineStartScale * (float) Math.pow(Math.max(0f, 1f - lineShapeT), 0.9);
                    boolean hasLine = lineFadeT < 1f;
                    if (hasLine && lineCoreThickness > FIREBALL_FLASH_LINE_MIN_THICKNESS_PX) {
                        Graphics2D gl = (Graphics2D) go.create();
                        gl.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        float lineStartAlpha = FIREBALL_FLASH_START_ALPHA + (1f - FIREBALL_FLASH_START_ALPHA) * lineAppearT;
                        float lineAlpha = 0.96f * tailFade * lineStartAlpha;
                        lineAlpha *= (float) Math.pow(Math.max(0f, 1f - lineFadeT), 1.15);
                        float lineCloseT = 1f - (float) Math.pow(1f - lineFadeT, 1.75f);
                        Rectangle clip = gl.getClipBounds();
                        if (clip == null) {
                            clip = new Rectangle(0, 0, boardW, boardH);
                        }
                        float startX = clip.x;
                        float endX = clip.x + clip.width;
                        int x1 = Math.round(startX + (cx - startX) * lineCloseT);
                        int x2 = Math.round(endX - (endX - cx) * lineCloseT);
                        if (x2 <= x1) {
                            gl.dispose();
                            continue;
                        }

                        float redLineThickness = lineCoreThickness * 3.1f;
                        gl.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, lineAlpha * 0.16f));
                        gl.setColor(new Color(255, 64, 44, 255));
                        gl.setStroke(new BasicStroke(redLineThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        gl.drawLine(x1, cy, x2, cy);

                        float yellowLineThickness = lineCoreThickness * 2.1f;
                        gl.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, lineAlpha * 0.28f));
                        gl.setColor(new Color(255, 225, 118, 255));
                        gl.setStroke(new BasicStroke(yellowLineThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        gl.drawLine(x1, cy, x2, cy);

                        gl.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, lineAlpha));
                        gl.setColor(new Color(255, 255, 255, 255));
                        gl.setStroke(new BasicStroke(lineCoreThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        gl.drawLine(x1, cy, x2, cy);
                        gl.dispose();
                    }

                    if (hasFlash && fireballImpactFlashSprite != null) {
                        BufferedImage glowSprite = (fireballImpactFlashSoftSprite != null)
                            ? fireballImpactFlashSoftSprite
                            : fireballImpactFlashSprite;
                        BufferedImage whiteSprite = fireballImpactFlashSprite;
                        float baseH = Math.max(1f, unit * 8.6f);
                        float aspect = glowSprite.getWidth() / (float) Math.max(1, glowSprite.getHeight());
                        float drawH = Math.max(1f, baseH * shrink);
                        float drawW = Math.max(1f, drawH * aspect);
                        int glowW = Math.max(1, glowSprite.getWidth());
                        int glowH = Math.max(1, glowSprite.getHeight());
                        float baseScaleX = drawW / glowW;
                        float baseScaleY = drawH / glowH;

                        // Warm yellow same-shape glow around the white core.
                        for (int i = 0; i < FIREBALL_YELLOW_LAYER_SCALES.length; i++) {
                            float layerAlpha = Math.max(0f, Math.min(1f, fadeAlpha * FIREBALL_YELLOW_LAYER_ALPHAS[i]));
                            if (layerAlpha <= 0f) continue;
                            Graphics2D gs = (Graphics2D) go.create();
                            gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            gs.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                            gs.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, layerAlpha));
                            gs.translate(cx, cy);
                            gs.scale(baseScaleX * FIREBALL_YELLOW_LAYER_SCALES[i], baseScaleY * FIREBALL_YELLOW_LAYER_SCALES[i]);
                            gs.drawImage(glowSprite, FIREBALL_YELLOW_TINT, -glowW / 2, -glowH / 2);
                            gs.dispose();
                        }

                        // Red same-shape glow around the yellow glow.
                        for (int i = 0; i < FIREBALL_RED_LAYER_SCALES.length; i++) {
                            float layerAlpha = Math.max(0f, Math.min(1f, fadeAlpha * FIREBALL_RED_LAYER_ALPHAS[i]));
                            if (layerAlpha <= 0f) continue;
                            Graphics2D gs = (Graphics2D) go.create();
                            gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            gs.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                            gs.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, layerAlpha));
                            gs.translate(cx, cy);
                            gs.scale(baseScaleX * FIREBALL_RED_LAYER_SCALES[i], baseScaleY * FIREBALL_RED_LAYER_SCALES[i]);
                            gs.drawImage(glowSprite, FIREBALL_RED_TINT, -glowW / 2, -glowH / 2);
                            gs.dispose();
                        }

                        // Soft white feather around the core so transitions are blended.
                        Graphics2D gsSoftWhite = (Graphics2D) go.create();
                        gsSoftWhite.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        gsSoftWhite.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                        gsSoftWhite.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, fadeAlpha * 0.36f))));
                        gsSoftWhite.translate(cx, cy);
                        gsSoftWhite.scale(baseScaleX * 1.13f, baseScaleY * 1.13f);
                        gsSoftWhite.drawImage(glowSprite, -glowW / 2, -glowH / 2, null);
                        gsSoftWhite.dispose();

                        float coreW = Math.max(1f, drawW * FIREBALL_FLASH_WHITE_CORE_SCALE);
                        float coreH = Math.max(1f, drawH * FIREBALL_FLASH_WHITE_CORE_SCALE);
                        Graphics2D gCore = (Graphics2D) go.create();
                        gCore.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        gCore.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                        gCore.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                        gCore.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, fadeAlpha))));
                        gCore.translate(cx, cy);
                        gCore.scale(coreW / Math.max(1f, whiteSprite.getWidth()), coreH / Math.max(1f, whiteSprite.getHeight()));
                        gCore.drawImage(
                            whiteSprite,
                            Math.round(-whiteSprite.getWidth() / 2f),
                            Math.round(-whiteSprite.getHeight() / 2f),
                            null
                        );
                        gCore.dispose();
                    } else if (hasFlash) {
                        float r = Math.max(1f, unit * 3.52f * shrink);
                        go.setColor(new Color(255, 255, 255, 255));
                        go.fillOval(Math.round(cx - r), Math.round(cy - r), Math.round(r * 2f), Math.round(r * 2f));
                    }

                    go.dispose();
                    BufferedImage overlayToDraw = impactOverlay;
                    if (FIREBALL_USE_FULL_SCREEN_IMPACT_BLUR) {
                        overlayToDraw = blurImpactOverlay(impactOverlay);
                    }
                    ge.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                    ge.drawImage(overlayToDraw, 0, 0, null);
                }
                drawFireballLocalWhiteout(ge, cx, cy, postImpactElapsedMs);
            }
            if (postImpactElapsedMs >= FIREBALL_SMOKE_DELAY_MS) {
                drawFireballSmokePlume(ge, fx, boardOriginX, boardOriginY, impactMs, postImpactCloudElapsedMs);
            }
            if (elapsedMs < impactMs && vignetteEnvelope > 0f) {
                Graphics2D gv = (Graphics2D) ge.create();
                gv.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, tailFade))));
                drawFireballDirectionalCornerVignette(gv, boardW, boardH, fx.row < 4, vignetteEnvelope);
                gv.dispose();
            }
        }

        ge.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        ge.dispose();
    }

    private void drawFireballLocalWhiteout(Graphics2D source, int cx, int cy, long elapsedMs) {
        if (elapsedMs < 0L || elapsedMs > FIREBALL_LOCAL_FLASH_DURATION_MS) return;

        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // First beat: a tiny, nearly solid impact spark.
        if (elapsedMs <= 55L) {
            float t = elapsedMs / 55f;
            float alpha = 1f - t;
            float radius = SQUARE_SIZE * (0.20f + 0.38f * t);
            g.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER,
                Math.max(0f, Math.min(1f, alpha))
            ));
            g.setColor(Color.WHITE);
            g.fillOval(
                Math.round(cx - radius),
                Math.round(cy - radius),
                Math.round(radius * 2f),
                Math.round(radius * 2f)
            );
        }

        // Second beat: a hard white camera burn feathering across the nearby 3x3 area.
        if (elapsedMs >= 38L) {
            float t = Math.min(1f, (elapsedMs - 38L) / 142f);
            float easedExpansion = 1f - (float) Math.pow(1f - t, 3);
            float alpha = (float) Math.pow(1f - t, 0.72);
            float radius = SQUARE_SIZE * (0.52f + 1.18f * easedExpansion);
            int coreAlpha = Math.round(255f * alpha);
            int midAlpha = Math.round(238f * alpha);
            int edgeAlpha = Math.round(150f * alpha);

            g.setComposite(AlphaComposite.SrcOver);
            g.setPaint(new RadialGradientPaint(
                new Point(cx, cy),
                Math.max(1f, radius),
                new float[]{0f, 0.28f, 0.68f, 1f},
                new Color[]{
                    new Color(255, 255, 255, coreAlpha),
                    new Color(255, 255, 255, midAlpha),
                    new Color(255, 252, 232, edgeAlpha),
                    new Color(255, 245, 205, 0)
                }
            ));
            g.fillOval(
                Math.round(cx - radius),
                Math.round(cy - radius),
                Math.round(radius * 2f),
                Math.round(radius * 2f)
            );
        }
        g.dispose();
    }

    private void drawFireballSmokePlume(
        Graphics2D ge,
        FireballImpactFx fx,
        int boardOriginX,
        int boardOriginY,
        long impactMs,
        long postImpactElapsedMs
    ) {
        if (fireballSmokeSprite == null || postImpactElapsedMs < 0L) return;
        if (postImpactElapsedMs > getFireballSmokeTotalDurationMs()) return;

        int dc = boardFlipped ? (7 - fx.col) : fx.col;
        int dr = boardFlipped ? (7 - fx.row) : fx.row;
        float cx = boardOriginX + dc * SQUARE_SIZE + SQUARE_SIZE * 0.5f;
        float baseY = boardOriginY + dr * SQUARE_SIZE + SQUARE_SIZE * 0.58f;

        float smokeT = Math.max(0f, Math.min(1f, postImpactElapsedMs / (float) Math.max(1L, FIREBALL_SMOKE_DURATION_MS)));
        float riseEase = (float) Math.pow(smokeT, 0.82);
        float alphaEnvelope;
        if (smokeT < FIREBALL_SMOKE_FADE_IN_WHITE_END_T) {
            alphaEnvelope = smokeT / FIREBALL_SMOKE_FADE_IN_WHITE_END_T; // fade in white
        } else if (smokeT > FIREBALL_SMOKE_FADE_START_T) {
            float tailT = (smokeT - FIREBALL_SMOKE_FADE_START_T) / Math.max(0.001f, 1f - FIREBALL_SMOKE_FADE_START_T);
            alphaEnvelope = (float) Math.pow(Math.max(0f, 1f - tailT), 1.45);
        } else {
            alphaEnvelope = 1.0f;
        }
        if (alphaEnvelope <= 0f) return;

        int iw = Math.max(1, fireballSmokeSprite.getWidth());
        int ih = Math.max(1, fireballSmokeSprite.getHeight());
        Graphics2D gs = (Graphics2D) ge.create();
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        gs.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gs.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);

        for (int i = 0; i < 1; i++) {
            float phase = Math.max(0f, Math.min(1f, smokeT * (1.03f + i * 0.08f)));
            float puffRise = FIREBALL_SMOKE_RISE_SQ * FIREBALL_CLOUD_MOTION_RATE_MULT * SQUARE_SIZE * (float) Math.pow(phase, 0.86);
            float puffScale = (FIREBALL_SMOKE_BASE_SCALE_SQ + (0.09f + 0.022f * i) * phase) * FIREBALL_CLOUD_SPRITE_SCALE_MULT;
            float puffAlpha = alphaEnvelope;
            if (puffAlpha <= 0.01f) continue;

            Graphics2D gp = (Graphics2D) gs.create();
            gp.translate(cx, baseY - puffRise - i * SQUARE_SIZE * 0.08f);
            gp.scale(puffScale, puffScale);

            int spriteX = -iw / 2;
            int spriteY = -ih / 2;
            // Bottom region: draw first so it stays under the main cloud layer.
            if (fireballSmallSmokeSprite != null) {
                Graphics2D gpBottom = (Graphics2D) gs.create();
                long smallSmokeMs = Math.max(0L, postImpactElapsedMs - FIREBALL_SMALL_SMOKE_DELAY_MS);
                float smallT = Math.max(0f, Math.min(1f, smallSmokeMs / (float) Math.max(1L, FIREBALL_SMALL_SMOKE_DURATION_MS)));
                float delayedFade = 1.0f;
                long fadeStartMs = Math.round(FIREBALL_SMALL_SMOKE_DURATION_MS * FIREBALL_SMALL_SMOKE_FADE_START_T);
                if (smallSmokeMs > fadeStartMs) {
                    long fadeSpanMs = Math.max(1L, FIREBALL_SMALL_SMOKE_DURATION_MS - fadeStartMs);
                    float fadeT = (smallSmokeMs - fadeStartMs) / (float) fadeSpanMs;
                    fadeT = Math.max(0f, Math.min(1f, fadeT));
                    // Smootherstep keeps both ends gentle so the cloud eases away without popping.
                    float smoothFadeT = fadeT * fadeT * fadeT * (fadeT * (fadeT * 6f - 15f) + 10f);
                    delayedFade = 1f - smoothFadeT;
                }
                float smallFadeInT = Math.max(0f, Math.min(1f, smallT / 0.24f));
                float smallFadeIn = (float) (0.5 - 0.5 * Math.cos(Math.PI * smallFadeInT));
                // Keep bottom cloud on its own fade timeline (not tied to main plume alpha).
                float bottomAlpha = delayedFade * smallFadeIn;
                if (postImpactElapsedMs >= FIREBALL_SMALL_SMOKE_DELAY_MS && bottomAlpha > 0.01f) {
                    int siw = Math.max(1, fireballSmallSmokeSprite.getWidth());
                    int sih = Math.max(1, fireballSmallSmokeSprite.getHeight());
                    // Tiny cloud: delayed appear, subtle fade-in, lower placement, and longer linger.
                    float growthT = (float) Math.pow(Math.max(0f, Math.min(1f, smallT)), 0.55f);
                    float smallScale = 0.231f * FIREBALL_CLOUD_SPRITE_SCALE_MULT * FIREBALL_BOTTOM_CLOUD_SCALE_MULT * (1.0f + 0.18f * growthT);
                    int drawW = Math.max(1, Math.round(siw * smallScale));
                    int drawH = Math.max(1, Math.round(sih * smallScale));
                    int centerX = Math.round(boardOriginX + dc * SQUARE_SIZE + SQUARE_SIZE * 0.5f);
                    int risePx = Math.round(SQUARE_SIZE * 0.08f * FIREBALL_CLOUD_MOTION_RATE_MULT * (float) Math.pow(Math.max(0f, Math.min(1f, smallT)), 0.9f));
                    int centerY = Math.round(boardOriginY + dr * SQUARE_SIZE + SQUARE_SIZE * 0.5f + SQUARE_SIZE * 0.06f) - risePx;
                    int smallX = centerX - (drawW / 2);
                    int smallY = centerY - (drawH / 2);
                    gpBottom.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, bottomAlpha))));
                    Graphics2D gSmall = (Graphics2D) gpBottom.create();
                    gSmall.translate(smallX, smallY);
                    gSmall.scale(drawW / (float) siw, drawH / (float) sih);
                    RescaleOp smallTint;
                    if (smallT < FIREBALL_SMALL_SMOKE_FADE_IN_WHITE_END_T) {
                        smallTint = FIREBALL_SMOKE_WHITE_TINT;
                    } else if (smallT < FIREBALL_SMALL_SMOKE_HOLD_WHITE_END_T) {
                        smallTint = FIREBALL_SMOKE_WHITE_TINT;
                    } else if (smallT < FIREBALL_SMALL_SMOKE_TO_WARM_END_T) {
                        float transT = (smallT - FIREBALL_SMALL_SMOKE_HOLD_WHITE_END_T) / (FIREBALL_SMALL_SMOKE_TO_WARM_END_T - FIREBALL_SMALL_SMOKE_HOLD_WHITE_END_T);
                        smallTint = interpolateTint("small-white-warm", FIREBALL_SMOKE_WHITE_TINT, FIREBALL_SMOKE_WARM_TINT, transT);
                    } else if (smallT < FIREBALL_SMALL_SMOKE_TO_GRAY_END_T) {
                        float transT = (smallT - FIREBALL_SMALL_SMOKE_TO_WARM_END_T) / (FIREBALL_SMALL_SMOKE_TO_GRAY_END_T - FIREBALL_SMALL_SMOKE_TO_WARM_END_T);
                        smallTint = interpolateTint("small-warm-gray", FIREBALL_SMOKE_WARM_TINT, FIREBALL_SMOKE_GRAY_TINT, transT);
                    } else {
                        smallTint = FIREBALL_SMOKE_GRAY_TINT;
                    }
                    gSmall.drawImage(fireballSmallSmokeSprite, smallTint, 0, 0);
                    gSmall.dispose();
                }
                gpBottom.dispose();
            }

            // Main plume: render full sprite over the small cloud.
            Graphics2D gpTop = (Graphics2D) gp.create();
            RescaleOp puffTint;
            if (smokeT < FIREBALL_SMOKE_FADE_IN_WHITE_END_T) {
                puffTint = FIREBALL_SMOKE_WHITE_TINT;
            } else if (smokeT < FIREBALL_SMOKE_HOLD_WHITE_END_T) {
                puffTint = FIREBALL_SMOKE_WHITE_TINT;
            } else if (smokeT < FIREBALL_SMOKE_TO_WARM_END_T) {
                float transT = (smokeT - FIREBALL_SMOKE_HOLD_WHITE_END_T) / (FIREBALL_SMOKE_TO_WARM_END_T - FIREBALL_SMOKE_HOLD_WHITE_END_T);
                puffTint = interpolateTint("main-white-warm", FIREBALL_SMOKE_WHITE_TINT, FIREBALL_SMOKE_WARM_TINT, transT);
            } else if (smokeT < FIREBALL_SMOKE_TO_GRAY_END_T) {
                float transT = (smokeT - FIREBALL_SMOKE_TO_WARM_END_T) / (FIREBALL_SMOKE_TO_GRAY_END_T - FIREBALL_SMOKE_TO_WARM_END_T);
                puffTint = interpolateTint("main-warm-gray", FIREBALL_SMOKE_WARM_TINT, FIREBALL_SMOKE_GRAY_TINT, transT);
            } else {
                puffTint = FIREBALL_SMOKE_GRAY_TINT;
            }
            float yellowAlpha = 0f;
            if (smokeT <= FIREBALL_SMOKE_HOLD_WHITE_END_T) {
                yellowAlpha = alphaEnvelope * 0.36f;
            } else if (smokeT < FIREBALL_SMOKE_TO_WARM_END_T) {
                float yellowFadeT = (smokeT - FIREBALL_SMOKE_HOLD_WHITE_END_T) / Math.max(0.001f, FIREBALL_SMOKE_TO_WARM_END_T - FIREBALL_SMOKE_HOLD_WHITE_END_T);
                yellowAlpha = alphaEnvelope * 0.36f * (1f - yellowFadeT);
            }
            if (yellowAlpha > 0f) {
                float yellowScale = FIREBALL_CLOUD_SPRITE_SCALE_MULT * FIREBALL_YELLOW_CLOUD_SCALE_MULT * (1.12f + 0.06f * Math.max(0f, 1f - Math.min(1f, smokeT / FIREBALL_SMOKE_HOLD_WHITE_END_T)));
                Graphics2D gy = (Graphics2D) gpTop.create();
                gy.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, yellowAlpha))));
                gy.scale(yellowScale, yellowScale);
                gy.drawImage(fireballSmokeSprite, FIREBALL_SMOKE_YELLOW_SILHOUETTE_TINT, spriteX, spriteY);
                gy.dispose();
            }
            gpTop.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, puffAlpha));
            gpTop.drawImage(fireballSmokeSprite, puffTint, spriteX, spriteY);
            gpTop.dispose();
            gp.dispose();
        }
        gs.dispose();
    }

    private BufferedImage blurImpactOverlay(BufferedImage src) {
        if (src == null) return null;
        try {
            if (cachedImpactBlurOp == null) {
                float[] g5 = new float[] {
                    1f, 4f, 6f, 4f, 1f,
                    4f, 16f, 24f, 16f, 4f,
                    6f, 24f, 36f, 24f, 6f,
                    4f, 16f, 24f, 16f, 4f,
                    1f, 4f, 6f, 4f, 1f
                };
                float sum = 0f;
                for (float v : g5) sum += v;
                for (int i = 0; i < g5.length; i++) g5[i] /= sum;
                cachedImpactBlurOp = new ConvolveOp(new Kernel(5, 5, g5), ConvolveOp.EDGE_NO_OP, null);
            }
            if (cachedImpactBlurOverlay == null
                || cachedImpactBlurOverlay.getWidth() != src.getWidth()
                || cachedImpactBlurOverlay.getHeight() != src.getHeight()) {
                cachedImpactBlurOverlay = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            }
            cachedImpactBlurOp.filter(src, cachedImpactBlurOverlay);
            return cachedImpactBlurOverlay;
        } catch (Exception ignored) {
            return src;
        }
    }

    private void drawFireballDirectionalCornerVignette(Graphics2D g, int w, int h, boolean victimInTopHalf, float intensity) {
        float a = Math.max(0f, Math.min(1f, intensity * FIREBALL_CORNER_VIGNETTE_MAX_ALPHA));
        if (a <= 0f || w <= 0 || h <= 0) return;

        int y = victimInTopHalf ? h : 0;
        float radius = Math.max(80f, Math.min(w, h) * 0.55f);
        float[] dist = new float[] {0f, 1f};
        Color[] colors = new Color[] {
            new Color(28, 18, 10, Math.round(255f * a)),
            new Color(28, 18, 10, 0)
        };

        Graphics2D gl = (Graphics2D) g.create();
        gl.setComposite(AlphaComposite.SrcOver);
        gl.setPaint(new RadialGradientPaint(new Point(0, y), radius, dist, colors));
        gl.fillRect(0, Math.max(0, y - Math.round(radius)), Math.round(radius), Math.round(radius) * 2);
        gl.setPaint(new RadialGradientPaint(new Point(w, y), radius, dist, colors));
        gl.fillRect(Math.max(0, w - Math.round(radius)), Math.max(0, y - Math.round(radius)), Math.round(radius), Math.round(radius) * 2);
        gl.dispose();
    }

    private float getFireballVignetteEnvelope(long elapsedMs, long impactMs) {
        // Fade in with nuke approach, then fade out with the smoke/cloud tail.
        long fadeInStart = Math.max(0L, impactMs - FIREBALL_VIGNETTE_FADE_IN_MS);
        long fadeInEnd = impactMs;
        long fadeOutEnd = impactMs + FIREBALL_SMOKE_DURATION_MS;
        long fadeOutStart = Math.max(fadeInEnd, fadeOutEnd - FIREBALL_VIGNETTE_FADE_OUT_MS);

        if (elapsedMs < fadeInStart) return 0f;
        if (elapsedMs < fadeInEnd) {
            return Math.max(0f, Math.min(1f, (elapsedMs - fadeInStart) / (float) Math.max(1L, fadeInEnd - fadeInStart)));
        }
        if (elapsedMs < fadeOutStart) return 1f;
        if (elapsedMs <= fadeOutEnd) {
            float outT = (elapsedMs - fadeOutStart) / (float) Math.max(1L, fadeOutEnd - fadeOutStart);
            return Math.max(0f, Math.min(1f, 1f - outT));
        }
        return 0f;
    }

    private Point getFireballBoardShakeOffset(long nowMs) {
        if (!isSpellChessMode() || fireballImpactFx.isEmpty()) return new Point(0, 0);

        float maxAmpPx = SQUARE_SIZE * FIREBALL_SHAKE_MAX_SQUARE_FACTOR;
        double shakeX = 0.0;
        double shakeY = 0.0;
        int active = 0;
        long tick = nowMs / 18L;

        for (FireballImpactFx fx : fireballImpactFx) {
            long elapsedMs = nowMs - fx.startMs;
            if (elapsedMs < 0L || fx.durationMs <= 0L) continue;

            float t = (float) (elapsedMs / (double) Math.max(1L, fx.durationMs));
            if (t <= 0f || t > 1f) continue;

            float nukeImpactT = Math.max(0.05f, Math.min(FIREBALL_IMPACT_T, FIREBALL_IMPACT_T / Math.max(0.01f, FIREBALL_NUKE_TRAVEL_BOOST)));
            long impactMs = Math.round(fx.durationMs * nukeImpactT);
            long sinceImpactMs = elapsedMs - impactMs;
            if (sinceImpactMs < 0L) continue; // no shake before flash trigger

            // Keep shake only for a brief burst after flash starts.
            long shakeWindowMs = Math.min(FIREBALL_FLASH_SHAKE_BRIEF_MS, FIREBALL_IMPACT_FLASH_DURATION_MS);
            if (sinceImpactMs > shakeWindowMs) continue;

            float flashT = Math.max(0f, Math.min(1f, sinceImpactMs / (float) Math.max(1L, shakeWindowMs)));
            // Fast hit with quick decay.
            float envelope = (float) Math.pow(Math.max(0f, 1f - flashT), 1.8);

            if (envelope <= 0f) continue;

            long seed = (((long) fx.row) << 32) ^ (((long) fx.col) << 24) ^ fx.startMs;
            double sx = ((hashToUnit(seed, tick, 41) * 2.0) - 1.0) * (maxAmpPx * envelope);
            double sy = ((hashToUnit(seed, tick, 73) * 2.0) - 1.0) * (maxAmpPx * envelope);
            shakeX += sx;
            shakeY += sy;
            active++;
        }

        if (active <= 0) return new Point(0, 0);

        double maxTotalAmp = maxAmpPx * Math.max(1.0, Math.sqrt(active));
        double mag = Math.hypot(shakeX, shakeY);
        if (mag > maxTotalAmp && mag > 0.0001) {
            double scale = maxTotalAmp / mag;
            shakeX *= scale;
            shakeY *= scale;
        }

        return new Point((int) Math.round(shakeX), (int) Math.round(shakeY));
    }

    private boolean hasActiveFireballBoardShake(long nowMs) {
        if (!isSpellChessMode() || fireballImpactFx.isEmpty()) return false;
        for (FireballImpactFx fx : fireballImpactFx) {
            long elapsedMs = nowMs - fx.startMs;
            if (elapsedMs < 0L || fx.durationMs <= 0L) continue;
            float nukeImpactT = Math.max(0.05f, Math.min(FIREBALL_IMPACT_T, FIREBALL_IMPACT_T / Math.max(0.01f, FIREBALL_NUKE_TRAVEL_BOOST)));
            long impactMs = Math.round(fx.durationMs * nukeImpactT);
            long sinceImpactMs = elapsedMs - impactMs;
            if (sinceImpactMs >= 0L && sinceImpactMs <= Math.min(FIREBALL_FLASH_SHAKE_BRIEF_MS, FIREBALL_IMPACT_FLASH_DURATION_MS)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPieceInActivePreExplosionAt(int row, int col, Piece piece) {
        for (PieceExplosionFx fx : pieceExplosionFx) {
            if (fx.row == row && fx.col == col && fx.piece == piece && !fx.shatterStarted) {
                return true;
            }
        }
        return false;
    }

    private void drawPieceExplosionLighting(Graphics2D g2d) {
        if (pieceExplosionFx.isEmpty()) return;
        long now = System.currentTimeMillis();
        int maxDarkAlpha = 0;
        for (PieceExplosionFx fx : pieceExplosionFx) {
            maxDarkAlpha = Math.max(maxDarkAlpha, getPieceExplosionDarkAlpha(fx, now));
        }
        if (maxDarkAlpha > 0) {
            Graphics2D gd = (Graphics2D) g2d.create();
            gd.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            gd.setColor(new Color(0, 0, 0, Math.max(0, Math.min(210, maxDarkAlpha))));
            gd.fillRect(0, 0, BOARD_SIZE, BOARD_SIZE);
            gd.dispose();
        }
        for (PieceExplosionFx fx : pieceExplosionFx) {
            if (fx.shatterStarted) continue;
            double t = Math.max(0.0, Math.min(1.0, (now - fx.startMs) / (double) Math.max(1L, fx.preDurationMs)));
            int dc = boardFlipped ? (7 - fx.col) : fx.col;
            int dr = boardFlipped ? (7 - fx.row) : fx.row;
            int cx = dc * SQUARE_SIZE + SQUARE_SIZE / 2;
            int cy = dr * SQUARE_SIZE + SQUARE_SIZE / 2;

            Graphics2D gl = (Graphics2D) g2d.create();
            float[] dist = {0f, 0.3f, 1f};
            Color[] colors = {
                new Color(255, 245, 225, (int) Math.round(150 * t)),
                new Color(255, 200, 140, (int) Math.round(80 * t)),
                new Color(255, 180, 120, 0)
            };
            float radius = (float) (SQUARE_SIZE * (1.4 + 2.4 * t));
            gl.setPaint(new RadialGradientPaint(new Point(cx, cy), radius, dist, colors));
            gl.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.9 * t)));
            gl.fillOval((int) (cx - radius), (int) (cy - radius), (int) (radius * 2), (int) (radius * 2));

            gl.dispose();
        }
    }

    private int getPieceExplosionDarkAlpha(PieceExplosionFx fx, long nowMs) {
        if (!fx.shatterStarted) {
            double t = Math.max(0.0, Math.min(1.0, (nowMs - fx.startMs) / (double) Math.max(1L, fx.preDurationMs)));
            // Ease-in so board darkening ramps up more gradually.
            double eased = Math.pow(t, 1.35);
            return (int) Math.round(170 * eased);
        }
        long sinceShatterMs = nowMs - fx.shatterStartMs;
        if (sinceShatterMs <= PIECE_POST_DARK_HOLD_MS) {
            return 170;
        }
        long fadeMs = sinceShatterMs - PIECE_POST_DARK_HOLD_MS;
        if (fadeMs >= PIECE_POST_DARK_FADE_MS) {
            return 0;
        }
        double k = 1.0 - (fadeMs / (double) Math.max(1L, PIECE_POST_DARK_FADE_MS));
        return (int) Math.round(170 * Math.max(0.0, Math.min(1.0, k)));
    }

    private void drawPieceExplosionFx(Graphics2D g2d) {
        if (pieceExplosionFx.isEmpty()) return;
        long now = System.currentTimeMillis();
        Graphics2D ge = (Graphics2D) g2d.create();
        ge.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (PieceExplosionFx fx : pieceExplosionFx) {
            if (!fx.shatterStarted) {
                drawPreExplosionPiece(ge, fx, now);
            } else {
                drawShatterOverlay(ge, fx, now);
            }
        }
        ge.dispose();
    }

    private void drawThreeCheckAmbientRings(Graphics2D g2d) {
        if (!isThreeCheckMode()) return;
        if (!inputEnabled) return;
        if (hasThreeCheckWinner()) return;
        long now = System.currentTimeMillis();

        // White king has been checked by black's delivered checks.
        int[] whiteKingPos = findKingPosition(true);
        if (whiteKingPos != null) {
            int checksOnWhiteKing = Math.max(0, Math.min(2, blackChecksDelivered));
            if (whiteKingRingStage != checksOnWhiteKing) {
                whiteKingRingStage = checksOnWhiteKing;
                whiteKingRingPhaseStartMs = now; // immediate pulse on stage change
            }
            drawThreeCheckAmbientRingAt(g2d, whiteKingPos[0], whiteKingPos[1], checksOnWhiteKing, now, whiteKingRingPhaseStartMs);
        }

        // Black king has been checked by white's delivered checks.
        int[] blackKingPos = findKingPosition(false);
        if (blackKingPos != null) {
            int checksOnBlackKing = Math.max(0, Math.min(2, whiteChecksDelivered));
            if (blackKingRingStage != checksOnBlackKing) {
                blackKingRingStage = checksOnBlackKing;
                blackKingRingPhaseStartMs = now; // immediate pulse on stage change
            }
            drawThreeCheckAmbientRingAt(g2d, blackKingPos[0], blackKingPos[1], checksOnBlackKing, now, blackKingRingPhaseStartMs);
        }
    }

    private void drawThreeCheckAmbientRingAt(Graphics2D g2d, int row, int col, int checks, long nowMs, long phaseStartMs) {
        long intervalMs;
        Color ringColor;
        switch (checks) {
            case 0:
                intervalMs = 5000L;
                ringColor = new Color(170, 255, 85); // subtle lime
                break;
            case 1:
                intervalMs = 3000L;
                ringColor = new Color(255, 186, 78); // yellow-orange
                break;
            default:
                intervalMs = 1000L;
                ringColor = new Color(255, 80, 80); // red
                break;
        }

        long pulseDurationMs = Math.max(500L, Math.min(1400L, (long) (intervalMs * 0.72)));
        long elapsedMs = Math.max(0L, nowMs - phaseStartMs);
        long phaseMs = (intervalMs > 0L) ? (elapsedMs % intervalMs) : 0L;
        if (phaseMs > pulseDurationMs) return;
        float phase = phaseMs / (float) pulseDurationMs; // 0..1
        if (checks == 1) {
            phase = Math.min(1.0f, phase * 1.15f); // orange ring expands a bit faster
        }

        int dc = boardFlipped ? (7 - col) : col;
        int dr = boardFlipped ? (7 - row) : row;
        int cx = dc * SQUARE_SIZE + SQUARE_SIZE / 2;
        int cy = dr * SQUARE_SIZE + SQUARE_SIZE / 2;

        float maxOuterRadius = SQUARE_SIZE * THREE_CHECK_RING_MAX_RADIUS_SQ;
        float minCenterRadius = SQUARE_SIZE * 0.26f;
        float bandThickness = (float) (10.0 - 3.0 * phase);
        float maxCenterRadius = Math.max(minCenterRadius, maxOuterRadius - bandThickness * 0.5f);
        float ringRadius = minCenterRadius + phase * (maxCenterRadius - minCenterRadius);
        float opacityBoost = (checks == 1) ? 1.14f : ((checks >= 2) ? 1.474f : 1.0f);
        float ringAlpha = (float) (0.72 * Math.pow(1.0 - phase, 0.95) * opacityBoost * 1.10);
        if (ringAlpha <= 0.004f) return;

        float innerRadius = Math.max(1.0f, ringRadius - bandThickness * 0.5f);
        float outerRadius = Math.max(innerRadius + 1.0f, ringRadius + bandThickness * 0.5f);
        outerRadius = Math.min(maxOuterRadius, outerRadius);
        float feather = Math.max(1.2f, bandThickness * 0.45f);
        float gradOuter = outerRadius + feather;

        float r1 = Math.max(0.001f, (innerRadius - feather) / gradOuter);
        float r2 = Math.max(r1 + 0.001f, Math.min(0.995f, innerRadius / gradOuter));
        float r3 = Math.max(r2 + 0.001f, Math.min(0.997f, outerRadius / gradOuter));
        float r4 = Math.max(r3 + 0.001f, Math.min(0.999f, (outerRadius + feather) / gradOuter));

        float[] stops = new float[] {0.0f, r1, r2, r3, r4, 1.0f};
        Color[] cols = new Color[] {
            withAlpha(ringColor, 0),
            withAlpha(ringColor, (int) Math.round(46 * ringAlpha)),
            withAlpha(ringColor, (int) Math.round(220 * ringAlpha)),
            withAlpha(ringColor, (int) Math.round(160 * ringAlpha)),
            withAlpha(ringColor, (int) Math.round(22 * ringAlpha)),
            withAlpha(ringColor, 0)
        };

        Graphics2D gr = (Graphics2D) g2d.create();
        java.awt.geom.Ellipse2D.Float outerEllipse =
            new java.awt.geom.Ellipse2D.Float(cx - (outerRadius + feather), cy - (outerRadius + feather), (outerRadius + feather) * 2f, (outerRadius + feather) * 2f);
        java.awt.geom.Ellipse2D.Float innerEllipse =
            new java.awt.geom.Ellipse2D.Float(cx - Math.max(0f, innerRadius - feather), cy - Math.max(0f, innerRadius - feather),
                Math.max(0f, innerRadius - feather) * 2f, Math.max(0f, innerRadius - feather) * 2f);
        java.awt.geom.Area annulus = new java.awt.geom.Area(outerEllipse);
        annulus.subtract(new java.awt.geom.Area(innerEllipse));

        Shape oldClip = gr.getClip();
        gr.setClip(annulus);
        gr.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        gr.setPaint(new RadialGradientPaint(new Point(cx, cy), gradOuter, stops, cols));
        int d = (int) Math.ceil(gradOuter * 2.0);
        int x = (int) Math.floor(cx - gradOuter);
        int y = (int) Math.floor(cy - gradOuter);
        gr.fillOval(x, y, d, d);
        gr.setClip(oldClip);
        gr.dispose();
    }

    private Color withAlpha(Color c, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private void drawPreExplosionPiece(Graphics2D g2d, PieceExplosionFx fx, long nowMs) {
        if (fx.pieceSprite == null) return;
        int dc = boardFlipped ? (7 - fx.col) : fx.col;
        int dr = boardFlipped ? (7 - fx.row) : fx.row;
        int baseX = dc * SQUARE_SIZE;
        int baseY = dr * SQUARE_SIZE;

        double t = Math.max(0.0, Math.min(1.0, (nowMs - fx.startMs) / (double) Math.max(1L, fx.preDurationMs)));
        double scale = 1.0 + 0.28 * t;
        double shakeAmp = 0.35 + 2.0 * t;
        long tick = nowMs / 24L;
        double sx = ((hashToUnit(fx.shakeSeed, tick, 13) * 2.0) - 1.0) * shakeAmp;
        double sy = ((hashToUnit(fx.shakeSeed, tick, 29) * 2.0) - 1.0) * shakeAmp;

        BufferedImage tint = tintPieceToWhite(fx.pieceSprite, (float) t);
        Graphics2D gp = (Graphics2D) g2d.create();
        gp.translate(baseX + SQUARE_SIZE / 2.0 + sx, baseY + SQUARE_SIZE / 2.0 + sy);
        gp.scale(scale, scale);
        gp.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.96f));
        gp.drawImage(tint, -SQUARE_SIZE / 2, -SQUARE_SIZE / 2, SQUARE_SIZE, SQUARE_SIZE, null);
        gp.dispose();
    }

    private void drawShatterOverlay(Graphics2D g2d, PieceExplosionFx fx, long nowMs) {
        BufferedImage shardSprite = (fx.shatterSprite != null) ? fx.shatterSprite : fx.pieceSprite;
        if (shardSprite == null || fx.shards.isEmpty()) return;
        double t = Math.max(0.0, Math.min(1.0, (nowMs - fx.shatterStartMs) / (double) Math.max(1L, fx.shatterDurationMs)));
        if (t >= 1.0) return;
        int dc = boardFlipped ? (7 - fx.col) : fx.col;
        int dr = boardFlipped ? (7 - fx.row) : fx.row;
        int baseX = dc * SQUARE_SIZE;
        int baseY = dr * SQUARE_SIZE;
        int cx = baseX + SQUARE_SIZE / 2;
        int cy = baseY + SQUARE_SIZE / 2;
        double gravity = 0.0;
        float alpha = (float) Math.max(0.0, Math.pow(1.0 - t, 1.02));

        // Shockwave band emitted at explosion start.
        double maxRadius = getMaxDistanceToBoardCorner(cx, cy);
        double ringPhase = Math.min(1.0, t * 3.4);
        float ringRadius = (float) (SQUARE_SIZE * 0.55 + ringPhase * maxRadius);
        float ringAlpha = (float) Math.max(0.0, 0.98 * Math.pow(1.0 - ringPhase, 1.35));
        if (ringAlpha > 0.01f) {
            float bandThickness = (float) (46.0 - 14.0 * ringPhase);
            Graphics2D gr = (Graphics2D) g2d.create();
            float innerRadius = Math.max(1.0f, ringRadius - bandThickness * 0.5f);
            float outerRadius = Math.max(innerRadius + 1.0f, ringRadius + bandThickness * 0.5f);
            float feather = Math.max(2.5f, bandThickness * 0.28f);
            float gradOuter = outerRadius + feather;
            float r1 = Math.max(0.001f, (innerRadius - feather) / gradOuter);
            float r2 = Math.max(r1 + 0.001f, Math.min(0.995f, innerRadius / gradOuter));
            float r3 = Math.max(r2 + 0.001f, Math.min(0.997f, outerRadius / gradOuter));
            float r4 = Math.max(r3 + 0.001f, Math.min(0.999f, (outerRadius + feather) / gradOuter));

            float[] stops = new float[] {0.0f, r1, r2, r3, r4, 1.0f};
            Color[] cols = new Color[] {
                new Color(255, 255, 255, 0),
                new Color(255, 255, 255, (int) Math.round(16 * ringAlpha)),
                new Color(255, 255, 255, (int) Math.round(210 * ringAlpha)),
                new Color(255, 255, 255, (int) Math.round(128 * ringAlpha)),
                new Color(255, 255, 255, (int) Math.round(10 * ringAlpha)),
                new Color(255, 255, 255, 0)
            };

            java.awt.geom.Ellipse2D.Float outerEllipse =
                new java.awt.geom.Ellipse2D.Float(cx - (outerRadius + feather), cy - (outerRadius + feather), (outerRadius + feather) * 2f, (outerRadius + feather) * 2f);
            java.awt.geom.Ellipse2D.Float innerEllipse =
                new java.awt.geom.Ellipse2D.Float(cx - Math.max(0f, innerRadius - feather), cy - Math.max(0f, innerRadius - feather),
                    Math.max(0f, innerRadius - feather) * 2f, Math.max(0f, innerRadius - feather) * 2f);
            java.awt.geom.Area annulus = new java.awt.geom.Area(outerEllipse);
            annulus.subtract(new java.awt.geom.Area(innerEllipse));

            Shape oldClip = gr.getClip();
            gr.setClip(annulus);
            gr.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            gr.setPaint(new RadialGradientPaint(new Point(cx, cy), gradOuter, stops, cols));
            int d = (int) Math.ceil(gradOuter * 2.0);
            int x = (int) Math.floor(cx - gradOuter);
            int y = (int) Math.floor(cy - gradOuter);
            gr.fillOval(x, y, d, d);
            gr.setClip(oldClip);
            gr.dispose();
        }

        for (PieceExplosionShard s : fx.shards) {
            double px = baseX + s.sx + s.vx * t;
            double py = baseY + s.sy + s.vy * t + gravity * t * t;
            double rot = s.angleOffset + s.spin * t;

            Graphics2D gs = (Graphics2D) g2d.create();
            gs.translate(px + s.sw / 2.0, py + s.sh / 2.0);
            gs.rotate(rot);
            gs.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            int drawW = (int) Math.round(s.sw * 2.6);
            int drawH = (int) Math.round(s.sh * 2.6);
            Shape oldClip = gs.getClip();
            if (s.pcount >= 3 && s.px != null && s.py != null) {
                Polygon poly = new Polygon(s.px, s.py, s.pcount);
                gs.clip(poly);
            }
            gs.drawImage(
                shardSprite,
                -drawW / 2, -drawH / 2, drawW / 2, drawH / 2,
                s.sx, s.sy, s.sx + s.sw, s.sy + s.sh,
                null
            );
            gs.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0f, alpha * 0.72f)));
            gs.setColor(new Color(255, 255, 255, 255));
            gs.fillRect(-drawW / 2, -drawH / 2, drawW, drawH);
            gs.setClip(oldClip);
            gs.dispose();
        }
    }

    private double getMaxDistanceToBoardCorner(int cx, int cy) {
        double d1 = Math.hypot(cx, cy);
        double d2 = Math.hypot(BOARD_SIZE - cx, cy);
        double d3 = Math.hypot(cx, BOARD_SIZE - cy);
        double d4 = Math.hypot(BOARD_SIZE - cx, BOARD_SIZE - cy);
        return Math.max(Math.max(d1, d2), Math.max(d3, d4));
    }

    private BufferedImage createPieceSprite(Piece piece) {
        if (piece == null) return null;
        BufferedImage img = new BufferedImage(SQUARE_SIZE, SQUARE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        piece.draw(g, 0, 0);
        g.dispose();
        return img;
    }

    private BufferedImage createGlowingShatterSprite(BufferedImage baseSprite) {
        if (baseSprite == null) return null;
        BufferedImage white = new BufferedImage(baseSprite.getWidth(), baseSprite.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < baseSprite.getHeight(); y++) {
            for (int x = 0; x < baseSprite.getWidth(); x++) {
                int argb = baseSprite.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) continue;
                // Force all visible piece pixels to pure white.
                white.setRGB(x, y, (255 << 24) | 0x00FFFFFF);
            }
        }
        BufferedImage out = new BufferedImage(baseSprite.getWidth(), baseSprite.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Strong bloom halo so shards look intensely bright.
        int[][] offsets = {
            {-12, 0}, {12, 0}, {0, -12}, {0, 12},
            {-10, -7}, {-10, 7}, {10, -7}, {10, 7},
            {-8, 0}, {8, 0}, {0, -8}, {0, 8},
            {-7, 0}, {7, 0}, {0, -7}, {0, 7},
            {-6, -4}, {-6, 4}, {6, -4}, {6, 4},
            {-3, 0}, {3, 0}, {0, -3}, {0, 3},
            {-3, -3}, {-3, 3}, {3, -3}, {3, 3},
            {-5, 0}, {5, 0}, {0, -5}, {0, 5}
        };
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.86f));
        for (int[] o : offsets) {
            g.drawImage(white, o[0], o[1], null);
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.drawImage(white, 0, 0, null);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f));
        g.drawImage(white, 0, 0, null);
        g.dispose();
        return out;
    }

    private ArrayList<PieceExplosionShard> buildPieceShards(BufferedImage sprite) {
        ArrayList<PieceExplosionShard> shards = new ArrayList<>();
        if (sprite == null) return shards;
        java.util.Random rnd = new java.util.Random(System.nanoTime());
        int cell = 12;
        int centerX = sprite.getWidth() / 2;
        int centerY = sprite.getHeight() / 2;
        for (int y = 0; y < sprite.getHeight(); y += cell) {
            for (int x = 0; x < sprite.getWidth(); x += cell) {
                int sw = Math.min(cell, sprite.getWidth() - x);
                int sh = Math.min(cell, sprite.getHeight() - y);
                int sample = sprite.getRGB(x + sw / 2, y + sh / 2);
                int alpha = (sample >>> 24) & 0xFF;
                if (alpha < 30) continue;

                double dx = (x + sw / 2.0) - centerX;
                double dy = (y + sh / 2.0) - centerY;
                double len = Math.max(6.0, Math.hypot(dx, dy));
                PieceExplosionShard s = new PieceExplosionShard();
                s.sx = x;
                s.sy = y;
                s.sw = sw;
                s.sh = sh;
                double launch = 150 + len * 3.5;
                s.vx = (dx / len) * launch;
                s.vy = (dy / len) * launch;
                s.spin = (dx >= 0 ? 1 : -1) * (0.8 + (len / 28.0));
                s.angleOffset = (dx + dy) * 0.02;
                buildRandomShardPolygon(s, rnd);
                shards.add(s);
            }
        }
        return shards;
    }

    private void buildRandomShardPolygon(PieceExplosionShard s, java.util.Random rnd) {
        if (s == null) return;
        int maxW = Math.max(2, s.sw);
        int maxH = Math.max(2, s.sh);
        int points = 3 + rnd.nextInt(4); // 3..6
        int[] px = new int[points];
        int[] py = new int[points];
        double cx = maxW / 2.0;
        double cy = maxH / 2.0;
        for (int i = 0; i < points; i++) {
            double ang = (Math.PI * 2.0 * i / points) + (rnd.nextDouble() - 0.5) * 0.65;
            double rx = (maxW * (0.25 + rnd.nextDouble() * 0.35));
            double ry = (maxH * (0.25 + rnd.nextDouble() * 0.35));
            int vx = (int) Math.round(cx + Math.cos(ang) * rx);
            int vy = (int) Math.round(cy + Math.sin(ang) * ry);
            px[i] = Math.max(0, Math.min(maxW - 1, vx));
            py[i] = Math.max(0, Math.min(maxH - 1, vy));
        }
        s.px = px;
        s.py = py;
        s.pcount = points;
    }

    private BufferedImage tintPieceToWhite(BufferedImage src, float amount) {
        if (src == null) return null;
        float a = Math.max(0f, Math.min(1f, amount));
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    out.setRGB(x, y, argb);
                    continue;
                }
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                int nr = (int) Math.round(r + (255 - r) * a);
                int ng = (int) Math.round(g + (255 - g) * a);
                int nb = (int) Math.round(b + (255 - b) * a);
                out.setRGB(x, y, (alpha << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return out;
    }

    private double hashToUnit(long seed, long tick, int salt) {
        long v = seed ^ (tick * 1103515245L) ^ (salt * 12345L);
        v ^= (v << 13);
        v ^= (v >>> 7);
        v ^= (v << 17);
        long positive = v & 0x7fffffffffffffffL;
        return (positive % 10000L) / 10000.0;
    }

    private void drawShieldOverlayAt(Graphics2D g2d, int row, int col, float alpha, long nowMs) {
        if (!inBounds(row, col) || alpha <= 0f) return;
        int dc = boardFlipped ? (7 - col) : col;
        int dr = boardFlipped ? (7 - row) : row;
        int x = dc * SQUARE_SIZE;
        int y = dr * SQUARE_SIZE;

        ensureShieldOverlayIcon();
        if (shieldOverlayIcon == null) return;
        float pulse = 1.0f;

        Graphics2D gs = (Graphics2D) g2d.create();
        gs.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gs.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha * pulse)));

        int iconSize = SQUARE_SIZE / 2;
        int ix = x + (SQUARE_SIZE - iconSize-1);
        int iy = y + (SQUARE_SIZE - iconSize-2);
        gs.drawImage(shieldOverlayIcon, ix, iy, iconSize, iconSize, null);

        gs.dispose();
    }

    private void ensureShieldOverlayIcon() {
        if (shieldOverlayIcon != null) return;
        try {
            java.net.URL u = getClass().getResource("/assets/extras/shield.png");
            if (u != null) {
                shieldOverlayIcon = ImageIO.read(u);
                return;
            }
        } catch (Exception ignored) {
        }
        String[] paths = new String[] {
            "src/assets/extras/shield.png",
            "assets/extras/shield.png"
        };
        for (String p : paths) {
            try {
                java.io.File f = new java.io.File(p);
                if (!f.exists()) continue;
                shieldOverlayIcon = ImageIO.read(f);
                if (shieldOverlayIcon != null) return;
            } catch (Exception ignored) {
            }
        }
    }

    private void ensureFreezeSnowflakeIcon() {
        if (freezeSnowflakeIcon != null) return;
        try {
            java.net.URL u = getClass().getResource("/assets/spells/snowflake.png");
            if (u != null) {
                freezeSnowflakeIcon = ImageIO.read(u);
                return;
            }
        } catch (Exception ignored) {
        }
        String[] paths = new String[] {
            "src/assets/spells/snowflake.png",
            "assets/spells/snowflake.png"
        };
        for (String p : paths) {
            try {
                java.io.File f = new java.io.File(p);
                if (!f.exists()) continue;
                freezeSnowflakeIcon = ImageIO.read(f);
                if (freezeSnowflakeIcon != null) return;
            } catch (Exception ignored) {
            }
        }
    }

    private void ensureFireballMissileFrames() {
        if (fireballMissileFramesLoaded) return;
        fireballMissileFramesLoaded = true;
        fireballMissileFrames.clear();

        loadSequentialFireballMissileFramesFromResources("/assets/spellchesssprites/nuke_series/");

        if (fireballMissileFrames.isEmpty()) {
            String[] frameDirs = new String[] {
                "src/assets/spellchesssprites/nuke_series",
                "assets/spellchesssprites/nuke_series"
            };
            for (String dirPath : frameDirs) {
                loadSequentialFireballMissileFramesFromDirectory(dirPath);
                if (!fireballMissileFrames.isEmpty()) break;
            }
        }

        if (fireballMissileFrames.isEmpty()) {
            ensureFireballMissileSprite();
        }
    }

    private void loadSequentialFireballMissileFramesFromResources(String baseResourceDir) {
        boolean foundAny = false;
        int missesAfterFirstHit = 0;
        for (int i = 1; i <= MAX_NUKE_SERIES_FRAMES; i++) {
            String name = String.format("%03d.png", i);
            String resourcePath = baseResourceDir + name;
            try {
                java.net.URL u = getClass().getResource(resourcePath);
                if (u == null) {
                    if (foundAny && ++missesAfterFirstHit >= 4) break;
                    continue;
                }
                BufferedImage img = ImageIO.read(u);
                if (img == null) continue;
                fireballMissileFrames.add(img);
                foundAny = true;
                missesAfterFirstHit = 0;
            } catch (Exception ignored) {
            }
        }
    }

    private void loadSequentialFireballMissileFramesFromDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) return;
        for (int i = 1; i <= MAX_NUKE_SERIES_FRAMES; i++) {
            String name = String.format("%03d.png", i);
            File frame = new File(dir, name);
            if (!frame.exists()) {
                if (!fireballMissileFrames.isEmpty()) break;
                continue;
            }
            try {
                BufferedImage img = ImageIO.read(frame);
                if (img != null) fireballMissileFrames.add(img);
            } catch (Exception ignored) {
            }
        }
    }

    private void ensureFireballMissileSprite() {
        if (fireballMissileSprite != null) return;
        String[] resourcePaths = new String[] {
            "/assets/spellchesssprites/nuke.png",
            "/assets/spellchesssprites/nuke_sprite.png",
            "/assets/spellchesssprites/nuke1.png"
        };
        for (String rp : resourcePaths) {
            try {
                java.net.URL u = getClass().getResource(rp);
                if (u == null) continue;
                fireballMissileSprite = ImageIO.read(u);
                if (fireballMissileSprite != null) return;
            } catch (Exception ignored) {
            }
        }
        String[] filePaths = new String[] {
            "src/assets/spellchesssprites/nuke.png",
            "src/assets/spellchesssprites/nuke_sprite.png",
            "src/assets/spellchesssprites/nuke1.png",
            "assets/spellchesssprites/nuke.png",
            "assets/spellchesssprites/nuke_sprite.png",
            "assets/spellchesssprites/nuke1.png"
        };
        for (String p : filePaths) {
            try {
                java.io.File f = new java.io.File(p);
                if (!f.exists()) continue;
                fireballMissileSprite = ImageIO.read(f);
                if (fireballMissileSprite != null) return;
            } catch (Exception ignored) {
            }
        }
    }

    private void ensureFireballImpactFlashSprite() {
        if (fireballImpactFlashSprite != null) return;
        String[] resourcePaths = new String[] {
            "/assets/spellchesssprites/flashh.png"
        };
        for (String rp : resourcePaths) {
            try {
                java.net.URL u = getClass().getResource(rp);
                if (u == null) continue;
                fireballImpactFlashSprite = ImageIO.read(u);
                if (fireballImpactFlashSprite != null) {
                    fireballImpactFlashSoftSprite = createSoftenedFlashSprite(fireballImpactFlashSprite);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        String[] filePaths = new String[] {
            "src/assets/spellchesssprites/flashh.png",
            "assets/spellchesssprites/flashh.png"
        };
        for (String p : filePaths) {
            try {
                java.io.File f = new java.io.File(p);
                if (!f.exists()) continue;
                fireballImpactFlashSprite = ImageIO.read(f);
                if (fireballImpactFlashSprite != null) {
                    fireballImpactFlashSoftSprite = createSoftenedFlashSprite(fireballImpactFlashSprite);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void ensureFireballSmokeSprite() {
        if (fireballSmokeSprite != null && fireballSmallSmokeSprite != null) return;
        String[] resourcePaths = new String[] {
            "/assets/spellchesssprites/smoke.png",
            "/assets/spellchesssprites/small smoke.png"
        };
        for (String rp : resourcePaths) {
            try {
                java.net.URL u = getClass().getResource(rp);
                if (u == null) continue;
                BufferedImage img = ImageIO.read(u);
                if (img == null) continue;
                if (rp.endsWith("smoke.png") && !rp.contains("small")) {
                    fireballSmokeSprite = img;
                } else if (rp.endsWith("small smoke.png")) {
                    fireballSmallSmokeSprite = img;
                }
            } catch (Exception ignored) {
            }
        }
        String[] filePaths = new String[] {
            "src/assets/spellchesssprites/smoke.png",
            "src/assets/spellchesssprites/small smoke.png",
            "assets/spellchesssprites/smoke.png"
            ,"assets/spellchesssprites/small smoke.png"
        };
        for (String p : filePaths) {
            try {
                java.io.File f = new java.io.File(p);
                if (!f.exists()) continue;
                BufferedImage img = ImageIO.read(f);
                if (img == null) continue;
                String norm = p.replace('\\', '/').toLowerCase();
                if (norm.endsWith("/small smoke.png")) {
                    fireballSmallSmokeSprite = img;
                } else if (norm.endsWith("/smoke.png")) {
                    fireballSmokeSprite = img;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private BufferedImage createSoftenedFlashSprite(BufferedImage src) {
        if (src == null) return null;
        try {
            BufferedImage argb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = argb.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(src, 0, 0, null);
            g2.dispose();

            float[] g5 = new float[] {
                1f, 4f, 6f, 4f, 1f,
                4f, 16f, 24f, 16f, 4f,
                6f, 24f, 36f, 24f, 6f,
                4f, 16f, 24f, 16f, 4f,
                1f, 4f, 6f, 4f, 1f
            };
            float sum = 0f;
            for (float v : g5) sum += v;
            for (int i = 0; i < g5.length; i++) g5[i] /= sum;
            ConvolveOp blur = new ConvolveOp(new Kernel(5, 5, g5), ConvolveOp.EDGE_NO_OP, null);

            BufferedImage pass1 = new BufferedImage(argb.getWidth(), argb.getHeight(), BufferedImage.TYPE_INT_ARGB);
            blur.filter(argb, pass1);
            BufferedImage pass2 = new BufferedImage(argb.getWidth(), argb.getHeight(), BufferedImage.TYPE_INT_ARGB);
            blur.filter(pass1, pass2);
            return pass2;
        } catch (Exception ignored) {
            return src;
        }
    }

    private void ensureBomberShadowSilhouetteSprite() {
        if (bomberShadowSilhouetteSprite != null) return;
        String[] resourcePaths = new String[] {
            "/assets/spellchesssprites/bomber_sillhouette.png"
        };
        for (String rp : resourcePaths) {
            try {
                java.net.URL u = getClass().getResource(rp);
                if (u == null) continue;
                bomberShadowSilhouetteSprite = ImageIO.read(u);
                if (bomberShadowSilhouetteSprite != null) return;
            } catch (Exception ignored) {
            }
        }
        String[] filePaths = new String[] {
            "src/assets/spellchesssprites/bomber_sillhouette.png",
            "assets/spellchesssprites/bomber_sillhouette.png"
        };
        for (String p : filePaths) {
            try {
                java.io.File f = new java.io.File(p);
                if (!f.exists()) continue;
                bomberShadowSilhouetteSprite = ImageIO.read(f);
                if (bomberShadowSilhouetteSprite != null) return;
            } catch (Exception ignored) {
            }
        }
    }

    private void drawShieldVisualFx(Graphics2D g2d, boolean fogActive, boolean fogViewerIsWhite, boolean[][] visibilityMap) {
        long now = System.currentTimeMillis();

        java.util.IdentityHashMap<Piece, Float> fadeInAlpha = new java.util.IdentityHashMap<>();
        for (ShieldVisualFx fx : shieldVisualFx) {
            if (!fx.fadeIn) continue;
            if (fx.piece == null || fx.piece.getShieldedTurnsRemaining() <= 0) continue;
            double t = Math.max(0.0, Math.min(1.0, (now - fx.startMs) / (double) fx.durationMs));
            fadeInAlpha.put(fx.piece, (float) (SHIELD_BASE_ALPHA * t));
        }

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p == null || p.getShieldedTurnsRemaining() <= 0) continue;
                if (fogActive && !isPieceVisibleAt(r, c, fogViewerIsWhite, visibilityMap)) continue;
                float alpha = fadeInAlpha.getOrDefault(p, SHIELD_BASE_ALPHA);
                drawShieldOverlayAt(g2d, r, c, alpha, now);
            }
        }

        for (ShieldVisualFx fx : shieldVisualFx) {
            if (fx.fadeIn) continue;
            double t = Math.max(0.0, Math.min(1.0, (now - fx.startMs) / (double) fx.durationMs));
            float alpha = (float) (SHIELD_BASE_ALPHA * (1.0 - t));
            if (alpha <= 0f) continue;
            int row = fx.anchorRow, col = fx.anchorCol;
            int[] pos = findPiecePosition(fx.piece);
            if (pos != null) {
                row = pos[0];
                col = pos[1];
                fx.anchorRow = row;
                fx.anchorCol = col;
            }
            if (fogActive && !isPieceVisibleAt(row, col, fogViewerIsWhite, visibilityMap)) continue;
            drawShieldOverlayAt(g2d, row, col, alpha, now);
        }
    }

    private void drawFrozenPieceTintAt(Graphics2D g2d, Piece piece, int row, int col, float alpha) {
        if (piece == null || !inBounds(row, col) || alpha <= 0f) return;
        int dc = boardFlipped ? (7 - col) : col;
        int dr = boardFlipped ? (7 - row) : row;
        int x = dc * SQUARE_SIZE;
        int y = dr * SQUARE_SIZE;

        float tintStrength = Math.max(0f, Math.min(1f, alpha)) * 0.42f;
        if (tintStrength <= 0f) return;

        BufferedImage tinted = new BufferedImage(SQUARE_SIZE, SQUARE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gt = tinted.createGraphics();
        gt.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        piece.draw(gt, 0, 0);
        gt.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, tintStrength));
        gt.setColor(new Color(108, 178, 255));
        gt.fillRect(0, 0, SQUARE_SIZE, SQUARE_SIZE);
        gt.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, Math.max(0f, tintStrength * 0.35f)));
        gt.setColor(new Color(222, 244, 255));
        gt.fillRect(0, 0, SQUARE_SIZE, SQUARE_SIZE);
        gt.dispose();

        g2d.drawImage(tinted, x, y, null);
    }

    private void drawColdflakeMarkerAt(Graphics2D g2d, int row, int col, float alpha) {
        if (!inBounds(row, col) || alpha <= 0f) return;
        int dc = boardFlipped ? (7 - col) : col;
        int dr = boardFlipped ? (7 - row) : row;
        int x = dc * SQUARE_SIZE;
        int y = dr * SQUARE_SIZE;

        Graphics2D gf = (Graphics2D) g2d.create();
        gf.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float a = Math.max(0f, Math.min(1f, alpha));
        int iconSize = 25;
        int ix = x + SQUARE_SIZE - iconSize - 3;
        int iy = y + SQUARE_SIZE - iconSize - 3;
        int cx = ix + iconSize / 2;
        int cy = iy + iconSize / 2;

        ensureFreezeSnowflakeIcon();
        if (freezeSnowflakeIcon != null) {
            gf.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            java.awt.geom.AffineTransform old = gf.getTransform();
            gf.rotate(Math.toRadians(30.0), cx, cy);
            gf.drawImage(freezeSnowflakeIcon, ix, iy, iconSize, iconSize, null);
            gf.setTransform(old);
        } else {
            gf.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            gf.setColor(new Color(232, 246, 255));
            gf.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int r = 5;
            gf.drawLine(cx - r, cy, cx + r, cy);
            gf.drawLine(cx, cy - r, cx, cy + r);
            gf.drawLine(cx - 3, cy - 3, cx + 3, cy + 3);
            gf.drawLine(cx - 3, cy + 3, cx + 3, cy - 3);
        }

        gf.dispose();
    }

    private void drawFreezeBurstFx(Graphics2D source, FreezeBurstFx fx, long now) {
        if (fx == null || !inBounds(fx.row, fx.col)) return;
        long elapsed = Math.max(0L, now - fx.startMs);
        float t = Math.max(0f, Math.min(1f, elapsed / (float) FREEZE_BURST_MS));
        int dc = boardFlipped ? 7 - fx.col : fx.col;
        int dr = boardFlipped ? 7 - fx.row : fx.row;
        float targetX = dc * SQUARE_SIZE + SQUARE_SIZE / 2f;
        float targetY = dr * SQUARE_SIZE + SQUARE_SIZE / 2f;
        float centerX = BOARD_SIZE / 2f;
        float centerY = BOARD_SIZE / 2f;

        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        float streamFade = elapsed < FREEZE_STREAM_MS
                ? 1f : Math.max(0f, 1f - (elapsed - FREEZE_STREAM_MS) / (float) FREEZE_STREAM_TRAVEL_MS);
        if (streamFade > 0f) {
            java.awt.geom.Path2D.Float beam = new java.awt.geom.Path2D.Float();
            beam.moveTo(centerX, centerY);
            beam.quadTo((centerX + targetX) / 2f - 10f, (centerY + targetY) / 2f - 18f, targetX, targetY);
            float beat = 0.72f + 0.28f * (float) Math.sin(
                    elapsed / (double) FREEZE_STREAM_MS * FREEZE_STREAM_BEATS * Math.PI * 2.0);
            g.setStroke(new BasicStroke(13f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(70, 184, 255, Math.round(36 * streamFade * beat)));
            g.draw(beam);
            g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(220, 250, 255, Math.round(105 * streamFade * beat)));
            g.draw(beam);
        }

        int emittedParticles = (int) Math.min(
                FREEZE_STREAM_MS / FREEZE_STREAM_PARTICLE_INTERVAL_MS + 1,
                elapsed / FREEZE_STREAM_PARTICLE_INTERVAL_MS + 1);
        for (int i = 0; i < emittedParticles; i++) {
            long bornAt = i * FREEZE_STREAM_PARTICLE_INTERVAL_MS;
            long age = elapsed - bornAt;
            if (age < 0 || age > FREEZE_STREAM_TRAVEL_MS) continue;
            float localT = smootherStepValue(age / (float) FREEZE_STREAM_TRAVEL_MS);
            float beatPhase = bornAt / (float) FREEZE_STREAM_MS * FREEZE_STREAM_BEATS;
            float beatStrength = 0.78f + 0.22f * (float) Math.sin(beatPhase * Math.PI * 2.0);
            float bend = (float) Math.sin(i * 2.17) * 22f;
            float x = quadraticPoint(centerX, (centerX + targetX) / 2f + bend, targetX, localT);
            float y = quadraticPoint(centerY, (centerY + targetY) / 2f
                    + (float) Math.cos(i * 1.63) * 34f, targetY, localT);
            float orbit = (float) Math.sin(localT * Math.PI) * (7f + (i % 4) * 3f);
            x += (float) Math.cos(i * 1.37) * orbit;
            y += (float) Math.sin(i * 1.91) * orbit;
            float alpha = Math.min(1f, localT * 7f) * Math.min(1f, (1f - localT) * 8f);
            drawSnowflake(g, x, y, (5f + i % 4) * beatStrength, i * 0.48 + localT * 5.2,
                    new Color(220, 248, 255, Math.round(245 * alpha)));
        }

        long crystalStartMs = FREEZE_STREAM_MS + FREEZE_STREAM_TRAVEL_MS / 2L;
        float crystalT = Math.max(0f, Math.min(1f,
                (elapsed - crystalStartMs) / (float) FREEZE_CRYSTAL_GROW_MS));
        if (crystalT > 0f) {
            float grow = smootherStepValue(Math.min(1f, crystalT / 0.72f));
            float settle = crystalT < 0.82f ? 1f : 1f - smootherStepValue((crystalT - 0.82f) / 0.18f);
            float crystalAlpha = 0.78f * Math.max(0f, settle);
            int baseY = Math.round(targetY + SQUARE_SIZE * 0.42f);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, crystalAlpha));
            for (int i = 0; i < 9; i++) {
                float angle = (float) (-Math.PI * 0.82 + i * Math.PI * 0.205);
                float height = SQUARE_SIZE * (0.48f + (i % 3) * 0.15f) * grow;
                float width = SQUARE_SIZE * (0.10f + (i % 2) * 0.035f) * grow;
                float rootX = targetX + (i - 4) * SQUARE_SIZE * 0.105f;
                float tipX = rootX + (float) Math.cos(angle) * height * 0.28f;
                float tipY = baseY + (float) Math.sin(angle) * height;

                java.awt.geom.Path2D.Float crystal = new java.awt.geom.Path2D.Float();
                crystal.moveTo(rootX - width, baseY);
                crystal.lineTo(tipX, tipY);
                crystal.lineTo(rootX + width, baseY);
                crystal.closePath();
                g.setPaint(new GradientPaint(
                        rootX, baseY, new Color(70, 166, 255, 175),
                        tipX, tipY, new Color(232, 252, 255, 225)));
                g.fill(crystal);
                g.setColor(new Color(225, 250, 255, 220));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(crystal);
            }
        }
        g.dispose();
    }

    private void drawSnowflake(Graphics2D g, float x, float y, float radius, double rotation, Color color) {
        Graphics2D flake = (Graphics2D) g.create();
        flake.translate(x, y);
        flake.rotate(rotation);
        flake.setColor(color);
        flake.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int arm = 0; arm < 3; arm++) {
            flake.rotate(Math.PI / 3.0);
            flake.drawLine(Math.round(-radius), 0, Math.round(radius), 0);
            int branchX = Math.round(radius * 0.48f);
            int branchY = Math.max(2, Math.round(radius * 0.28f));
            flake.drawLine(branchX, 0, Math.round(radius * 0.78f), branchY);
            flake.drawLine(branchX, 0, Math.round(radius * 0.78f), -branchY);
        }
        flake.dispose();
    }

    private static float quadraticPoint(float start, float control, float end, float t) {
        float inverse = 1f - t;
        return inverse * inverse * start + 2f * inverse * t * control + t * t * end;
    }

    private static float smootherStepValue(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    private void drawFreezeVisualFx(Graphics2D g2d, boolean fogActive, boolean fogViewerIsWhite, boolean[][] visibilityMap) {
        long now = System.currentTimeMillis();

        java.util.IdentityHashMap<Piece, Float> fadeInAlpha = new java.util.IdentityHashMap<>();
        for (FreezeVisualFx fx : freezeVisualFx) {
            if (!fx.fadeIn) continue;
            if (fx.piece == null || fx.piece.getFrozenTurnsRemaining() <= 0) continue;
            double t = Math.max(0.0, Math.min(1.0, (now - fx.startMs) / (double) fx.durationMs));
            fadeInAlpha.put(fx.piece, (float) (FREEZE_BASE_ALPHA * t));
        }
        for (FreezeBurstFx fx : freezeBurstFx) {
            if (fx.piece == null || fx.piece.getFrozenTurnsRemaining() <= 0) continue;
            float t = Math.max(0f, Math.min(1f, (now - fx.startMs) / (float) FREEZE_BURST_MS));
            fadeInAlpha.put(fx.piece,
                    FREEZE_BASE_ALPHA * smootherStepValue(Math.max(0f, (t - 0.72f) / 0.28f)));
        }

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p == null || p.getFrozenTurnsRemaining() <= 0) continue;
                if (pendingFreezeVisualPieces.contains(p)) continue;
                if (fogActive && !isPieceVisibleAt(r, c, fogViewerIsWhite, visibilityMap)) continue;
                float alpha = fadeInAlpha.getOrDefault(p, FREEZE_BASE_ALPHA);
                drawFrozenPieceTintAt(g2d, p, r, c, alpha);
                drawColdflakeMarkerAt(g2d, r, c, alpha);
            }
        }

        for (FreezeBurstFx fx : freezeBurstFx) {
            if (fogActive && !isPieceVisibleAt(fx.row, fx.col, fogViewerIsWhite, visibilityMap)) continue;
            drawFreezeBurstFx(g2d, fx, now);
        }

        for (FreezeVisualFx fx : freezeVisualFx) {
            if (fx.fadeIn) continue;
            double t = Math.max(0.0, Math.min(1.0, (now - fx.startMs) / (double) fx.durationMs));
            float alpha = (float) (FREEZE_BASE_ALPHA * (1.0 - t));
            if (alpha <= 0f) continue;
            int row = fx.anchorRow, col = fx.anchorCol;
            int[] pos = findPiecePosition(fx.piece);
            if (pos != null) {
                row = pos[0];
                col = pos[1];
                fx.anchorRow = row;
                fx.anchorCol = col;
            }
            if (fogActive && !isPieceVisibleAt(row, col, fogViewerIsWhite, visibilityMap)) continue;
            Piece p = inBounds(row, col) ? board[row][col] : null;
            if (p == fx.piece) {
                drawFrozenPieceTintAt(g2d, p, row, col, alpha);
            }
            drawColdflakeMarkerAt(g2d, row, col, alpha);
        }
    }

    public boolean inBoundsForSpell(int r, int c) {
        return inBounds(r, c);
    }

    public Piece getPieceAt(int r, int c) {
        if (!inBounds(r, c)) return null;
        return board[r][c];
    }

    public void setPieceAt(int r, int c, Piece p) {
        if (!inBounds(r, c)) return;
        board[r][c] = p;
    }

    public PlayerState getPlayerState(boolean white) {
        return white ? whiteState : blackState;
    }

    public boolean isWhiteTurn() {
        return whiteTurn;
    }

    private boolean isSideDisplayedAtTop(boolean sideWhite) {
        return sideWhite == boardFlipped;
    }

    public boolean isKingInCheckFor(boolean white) {
        return new IsLegal(board).isInCheck(white);
    }

    public void addCapturedPieceToOwner(boolean ownerWhite, Piece capturedPiece) {
        getPlayerState(ownerWhite).addCapturedPiece(capturedPiece);
    }

    public Piece createPieceByType(String pieceType, boolean white) {
        if (pieceType == null) return null;
        switch (pieceType.toLowerCase()) {
            case "queen": return new Queen(white);
            case "rook": return new Rook(white);
            case "bishop": return new Bishop(white);
            case "knight": return new Knight(white);
            case "pawn": return new Pawn(white);
            case "zoglin": return new Zoglin(white);
            default: return null;
        }
    }

    public boolean canPlayerSeeSquare(boolean viewerWhite, int row, int col) {
        if (!inBounds(row, col)) return false;
        if (!isSpellFogActiveForViewer(viewerWhite)) return true;
        boolean[][] visibilityMap = computeVisibilityMap(viewerWhite);
        return visibilityMap[row][col];
    }

    public int getElixirForSide(boolean white) {
        return getPlayerState(white).getElixir();
    }

    public List<Spell> getSpellDefinitions() {
        return spellManager.getAllSpells();
    }

    public boolean canSideCastAny(String spellId, boolean sideWhite) {
        if (animating) return false;
        if (sideWhite != whiteTurn) return false;
        if (pendingEndermanPiecePick || endermanPhaseActive) return false;
        if (pendingBomberRookPick || bomberPrimedRookRow != -1) return false;
        if (pendingSpellTargetingId != null) return false;
        if (pendingUrielPlacement) return false;
        if (SpellManager.ENDERMAN.equals(spellId)) {
            if (getPlayerState(sideWhite).getElixir() < ENDERMAN_COST) return false;
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    Piece p = board[r][c];
                    if (p == null) continue;
                    board[r][c] = null;
                    try {
                        if (!getActiveLegalMoves(sideWhite).isEmpty()) return true;
                    } finally {
                        board[r][c] = p;
                    }
                }
            }
            return false;
        }
        return spellManager.canCastAny(this, spellId, sideWhite);
    }

    private String toSquareName(int row, int col) {
        char file = (char) ('a' + col);
        int rank = 8 - row;
        return "" + file + rank;
    }

    private String describeTarget(String spellId, SpellTarget t) {
        if (SpellManager.ENDERMAN.equals(spellId)) {
            return toSquareName(t.sourceRow, t.sourceCol) + " -> " + toSquareName(t.destRow, t.destCol);
        }
        if (SpellManager.BOMBER.equals(spellId)) {
            return toSquareName(t.sourceRow, t.sourceCol) + " -> " + toSquareName(t.destRow, t.destCol);
        }
        if (SpellManager.URIEL.equals(spellId)) {
            return t.resurrectPieceType + " @ " + toSquareName(t.destRow, t.destCol);
        }
        if (SpellManager.FOG.equals(spellId)) {
            return "Apply fog to opponent";
        }
        if (t.targetRow != null && t.targetCol != null) {
            Piece p = getPieceAt(t.targetRow, t.targetCol);
            String type = (p == null) ? "Square" : p.getClass().getSimpleName();
            return type + " @ " + toSquareName(t.targetRow, t.targetCol);
        }
        return "Target";
    }

    public void requestSpellElixirRefund() {
        refundLastSpellElixir = true;
    }

    public boolean consumeSpellElixirRefundRequest() {
        boolean v = refundLastSpellElixir;
        refundLastSpellElixir = false;
        return v;
    }

    public void requestSpellCardPreserve() {
        preserveLastSpellCard = true;
    }

    public boolean consumeSpellCardPreserveRequest() {
        boolean v = preserveLastSpellCard;
        preserveLastSpellCard = false;
        return v;
    }

    private void beginDeferredSpellVisualEffects() {
        deferSpellVisualEffects = true;
        deferredSpellVisualEffects.clear();
    }

    private void cancelDeferredSpellVisualEffects() {
        deferSpellVisualEffects = false;
        deferredSpellVisualEffects.clear();
        pendingFreezeVisualPieces.clear();
        pendingFireballVictims.clear();
        pendingUrielVisualPieces.clear();
    }

    public void releaseDeferredSpellVisualEffects() {
        deferSpellVisualEffects = false;
        ArrayList<Runnable> effects = new ArrayList<>(deferredSpellVisualEffects);
        deferredSpellVisualEffects.clear();
        for (Runnable effect : effects) {
            effect.run();
        }
        repaint();
    }

    public Point getEndermanCardTargetPoint() {
        if (!inBounds(endermanCardTargetRow, endermanCardTargetCol)) return null;
        int displayRow = boardFlipped ? 7 - endermanCardTargetRow : endermanCardTargetRow;
        int displayCol = boardFlipped ? 7 - endermanCardTargetCol : endermanCardTargetCol;
        double scale = renderScale();
        Point target = new Point(
                (int) Math.round((displayCol * SQUARE_SIZE + SQUARE_SIZE / 2.0) * scale),
                (int) Math.round((displayRow * SQUARE_SIZE + SQUARE_SIZE / 2.0) * scale));
        endermanCardTargetRow = -1;
        endermanCardTargetCol = -1;
        return target;
    }

    private void queueEndermanTeleportAfterCard(int fromRow, int fromCol, int toRow, int toCol) {
        pendingEndermanTeleportFromRow = fromRow;
        pendingEndermanTeleportFromCol = fromCol;
        pendingEndermanTeleportToRow = toRow;
        pendingEndermanTeleportToCol = toCol;
        endermanCardTargetRow = fromRow;
        endermanCardTargetCol = fromCol;
        endermanConsumePending = true;
        endermanConsumeSideWhite = whiteTurn;
        animating = true;
        selectedPiece = null;
        draggedPiece = null;
        dragging = false;
        possibleMoves.clear();
        pendingSpellHighlightSquares.clear();
        endermanChangedSources.clear();
        if (gameController != null) {
            gameController.onBoardSpellCastResolved(SpellManager.ENDERMAN, endermanConsumeSideWhite);
        }
        repaint();
    }

    public void completePendingEndermanTeleport() {
        if (!inBounds(pendingEndermanTeleportFromRow, pendingEndermanTeleportFromCol)
                || !inBounds(pendingEndermanTeleportToRow, pendingEndermanTeleportToCol)) {
            animating = false;
            return;
        }
        endermanPickupFxRow = pendingEndermanTeleportFromRow;
        endermanPickupFxCol = pendingEndermanTeleportFromCol;
        endermanPickupFxDestRow = pendingEndermanTeleportToRow;
        endermanPickupFxDestCol = pendingEndermanTeleportToCol;
        endermanTeleportFxPiece = board[endermanPickupFxRow][endermanPickupFxCol];
        endermanTeleportFxMoved = false;
        endermanTeleportSoundPlayed = false;
        endermanPickupFxStartedAtMs = System.currentTimeMillis();
        if (endermanPickupFxTimer != null) endermanPickupFxTimer.stop();
        endermanPickupFxTimer = new Timer(AnimationTiming.FRAME_DELAY_MS, e -> {
            long elapsed = System.currentTimeMillis() - endermanPickupFxStartedAtMs;
            repaint();
            long moveAtMs = ENDERMAN_PICKUP_FX_MS + ENDERMAN_POP_OUT_MS;
            if (!endermanTeleportSoundPlayed
                    && elapsed >= moveAtMs - ENDERMAN_SOUND_EARLY_MS) {
                endermanTeleportSoundPlayed = true;
                SoundManager.playExtraSound("teleport.wav");
            }
            if (!endermanTeleportFxMoved
                    && elapsed >= moveAtMs) {
                endermanTeleportFxMoved = true;
                performPendingEndermanTeleport();
                animating = true;
            }
            if (elapsed >= ENDERMAN_PICKUP_FX_MS + ENDERMAN_POP_OUT_MS + ENDERMAN_POP_IN_MS) {
                ((Timer) e.getSource()).stop();
                endermanPickupFxTimer = null;
                endermanPickupFxStartedAtMs = -1L;
                endermanPickupFxRow = -1;
                endermanPickupFxCol = -1;
                endermanPickupFxDestRow = -1;
                endermanPickupFxDestCol = -1;
                endermanTeleportFxPiece = null;
                endermanTeleportFxMoved = false;
                endermanTeleportSoundPlayed = false;
                animating = false;
                repaint();
            }
        });
        endermanPickupFxTimer.setCoalesce(true);
        endermanPickupFxTimer.setInitialDelay(0);
        endermanPickupFxTimer.start();
    }

    public void playEndermanAbsorbWhoosh() {
        playSpellChessSoundAsync("whoosh-wind.mp3", 0.60f, 0);
    }

    private void performPendingEndermanTeleport() {
        int fromRow = pendingEndermanTeleportFromRow;
        int fromCol = pendingEndermanTeleportFromCol;
        int toRow = pendingEndermanTeleportToRow;
        int toCol = pendingEndermanTeleportToCol;
        pendingEndermanTeleportFromRow = -1;
        pendingEndermanTeleportFromCol = -1;
        pendingEndermanTeleportToRow = -1;
        pendingEndermanTeleportToCol = -1;
        completingEndermanTeleport = true;
        animating = false;
        try {
            executeInstant(fromRow, fromCol, toRow, toCol);
        } finally {
            completingEndermanTeleport = false;
        }
    }

    private void drawEndermanPickupFx(Graphics2D source, long nowMs) {
        if (endermanPickupFxStartedAtMs < 0L
                || !inBounds(endermanPickupFxRow, endermanPickupFxCol)) {
            return;
        }
        long elapsedMs = nowMs - endermanPickupFxStartedAtMs;
        boolean popIn = elapsedMs >= ENDERMAN_PICKUP_FX_MS + ENDERMAN_POP_OUT_MS;
        int effectRow = popIn ? endermanPickupFxDestRow : endermanPickupFxRow;
        int effectCol = popIn ? endermanPickupFxDestCol : endermanPickupFxCol;
        if (!inBounds(effectRow, effectCol)) return;
        int displayRow = boardFlipped ? 7 - effectRow : effectRow;
        int displayCol = boardFlipped ? 7 - effectCol : effectCol;
        float cx = displayCol * SQUARE_SIZE + SQUARE_SIZE / 2f;
        float cy = displayRow * SQUARE_SIZE + SQUARE_SIZE / 2f;

        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float pulse = 0f;
        float chargeProgress = Math.max(0f, Math.min(1f,
                elapsedMs / (float) ENDERMAN_PICKUP_FX_MS));
        float chargeStrength = chargeProgress * chargeProgress * (3f - 2f * chargeProgress);
        if (elapsedMs < ENDERMAN_PICKUP_FX_MS) {
            for (int wave = 0; wave < 4; wave++) {
                float waveT = ((elapsedMs + wave * 58L) % 230L) / 230f;
                float waveAlpha = 1f - waveT;
                float radius = SQUARE_SIZE * (0.32f + waveT * 0.72f);
                g.setStroke(new BasicStroke(
                        10f + 6f * waveAlpha,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g.setColor(new Color(151, 73, 235, Math.round(18f * waveAlpha)));
                g.drawOval(
                        Math.round(cx - radius),
                        Math.round(cy - radius),
                        Math.round(radius * 2f),
                        Math.round(radius * 2f));
                g.setStroke(new BasicStroke(
                        5f + 3f * waveAlpha,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g.setColor(new Color(188, 112, 255, Math.round(40f * waveAlpha)));
                g.drawOval(
                        Math.round(cx - radius),
                        Math.round(cy - radius),
                        Math.round(radius * 2f),
                        Math.round(radius * 2f));
                g.setStroke(new BasicStroke(
                        1.6f + 1.8f * waveAlpha,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g.setColor(new Color(230, 194, 255, Math.round(105f * waveAlpha)));
                g.drawOval(
                        Math.round(cx - radius),
                        Math.round(cy - radius),
                        Math.round(radius * 2f),
                        Math.round(radius * 2f));
            }
            pulse = 0.5f + 0.5f * (float) Math.sin(elapsedMs * Math.PI * 2.0 / 180.0);
        } else if (!popIn) {
            float popOutT = Math.max(0f, Math.min(1f,
                    (elapsedMs - ENDERMAN_PICKUP_FX_MS) / (float) ENDERMAN_POP_OUT_MS));
            pulse = 1f - popOutT;
        } else {
            float popInT = Math.max(0f, Math.min(1f,
                    (elapsedMs - ENDERMAN_PICKUP_FX_MS - ENDERMAN_POP_OUT_MS)
                            / (float) ENDERMAN_POP_IN_MS));
            pulse = (float) Math.sin(Math.PI * popInT);
            float ringRadius = SQUARE_SIZE * (0.20f + easeOutCubic(popInT) * 0.82f);
            float ringAlpha = 1f - popInT;
            g.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(155, 71, 235, Math.round(30f * ringAlpha)));
            g.drawOval(
                    Math.round(cx - ringRadius),
                    Math.round(cy - ringRadius),
                    Math.round(ringRadius * 2f),
                    Math.round(ringRadius * 2f));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(236, 211, 255, Math.round(190f * ringAlpha)));
            g.drawOval(
                    Math.round(cx - ringRadius),
                    Math.round(cy - ringRadius),
                    Math.round(ringRadius * 2f),
                    Math.round(ringRadius * 2f));
        }

        float glowRadius = SQUARE_SIZE * (0.40f + pulse * 0.55f);
        RadialGradientPaint glow = new RadialGradientPaint(
                new java.awt.geom.Point2D.Float(cx, cy),
                Math.max(1f, glowRadius),
                new float[]{0f, 0.42f, 1f},
                new Color[]{
                    new Color(255, 255, 255, Math.round(165f * pulse)),
                    new Color(189, 104, 255, Math.round(105f * pulse)),
                    new Color(92, 31, 170, 0)
                });
        g.setPaint(glow);
        g.fillOval(
                Math.round(cx - glowRadius),
                Math.round(cy - glowRadius),
                Math.round(glowRadius * 2f),
                Math.round(glowRadius * 2f));

        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI * 2.0 / 12.0 + elapsedMs * 0.0036;
            float inner = SQUARE_SIZE * 0.34f;
            float outer = inner + SQUARE_SIZE * 0.32f * pulse;
            int x1 = Math.round(cx + (float) Math.cos(angle) * inner);
            int y1 = Math.round(cy + (float) Math.sin(angle) * inner);
            int x2 = Math.round(cx + (float) Math.cos(angle) * outer);
            int y2 = Math.round(cy + (float) Math.sin(angle) * outer);
            g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(137, 56, 220, Math.round(24f * pulse)));
            g.drawLine(x1, y1, x2, y2);
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(188, 109, 255, Math.round(52f * pulse)));
            g.drawLine(x1, y1, x2, y2);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(236, 211, 255, Math.round(190f * pulse)));
            g.drawLine(
                    x1, y1, x2, y2);
        }

        Piece piece = endermanTeleportFxPiece;
        if (piece != null) {
            float pieceScale;
            if (elapsedMs < ENDERMAN_PICKUP_FX_MS) {
                pieceScale = 1f + 0.18f * chargeStrength;
            } else if (!popIn) {
                float popOutT = Math.max(0f, Math.min(1f,
                        (elapsedMs - ENDERMAN_PICKUP_FX_MS) / (float) ENDERMAN_POP_OUT_MS));
                float easedPopOut = popOutT * popOutT * (3f - 2f * popOutT);
                pieceScale = 1.18f - easedPopOut * 1.12f;
            } else {
                float popInT = Math.max(0f, Math.min(1f,
                        (elapsedMs - ENDERMAN_PICKUP_FX_MS - ENDERMAN_POP_OUT_MS)
                                / (float) ENDERMAN_POP_IN_MS));
                if (popInT < 0.58f) {
                    float emergeT = easeOutCubic(popInT / 0.58f);
                    pieceScale = 0.06f + emergeT * 1.14f;
                } else {
                    float settleT = (popInT - 0.58f) / 0.42f;
                    float easedSettle = settleT * settleT * (3f - 2f * settleT);
                    pieceScale = 1.20f - easedSettle * 0.20f;
                }
            }
            Graphics2D gp = (Graphics2D) g.create();
            gp.translate(cx, cy);
            gp.scale(pieceScale, pieceScale);
            gp.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.96f));
            piece.draw(gp, -SQUARE_SIZE / 2, -SQUARE_SIZE / 2);
            gp.dispose();
        }
        g.dispose();
    }

    private boolean isPendingEndermanPieceSquare(int row, int col) {
        if (!pendingEndermanPiecePick) return false;
        for (int[] square : pendingSpellHighlightSquares) {
            if (square != null && square.length >= 2
                    && square[0] == row && square[1] == col) {
                return true;
            }
        }
        return false;
    }

    private void drawEndermanPhaseOutline(
            Graphics2D source, Piece piece, int x, int y, int row, int col, long nowMs) {
        BufferedImage sprite = createPieceSprite(piece);
        if (sprite == null) return;
        long seed = Integer.toUnsignedLong(System.identityHashCode(piece))
                ^ ((row * 8L + col + 1L) * 0x9E3779B9L);
        seed ^= seed >>> 16;

        long sweepDurationMs = 1350L + Math.floorMod(seed, 650L);
        long pauseDurationMs = 250L + Math.floorMod(seed >>> 9, 750L);
        long cycleDurationMs = sweepDurationMs + pauseDurationMs;
        long phaseOffsetMs = Math.floorMod(seed >>> 18, cycleDurationMs);
        long cycleTimeMs = Math.floorMod(nowMs + phaseOffsetMs, cycleDurationMs);
        if (cycleTimeMs >= sweepDurationMs) {
            return;
        }

        float sweepT = cycleTimeMs / (float) sweepDurationMs;
        float bandCenterY = y + SQUARE_SIZE * (1.18f - sweepT * 1.36f);
        float bandHeight = SQUARE_SIZE * 0.32f;

        BufferedImage phaseLayer = new BufferedImage(
                SQUARE_SIZE, SQUARE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D layer = phaseLayer.createGraphics();
        layer.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        layer.drawImage(sprite, 0, 0, SQUARE_SIZE, SQUARE_SIZE, null);
        layer.setComposite(AlphaComposite.SrcIn);
        layer.setPaint(new LinearGradientPaint(
                0f,
                bandCenterY - y - bandHeight,
                0f,
                bandCenterY - y + bandHeight,
                new float[]{0f, 0.32f, 0.5f, 0.68f, 1f},
                new Color[]{
                    new Color(113, 44, 190, 0),
                    new Color(150, 76, 232, 70),
                    new Color(225, 188, 255, 205),
                    new Color(150, 76, 232, 70),
                    new Color(113, 44, 190, 0)
                }));
        layer.fillRect(0, 0, SQUARE_SIZE, SQUARE_SIZE);
        layer.dispose();

        Graphics2D g = (Graphics2D) source.create();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.86f));
        g.drawImage(phaseLayer, x, y, null);
        g.clip(new Rectangle(x, Math.round(bandCenterY - 2f), SQUARE_SIZE, 5)); // clip() intersects with the current clip (setClip replaced it and could draw outside a scrolled/clipped area)
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.58f));
        g.drawImage(sprite, x - 1, y, SQUARE_SIZE, SQUARE_SIZE, null);
        g.drawImage(sprite, x + 1, y, SQUARE_SIZE, SQUARE_SIZE, null);
        g.dispose();
    }

    private void startEndermanPhasePoof(Piece piece, int row, int col, boolean reversing) {
        if (piece == null || !inBounds(row, col)) return;
        endermanPhasePoofPiece = piece;
        endermanPhasePoofRow = row;
        endermanPhasePoofCol = col;
        endermanPhasePoofReversing = reversing;
        endermanPhasePoofStartedAtMs = System.currentTimeMillis();
        ensureGlobalFxRepaintTimerRunning();
    }

    private void startEndermanPhaseUnpoof() {
        if (!endermanPhaseActive || !inBounds(endermanPhaseRow, endermanPhaseCol)) return;
        Piece piece = board[endermanPhaseRow][endermanPhaseCol];
        startEndermanPhasePoof(piece, endermanPhaseRow, endermanPhaseCol, true);
    }

    private void startEndermanOverlayFadeOut() {
        if (!endermanPhaseActive || !inBounds(endermanPhaseRow, endermanPhaseCol)) return;
        Piece piece = board[endermanPhaseRow][endermanPhaseCol];
        if (piece == null) return;
        endermanOverlayFadePiece = piece;
        endermanOverlayFadeRow = endermanPhaseRow;
        endermanOverlayFadeCol = endermanPhaseCol;
        endermanOverlayFadeStartedAtMs = System.currentTimeMillis();
        ensureGlobalFxRepaintTimerRunning();
    }

    private void drawEndermanOverlayFadeOut(Graphics2D source, long nowMs) {
        if (endermanOverlayFadeStartedAtMs < 0L || endermanOverlayFadePiece == null) return;
        float t = Math.max(0f, Math.min(1f,
                (nowMs - endermanOverlayFadeStartedAtMs) / (float) ENDERMAN_OVERLAY_FADE_MS));
        float alpha = 1f - smoothStep(t);

        int row = endermanOverlayFadeRow;
        int col = endermanOverlayFadeCol;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == endermanOverlayFadePiece) {
                    row = r;
                    col = c;
                }
            }
        }

        if (inBounds(row, col)) {
            int displayRow = boardFlipped ? 7 - row : row;
            int displayCol = boardFlipped ? 7 - col : col;
            BufferedImage sprite = createPieceSprite(endermanOverlayFadePiece);
            if (sprite != null) {
                BufferedImage tint = new BufferedImage(
                        SQUARE_SIZE, SQUARE_SIZE, BufferedImage.TYPE_INT_ARGB);
                Graphics2D tg = tint.createGraphics();
                tg.drawImage(sprite, 0, 0, null);
                tg.setComposite(AlphaComposite.SrcIn);
                tg.setColor(new Color(155, 92, 221, 205));
                tg.fillRect(0, 0, SQUARE_SIZE, SQUARE_SIZE);
                tg.dispose();

                Graphics2D g = (Graphics2D) source.create();
                g.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, alpha * 0.62f));
                g.drawImage(
                        tint,
                        displayCol * SQUARE_SIZE,
                        displayRow * SQUARE_SIZE,
                        null);
                g.dispose();
            }
        }

        if (t >= 1f) {
            endermanOverlayFadeStartedAtMs = -1L;
            endermanOverlayFadePiece = null;
            endermanOverlayFadeRow = -1;
            endermanOverlayFadeCol = -1;
        }
    }

    private boolean isPieceHandledByEndermanPhasePoof(Piece piece, int row, int col) {
        if (piece == null) return false;
        return endermanPhasePoofStartedAtMs >= 0L
                && piece == endermanPhasePoofPiece
                && row == endermanPhasePoofRow
                && col == endermanPhasePoofCol;
    }

    private void drawEndermanPhasePoof(Graphics2D source, long nowMs) {
        if (endermanPhasePoofStartedAtMs < 0L || endermanPhasePoofPiece == null
                || !inBounds(endermanPhasePoofRow, endermanPhasePoofCol)) {
            return;
        }

        int durationMs = endermanPhasePoofReversing
                ? ENDERMAN_PHASE_UNPOOF_MS : ENDERMAN_PHASE_POOF_MS;
        float t = Math.max(0f, Math.min(1f,
                (nowMs - endermanPhasePoofStartedAtMs) / (float) durationMs));

        int displayRow = boardFlipped ? 7 - endermanPhasePoofRow : endermanPhasePoofRow;
        int displayCol = boardFlipped ? 7 - endermanPhasePoofCol : endermanPhasePoofCol;
        float cx = displayCol * SQUARE_SIZE + SQUARE_SIZE / 2f;
        float cy = displayRow * SQUARE_SIZE + SQUARE_SIZE / 2f;
        BufferedImage sprite = createPieceSprite(endermanPhasePoofPiece);

        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (endermanPhasePoofReversing) {
            drawEndermanCloudReturn(g, sprite, cx, cy, t);
        } else {
            drawEndermanThreeBurstPoof(g, sprite, cx, cy, t);
        }
        g.dispose();

        if (t >= 1f) {
            endermanPhasePoofStartedAtMs = -1L;
            endermanPhasePoofPiece = null;
            endermanPhasePoofRow = -1;
            endermanPhasePoofCol = -1;
            endermanPhasePoofReversing = false;
        }
    }

    private void drawEndermanThreeBurstPoof(
            Graphics2D g, BufferedImage sprite, float cx, float cy, float t) {
        float elapsedMs = t * ENDERMAN_PHASE_POOF_MS;
        float pieceAlpha = elapsedMs < 850f
                ? 1f
                : 1f - smoothStep(Math.min(1f, (elapsedMs - 850f) / 650f)) * 0.70f;
        drawEndermanSprite(g, sprite, cx, cy, 1f, pieceAlpha);

        if (elapsedMs >= 600f && elapsedMs < 1100f) {
            float burstT = (elapsedMs - 600f) / 500f;
            if (burstT < 0.62f) {
                drawEndermanPurplePopLines(g, cx, cy, burstT / 0.62f, 0);
            } else {
                float cloudT = (burstT - 0.62f) / 0.38f;
                float growth = easeOutCubic(Math.min(1f, cloudT / 0.42f));
                drawEndermanSolidCloudShell(
                        g, cx, cy,
                        SQUARE_SIZE * (0.12f + growth * 0.48f),
                        1f);
            }
            return;
        }

        if (elapsedMs >= 1100f && elapsedMs < 1700f) {
            float holdT = (elapsedMs - 1100f) / 600f;
            float breathe = 0.5f + 0.5f
                    * (float) Math.sin(holdT * Math.PI * 3f);
            float preExpansion = smoothStep(Math.max(0f, (holdT - 0.72f) / 0.28f));
            float size = SQUARE_SIZE
                    * (0.60f + breathe * 0.035f + preExpansion * 0.18f);
            drawEndermanSolidCloudShell(
                    g, cx, cy,
                    size,
                    0.72f + breathe * 0.08f + preExpansion * 0.18f);
        }

        if (elapsedMs >= 1700f && elapsedMs < 2700f) {
            float burstT = (elapsedMs - 1700f) / 1000f;
            drawEndermanScatteringCloudArcs(g, cx, cy, burstT);
        }
    }

    private void drawEndermanScatteringCloudArcs(
            Graphics2D g, float cx, float cy, float t) {
        float shellFade = 1f - smoothStep(Math.min(1f, t / 0.12f));
        if (shellFade > 0f) {
            drawEndermanSolidCloudShell(
                    g, cx, cy, SQUARE_SIZE * (0.78f + t * 0.08f), shellFade);
        }

        float dissolveT = Math.max(0f, Math.min(1f, (t - 0.42f) / 0.58f));
        int arcCount = 5;

        for (int arc = 0; arc < arcCount; arc++) {
            double direction = -Math.PI / 2.0 + arc * Math.PI * 2.0 / arcCount;
            float speed = 0.82f + arc * 0.08f;
            float arcProgress = easeOutCubic(Math.min(1f, t * speed / 0.68f));
            float travelDistance = SQUARE_SIZE * (0.46f + arc * 0.08f);
            float travel = SQUARE_SIZE * 0.18f + travelDistance * arcProgress;
            float fragmentCx = cx + (float) Math.cos(direction) * travel;
            float fragmentCy = cy + (float) Math.sin(direction) * travel;
            float width = SQUARE_SIZE * (0.54f + (arc % 2) * 0.08f);
            float height = SQUARE_SIZE * (0.34f + (arc % 3) * 0.035f);
            drawEndermanCloudFragment(
                    g, fragmentCx, fragmentCy,
                    direction + Math.PI / 2.0,
                    width, height,
                    dissolveT,
                    arc);
        }

        for (int i = 0; i < 5; i++) {
            double direction = -0.45 + i * Math.PI * 2.0 / 5.0;
            float speed = 0.76f + i * 0.09f;
            float progress = easeOutCubic(Math.min(1f, t * speed / 0.70f));
            float distance = SQUARE_SIZE * (0.34f + i * 0.065f) * progress;
            float radius = SQUARE_SIZE * (0.055f + (i % 3) * 0.012f);
            float residualDissolve = Math.max(0f, Math.min(1f,
                    (dissolveT - i * 0.07f) / 0.72f));
            float residualAlpha = 1f - smoothStep(residualDissolve);
            drawEndermanResidualBlob(
                    g,
                    cx + (float) Math.cos(direction) * distance,
                    cy + (float) Math.sin(direction) * distance,
                    radius,
                    residualAlpha);
        }
    }

    private void drawEndermanSolidCloudShell(
            Graphics2D source, float cx, float cy, float diameter, float alpha) {
        if (diameter <= 0f || alpha <= 0f) return;
        java.awt.geom.Area shell = createEndermanCloudShellArea(cx, cy, diameter);
        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));

        java.awt.geom.Area underside = new java.awt.geom.Area(shell);
        underside.transform(java.awt.geom.AffineTransform.getTranslateInstance(
                0, diameter * 0.075f));
        g.setColor(new Color(79, 24, 139, 225));
        g.fill(underside);

        g.setColor(new Color(151, 66, 224, 245));
        g.fill(shell);

        Shape oldClip = g.getClip();
        g.clip(shell);
        g.setPaint(new LinearGradientPaint(
                cx,
                cy - diameter * 0.48f,
                cx,
                cy + diameter * 0.32f,
                new float[]{0f, 0.48f, 1f},
                new Color[]{
                    new Color(225, 186, 255, 205),
                    new Color(175, 94, 239, 95),
                    new Color(82, 25, 145, 125)
                }));
        g.fillRect(
                Math.round(cx - diameter),
                Math.round(cy - diameter),
                Math.round(diameter * 2f),
                Math.round(diameter * 2f));
        g.setClip(oldClip);
        g.dispose();
    }

    private java.awt.geom.Area createEndermanCloudShellArea(
            float cx, float cy, float diameter) {
        float radius = diameter / 2f;
        java.awt.geom.Area shell = new java.awt.geom.Area(
                new java.awt.geom.Ellipse2D.Float(
                        cx - radius * 0.78f,
                        cy - radius * 0.72f,
                        radius * 1.56f,
                        radius * 1.44f));
        int lobes = 11;
        for (int i = 0; i < lobes; i++) {
            double angle = -Math.PI / 2.0 + i * Math.PI * 2.0 / lobes;
            float distance = radius * (0.68f + (i % 3) * 0.035f);
            float lobeRadius = radius * (0.29f + (i % 4) * 0.025f);
            float x = cx + (float) Math.cos(angle) * distance;
            float y = cy + (float) Math.sin(angle) * distance;
            shell.add(new java.awt.geom.Area(
                    new java.awt.geom.Ellipse2D.Float(
                            x - lobeRadius,
                            y - lobeRadius,
                            lobeRadius * 2f,
                            lobeRadius * 2f)));
        }
        return shell;
    }

    private void drawEndermanCloudFragment(
            Graphics2D source, float cx, float cy, double rotation,
            float width, float height, float dissolveT, int variant) {
        if (dissolveT >= 1f) return;
        java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
        path.moveTo(-width * 0.52f, height * 0.02f);
        path.curveTo(
                -width * 0.58f, -height * 0.27f,
                -width * 0.34f, -height * 0.52f,
                -width * 0.08f, -height * 0.40f);
        path.curveTo(
                width * 0.16f, -height * 0.58f,
                width * 0.51f, -height * 0.31f,
                width * 0.52f, -height * 0.02f);
        path.curveTo(
                width * 0.38f, height * 0.03f,
                width * 0.25f, height * 0.26f,
                width * 0.03f, height * 0.22f);
        path.curveTo(
                -width * 0.18f, height * 0.29f,
                -width * 0.30f, height * 0.03f,
                -width * 0.52f, height * 0.02f);
        path.closePath();

        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(cx, cy);
        g.rotate(rotation + (variant % 2 == 0 ? -0.08 : 0.08));

        int rows = 3;
        int columns = 4;
        float chunkWidth = width * 1.16f / columns;
        float chunkHeight = height * 1.08f / rows;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                float inwardOrder = (rows - 1 - row) / (float) (rows - 1);
                float jitter = ((column * 3 + row * 5 + variant * 7) % 5) * 0.025f;
                float chunkStart = inwardOrder * 0.48f + jitter;
                float chunkAlpha = 1f - smoothStep(
                        Math.max(0f, Math.min(1f, (dissolveT - chunkStart) / 0.34f)));
                if (chunkAlpha <= 0f) continue;

                float x = -width * 0.58f + column * chunkWidth;
                float y = -height * 0.54f + row * chunkHeight;
                float overlap = Math.max(1.5f, Math.min(width, height) * 0.025f);
                Graphics2D chunk = (Graphics2D) g.create();
                chunk.clip(new RoundRectangle2D.Float(
                        x - overlap,
                        y - overlap,
                        chunkWidth + overlap * 2f,
                        chunkHeight + overlap * 2f,
                        chunkHeight * 0.55f,
                        chunkHeight * 0.55f));
                chunk.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, chunkAlpha));
                drawEndermanCloudFragmentShape(chunk, path, width, height);
                chunk.dispose();
            }
        }
        g.dispose();
    }

    private void drawEndermanCloudFragmentShape(
            Graphics2D g, Shape path, float width, float height) {
        java.awt.geom.AffineTransform oldTransform = g.getTransform();
        g.translate(0, height * 0.09f);
        g.setColor(new Color(72, 20, 132, 225));
        g.fill(path);
        g.setTransform(oldTransform);

        g.setColor(new Color(145, 57, 218, 245));
        g.fill(path);
        Shape oldClip = g.getClip();
        g.clip(path);
        g.setPaint(new LinearGradientPaint(
                0f, -height * 0.52f,
                0f, height * 0.28f,
                new float[]{0f, 0.55f, 1f},
                new Color[]{
                    new Color(226, 190, 255, 205),
                    new Color(172, 88, 235, 85),
                    new Color(76, 22, 137, 115)
                }));
        g.fillRect(
                Math.round(-width),
                Math.round(-height),
                Math.round(width * 2f),
                Math.round(height * 2f));
        g.setClip(oldClip);
    }

    private void drawEndermanResidualBlob(
            Graphics2D source, float cx, float cy, float radius, float alpha) {
        if (alpha <= 0f || radius <= 0f) return;
        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
        g.setColor(new Color(72, 20, 132, 215));
        g.fillOval(
                Math.round(cx - radius),
                Math.round(cy - radius * 0.72f),
                Math.round(radius * 2f),
                Math.round(radius * 2f));
        g.setColor(new Color(178, 91, 240, 240));
        g.fillOval(
                Math.round(cx - radius),
                Math.round(cy - radius),
                Math.round(radius * 2f),
                Math.round(radius * 2f));
        g.dispose();
    }

    private void drawEndermanPurplePopLines(
            Graphics2D g, float cx, float cy, float burstT, int burstIndex) {
        int lineCount = burstIndex == 0 ? 9 : 7;
        long seed = 0x4F1BBCDCL + burstIndex * 0x9E3779B9L;
        Graphics2D lines = (Graphics2D) g.create();
        lines.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int layer = 0; layer < 2; layer++) {
            for (int i = 0; i < lineCount; i++) {
                long value = seed + i * 0x6A09E667L + layer * 0x3C6EF372L;
                float randomDelay = Math.floorMod(value >>> 8, 19L) / 100f;
                float startDelay = randomDelay + layer * 0.12f;
                float localT = (burstT - startDelay) / Math.max(0.01f, 1f - startDelay);
                if (localT < 0f || localT > 1f) continue;

                localT = Math.max(0f, Math.min(1f, localT));
                float alpha = 1f - smoothStep(Math.max(0f, (localT - 0.88f) / 0.12f));
                double angle = i * Math.PI * 2.0 / lineCount
                        + Math.floorMod(value >>> 21, 36L) / 100.0;
                float contraction = smoothStep(localT);
                float fullLength = SQUARE_SIZE
                        * (0.30f + (i % 4) * 0.045f)
                        * (layer == 0 ? 1f : 0.88f);
                float outerStartRadius = SQUARE_SIZE
                        * (0.54f + (i % 3) * 0.045f + layer * 0.16f);
                float movingStartRadius = outerStartRadius * (1f - contraction);
                float lineLength = fullLength * (1f - contraction * 0.80f);
                float lineWidth = SQUARE_SIZE
                        * (0.060f + (i % 2) * 0.011f)
                        * (layer == 0 ? 1f : 0.86f);

                lines.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
                lines.setColor(layer == 0
                        ? (i % 3 == 0
                                ? new Color(234, 112, 255)
                                : new Color(184, 44, 224))
                        : (i % 3 == 0
                                ? new Color(216, 133, 255)
                                : new Color(151, 61, 216)));

                Graphics2D ray = (Graphics2D) lines.create();
                ray.translate(cx, cy);
                ray.rotate(angle);
                int stickX = Math.round(movingStartRadius);
                int stickY = Math.round(-lineWidth / 2f);
                int stickWidth = Math.max(1, Math.round(lineLength));
                int stickHeight = Math.max(2, Math.round(lineWidth));
                int stickArc = Math.round(lineWidth * 0.35f);
                Shape stick = new RoundRectangle2D.Float(
                        stickX, stickY, stickWidth, stickHeight, stickArc, stickArc);
                ray.fill(stick);

                Shape oldClip = ray.getClip();
                ray.clip(stick);
                ray.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, Math.min(0.72f, alpha * 0.72f)));
                ray.setColor(layer == 0
                        ? new Color(228, 176, 255)
                        : new Color(195, 135, 245));
                float puffRadius = Math.max(2f, lineWidth * 0.54f);
                for (int puff = 0; puff < 5; puff++) {
                    float puffX = movingStartRadius
                            + lineLength * (0.12f + puff * 0.19f);
                    float puffY = ((puff + i + layer) % 3 - 1) * lineWidth * 0.16f;
                    ray.fillOval(
                            Math.round(puffX - puffRadius),
                            Math.round(puffY - puffRadius),
                            Math.round(puffRadius * 2f),
                            Math.round(puffRadius * 2f));
                }
                ray.setClip(oldClip);
                ray.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, Math.min(0.55f, alpha * 0.55f)));
                ray.setColor(new Color(91, 27, 151));
                ray.drawRoundRect(
                        Math.round(movingStartRadius),
                        Math.round(-lineWidth / 2f),
                        Math.max(1, Math.round(lineLength)),
                        Math.max(2, Math.round(lineWidth)),
                        Math.round(lineWidth * 0.35f),
                        Math.round(lineWidth * 0.35f));
                ray.dispose();
            }
        }
        lines.dispose();
    }

    private void drawEndermanCloudReturn(
            Graphics2D g, BufferedImage sprite, float cx, float cy, float t) {
        float p = smoothStep(t);
        drawEndermanSprite(g, sprite, cx, cy, 0.97f + p * 0.03f, 0.30f + p * 0.70f);
        if (t < 0.78f) {
            float cloudT = t / 0.78f;
            drawEndermanBillowyCloud(
                    g, cx, cy + SQUARE_SIZE * 0.08f,
                    SQUARE_SIZE * (0.72f - cloudT * 0.38f),
                    SQUARE_SIZE * (0.53f - cloudT * 0.24f),
                    (1f - smoothStep(cloudT)) * 0.58f,
                    3,
                    0.86f);
        }
    }

    private void drawEndermanBillowyCloud(
            Graphics2D g, float cx, float cy, float width, float height,
            float alpha, int burstIndex, float saturation) {
        if (alpha <= 0f || width <= 0f || height <= 0f) return;
        int lobeCount = burstIndex == 2 ? 24 : 13;
        long seed = 0x6A09E667L + burstIndex * 0x9E3779B9L;

        drawEndermanCloudLobe(
                g, cx, cy, width * 0.47f, height * 0.47f,
                alpha * 0.84f, saturation, true);

        for (int i = 0; i < lobeCount; i++) {
            double angle = i * Math.PI * 2.0 / lobeCount
                    + ((seed >>> (i % 24)) & 7L) * 0.035;
            float edgeBias = 0.54f + (i % 4) * 0.075f;
            float px = cx + (float) Math.cos(angle) * width * edgeBias * 0.50f;
            float py = cy + (float) Math.sin(angle) * height * edgeBias * 0.50f;
            float radiusX = width * (0.15f + (i % 5) * 0.014f);
            float radiusY = height * (0.18f + (i % 4) * 0.018f);
            float lobeAlpha = alpha * (0.54f + (i % 3) * 0.10f);
            drawEndermanCloudLobe(
                    g, px, py, radiusX, radiusY,
                    lobeAlpha, saturation, false);
        }

        int innerCount = burstIndex == 2 ? 11 : 6;
        for (int i = 0; i < innerCount; i++) {
            double angle = i * 2.399963229728653;
            float distance = (i + 1f) / innerCount;
            float px = cx + (float) Math.cos(angle) * width * distance * 0.23f;
            float py = cy + (float) Math.sin(angle) * height * distance * 0.20f;
            drawEndermanCloudLobe(
                    g, px, py,
                    width * (0.18f + (i % 3) * 0.018f),
                    height * (0.20f + (i % 2) * 0.025f),
                    alpha * 0.58f, saturation * 1.08f, true);
        }
    }

    private void drawEndermanCloudLobe(
            Graphics2D g, float cx, float cy, float radiusX, float radiusY,
            float alpha, float saturation, boolean hotCore) {
        int outerAlpha = Math.max(0, Math.min(255, Math.round(alpha * 118f)));
        int coreAlpha = Math.max(0, Math.min(255,
                Math.round(alpha * (hotCore ? 205f : 158f))));
        int magenta = Math.min(255, Math.round(174f * saturation));
        int blue = Math.min(255, Math.round(207f / Math.max(0.8f, saturation)));
        float gradientRadius = Math.max(1f, Math.max(radiusX, radiusY));

        Graphics2D lobe = (Graphics2D) g.create();
        lobe.translate(cx, cy);
        lobe.scale(radiusX / gradientRadius, radiusY / gradientRadius);
        lobe.setPaint(new RadialGradientPaint(
                new Point.Float(-gradientRadius * 0.12f, -gradientRadius * 0.13f),
                gradientRadius,
                new float[]{0f, 0.26f, 0.58f, 0.82f, 1f},
                new Color[]{
                    new Color(255, 102, magenta, coreAlpha),
                    new Color(244, 111, blue, Math.round(coreAlpha * 0.92f)),
                    new Color(247, 174, 221, outerAlpha),
                    new Color(255, 211, 235, Math.round(outerAlpha * 0.46f)),
                    new Color(255, 226, 241, 0)
                }));
        lobe.fillOval(
                Math.round(-gradientRadius),
                Math.round(-gradientRadius),
                Math.round(gradientRadius * 2f),
                Math.round(gradientRadius * 2f));
        lobe.dispose();
    }

    private void drawEndermanSprite(
            Graphics2D g, BufferedImage sprite, float cx, float cy, float scale, float alpha) {
        if (sprite == null || alpha <= 0f) return;
        float size = SQUARE_SIZE * scale;
        g.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
        g.drawImage(sprite,
                Math.round(cx - size / 2f), Math.round(cy - size / 2f),
                Math.round(size), Math.round(size), null);
        g.setComposite(AlphaComposite.SrcOver);
    }

    private float smoothStep(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private boolean isPieceHandledByEndermanPop(Piece piece, int row, int col, long nowMs) {
        if (piece == null || piece != endermanTeleportFxPiece || endermanPickupFxStartedAtMs < 0L) {
            return false;
        }
        if (!endermanTeleportFxMoved) {
            return row == endermanPickupFxRow && col == endermanPickupFxCol;
        }
        return row == endermanPickupFxDestRow && col == endermanPickupFxDestCol;
    }

    private boolean deferSpellVisualEffect(Runnable effect) {
        if (!deferSpellVisualEffects || effect == null) return false;
        deferredSpellVisualEffects.add(effect);
        return true;
    }

    public void applyFogSpell(boolean casterWhite) {
        if (isSpellSimulationActive()) {
            getPlayerState(!casterWhite).setFogTurnsRemaining(4);
            return;
        }
        if (deferSpellVisualEffect(() -> applyFogSpell(casterWhite))) return;
        getPlayerState(!casterWhite).setFogTurnsRemaining(4);
        fogVisualAlpha = 1.0f;
        fogTargetAlpha = 1.0f;
        fogRippleActive = true;
        fogRippleStartedAtMs = System.currentTimeMillis();
        fogRippleViewerIsWhite = !casterWhite;
        startFogAnimation();
        repaint();
    }

    private float getFogRippleRadius(long nowMs) {
        long rippleElapsedMs = nowMs - fogRippleStartedAtMs - FOG_RIPPLE_CHARGE_MS;
        float progress = Math.max(0f, Math.min(1f,
                rippleElapsedMs / (float) FOG_RIPPLE_DURATION_MS));
        float eased = easeOutCubic(progress);
        float maxRadius = (float) Math.hypot(BOARD_SIZE / 2.0, BOARD_SIZE / 2.0) + SQUARE_SIZE;
        return maxRadius * eased;
    }

    private boolean hasFogRippleReachedSquare(int displayRow, int displayCol, long nowMs) {
        if (!fogRippleActive) return true;
        float squareCenterX = displayCol * SQUARE_SIZE + SQUARE_SIZE / 2f;
        float squareCenterY = displayRow * SQUARE_SIZE + SQUARE_SIZE / 2f;
        float dx = squareCenterX - BOARD_SIZE / 2f;
        float dy = squareCenterY - BOARD_SIZE / 2f;
        return Math.hypot(dx, dy) <= getFogRippleRadius(nowMs);
    }

    private void drawFogRipple(Graphics2D source, long nowMs) {
        if (!fogRippleActive
                || nowMs - fogRippleStartedAtMs < FOG_RIPPLE_CHARGE_MS) {
            return;
        }

        float radius = getFogRippleRadius(nowMs);
        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float[] offsets = {-12f, -6f, 0f, 6f, 12f};
        float[] widths = {18f, 13f, 8f, 13f, 18f};
        int[] alphas = {7, 13, 28, 13, 7};
        for (int i = 0; i < offsets.length; i++) {
            float layerRadius = Math.max(1f, radius + offsets[i]);
            g.setStroke(new BasicStroke(widths[i], BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 255, 255, alphas[i]));
            g.drawOval(
                    Math.round(BOARD_SIZE / 2f - layerRadius),
                    Math.round(BOARD_SIZE / 2f - layerRadius),
                    Math.round(layerRadius * 2f),
                    Math.round(layerRadius * 2f));
        }

        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 72));
        g.drawOval(
                Math.round(BOARD_SIZE / 2f - radius),
                Math.round(BOARD_SIZE / 2f - radius),
                Math.round(radius * 2f),
                Math.round(radius * 2f));
        g.dispose();
    }

    private void drawFogDarknessEnvelope(Graphics2D source, long nowMs) {
        if (!fogRippleActive) return;
        long elapsedMs = nowMs - fogRippleStartedAtMs;
        float darkness;
        if (elapsedMs < FOG_RIPPLE_CHARGE_MS) {
            float chargeProgress = Math.max(0f, Math.min(1f,
                    elapsedMs / (float) FOG_RIPPLE_CHARGE_MS));
            darkness = chargeProgress * chargeProgress * (3f - 2f * chargeProgress);
        } else {
            float rippleProgress = Math.max(0f, Math.min(1f,
                    (elapsedMs - FOG_RIPPLE_CHARGE_MS) / (float) FOG_RIPPLE_DURATION_MS));
            float easedRipple = rippleProgress * rippleProgress * (3f - 2f * rippleProgress);
            darkness = 1f - easedRipple;
        }
        Graphics2D g = (Graphics2D) source.create();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, darkness * 0.35f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, BOARD_SIZE, BOARD_SIZE);
        g.dispose();
    }

    public String castSpellInteractiveForSide(String spellId, boolean sideWhite) {
        if (!isSpellChessMode()) return "Spell Chess mode is not active.";
        if (animating) return "Finish the current move animation first.";
        if (sideWhite != whiteTurn) return "It is not this side's turn.";
        if (pendingSpellTargetingId != null) {
            if (pendingSpellTargetingSideWhite == sideWhite && pendingSpellTargetingId.equals(spellId)) {
                clearPendingSpellTargeting();
                repaint();
                return "Cancelled.";
            }
            return "Finish current spell targeting on the board first.";
        }
        if (pendingEndermanPiecePick || endermanPhaseActive) {
            if (SpellManager.ENDERMAN.equals(spellId) && sideWhite == (pendingEndermanPiecePick ? pendingEndermanSideWhite : endermanPhaseSideWhite)) {
                startEndermanPhaseUnpoof();
                clearEndermanState(true);
                selectedPiece = null;
                draggedPiece = null;
                dragging = false;
                possibleMoves.clear();
                repaint();
                return "Cancelled.";
            }
            return "Finish Enderman selection/move first.";
        }
        if (pendingBomberRookPick || bomberPrimedRookRow != -1) {
            if (SpellManager.BOMBER.equals(spellId) && sideWhite == (pendingBomberRookPick ? pendingBomberSideWhite : bomberPrimedSideWhite)) {
                clearBomberState(true);
                selectedPiece = null;
                draggedPiece = null;
                dragging = false;
                possibleMoves.clear();
                repaint();
                return "Cancelled.";
            }
            return "Finish Bomber selection/move first.";
        }
        if (urielChooserActive || pendingUrielPlacement) {
            boolean urielSide = urielChooserActive ? urielChooserSideWhite : pendingUrielSideWhite;
            if (SpellManager.URIEL.equals(spellId) && sideWhite == urielSide) {
                clearUrielChooser();
                clearPendingUrielPlacement();
                repaint();
                return "Cancelled.";
            }
            return "Finish Uriel selection and placement first.";
        }

        if (SpellManager.URIEL.equals(spellId)) {
            List<SpellTarget> legalTargets = spellManager.getLegalTargets(this, spellId, sideWhite);
            if (legalTargets.isEmpty()) return "No legal targets for this spell.";

            java.util.Set<String> legalTypes = new java.util.HashSet<>();
            for (SpellTarget t : legalTargets) {
                if (t.resurrectPieceType != null) legalTypes.add(t.resurrectPieceType.toLowerCase());
            }

            List<CapturedPieceRecord> captured = getPlayerState(sideWhite).getCapturedPieces();
            java.util.Map<String, Integer> totals = new java.util.HashMap<>();
            for (CapturedPieceRecord rec : captured) {
                String type = rec.getPieceType();
                if (type == null) continue;
                String key = type.toLowerCase();
                if ("king".equals(key) || !legalTypes.contains(key)) continue;
                totals.put(key, totals.getOrDefault(key, 0) + 1);
            }
            if (totals.isEmpty()) return "No captured pieces available for Uriel.";

            List<String> labels = new ArrayList<>();
            List<String> types = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            String[] displayOrder = {"Pawn", "Knight", "Bishop", "Rook", "Queen", "Zoglin"};
            for (String type : displayOrder) {
                Integer count = totals.get(type.toLowerCase());
                if (count == null || count <= 0) continue;
                labels.add(type);
                types.add(type);
                counts.add(count);
            }

            beginUrielChooser(sideWhite, labels, types, counts);
            return SPELL_TARGETING_PENDING;
        }

        if (SpellManager.FIREBALL.equals(spellId)
                || SpellManager.FREEZE.equals(spellId)
                || SpellManager.SHIELD.equals(spellId)
                || SpellManager.ZOGLIN.equals(spellId)) {
            List<SpellTarget> legalTargets = spellManager.getLegalTargets(this, spellId, sideWhite);
            if (legalTargets.isEmpty()) return "No legal targets for this spell.";
            beginSpellTargeting(spellId, sideWhite, legalTargets);
            return SPELL_TARGETING_PENDING;
        }
        if (SpellManager.BOMBER.equals(spellId)) {
            List<SpellTarget> legalTargets = spellManager.getLegalTargets(this, spellId, sideWhite);
            if (legalTargets.isEmpty()) return "No legal targets for this spell.";
            pendingBomberRookPick = true;
            pendingBomberSideWhite = sideWhite;
            selectedPiece = null;
            draggedPiece = null;
            dragging = false;
            possibleMoves.clear();
            repaint();
            return SPELL_TARGETING_PENDING;
        }
        if (SpellManager.ENDERMAN.equals(spellId)) {
            if (getPlayerState(sideWhite).getElixir() < ENDERMAN_COST) return "Not enough elixir.";
            beginEndermanPiecePick(sideWhite);
            repaint();
            return SPELL_TARGETING_PENDING;
        }

        List<SpellTarget> legalTargets = spellManager.getLegalTargets(this, spellId, sideWhite);
        if (legalTargets.isEmpty()) return "No legal targets for this spell.";

        SpellTarget chosen;
        if (legalTargets.size() == 1) {
            chosen = legalTargets.get(0);
        } else {
            String[] options = new String[legalTargets.size()];
            for (int i = 0; i < legalTargets.size(); i++) {
                options[i] = describeTarget(spellId, legalTargets.get(i));
            }
            String pick = (String) JOptionPane.showInputDialog(
                    this,
                    "Choose target:",
                    "Cast " + spellId,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            if (pick == null) return "Cancelled.";
            int idx = -1;
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(pick)) { idx = i; break; }
            }
            if (idx < 0) return "Invalid selection.";
            chosen = legalTargets.get(idx);
        }

        beginDeferredSpellVisualEffects();
        String result = spellManager.castSpell(this, spellId, sideWhite, chosen);
        if (result == null) {
            if (gameController != null) {
                gameController.onLocalSpellCast(spellId, sideWhite, cloneSpellTarget(chosen));
            }
        } else {
            cancelDeferredSpellVisualEffects();
        }
        return result;
    }

    private SpellTarget cloneSpellTarget(SpellTarget src) {
        SpellTarget out = new SpellTarget();
        if (src == null) return out;
        out.sourceRow = src.sourceRow;
        out.sourceCol = src.sourceCol;
        out.targetRow = src.targetRow;
        out.targetCol = src.targetCol;
        out.destRow = src.destRow;
        out.destCol = src.destCol;
        out.resurrectPieceType = src.resurrectPieceType;
        return out;
    }

    private void beginSpellTargeting(String spellId, boolean sideWhite, List<SpellTarget> legalTargets) {
        pendingSpellTargetingId = spellId;
        pendingSpellTargetingSideWhite = sideWhite;
        pendingSpellLegalTargets.clear();
        pendingSpellHighlightSquares.clear();
        clearTabSelectedSpellTarget();
        possibleMoves.clear();
        if (legalTargets != null) pendingSpellLegalTargets.addAll(legalTargets);

        for (SpellTarget t : pendingSpellLegalTargets) {
            if (t == null) continue;
            int row = -1, col = -1;
            if (t.targetRow != null && t.targetCol != null) {
                row = t.targetRow;
                col = t.targetCol;
            } else if (t.destRow != null && t.destCol != null) {
                row = t.destRow;
                col = t.destCol;
            }
            if (!inBounds(row, col)) continue;

            if (SpellManager.ZOGLIN.equals(spellId)) {
                possibleMoves.add(new int[]{-1, -1, row, col});
            } else {
                pendingSpellHighlightSquares.add(new int[]{row, col});
            }
        }

        selectedPiece = null;
        dragging = false;
        draggedPiece = null;
        tabFocusedPieceRow = -1;
        tabFocusedPieceCol = -1;
        tabFocusedLegalMoveIndex = -1;
        tabPieceSelectionConfirmed = false;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusOwner();
        requestFocus();
        requestFocusInWindow();
        grabFocus();
        SwingUtilities.invokeLater(this::requestFocusInWindow);
        repaint();
    }

    private void clearPendingSpellTargeting() {
        pendingSpellTargetingId = null;
        pendingSpellLegalTargets.clear();
        pendingSpellHighlightSquares.clear();
        clearTabSelectedSpellTarget();
        possibleMoves.clear();
    }

    private void clearBomberState(boolean notifyUi) {
        boolean side = pendingBomberRookPick ? pendingBomberSideWhite : bomberPrimedSideWhite;
        pendingBomberRookPick = false;
        pendingBomberSideWhite = false;
        bomberPrimedRookRow = -1;
        bomberPrimedRookCol = -1;
        bomberPrimedSideWhite = false;
        bomberConsumePending = false;
        bomberConsumeSideWhite = false;
        if (notifyUi && gameController != null) {
            gameController.cancelPendingSpellSelection(side, SpellManager.BOMBER);
        }
    }

    public boolean cancelActiveSpellInteraction() {
        if (!isSpellChessMode()) return false;
        boolean hadAny = false;
        if (urielChooserActive) {
            hadAny = true;
            clearUrielChooser();
        }
        if (pendingSpellTargetingId != null) {
            hadAny = true;
            clearPendingSpellTargeting();
        }
        if (pendingUrielPlacement) {
            hadAny = true;
            clearPendingUrielPlacement();
        }
        if (pendingBomberRookPick || bomberPrimedRookRow != -1) {
            hadAny = true;
            clearBomberState(false);
        }
        if (pendingEndermanPiecePick || endermanPhaseActive) {
            hadAny = true;
            startEndermanPhaseUnpoof();
            clearEndermanState(false);
        }
        if (hadAny) {
            selectedPiece = null;
            draggedPiece = null;
            dragging = false;
            possibleMoves.clear();
            repaint();
        }
        return hadAny;
    }

    public boolean cancelKeyboardTabSelection() {
        boolean changed = false;
        if (tabFocusedPieceRow != -1 || tabFocusedPieceCol != -1) {
            tabFocusedPieceRow = -1;
            tabFocusedPieceCol = -1;
            changed = true;
        }
        if (tabFocusedLegalMoveIndex != -1) {
            tabFocusedLegalMoveIndex = -1;
            changed = true;
        }
        if (tabSelectedSpellTargetRow != -1 || tabSelectedSpellTargetCol != -1) {
            clearTabSelectedSpellTarget();
            changed = true;
        }
        if (selectedPiece != null) {
            selectedPiece = null;
            draggedPiece = null;
            dragging = false;
            possibleMoves.clear();
            changed = true;
        }
        tabPieceSelectionConfirmed = false;
        if (changed) repaint();
        return changed;
    }

    private boolean isBomberPrimedForSquare(int row, int col, boolean sideWhite) {
        return bomberPrimedRookRow == row && bomberPrimedRookCol == col && bomberPrimedSideWhite == sideWhite;
    }

    private void beginEndermanPiecePick(boolean sideWhite) {
        pendingEndermanPiecePick = true;
        pendingEndermanSideWhite = sideWhite;
        pendingSpellHighlightSquares.clear();
        possibleMoves.clear();
        endermanChangedSources.clear();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] != null) pendingSpellHighlightSquares.add(new int[]{r, c});
            }
        }
        selectedPiece = null;
        draggedPiece = null;
        dragging = false;
    }

    private void activateEndermanPhase(boolean sideWhite, int row, int col) {
        Piece phasedPiece = inBounds(row, col) ? board[row][col] : null;
        endermanPhaseActive = true;
        endermanPhaseSideWhite = sideWhite;
        endermanPhaseRow = row;
        endermanPhaseCol = col;
        pendingEndermanPiecePick = false;
        pendingEndermanSideWhite = false;
        startEndermanPhasePoof(phasedPiece, row, col, false);

        endermanChangedSources.clear();
        pendingSpellHighlightSquares.clear();
        possibleMoves.clear();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p == null || p.isWhite() != sideWhite) continue;
                if (r == endermanPhaseRow && c == endermanPhaseCol) continue;
                boolean attackedGhostPiece = p.getFrozenTurnsRemaining() <= 0
                        && p.getResurrectCooldownTurnsRemaining() <= 0
                        && canPieceReachEndermanGhostSquare(
                                p, r, c, endermanPhaseRow, endermanPhaseCol);
                if (attackedGhostPiece) {
                    endermanChangedSources.add(new int[]{r, c});
                    pendingSpellHighlightSquares.add(new int[]{r, c});
                }
            }
        }
        selectedPiece = null;
        draggedPiece = null;
        dragging = false;
    }

    private boolean canPieceReachEndermanGhostSquare(
            Piece piece, int fromRow, int fromCol, int ghostRow, int ghostCol) {
        if (piece.isValidMove(fromRow, fromCol, ghostRow, ghostCol, board)) {
            return true;
        }

        Piece[][] ghostlessBoard = new Piece[8][8];
        for (int r = 0; r < 8; r++) {
            System.arraycopy(board[r], 0, ghostlessBoard[r], 0, 8);
        }
        ghostlessBoard[ghostRow][ghostCol] = null;
        return piece.isValidMove(fromRow, fromCol, ghostRow, ghostCol, ghostlessBoard);
    }

    private boolean isEndermanChangedSourceSquare(int row, int col) {
        for (int[] s : endermanChangedSources) {
            if (s[0] == row && s[1] == col) return true;
        }
        return false;
    }

    private void clearEndermanState(boolean notifyUi) {
        boolean side = pendingEndermanPiecePick ? pendingEndermanSideWhite : endermanPhaseSideWhite;
        startEndermanOverlayFadeOut();
        pendingEndermanPiecePick = false;
        pendingEndermanSideWhite = false;
        endermanPhaseActive = false;
        endermanPhaseSideWhite = false;
        endermanPhaseRow = -1;
        endermanPhaseCol = -1;
        endermanConsumePending = false;
        endermanConsumeSideWhite = false;
        endermanChangedSources.clear();
        pendingSpellHighlightSquares.clear();
        if (notifyUi && gameController != null) {
            gameController.cancelPendingSpellSelection(side, SpellManager.ENDERMAN);
        }
    }

    private boolean tryResolvePendingSpellTargeting(int row, int col) {
        if (pendingSpellTargetingId == null) return false;
        if (!inBounds(row, col)) return true;
        if (whiteTurn != pendingSpellTargetingSideWhite) {
            clearPendingSpellTargeting();
            return true;
        }

        SpellTarget chosen = null;
        for (SpellTarget t : pendingSpellLegalTargets) {
            if (t == null) continue;
            Integer tr = t.targetRow;
            Integer tc = t.targetCol;
            Integer dr = t.destRow;
            Integer dc = t.destCol;
            if (tr != null && tc != null && tr == row && tc == col) {
                chosen = t;
                break;
            }
            if (dr != null && dc != null && dr == row && dc == col) {
                chosen = t;
                break;
            }
        }
        if (chosen == null) return true;

        String spellId = pendingSpellTargetingId;
        boolean sideWhite = pendingSpellTargetingSideWhite;
        beginDeferredSpellVisualEffects();
        String result = spellManager.castSpell(this, spellId, sideWhite, chosen);
        if (result == null) {
            if (gameController != null) {
                gameController.onLocalSpellCast(spellId, sideWhite, cloneSpellTarget(chosen));
            }
            clearPendingSpellTargeting();
            if (gameController != null) {
                gameController.onBoardSpellCastResolved(spellId, sideWhite);
            }
        } else {
            cancelDeferredSpellVisualEffects();
            if (!"Cancelled.".equals(result)) {
                GameDialogs.info(this, "Spell", String.valueOf(result));
            }
        }
        repaint();
        return true;
    }

    private Color getSpellCastColor(String spellId) {
        if (SpellManager.FIREBALL.equals(spellId)) return new Color(255, 105, 35);
        if (SpellManager.FREEZE.equals(spellId)) return new Color(82, 210, 255);
        if (SpellManager.SHIELD.equals(spellId)) return new Color(67, 224, 126);
        if (SpellManager.ENDERMAN.equals(spellId)) return new Color(174, 102, 255);
        if (SpellManager.URIEL.equals(spellId)) return new Color(255, 218, 92);
        if (SpellManager.FOG.equals(spellId)) return new Color(89, 207, 192);
        if (SpellManager.BOMBER.equals(spellId)) return new Color(119, 231, 82);
        if (SpellManager.ZOGLIN.equals(spellId)) return new Color(235, 72, 82);
        return new Color(122, 176, 255);
    }

    private void drawSpellCastCardFx(Graphics2D source) {
        SpellCastCardFx fx = spellCastCardFx;
        if (fx == null) return;
        long elapsed = System.currentTimeMillis() - fx.startMs;
        if (elapsed < 0 || elapsed >= SPELL_CAST_CARD_TOTAL_MS) return;

        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        Color spellColor = getSpellCastColor(fx.spellId);
        int centerX = BOARD_SIZE / 2;
        int centerY = BOARD_SIZE / 2;
        int displayRow = boardFlipped ? 7 - fx.targetRow : fx.targetRow;
        int displayCol = boardFlipped ? 7 - fx.targetCol : fx.targetCol;
        int targetX = displayCol * SQUARE_SIZE + SQUARE_SIZE / 2;
        int targetY = displayRow * SQUARE_SIZE + SQUARE_SIZE / 2;

        float veilAlpha = elapsed < SPELL_CAST_CARD_FLIP_MS
                ? Math.min(0.32f, elapsed / 250f * 0.32f)
                : Math.max(0f, 0.32f * (1f - (elapsed - SPELL_CAST_CARD_FLIP_MS) / 500f));
        if (veilAlpha > 0f) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, veilAlpha));
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, BOARD_SIZE, BOARD_SIZE);
        }

        if (elapsed < SPELL_CAST_CARD_FLIP_MS) {
            float t = Math.min(1f, elapsed / (float) SPELL_CAST_CARD_FLIP_MS);
            float arrive = easeOutCubic(Math.min(1f, t / 0.32f));
            float flip = Math.max(0f, (t - 0.18f) / 0.72f);
            boolean faceUp = flip >= 0.5f;
            float widthScale = Math.max(0.045f, Math.abs((float) Math.cos(Math.PI * flip)));
            float cardScale = 0.76f + 0.24f * arrive;
            int cardW = Math.round(150 * cardScale * widthScale);
            int cardH = Math.round(218 * cardScale);
            int cardX = centerX - cardW / 2;
            int cardY = Math.round(-cardH + (centerY - cardH / 2f + cardH) * arrive);

            float glow = 0.55f + 0.25f * (float) Math.sin(elapsed / 55.0);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, glow)));
            g.setColor(new Color(spellColor.getRed(), spellColor.getGreen(), spellColor.getBlue(), 95));
            g.fillOval(centerX - 112, centerY - 148, 224, 296);

            g.setComposite(AlphaComposite.SrcOver);
            if (!faceUp) {
                g.setPaint(new GradientPaint(cardX, cardY, new Color(45, 29, 72),
                        cardX + cardW, cardY + cardH, new Color(13, 17, 38)));
                g.fillRoundRect(cardX, cardY, cardW, cardH, 22, 22);
                g.setColor(spellColor);
                g.setStroke(new BasicStroke(3f));
                g.drawRoundRect(cardX + 2, cardY + 2, Math.max(1, cardW - 5), cardH - 5, 20, 20);
                if (cardW > 20) {
                    g.drawOval(centerX - cardW / 4, cardY + cardH / 2 - cardW / 4, cardW / 2, cardW / 2);
                }
            } else {
                g.setPaint(new GradientPaint(cardX, cardY, spellColor.brighter(),
                        cardX, cardY + cardH, spellColor.darker().darker()));
                g.fillRoundRect(cardX, cardY, cardW, cardH, 22, 22);
                if (cardW > 12 && fx.art != null) {
                    Shape oldClip = g.getClip();
                    g.clip(new RoundRectangle2D.Float(cardX + 7, cardY + 8,
                            Math.max(1, cardW - 14), cardH - 49, 15, 15));
                    g.drawImage(fx.art, cardX + 7, cardY + 8, Math.max(1, cardW - 14), cardH - 49, null);
                    g.setClip(oldClip);
                }
                g.setColor(new Color(16, 12, 24, 225));
                g.fillRoundRect(cardX + 6, cardY + cardH - 39, Math.max(1, cardW - 12), 31, 10, 10);
                if (cardW > 42) {
                    g.setFont(new Font("Arial", Font.BOLD, 15));
                    FontMetrics fm = g.getFontMetrics();
                    String name = fx.spellName == null ? "SPELL" : fx.spellName.toUpperCase();
                    g.setColor(Color.WHITE);
                    g.drawString(name, centerX - fm.stringWidth(name) / 2, cardY + cardH - 17);
                }
                g.setColor(new Color(255, 245, 205));
                g.setStroke(new BasicStroke(3f));
                g.drawRoundRect(cardX + 2, cardY + 2, Math.max(1, cardW - 5), cardH - 5, 20, 20);
            }
        } else {
            float pulseT = (elapsed - SPELL_CAST_CARD_FLIP_MS) / (float) SPELL_CAST_CARD_PULSE_MS;
            pulseT = Math.max(0f, Math.min(1f, pulseT));
            float eased = easeOutCubic(pulseT);
            int orbX = Math.round(centerX + (targetX - centerX) * eased);
            int orbY = Math.round(centerY + (targetY - centerY) * eased);

            if (pulseT < 1f) {
                g.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(spellColor.getRed(), spellColor.getGreen(), spellColor.getBlue(), 48));
                g.drawLine(centerX, centerY, orbX, orbY);
                g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(255, 255, 255, 205));
                g.drawLine(centerX, centerY, orbX, orbY);
                int orbRadius = 10 + Math.round(4f * (float) Math.sin(pulseT * Math.PI * 6));
                g.setColor(new Color(spellColor.getRed(), spellColor.getGreen(), spellColor.getBlue(), 85));
                g.fillOval(orbX - orbRadius * 2, orbY - orbRadius * 2, orbRadius * 4, orbRadius * 4);
                g.setColor(Color.WHITE);
                g.fillOval(orbX - orbRadius / 2, orbY - orbRadius / 2, orbRadius, orbRadius);
            } else {
                float impactT = Math.min(1f,
                        (elapsed - SPELL_CAST_CARD_FLIP_MS - SPELL_CAST_CARD_PULSE_MS)
                                / (float) SPELL_CAST_CARD_IMPACT_MS);
                int radius = Math.round(SQUARE_SIZE * (0.18f + 0.75f * easeOutCubic(impactT)));
                int alpha = Math.max(0, Math.round(230 * (1f - impactT)));
                g.setStroke(new BasicStroke(5f * (1f - impactT) + 1f));
                g.setColor(new Color(spellColor.getRed(), spellColor.getGreen(), spellColor.getBlue(), alpha));
                g.drawOval(targetX - radius, targetY - radius, radius * 2, radius * 2);
                int flashRadius = Math.round(SQUARE_SIZE * (0.55f - 0.25f * impactT));
                g.setColor(new Color(255, 255, 255, Math.max(0, Math.round(150 * (1f - impactT)))));
                g.fillOval(targetX - flashRadius, targetY - flashRadius, flashRadius * 2, flashRadius * 2);
            }
        }
        g.dispose();
    }

    private void beginUrielChooser(
        boolean sideWhite,
        List<String> labels,
        List<String> types,
        List<Integer> counts
    ) {
        clearUrielChooser();
        urielChooserActive = true;
        urielChooserSideWhite = sideWhite;
        urielChooserLabels.addAll(labels);
        urielChooserTypes.addAll(types);
        urielChooserCounts.addAll(counts);
        urielChooserOpenedAtMs = System.currentTimeMillis();
        selectedPiece = null;
        draggedPiece = null;
        dragging = false;
        possibleMoves.clear();
        setCursor(Cursor.getDefaultCursor());

        urielChooserTimer = new Timer(AnimationTiming.FRAME_DELAY_MS, e -> {
            if (!urielChooserActive) {
                ((Timer) e.getSource()).stop();
                return;
            }
            if (urielChooserSelectedIndex >= 0
                    && System.currentTimeMillis() - urielChooserSelectedAtMs
                        >= URIEL_CHOOSER_TRANSITION_MS + URIEL_CHOOSER_HANDOFF_DELAY_MS) {
                int selected = urielChooserSelectedIndex;
                String type = selected < urielChooserTypes.size() ? urielChooserTypes.get(selected) : null;
                boolean side = urielChooserSideWhite;
                clearUrielChooser();
                if (type != null) beginUrielPlacement(side, type);
            }
            repaint();
        });
        urielChooserTimer.setCoalesce(true);
        urielChooserTimer.start();
        repaint();
    }

    private void clearUrielChooser() {
        urielChooserActive = false;
        urielChooserLabels.clear();
        urielChooserTypes.clear();
        urielChooserCounts.clear();
        urielChooserHoverIndex = -1;
        urielChooserSelectedIndex = -1;
        urielChooserOpenedAtMs = 0L;
        urielChooserSelectedAtMs = 0L;
        if (urielChooserTimer != null) {
            urielChooserTimer.stop();
            urielChooserTimer = null;
        }
    }

    private void selectUrielChooserPiece(int index) {
        if (!urielChooserActive || urielChooserSelectedIndex >= 0
                || index < 0 || index >= urielChooserTypes.size()) return;
        urielChooserSelectedIndex = index;
        urielChooserSelectedAtMs = System.currentTimeMillis();
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    private Rectangle getUrielChooserCardBounds(int index) {
        int count = urielChooserTypes.size();
        if (index < 0 || index >= count) return new Rectangle();
        int cardW = 76;
        int cardH = 112;
        int slotW = Math.min(92, (BOARD_SIZE - 24) / Math.max(1, count));
        int totalW = count * slotW;
        int startX = (BOARD_SIZE - totalW) / 2;
        float normalized = count <= 1 ? 0f : (index - (count - 1) / 2f) / ((count - 1) / 2f);
        int downwardCurve = Math.round(24f * (1f - normalized * normalized));
        int x = startX + index * slotW + Math.max(0, (slotW - cardW) / 2 - 4);
        int y = 210 + downwardCurve;
        return new Rectangle(x, y, cardW + 22, cardH + 28);
    }

    private int findUrielChooserCardAt(int x, int y) {
        for (int i = 0; i < urielChooserTypes.size(); i++) {
            Rectangle bounds = getUrielChooserCardBounds(i);
            if (bounds.contains(x, y)) return i;
        }
        return -1;
    }

    private void beginUrielPlacement(boolean sideWhite, String pieceType) {
        pendingUrielPlacement = true;
        pendingUrielSideWhite = sideWhite;
        pendingUrielPieceType = pieceType;
        urielHoverRow = -1;
        urielHoverCol = -1;
        selectedPiece = null;
        dragging = false;
        draggedPiece = null;
        refreshPendingUrielPlacementMoves();
        repaint();
    }

    private void clearPendingUrielPlacement() {
        pendingUrielPlacement = false;
        pendingUrielPieceType = null;
        urielHoverRow = -1;
        urielHoverCol = -1;
        possibleMoves.clear();
    }

    private void refreshPendingUrielPlacementMoves() {
        possibleMoves.clear();
        if (!pendingUrielPlacement || pendingUrielPieceType == null) return;
        List<SpellTarget> legalTargets = spellManager.getLegalTargets(this, SpellManager.URIEL, pendingUrielSideWhite);
        for (SpellTarget t : legalTargets) {
            if (t == null || t.destRow == null || t.destCol == null || t.resurrectPieceType == null) continue;
            if (!pendingUrielPieceType.equalsIgnoreCase(t.resurrectPieceType)) continue;
            possibleMoves.add(new int[]{-1, -1, t.destRow, t.destCol});
        }
    }

    private void tryResolvePendingUrielPlacement(int row, int col) {
        if (!pendingUrielPlacement || !inBounds(row, col)) return;
        if (whiteTurn != pendingUrielSideWhite) {
            clearPendingUrielPlacement();
            return;
        }
        if (board[row][col] != null) return;

        SpellTarget target = new SpellTarget();
        target.resurrectPieceType = pendingUrielPieceType;
        target.destRow = row;
        target.destCol = col;
        beginDeferredSpellVisualEffects();
        String result = spellManager.castSpell(this, SpellManager.URIEL, pendingUrielSideWhite, target);
        if (result == null) {
            if (gameController != null) {
                gameController.onLocalSpellCast(SpellManager.URIEL, pendingUrielSideWhite, cloneSpellTarget(target));
            }
            clearPendingUrielPlacement();
            if (gameController != null) {
                gameController.onBoardSpellCastResolved(SpellManager.URIEL, pendingUrielSideWhite);
            }
        } else {
            cancelDeferredSpellVisualEffects();
            if (!"Cancelled.".equals(result)) {
                GameDialogs.info(this, "Spell", String.valueOf(result));
                refreshPendingUrielPlacementMoves();
            }
        }
        repaint();
    }

    public void onUrielResurrected(Piece piece, int row, int col) {
        if (piece == null || !inBounds(row, col) || !animationsEnabled || isSpellSimulationActive()) return;
        if (deferSpellVisualEffects) {
            pendingUrielVisualPieces.add(piece);
            deferSpellVisualEffect(() -> {
                pendingUrielVisualPieces.remove(piece);
                onUrielResurrected(piece, row, col);
            });
            return;
        }
        urielResurrectionFx.add(new UrielResurrectionFx(
            piece, row, col, System.currentTimeMillis(), URIEL_RESURRECTION_MS
        ));
        playSpellChessSoundAsync(URIEL_RESURRECTION_SOUND, 0.82f, 120);
        ensureExplosionFxTimerRunning();
    }

    private boolean isPieceInUrielReveal(Piece piece, int row, int col) {
        if (pendingUrielVisualPieces.contains(piece)) return true;
        for (UrielResurrectionFx fx : urielResurrectionFx) {
            if (fx.piece == piece && fx.row == row && fx.col == col) return true;
        }
        return false;
    }

    private void drawPendingFireballVictims(Graphics2D source) {
        if (pendingFireballVictims.isEmpty()) return;
        Graphics2D g = (Graphics2D) source.create();
        for (PendingFireballVictim pending : pendingFireballVictims) {
            if (pending == null || pending.piece == null || !inBounds(pending.row, pending.col)) continue;
            int displayRow = boardFlipped ? 7 - pending.row : pending.row;
            int displayCol = boardFlipped ? 7 - pending.col : pending.col;
            pending.piece.draw(g, displayCol * SQUARE_SIZE, displayRow * SQUARE_SIZE);
        }
        g.dispose();
    }

    private boolean isPieceVisibleAt(int r, int c, boolean viewerIsWhite, boolean[][] visibilityMap) {
        Piece p = board[r][c];
        if (p == null) return false;
        if (p.isWhite() == viewerIsWhite) return true;
        return visibilityMap != null && visibilityMap[r][c];
    }

    private void ensureFogTexture() {
        if (fogCloudTexture != null) return;

        final int size = 1024;
        fogCloudTexture = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.util.Random rnd = new java.util.Random(42L);
        int[][] noise = new int[size][size];

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                noise[y][x] = rnd.nextInt(256);
            }
        }

        int radius = 5;
        long[][] integral = new long[size + 1][size + 1];
        for (int y = 0; y < size; y++) {
            long rowSum = 0L;
            for (int x = 0; x < size; x++) {
                rowSum += noise[y][x];
                integral[y + 1][x + 1] = integral[y][x + 1] + rowSum;
            }
        }

        for (int y = 0; y < size; y++) {
            int minY = Math.max(0, y - radius);
            int maxY = Math.min(size - 1, y + radius);
            for (int x = 0; x < size; x++) {
                int minX = Math.max(0, x - radius);
                int maxX = Math.min(size - 1, x + radius);
                long sum = integral[maxY + 1][maxX + 1]
                        - integral[minY][maxX + 1]
                        - integral[maxY + 1][minX]
                        + integral[minY][minX];
                int count = (maxX - minX + 1) * (maxY - minY + 1);
                int v = (int) (sum / count);
                int gray = Math.max(40, Math.min(185, v));
                int alpha = 115 + ((gray - 40) * 80 / 145);
                int rgb = (alpha << 24) | (gray << 16) | (gray << 8) | gray;
                fogCloudTexture.setRGB(x, y, rgb);
            }
        }
    }

    private void drawFogSquare(Graphics2D g2, int pixelX, int pixelY, int boardRow, int boardCol, float fogAlpha) {
        if (fogAlpha <= 0.001f) return;
        ensureFogTexture();
        int texW = fogCloudTexture.getWidth();
        int texH = fogCloudTexture.getHeight();
        int maxSx = Math.max(1, texW - SQUARE_SIZE - 1);
        int maxSy = Math.max(1, texH - SQUARE_SIZE - 1);

        int sx = Math.floorMod((int) (fogPhaseX + boardCol * 63 + boardRow * 37), maxSx);
        int sy = Math.floorMod((int) (fogPhaseY + boardCol * 29 + boardRow * 61), maxSy);

        Graphics2D gf = (Graphics2D) g2.create();
        gf.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, fogAlpha))));

        gf.drawImage(fogCloudTexture,
            pixelX, pixelY, pixelX + SQUARE_SIZE, pixelY + SQUARE_SIZE,
            sx, sy, sx + SQUARE_SIZE, sy + SQUARE_SIZE, null);

        int pulse = (int) (18 * (0.5 + 0.5 * Math.sin((fogPhaseX + boardRow * 21 + boardCol * 17) * 0.04)));
        int alpha = (int) ((120 + pulse) * fogAlpha);
        gf.setColor(new Color(10, 12, 16, Math.max(0, Math.min(255, alpha))));
        gf.fillRect(pixelX, pixelY, SQUARE_SIZE, SQUARE_SIZE);
        gf.dispose();
    }

    private boolean isDuckSquare(int row, int col) {
        return duckRow == row && duckCol == col;
    }

    private Piece[][] boardWithDuckBlocker(Piece[][] src) {
        if (!isDuckChessMode() || duckRow < 0 || duckCol < 0) return src;
        Piece[][] copy = new Piece[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                copy[r][c] = src[r][c];
            }
        }
        copy[duckRow][duckCol] = new Duck();
        return copy;
    }

    private ArrayList<int[]> getPseudoMovesForPiece(int r, int c) {
        return getPseudoMovesForPieceOnBoard(r, c, boardWithDuckBlocker(board));
    }

    private ArrayList<int[]> getPseudoMovesForPieceOnBoard(int r, int c, Piece[][] boardRef) {
        ArrayList<int[]> moves = new ArrayList<>();
        Piece p = boardRef[r][c];
        if (p == null) return moves;
        for (int tr = 0; tr < 8; tr++) {
            for (int tc = 0; tc < 8; tc++) {
                if (p.isValidMove(r, c, tr, tc, boardRef)) {
                    moves.add(new int[]{r, c, tr, tc});
                }
            }
        }
        return moves;
    }

    private ArrayList<int[]> getActiveLegalMoves(boolean sideWhite) {
        return withProperCastling(getActiveLegalMovesRaw(sideWhite), sideWhite);
    }

    private ArrayList<int[]> getActiveLegalMovesRaw(boolean sideWhite) {
        Piece[][] workingBoard = boardWithDuckBlocker(board);
        boolean removeEndermanBlocker = endermanPhaseActive && sideWhite == endermanPhaseSideWhite
                && inBounds(endermanPhaseRow, endermanPhaseCol);
        Piece saved = null;
        if (removeEndermanBlocker) {
            saved = workingBoard[endermanPhaseRow][endermanPhaseCol];
            workingBoard[endermanPhaseRow][endermanPhaseCol] = null;
        }
        try {
            ArrayList<int[]> baseMoves;
            if (isDuckChessMode()) {
                baseMoves = new ArrayList<>();
                for (int r = 0; r < 8; r++) {
                    for (int c = 0; c < 8; c++) {
                        Piece p = workingBoard[r][c];
                        if (p == null || p instanceof Duck || p.isWhite() != sideWhite) continue;
                        baseMoves.addAll(getPseudoMovesForPieceOnBoard(r, c, workingBoard));
                    }
                }
            } else if (fogOfWarEnabled && !isSpellChessMode()) {
                baseMoves = new ArrayList<>();
                for (int r = 0; r < 8; r++) {
                    for (int c = 0; c < 8; c++) {
                        Piece p = workingBoard[r][c];
                        if (p == null || p.isWhite() != sideWhite) continue;
                        baseMoves.addAll(getPseudoMovesForPieceOnBoard(r, c, workingBoard));
                    }
                }
            } else {
                baseMoves = new IsLegal(workingBoard).getAllLegalMoves(sideWhite);
            }
            if (!isSpellChessMode()) {
                return isAtomicMode() ? filterAtomicLegalMoves(baseMoves, sideWhite) : baseMoves;
            }

            ArrayList<int[]> filtered = new ArrayList<>();
            for (int[] m : baseMoves) {
                Piece moving = board[m[0]][m[1]];
                if (moving == null) continue;
                if (moving.getFrozenTurnsRemaining() > 0 || moving.getResurrectCooldownTurnsRemaining() > 0) continue;
                if (removeEndermanBlocker && m[2] == endermanPhaseRow && m[3] == endermanPhaseCol) continue;
                Piece target = board[m[2]][m[3]];
                if (target != null && target.isWhite() != sideWhite && target.getShieldedTurnsRemaining() > 0) continue;
                if (moving instanceof Pawn && target == null && m[1] != m[3]) {
                    Piece epTarget = board[m[0]][m[3]];
                    if (epTarget != null && epTarget.isWhite() != sideWhite && epTarget.getShieldedTurnsRemaining() > 0) continue;
                }
                filtered.add(m);
            }
            return isAtomicMode() ? filterAtomicLegalMoves(filtered, sideWhite) : filtered;
        } finally {
            if (removeEndermanBlocker) {
                workingBoard[endermanPhaseRow][endermanPhaseCol] = saved;
            }
        }
    }

    private ArrayList<int[]> filterAtomicLegalMoves(ArrayList<int[]> moves, boolean sideWhite) {
        if (!isAtomicMode()) return moves;

        ArrayList<int[]> filtered = new ArrayList<>();
        for (int[] m : moves) {
            Piece moving = board[m[0]][m[1]];
            if (moving == null) continue;

            Piece[][] backup = new Piece[8][8];
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    backup[r][c] = board[r][c];
                }
            }

            Piece captured = board[m[2]][m[3]];
            boolean isEnPassant = moving instanceof Pawn && captured == null && Math.abs(m[3] - m[1]) == 1;
            if (isEnPassant) {
                captured = board[m[0]][m[3]];
                board[m[0]][m[3]] = null;
            }

            board[m[0]][m[1]] = null;
            board[m[2]][m[3]] = moving;

            if (captured != null) {
                applyAtomicExplosionAt(m[2], m[3]);
            }

            boolean legal = findKing(sideWhite) != null;

            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    board[r][c] = backup[r][c];
                }
            }

            if (legal) {
                filtered.add(m);
            }
        }
        return filtered;
    }

    private ArrayList<int[]> getDuckPlacementMoves() {
        ArrayList<int[]> moves = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == null && !isDuckSquare(r, c)) {
                    moves.add(new int[] { -1, -1, r, c });
                }
            }
        }
        return moves;
    }

    private boolean isDuckPlacementAnimating() {
        if (!duckMoveAnimating) return false;
        long elapsed = System.currentTimeMillis() - duckAnimStartMs;
        if (elapsed >= DUCK_MOVE_ANIM_MS) {
            duckMoveAnimating = false;
            return false;
        }
        return true;
    }

    private void beginDuckPlacementPhase() {
        pendingDuckPlacement = true;
        pendingDuckPlacementSideWhite = whiteTurn;
        selectedPiece = null;
        draggedPiece = null;
        dragging = false;
        wasDragged = false;
        duckSelected = false;
        duckDragging = false;
        duckPressedOnDuck = false;
        duckMoveAnimating = false;
        possibleMoves.clear();
        possibleMoves.addAll(getDuckPlacementMoves());
        repaint();
    }

    private void completeDuckPlacementAndEndTurn(int row, int col) {
        if (board[row][col] != null || isDuckSquare(row, col)) return;
        int fromRow = duckRow;
        int fromCol = duckCol;
        duckSelected = false;
        duckDragging = false;
        duckPressedOnDuck = false;

        if (fromRow >= 0 && fromCol >= 0 && animationsEnabled) {
            duckMoveAnimating = true;
            duckAnimFromRow = fromRow;
            duckAnimFromCol = fromCol;
            duckAnimToRow = row;
            duckAnimToCol = col;
            duckAnimStartMs = System.currentTimeMillis();

            Timer timer = new Timer(AnimationTiming.FRAME_DELAY_MS, null);
            timer.addActionListener(ev -> {
                if (!isDuckPlacementAnimating()) {
                    ((Timer) ev.getSource()).stop();
                    finalizeDuckPlacementAndEndTurn(row, col);
                } else {
                    repaint();
                }
            });
            timer.start();
            repaint();
            return;
        }
        finalizeDuckPlacementAndEndTurn(row, col);
    }

    private void finalizeDuckPlacementAndEndTurn(int row, int col) {
        duckRow = row;
        duckCol = col;
        duckMoveAnimating = false;
        pendingDuckPlacement = false;
        pendingDuckPlacementSideWhite = false;
        possibleMoves.clear();

        whiteTurn = !whiteTurn;
        processSpellTurnStart(whiteTurn);
        if (autoFlipEnabled) {
            boardFlipped = !boardFlipped;
        }

        saveBoardState();
        recordCurrentPosition();

        if (analysisMode && analysisGame != null) analysisGame.onPositionChanged();

        String drawReason = getAutomaticDrawReason();
        if (drawReason != null) {
            if (gameController != null) gameController.stopTimer();
            setInputEnabled(false);
            if (gameController != null) {
                gameController.showInGameInfoPopup("Draw", drawReason);
            } else {
                GameDialogs.info(Board.this, "Game over", String.valueOf(drawReason));
            }
        }
        repaint();
    }

    public void setOnlineMode(boolean onlineMode, boolean localIsWhite) {
        this.onlineMode = onlineMode;
        this.localIsWhite = localIsWhite;
    }
    
    public void setBotMode(boolean isBotGame, boolean playerIsWhite) {
        this.isBotGame = isBotGame;
        this.playerIsWhite = playerIsWhite;
    }
    
    public void setAnalysisMode(boolean analysisMode) {
        this.analysisMode = analysisMode;
    }
    
    public void setAnalysisGame(AnalysisGame game) {
        this.analysisGame = game;
    }

    /**
     * Analysis only: you went back to an earlier move and want to play something
     * else from there. Drop the moves after it from this board's own (linear) history
     * so the position on screen becomes the latest one, instead of snapping forward to
     * the end of the old line. The analysis screen's tree keeps the old line as a
     * variation -- only this board's scratch history is trimmed.
     */
    private void branchFromHere() {
        int keep = currentStateIndex + 1;
        while (boardStates.size() > keep) boardStates.remove(boardStates.size() - 1);
        while (threeCheckStates.size() > keep) threeCheckStates.remove(threeCheckStates.size() - 1);
        while (moveHistoryFEN.size() > keep) moveHistoryFEN.remove(moveHistoryFEN.size() - 1);
        viewingPast = false;
    }

    /** 'P','N','B','R','Q','K' for the piece on the last move's destination square, or 0. */
    public char lastMovedPieceLetter() {
        if (lastMoveToRow < 0 || lastMoveToCol < 0) return 0;
        Piece p = board[lastMoveToRow][lastMoveToCol];
        if (p instanceof Pawn) return 'P';
        if (p instanceof Knight) return 'N';
        if (p instanceof Bishop) return 'B';
        if (p instanceof Rook) return 'R';
        if (p instanceof Queen) return 'Q';
        if (p instanceof King) return 'K';
        return 0;
    }

    /** Point the last-move highlight (and the review badge) at a move; -1 clears it. */
    public void setLastMoveSquares(int fromRow, int fromCol, int toRow, int toCol) {
        if (lastMoveFromRow == fromRow && lastMoveFromCol == fromCol
                && lastMoveToRow == toRow && lastMoveToCol == toCol) return;
        lastMoveFromRow = fromRow;
        lastMoveFromCol = fromCol;
        lastMoveToRow = toRow;
        lastMoveToCol = toCol;
        repaint();
    }

    public void setReviewBadgeClass(Review.MoveClass reviewClass) {
        this.reviewBadgeClass = reviewClass;
        repaint();
    }

    private void loadReviewBadgeImages() {
        reviewBadgeImages.clear();
        for (Review.MoveClass cls : Review.MoveClass.values()) {
            String file = moveLogoFileFor(cls);
            if (file == null) continue;
            // shared, pre-shrunk copy: the 2000px originals were redrawn every frame
            BufferedImage img = AnalysisTheme.logo(cls, 128);
            if (img != null) reviewBadgeImages.put(cls, img);
        }
    }

    private String moveLogoFileFor(Review.MoveClass cls) {
        if (cls == null) return null;
        return switch (cls) {
            case BOOK -> "booklogo.png";
            case FORCED -> "forcedlogo.png";
            case BRILLIANT -> "brilliantlogo.png";
            case GREAT -> "greatlogo.png";
            case BEST -> "bestlogo.png";
            case EXCELLENT -> "excellentlogo.png";
            case GOOD -> "excellentlogo.png";
            case INACCURACY -> "inaccuracylogo.png";
            case MISTAKE -> "mistakelogo.png";
            case BLUNDER -> "blunderlogo.png";
            case MISS -> "misslogo.png";
        };
    }
    
    public void setShowLegalMoves(boolean show) {
        this.showLegalMovesEnabled = show;
        repaint();
    }
    
    // ── custom start position (analysis "set up a position") ──────────────────
    private String customStartFen;

    /** Analysis only: start every game/replay from this FEN instead of the normal setup; null restores it. */
    public void setCustomStartFen(String fen) { customStartFen = fen; }

    public String getCustomStartFen() { return customStartFen; }

    /** Lay out {@code fen} on the (already cleared and set up) board. */
    private void applyCustomStart(String fen) {
        String[] parts = fen.trim().split("\\s+");
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) board[r][c] = null;
        String[] rows = parts[0].split("/");
        for (int r = 0; r < 8 && r < rows.length; r++) {
            int c = 0;
            for (char ch : rows[r].toCharArray()) {
                if (Character.isDigit(ch)) { c += ch - '0'; continue; }
                if (c > 7) break;
                boolean white = Character.isUpperCase(ch);
                Piece p;
                switch (Character.toLowerCase(ch)) {
                    case 'k': p = new King(white); break;
                    case 'q': p = new Queen(white); break;
                    case 'r': p = new Rook(white); break;
                    case 'b': p = new Bishop(white); break;
                    case 'n': p = new Knight(white); break;
                    default: p = new Pawn(white); break;
                }
                board[r][c++] = p;
            }
        }
        whiteTurn = parts.length < 2 || !"b".equals(parts[1]);
        String castling = parts.length > 2 ? parts[2] : "-";
        // castling rights live in the kings' and rooks' "has moved" flags
        markMovedUnless(7, 4, 7, 7, castling.contains("K"));
        markMovedUnless(7, 4, 7, 0, castling.contains("Q"));
        markMovedUnless(0, 4, 0, 7, castling.contains("k"));
        markMovedUnless(0, 4, 0, 0, castling.contains("q"));
        boolean wk = castling.contains("K") || castling.contains("Q"), bk = castling.contains("k") || castling.contains("q");
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            if (board[r][c] instanceof King) {
                King k = (King) board[r][c];
                if (k.isWhite() ? !wk : !bk) k.setMoved(true);
            }
        }
        try { halfmoveClock = parts.length > 4 ? Integer.parseInt(parts[4]) : 0; } catch (Exception e) { halfmoveClock = 0; }
        try { fullmoveNumber = parts.length > 5 ? Integer.parseInt(parts[5]) : 1; } catch (Exception e) { fullmoveNumber = 1; }
    }

    private void markMovedUnless(int kr, int kc, int rr, int rc, boolean right) {
        Piece rook = board[rr][rc];
        if (!right && rook instanceof Rook) ((Rook) rook).setMoved(true);
    }

    public void resetToStartPosition() {
        cancelScheduledSpellSounds();
        resetPremoveState();
        dropSelection();
        if (hintMove != null) { hintMove = null; moveHintFade.show(null); }
        // Clear board
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                board[r][c] = null;
            }
        }
        
        // Reset to starting position (or the analysis board's set-up position)
        setupPieces();
        resetSpellState();
        resetThreeCheckState();
        resetDuckState();
        whiteTurn = true;
        halfmoveClock = 0;
        fullmoveNumber = 1;
        if (customStartFen != null) applyCustomStart(customStartFen);
        lastMoveFromRow = -1;
        lastMoveFromCol = -1;
        lastMoveToRow = -1;
        lastMoveToCol = -1;
        
        // Clear history
        boardStates.clear();
        threeCheckStates.clear();
        moveHistoryFEN.clear();
        positionCounts.clear();
        saveBoardState();
        recordCurrentPosition();
        
        // Clear move history panel if it exists
        if (analysisGame != null && analysisGame.getMoveHistoryPanel() != null) {
            analysisGame.getMoveHistoryPanel().clearHistory();
        }
        
        repaint();
    }

    public int getCurrentStateIndex() {
        return currentStateIndex;
    }

    private void setupPieces() {
        // Reset king rotation state when setting up a new game
        rotatingKingRow = -1;
        rotatingKingCol = -1;
        kingRotationAngle = 0.0;
        kingRotating = false;
        
        for (int i = 0; i < 8; i++) {
            board[1][i] = new Pawn(false);
            board[6][i] = new Pawn(true);
        }

        if (isChess960()) {
            char[] rank = Chess960.backRank(chess960Position);
            boolean seenKing = false;
            for (int c = 0; c < 8; c++) {
                board[0][c] = backRankPiece(rank[c], false);
                board[7][c] = backRankPiece(rank[c], true);
                if (rank[c] == 'K') seenKing = true;
                if (rank[c] == 'R') {
                    if (seenKing) castleRookColKingside = c; else castleRookColQueenside = c;
                }
            }
            return;
        }
        castleRookColQueenside = 0;
        castleRookColKingside = 7;

        board[0][0] = new Rook(false);
        board[0][7] = new Rook(false);
        board[7][0] = new Rook(true);
        board[7][7] = new Rook(true);

        board[0][1] = new Knight(false);
        board[0][6] = new Knight(false);
        board[7][1] = new Knight(true);
        board[7][6] = new Knight(true);

        board[0][2] = new Bishop(false);
        board[0][5] = new Bishop(false);
        board[7][2] = new Bishop(true);
        board[7][5] = new Bishop(true);

        board[0][3] = new Queen(false);
        board[7][3] = new Queen(true);

        board[0][4] = new King(false);
        board[7][4] = new King(true);
    }

    private static Piece backRankPiece(char letter, boolean white) {
        switch (letter) {
            case 'R': return new Rook(white);
            case 'N': return new Knight(white);
            case 'B': return new Bishop(white);
            case 'Q': return new Queen(white);
            default:  return new King(white);
        }
    }

    public static class SpellSnapshot {
        Piece[][] boardCopy;
        PlayerState whiteCopy;
        PlayerState blackCopy;
        boolean whiteTurnCopy;
        int halfmoveClockCopy;
        int fullmoveNumberCopy;
        boolean boardFlippedCopy;
        boolean refundLastSpellElixirCopy;
        boolean preserveLastSpellCardCopy;
    }

    public SpellSnapshot createSpellSnapshot() {
        SpellSnapshot s = new SpellSnapshot();
        s.boardCopy = deepCopyBoard();
        s.whiteCopy = whiteState.copy();
        s.blackCopy = blackState.copy();
        s.whiteTurnCopy = whiteTurn;
        s.halfmoveClockCopy = halfmoveClock;
        s.fullmoveNumberCopy = fullmoveNumber;
        s.boardFlippedCopy = boardFlipped;
        s.refundLastSpellElixirCopy = refundLastSpellElixir;
        s.preserveLastSpellCardCopy = preserveLastSpellCard;
        return s;
    }

    public void restoreSpellSnapshot(SpellSnapshot snapshot) {
        if (snapshot == null) return;
        this.board = snapshot.boardCopy;
        this.whiteState.copyFrom(snapshot.whiteCopy);
        this.blackState.copyFrom(snapshot.blackCopy);
        this.whiteTurn = snapshot.whiteTurnCopy;
        this.halfmoveClock = snapshot.halfmoveClockCopy;
        this.fullmoveNumber = snapshot.fullmoveNumberCopy;
        this.boardFlipped = snapshot.boardFlippedCopy;
        this.refundLastSpellElixir = snapshot.refundLastSpellElixirCopy;
        this.preserveLastSpellCard = snapshot.preserveLastSpellCardCopy;
    }

    private Piece[][] deepCopyBoard() {
        Piece[][] copy = new Piece[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                copy[r][c] = (board[r][c] == null) ? null : board[r][c].copy();
            }
        }
        return copy;
    }

    private void processSpellTurnStart(boolean sideWhite) {
        if (!isSpellChessMode()) return;

        getPlayerState(sideWhite).gainElixirAtTurnStart();
        getPlayerState(sideWhite).decrementFogTurns();

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p == null || p.isWhite() != sideWhite) continue;
                if (p.getFrozenTurnsRemaining() > 0) {
                    int next = p.getFrozenTurnsRemaining() - 1;
                    p.setFrozenTurnsRemaining(next);
                    if (next == 0) onFreezeRemoved(p, r, c);
                }
                if (p.getShieldedTurnsRemaining() > 0) {
                    int next = p.getShieldedTurnsRemaining() - 1;
                    p.setShieldedTurnsRemaining(next);
                    if (next == 0) onShieldRemoved(p, r, c);
                }
                if (p.getBombRookTurnsRemaining() > 0) p.setBombRookTurnsRemaining(p.getBombRookTurnsRemaining() - 1);
                if (p.getResurrectCooldownTurnsRemaining() > 0) p.setResurrectCooldownTurnsRemaining(p.getResurrectCooldownTurnsRemaining() - 1);
            }
        }
    }
    
    public void flipBoard() {
        boardFlipped = !boardFlipped;
        if (gameController != null) {
            gameController.onBoardFlipped();
        }
        repaint();
    }

    /** Whether the board is currently shown flipped (Black at the bottom). Read-only, for
     *  anything outside this class that wants to render a position the same way the user
     *  is currently viewing it -- e.g. the Game Review window's own board panel. */
    public boolean isBoardFlipped() {
        return boardFlipped;
    }

    public int getLatestStateIndex() {
        return Math.max(0, boardStates.size() - 1);
    }


    // Animate from currentStateIndex to targetIndex (inclusive)
    public void animateToState(int targetIndex) {
        animateToState(targetIndex, 1f);
    }

    /** Multi-step navigation slowed down {@code slowdown} times (2 = twice as slow), for this trip only. */
    private float navSlowdown = 1f;

    public void animateToState(int targetIndex, float slowdown) {
        navSlowdown = Math.max(0.1f, slowdown);
        if (targetIndex < 0) targetIndex = 0;
        if (targetIndex >= boardStates.size()) targetIndex = boardStates.size() - 1;
        pendingHistoryTargetIndex = targetIndex;
        runPendingHistoryNavigation();
    }

    private void runPendingHistoryNavigation() {
        if (animating) return;
        if (pendingHistoryTargetIndex < 0) return;

        int latest = getLatestStateIndex();
        int targetIndex = Math.max(0, Math.min(latest, pendingHistoryTargetIndex));

        // If target is not the latest, mark viewingPast so we disable moves
        viewingPast = (targetIndex != latest);
        if (targetIndex == currentStateIndex) {
            pendingHistoryTargetIndex = -1;
            return;
        }

        if (targetIndex > currentStateIndex) {
            animateTransition(currentStateIndex, currentStateIndex + 1, this::runPendingHistoryNavigation);
        } else {
            animateTransition(currentStateIndex, currentStateIndex - 1, this::runPendingHistoryNavigation);
        }
    }

    private void animateStepForward(int finalIndex) {
        if (currentStateIndex >= finalIndex) return;
        int fromIdx = currentStateIndex;
        int toIdx = fromIdx + 1;
        animateTransition(fromIdx, toIdx, () -> animateStepForward(finalIndex));
    }

    private void animateStepBackward(int finalIndex) {
        if (currentStateIndex <= finalIndex) return;
        int fromIdx = currentStateIndex;
        int toIdx = fromIdx - 1;
        animateTransition(fromIdx, toIdx, () -> animateStepBackward(finalIndex));
    }

    public void stepForwardAnimated() {
        animateToState(Math.min(getLatestStateIndex(), currentStateIndex + 1));
    }

    public void stepBackAnimated() {
        animateToState(Math.max(0, currentStateIndex - 1));
    }

    // Take back the last move(s) - permanently removes from history
    // For bot games: takes back 2 moves (player's move + bot's response)
    // For analysis: takes back 1 move
    public void takebackMove() {
        if (boardStates.size() <= 1) return; // Can't take back from starting position
        resetPremoveState();
        dropSelection();
        if (hintMove != null) { hintMove = null; moveHintFade.show(null); }
        
        int movesToRemove = 1;
        
        // In bot games, take back 2 moves (player + bot) to return to player's turn
        if (isBotGame && boardStates.size() > 2) {
            movesToRemove = 2;
        }
        
        // Remove the last move(s) from history
        for (int i = 0; i < movesToRemove && boardStates.size() > 1; i++) {
            boardStates.remove(boardStates.size() - 1);
            if (moveHistoryFEN.size() > 0) {
                moveHistoryFEN.remove(moveHistoryFEN.size() - 1);
            }
        }
        
        // Also remove from move history panel
        if (gameController != null && gameController.getMoveHistoryPanel() != null) {
            for (int i = 0; i < movesToRemove; i++) {
                gameController.getMoveHistoryPanel().removeLastMove();
            }
        } else if (analysisMode && analysisGame != null && analysisGame.getMoveHistoryPanel() != null) {
            for (int i = 0; i < movesToRemove; i++) {
                analysisGame.getMoveHistoryPanel().removeLastMove();
            }
        }
        
        // Restore to the new latest state
        currentStateIndex = boardStates.size() - 1;
        Piece[][] stateToRestore = boardStates.get(currentStateIndex);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                board[r][c] = stateToRestore[r][c];
            }
        }
        
        // Update turn
        whiteTurn = (currentStateIndex % 2 == 0);
        // whose turn it is and the en-passant right come from the saved position itself
        restoreTurnStateAt(currentStateIndex);
        viewingPast = false;
        syncMoveClocksFromHistory();
        rebuildPositionCountsFromHistory();
        
        // Clear last move highlight
        lastMoveFromRow = -1;
        lastMoveFromCol = -1;
        lastMoveToRow = -1;
        lastMoveToCol = -1;
        
        // Update UCI moves for bot games
        if (isBotGame && gameController != null) {
            gameController.rebuildUCIMoves();
        }
        if (analysisMode && analysisGame != null) analysisGame.onPositionChanged();

        repaint();
    }
    
    public boolean isBotGame() {
        return isBotGame;
    }
    
    public boolean isAnalysisMode() {
        return analysisMode;
    }

    // Animate single transition between two adjacent saved states
    private void animateTransition(int fromIdx, int toIdx, Runnable done) {
        if (fromIdx < 0 || fromIdx >= boardStates.size() || toIdx < 0 || toIdx >= boardStates.size()) {
            if (done != null) done.run();
            return;
        }

        Piece[][] fromState = boardStates.get(fromIdx);
        Piece[][] toState = boardStates.get(toIdx);

        // Find moving piece by reference: piece present in fromState at (r1,c1) and same ref at (r2,c2) in toState
        int sr = -1, sc = -1, tr = -1, tc = -1;
        Piece moving = null;

        for (int r = 0; r < 8 && moving == null; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = fromState[r][c];
                if (p == null) continue;
                // search for this reference in toState
                boolean found = false;
                for (int r2 = 0; r2 < 8 && !found; r2++) {
                    for (int c2 = 0; c2 < 8; c2++) {
                        if (toState[r2][c2] == p) {
                            sr = r; sc = c; tr = r2; tc = c2; moving = p; found = true; break;
                        }
                    }
                }
            }
        }

        // Fallback: detect move by board-diff signature when references don't match
        // (promotion/copy-heavy variants can break reference continuity).
        if (moving == null || (sr == tr && sc == tc)) {
            int[] inferred = inferTransitionMove(fromState, toState);
            if (inferred != null) {
                sr = inferred[0];
                sc = inferred[1];
                tr = inferred[2];
                tc = inferred[3];
                moving = fromState[sr][sc];
            }
        }

        // If still unresolved, fallback to direct board copy without animation.
        if (moving == null || (sr == tr && sc == tc)) {
            setPositionAtMove(toIdx);
            if (done != null) done.run();
            return;
        }

        final int fr = sr, fc = sc, trr = tr, tcc = tc;
        final Piece mv = moving;
        boolean isCastling = mv instanceof King && Math.abs(fc - tcc) == 2;
        final boolean forwardInHistory = toIdx > fromIdx;

        // Prepare animation variables
        animPiece = mv;
        animating = true;
        board[fr][fc] = null;

        if (isCastling) {
            int rookCol = (tcc == 6) ? 7 : 0;
            animRook = fromState[trr][rookCol];
            // remove rook from from position during anim
            board[trr][rookCol] = null;
        }

        if (navStepMs > 0) {
            int remaining = pendingHistoryTargetIndex < 0 ? 1 : Math.abs(pendingHistoryTargetIndex - fromIdx);
            // Several moves in a row (rewinds, key-moment jumps): smooth, even steps.
            // Make each step in a sequence linger, with a long overall playback for large jumps.
            overrideAnimMs = remaining > 1 ? Math.max(600, Math.min(1600, 16000 / remaining)) : navStepMs;
            overrideAnimMs = Math.round(overrideAnimMs * navSlowdown);
        }
        animateMove(mv, fr, fc, trr, tcc, isCastling, () -> {
            overrideAnimMs = 0;
            // After animation, restore board to saved state
            Piece[][] stateToRestore = boardStates.get(toIdx);
            for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) board[r][c] = stateToRestore[r][c];
            currentStateIndex = toIdx;
            restoreTurnStateAt(toIdx);
            animating = false;
            animPiece = null;
            animRook = null;
            playHistoryNavigationSound(fromState, toState, fr, fc, trr, tcc, mv, isCastling, forwardInHistory);
            repaint();
            // Update viewingPast flag: true if we're not at latest
            viewingPast = (currentStateIndex != getLatestStateIndex());
            if (gameController != null) gameController.onPositionChanged(currentStateIndex);
            // The analysis screen has no gameController, so without this stepping back
            // and forth through the game left the engine analysing whatever position was
            // last played rather than the one on the board.
            if (analysisMode && analysisGame != null) analysisGame.onPositionChanged();
            if (done != null) done.run();
        }, true);  // Force animation for history navigation
    }

    private void playHistoryNavigationSound(Piece[][] fromState, Piece[][] toState,
                                            int fr, int fc, int tr, int tc,
                                            Piece moving, boolean isCastling,
                                            boolean forwardInHistory) {
        if (moving == null) {
            SoundManager.playMoveSound(false, false, false, false);
            return;
        }

        Piece[][] beforeState = forwardInHistory ? fromState : toState;
        Piece[][] afterState = forwardInHistory ? toState : fromState;

        int srcRow = forwardInHistory ? fr : tr;
        int srcCol = forwardInHistory ? fc : tc;
        int dstRow = forwardInHistory ? tr : fr;
        int dstCol = forwardInHistory ? tc : fc;
        Piece movedPiece = beforeState[srcRow][srcCol];
        if (movedPiece == null) movedPiece = moving;

        boolean isCapture = false;
        Piece destBefore = beforeState[dstRow][dstCol];
        if (destBefore != null && destBefore.isWhite() != movedPiece.isWhite()) {
            isCapture = true;
        } else if (movedPiece instanceof Pawn && srcCol != dstCol && destBefore == null) {
            // En passant: destination was empty but enemy pawn disappears from adjacent file.
            Piece epBefore = beforeState[srcRow][dstCol];
            Piece epAfter = afterState[srcRow][dstCol];
            if (epBefore instanceof Pawn && epAfter == null && epBefore.isWhite() != movedPiece.isWhite()) {
                isCapture = true;
            }
        }

        boolean isPromotion = movedPiece instanceof Pawn && !(afterState[dstRow][dstCol] instanceof Pawn);
        boolean check = false;
        try {
            IsLegal legalAfter = new IsLegal(afterState);
            check = legalAfter.isInCheck(!movedPiece.isWhite());
        } catch (Exception ignored) {
            check = false;
        }

        SoundManager.playMoveSound(isCapture, isCastling, isPromotion, check);
    }

    private int[] inferTransitionMove(Piece[][] fromState, Piece[][] toState) {
        java.util.ArrayList<int[]> sources = new java.util.ArrayList<>();
        java.util.ArrayList<int[]> targets = new java.util.ArrayList<>();

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece a = fromState[r][c];
                Piece b = toState[r][c];
                if (piecesEquivalent(a, b)) continue;
                if (a != null) sources.add(new int[] { r, c });
                if (b != null) targets.add(new int[] { r, c });
            }
        }

        // Prefer king move when castling changed multiple squares.
        for (int[] s : sources) {
            Piece sp = fromState[s[0]][s[1]];
            if (!(sp instanceof King)) continue;
            for (int[] t : targets) {
                Piece tp = toState[t[0]][t[1]];
                if (tp instanceof King && piecesEquivalent(sp, tp)) {
                    return new int[] { s[0], s[1], t[0], t[1] };
                }
            }
        }

        // Typical move/capture path.
        if (sources.size() == 1 && targets.size() == 1) {
            int[] s = sources.get(0);
            int[] t = targets.get(0);
            return new int[] { s[0], s[1], t[0], t[1] };
        }

        // Promotion (also with a capture): a pawn leaves, a new piece of its colour lands on
        // the last rank -- the types differ, so the pairing below would never match them.
        for (int[] s : sources) {
            Piece sp = fromState[s[0]][s[1]];
            if (!(sp instanceof Pawn)) continue;
            for (int[] t : targets) {
                Piece tp = toState[t[0]][t[1]];
                if (tp == null || tp instanceof Pawn || tp.isWhite() != sp.isWhite()) continue;
                if ((t[0] == 0 || t[0] == 7) && Math.abs(t[0] - s[0]) == 1 && Math.abs(t[1] - s[1]) <= 1) {
                    return new int[] { s[0], s[1], t[0], t[1] };
                }
            }
        }

        // Relaxed fallback: choose first compatible pair by color/type.
        for (int[] s : sources) {
            Piece sp = fromState[s[0]][s[1]];
            if (sp == null) continue;
            for (int[] t : targets) {
                Piece tp = toState[t[0]][t[1]];
                if (tp == null) continue;
                if (piecesEquivalent(sp, tp)) {
                    return new int[] { s[0], s[1], t[0], t[1] };
                }
            }
        }
        return null;
    }

    private boolean piecesEquivalent(Piece a, Piece b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.isWhite() == b.isWhite() && a.getClass() == b.getClass();
    }

    private boolean isUrielPlacementSquare(int row, int col) {
        for (int[] move : possibleMoves) {
            if (move != null && move.length >= 4 && move[2] == row && move[3] == col) return true;
        }
        return false;
    }

    private void drawUrielChooser(Graphics2D source) {
        if (!urielChooserActive) return;
        long now = System.currentTimeMillis();
        float rawProgress;
        if (urielChooserSelectedIndex < 0) {
            rawProgress = Math.min(
                1f,
                (now - urielChooserOpenedAtMs) / (float) URIEL_CHOOSER_TRANSITION_MS
            );
        } else {
            float exitProgress = Math.min(
                1f,
                (now - urielChooserSelectedAtMs) / (float) URIEL_CHOOSER_TRANSITION_MS
            );
            rawProgress = 1f - exitProgress;
        }
        float visualProgress = easeOutCubic(rawProgress);
        float visibility = visualProgress;

        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.76f * visibility));
        g.setColor(new Color(4, 5, 12));
        g.fillRect(0, 0, BOARD_SIZE, BOARD_SIZE);

        RadialGradientPaint glow = new RadialGradientPaint(
            new Point(BOARD_SIZE / 2, BOARD_SIZE / 2),
            BOARD_SIZE * 0.62f,
            new float[]{0f, 0.55f, 1f},
            new Color[]{
                new Color(126, 92, 24, Math.round(105 * visibility)),
                new Color(45, 31, 15, Math.round(38 * visibility)),
                new Color(0, 0, 0, 0)
            }
        );
        g.setPaint(glow);
        g.fillRect(0, 0, BOARD_SIZE, BOARD_SIZE);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, visibility));

        g.setFont(new Font("Serif", Font.BOLD, 28));
        String title = "HALL OF THE FALLEN";
        FontMetrics titleMetrics = g.getFontMetrics();
        int titleX = (BOARD_SIZE - titleMetrics.stringWidth(title)) / 2;
        g.setColor(new Color(0, 0, 0, 170));
        g.drawString(title, titleX + 2, 54);
        g.setColor(new Color(255, 224, 139));
        g.drawString(title, titleX, 52);

        g.setFont(new Font("Arial", Font.PLAIN, 13));
        String subtitle = "Choose a captured soul to return";
        FontMetrics subMetrics = g.getFontMetrics();
        g.setColor(new Color(230, 220, 195, 205));
        g.drawString(subtitle, (BOARD_SIZE - subMetrics.stringWidth(subtitle)) / 2, 75);

        double haloPhase = now / 620.0;
        for (int i = 0; i < urielChooserTypes.size(); i++) {
            Rectangle card = getUrielChooserCardBounds(i);
            boolean hovered = i == urielChooserHoverIndex && urielChooserSelectedIndex < 0;
            boolean selected = i == urielChooserSelectedIndex;
            float rise = hovered ? 9f : 0f;
            float transitionOffset = (1f - visualProgress) * (32f + (i % 3) * 8f);
            int drawY = Math.round(card.y - rise + transitionOffset);
            int cardW = card.width - 22;
            int cardH = card.height - 28;
            int duplicateCount = i < urielChooserCounts.size() ? urielChooserCounts.get(i) : 1;
            int visibleLayers = Math.min(4, Math.max(1, duplicateCount));

            Graphics2D cg = (Graphics2D) g.create();
            cg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, visibility));
            int arc = 18;
            for (int layer = visibleLayers - 1; layer >= 0; layer--) {
                int layerX = card.x + layer * 6;
                int layerY = drawY - layer * 4;
                cg.setPaint(new GradientPaint(
                    layerX, layerY,
                    hovered || selected ? new Color(73, 59, 31, 235) : new Color(35, 35, 43, 220),
                    layerX, layerY + cardH,
                    hovered || selected ? new Color(31, 23, 13, 240) : new Color(15, 16, 23, 235)
                ));
                cg.fillRoundRect(layerX, layerY, cardW, cardH, arc, arc);
                cg.setColor(hovered || selected ? new Color(255, 222, 123) : new Color(128, 119, 100));
                cg.setStroke(new BasicStroke(layer == 0 && (hovered || selected) ? 2.4f : 1.2f));
                cg.drawRoundRect(layerX, layerY, cardW - 1, cardH - 1, arc, arc);
            }

            int cx = card.x + cardW / 2;
            int pedestalY = drawY + cardH - 23;
            cg.setColor(new Color(255, 206, 82, hovered || selected ? 145 : 65));
            cg.fillOval(cx - 27, pedestalY - 5, 54, 12);
            cg.setColor(new Color(255, 232, 154, hovered || selected ? 220 : 110));
            cg.drawOval(cx - 27, pedestalY - 5, 54, 12);

            if (hovered || selected) {
                cg.rotate(haloPhase, cx, drawY + 45);
                cg.setColor(new Color(255, 223, 120, selected ? 235 : 185));
                cg.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                cg.drawOval(cx - 31, drawY + 14, 62, 62);
                for (int ray = 0; ray < 8; ray++) {
                    double a = ray * Math.PI / 4.0;
                    int x1 = cx + (int) Math.round(Math.cos(a) * 34);
                    int y1 = drawY + 45 + (int) Math.round(Math.sin(a) * 34);
                    int x2 = cx + (int) Math.round(Math.cos(a) * 39);
                    int y2 = drawY + 45 + (int) Math.round(Math.sin(a) * 39);
                    cg.drawLine(x1, y1, x2, y2);
                }
                cg.rotate(-haloPhase, cx, drawY + 45);
            }

            Piece displayPiece = createPieceByType(urielChooserTypes.get(i), urielChooserSideWhite);
            if (displayPiece != null) {
                Graphics2D pg = (Graphics2D) cg.create();
                float pieceAlpha = hovered || selected ? 1f : 0.62f;
                pg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pieceAlpha));
                displayPiece.draw(pg, cx - SQUARE_SIZE / 2, drawY + 4);
                pg.dispose();
            }

            String label = urielChooserLabels.get(i);
            cg.setFont(new Font("Arial", Font.BOLD, 13));
            FontMetrics lm = cg.getFontMetrics();
            cg.setColor(hovered || selected ? new Color(255, 238, 184) : new Color(205, 202, 194));
            cg.drawString(label, cx - lm.stringWidth(label) / 2, drawY + cardH + 20);
            cg.dispose();
        }

        if (urielChooserSelectedIndex < 0) {
            g.setFont(new Font("Arial", Font.PLAIN, 12));
            String cancel = "Click Uriel again or press Esc to cancel";
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(210, 205, 194, 170));
            g.drawString(cancel, (BOARD_SIZE - fm.stringWidth(cancel)) / 2, BOARD_SIZE - 18);
        }
        g.dispose();
    }

    private void drawUrielPlacementTargets(Graphics2D source, List<int[]> moves) {
        Graphics2D g = (Graphics2D) source.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double phase = (System.currentTimeMillis() % 2600L) / 2600.0 * Math.PI * 2.0;
        float pulse = (float) (0.5 + 0.5 * Math.sin(phase * 2.0));

        for (int[] move : moves) {
            if (move == null || move.length < 4) continue;
            int row = move[2], col = move[3];
            int displayRow = boardFlipped ? 7 - row : row;
            int displayCol = boardFlipped ? 7 - col : col;
            int cx = displayCol * SQUARE_SIZE + SQUARE_SIZE / 2;
            int cy = displayRow * SQUARE_SIZE + SQUARE_SIZE / 2;
            int radius = Math.round(SQUARE_SIZE * (0.25f + pulse * 0.025f));

            g.setColor(new Color(255, 220, 105, 34 + Math.round(pulse * 30)));
            g.fillOval(cx - radius - 7, cy - radius - 7, (radius + 7) * 2, (radius + 7) * 2);
            g.rotate(phase, cx, cy);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 231, 142, 205));
            g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4.0;
                int x1 = cx + (int) Math.round(Math.cos(a) * (radius - 4));
                int y1 = cy + (int) Math.round(Math.sin(a) * (radius - 4));
                int x2 = cx + (int) Math.round(Math.cos(a) * (radius + 5));
                int y2 = cy + (int) Math.round(Math.sin(a) * (radius + 5));
                g.drawLine(x1, y1, x2, y2);
            }
            g.rotate(-phase, cx, cy);
        }
        g.dispose();
    }

    private void drawUrielGhostPiece(Graphics2D source) {
        if (!pendingUrielPlacement || !inBounds(urielHoverRow, urielHoverCol)) return;
        Piece ghost = createPieceByType(pendingUrielPieceType, pendingUrielSideWhite);
        if (ghost == null) return;
        int displayRow = boardFlipped ? 7 - urielHoverRow : urielHoverRow;
        int displayCol = boardFlipped ? 7 - urielHoverCol : urielHoverCol;
        int x = displayCol * SQUARE_SIZE;
        int y = displayRow * SQUARE_SIZE;

        Graphics2D g = (Graphics2D) source.create();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.48f));
        ghost.draw(g, x, y);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
        g.setColor(new Color(255, 240, 172));
        g.setStroke(new BasicStroke(2f));
        g.drawOval(x + 9, y + 9, SQUARE_SIZE - 18, SQUARE_SIZE - 18);
        g.dispose();
    }

    private void drawUrielResurrectionFx(Graphics2D source) {
        long now = System.currentTimeMillis();
        for (UrielResurrectionFx fx : urielResurrectionFx) {
            float t = Math.max(0f, Math.min(1f, (now - fx.startMs) / (float) fx.durationMs));
            int displayRow = boardFlipped ? 7 - fx.row : fx.row;
            int displayCol = boardFlipped ? 7 - fx.col : fx.col;
            int x = displayCol * SQUARE_SIZE;
            int y = displayRow * SQUARE_SIZE;
            int cx = x + SQUARE_SIZE / 2;
            int cy = y + SQUARE_SIZE / 2;
            Graphics2D g = (Graphics2D) source.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            float flash = t < 0.22f ? t / 0.22f : Math.max(0f, 1f - (t - 0.22f) / 0.55f);
            if (flash > 0f) {
                g.setPaint(new GradientPaint(cx, 0, new Color(255, 255, 230, 0),
                    cx, cy, new Color(255, 235, 130, Math.round(190 * flash))));
                int beamW = Math.max(5, Math.round(SQUARE_SIZE * (0.10f + 0.20f * flash)));
                g.fillRoundRect(cx - beamW / 2, 0, beamW, cy + 8, beamW, beamW);
            }

            float sealT = Math.min(1f, t / 0.32f);
            float sealFade = t < 0.72f ? 1f : Math.max(0f, (1f - t) / 0.28f);
            int sealR = Math.round(SQUARE_SIZE * (0.16f + 0.42f * easeOutCubic(sealT)));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f * sealFade));
            g.setColor(new Color(255, 210, 70));
            g.setStroke(new BasicStroke(3f));
            g.drawOval(cx - sealR, cy - Math.round(sealR * 0.36f), sealR * 2, Math.round(sealR * 0.72f));

            float wingT = Math.max(0f, Math.min(1f, (t - 0.14f) / 0.42f));
            int wingW = Math.round(SQUARE_SIZE * 0.82f * easeOutCubic(wingT));
            int wingH = Math.round(SQUARE_SIZE * 0.58f);
            g.setColor(new Color(255, 244, 190, Math.round(210 * sealFade)));
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(cx - wingW, cy - wingH / 2, wingW, wingH, 205, 105);
            g.drawArc(cx, cy - wingH / 2, wingW, wingH, -130, 105);

            for (int i = 0; i < 18; i++) {
                double seed = i * 2.399963229728653;
                double angle = seed + t * Math.PI * 3.5;
                float orbit = SQUARE_SIZE * (0.17f + (i % 5) * 0.055f);
                float rise = t * SQUARE_SIZE * (0.35f + (i % 4) * 0.12f);
                int px = cx + Math.round((float) Math.cos(angle) * orbit);
                int py = cy + Math.round((float) Math.sin(angle) * orbit * 0.38f - rise + SQUARE_SIZE * 0.32f);
                float particleFade = Math.max(0f, 1f - t) * (0.55f + (i % 3) * 0.2f);
                int size = 2 + i % 4;
                g.setColor(new Color(255, 236, 145, Math.min(255, Math.round(255 * particleFade))));
                g.fillOval(px - size / 2, py - size / 2, size, size);
            }

            float reveal = Math.max(0f, Math.min(1f, (t - 0.25f) / 0.55f));
            reveal = 1f - (1f - reveal) * (1f - reveal);
            if (fx.piece != null && reveal > 0f) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, reveal));
                fx.piece.draw(g, x, y);
                float glow = Math.max(0f, 1f - Math.abs(t - 0.55f) / 0.35f);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glow * 0.42f));
                g.setColor(new Color(255, 255, 225));
                g.fillOval(x + 8, y + 8, SQUARE_SIZE - 16, SQUARE_SIZE - 16);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, reveal));
                fx.piece.draw(g, x, y);
            }
            g.dispose();
        }
    }

    private float easeOutCubic(float t) {
        float inv = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - inv * inv * inv;
    }

    // ── analysis "you've left the real game" wash ────────────────────────────
    // A soft white layer over the whole board (pieces included). Painted from here,
    // inside Board's own paint, so it can never fall out of step with piece
    // animations the way a separate overlay component could.
    private float washAlpha = 0f;

    public void setWashAlpha(float alpha) {
        float a = Math.max(0f, Math.min(1f, alpha));
        if (a == washAlpha) return;
        washAlpha = a;
        repaint();
    }

    // ── engine hint arrow (Analysis) ─────────────────────────────────────────
    private int hintFr = -1, hintFc, hintTr, hintTc;

    /** Show the engine's top move as a green arrow; pass a negative row to clear it. */
    public void setHintArrow(int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow == hintFr && fromCol == hintFc && toRow == hintTr && toCol == hintTc) return;
        hintFade.show(fromRow < 0 ? null : new int[]{fromRow, fromCol, toRow, toCol});
        hintFr = fromRow; hintFc = fromCol; hintTr = toRow; hintTc = toCol;
    }

    /**
     * Fades a suggestion arrow in and out: a new arrow fades in while the one it replaces
     * fades out, and a cleared arrow fades away instead of vanishing. While pieces are
     * moving the incoming arrow waits (at nothing) and fades in once they've landed.
     */
    private final class ArrowFade {
        private static final float IN_MS = 220f, OUT_MS = 170f;
        int[] cur, prev;
        float curA, prevA;
        private long last;
        private javax.swing.Timer timer;

        void show(int[] move) {
            if (cur != null && curA > 0.02f) { prev = cur; prevA = curA; }
            else if (move == null && cur == null) return;
            cur = move;
            curA = 0f;
            start();
        }

        private void start() {
            last = System.nanoTime();
            if (timer == null) {
                timer = AnimationTiming.createUiTimer(e -> tick());
            }
            if (!timer.isRunning()) timer.start();
            repaint();
        }

        private void tick() {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000f;
            last = now;
            boolean busy = false;
            if (prev != null) {
                prevA = Math.max(0f, prevA - dt / OUT_MS);
                if (prevA <= 0f) prev = null; else busy = true;
            }
            if (cur != null && curA < 1f) {
                if (!animating) curA = Math.min(1f, curA + dt / IN_MS);
                busy = true;
            }
            repaint();
            if (!busy) timer.stop();
        }

        /** Smoothstep, so the fade eases at both ends. */
        float ease(float t) { return t * t * (3f - 2f * t); }
    }
    private final ArrowFade hintFade = new ArrowFade();

    public void clearHintArrow() { setHintArrow(-1, 0, 0, 0); }

    private void paintHintArrow(Graphics g) {
        if (hintFade.prev != null) paintHintArrow(g, hintFade.prev, hintFade.ease(hintFade.prevA));
        if (hintFade.cur != null && !animating) paintHintArrow(g, hintFade.cur, hintFade.ease(hintFade.curA));
    }

    private void paintHintArrow(Graphics g, int[] m, float fade) {
        if (fade <= 0.001f) return;
        int hintFr = m[0], hintFc = m[1], hintTr = m[2], hintTc = m[3];
        Graphics2D a = (Graphics2D) g.create();
        a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        a.scale(renderScale(), renderScale());
        int fr = boardFlipped ? 7 - hintFr : hintFr, fc = boardFlipped ? 7 - hintFc : hintFc;
        int tr = boardFlipped ? 7 - hintTr : hintTr, tc = boardFlipped ? 7 - hintTc : hintTc;
        double sx = fc * SQUARE_SIZE + SQUARE_SIZE / 2.0, sy = fr * SQUARE_SIZE + SQUARE_SIZE / 2.0;
        double ex = tc * SQUARE_SIZE + SQUARE_SIZE / 2.0, ey = tr * SQUARE_SIZE + SQUARE_SIZE / 2.0;
        double shaft = SQUARE_SIZE * 0.20, headLen = SQUARE_SIZE * 0.42, headW = SQUARE_SIZE * 0.52;
        java.awt.geom.Path2D path = new java.awt.geom.Path2D.Double();
        boolean knight = Math.abs(hintFr - hintTr) * Math.abs(hintFc - hintTc) == 2;
        double cx = sx, cy = sy;               // corner for the L of a knight move
        if (knight) {
            if (Math.abs(ey - sy) > Math.abs(ex - sx)) { cx = sx; cy = ey; } else { cx = ex; cy = sy; }
        }
        // last segment direction (for the head)
        double lx = knight ? cx : sx, ly = knight ? cy : sy;
        double len = Math.hypot(ex - lx, ey - ly);
        if (len < 1) { a.dispose(); return; }
        double ux = (ex - lx) / len, uy = (ey - ly) / len;
        double bx = ex - ux * headLen, by = ey - uy * headLen;   // base of the head
        double startInset = SQUARE_SIZE * 0.18;
        double s0x = sx, s0y = sy;
        double fx = (knight ? cx : bx) - sx, fy = (knight ? cy : by) - sy;
        double flen = Math.hypot(fx, fy);
        if (flen > 0) { s0x = sx + fx / flen * startInset; s0y = sy + fy / flen * startInset; }
        path.moveTo(s0x, s0y);
        if (knight) path.lineTo(cx, cy);
        path.lineTo(bx, by);
        a.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f * fade));
        a.setColor(new Color(118, 186, 68));
        a.setStroke(new BasicStroke((float) shaft, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        a.draw(path);
        java.awt.geom.Path2D head = new java.awt.geom.Path2D.Double();
        double px = -uy, py = ux;
        head.moveTo(ex, ey);
        head.lineTo(bx + px * headW / 2, by + py * headW / 2);
        head.lineTo(bx - px * headW / 2, by - py * headW / 2);
        head.closePath();
        a.fill(head);
        a.dispose();
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        paintHintArrow(g);
        if (washAlpha > 0.001f) {
            Graphics2D wg = (Graphics2D) g.create();
            wg.setColor(new Color(255, 255, 255, Math.round(255 * washAlpha)));
            wg.fillRect(0, 0, getWidth(), getHeight());
            wg.dispose();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D gRoot = (Graphics2D) g.create();
        gRoot.scale(renderScale(), renderScale());
        g = gRoot;
        int boardShakeX = 0;
        int boardShakeY = 0;
        if (!fireballImpactFx.isEmpty()) {
            Point shake = getFireballBoardShakeOffset(System.currentTimeMillis());
            boardShakeX = shake.x;
            boardShakeY = shake.y;
            if (boardShakeX != 0 || boardShakeY != 0) {
                gRoot.translate(boardShakeX, boardShakeY);
            }
        }

        boolean fogViewerIsWhite = fogRippleActive ? fogRippleViewerIsWhite : getFogViewerIsWhite();
        boolean fogRulesActive = fogRippleActive
                || fogOfWarLive()
                || isSpellFogActiveForViewer(fogViewerIsWhite);
        updateFogTransitionTarget(fogViewerIsWhite);
        boolean fogOverlayActive = fogVisualAlpha > 0.001f;
        boolean[][] visibilityMap = (fogRulesActive || fogOverlayActive) ? computeVisibilityMap(fogViewerIsWhite) : null;
        long fogFrameTimeMs = System.currentTimeMillis();
        boolean spellFogRippleActive = fogRippleActive;

        if (!fogRulesActive && !isDuckChessMode()) {
            refreshCheckStatusCached();
        }
        boolean showCheck = AppSettings.highlightCheck();     // Settings > Gameplay > Highlight check
        boolean whiteInCheck = showCheck && !fogRulesActive && !isDuckChessMode() && cachedWhiteInCheck;
        boolean blackInCheck = showCheck && !fogRulesActive && !isDuckChessMode() && cachedBlackInCheck;
        if (selectedPiece != null) {
            if (glowingSelectedRow != selectedRow || glowingSelectedCol != selectedCol) {
                glowingSelectedRow = selectedRow;
                glowingSelectedCol = selectedCol;
                selectedGlowStartedAt = System.currentTimeMillis();
            }
        } else {
            glowingSelectedRow = -1;
            glowingSelectedCol = -1;
        }

        // pieces are drawn where they'll stand after the queued premoves
        final Piece[][] shown = premoveView();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int displayRow = boardFlipped ? (7 - r) : r;
                int displayCol = boardFlipped ? (7 - c) : c;
                boolean fogRippleReached = !spellFogRippleActive
                        || hasFogRippleReachedSquare(displayRow, displayCol, fogFrameTimeMs);
                boolean squareFogRulesActive = fogRulesActive && fogRippleReached;
                Color baseSquareColor = ((displayRow + displayCol) % 2 == 0) ? lightSquare : darkSquare;
                if (isKingOfTheHillMode() && isCenterSquareForKingHill(r, c)) {
                    int rr = Math.min(255, (baseSquareColor.getRed() * 3 + 110) / 4);
                    int gg = Math.min(255, (baseSquareColor.getGreen() * 3 + 145) / 4);
                    int bb = Math.min(255, (baseSquareColor.getBlue() * 3 + 220) / 4);
                    baseSquareColor = new Color(rr, gg, bb);
                }

                boolean kingInCheckSquare =
                        board[r][c] instanceof King &&
                        !fogRulesActive &&
                        ((board[r][c].isWhite() && whiteInCheck) ||
                         (!board[r][c].isWhite() && blackInCheck));
                
                boolean isBlinkingKing = (r == blinkKingRow && c == blinkKingCol);
                boolean isCheckFlashKing = (r == checkFlashKingRow && c == checkFlashKingCol && checkFlashStep >= 0);
                boolean isTabFocusedPieceSquare = (selectedPiece == null && r == tabFocusedPieceRow && c == tabFocusedPieceCol);
                boolean isSelectedSquare = (selectedPiece != null && r == selectedRow && c == selectedCol);
                boolean isTargetingSourceSquare = (pendingSpellTargetingId != null && selectedPiece != null
                        && r == selectedRow && c == selectedCol);
                boolean isTabMoveSourceSquare = (pendingSpellTargetingId == null && selectedPiece != null
                        && tabPieceSelectionConfirmed && r == selectedRow && c == selectedCol);
                boolean isBomberPrimedSquare = isBomberPrimedForSquare(r, c, whiteTurn);
                boolean tabSelectedSpellTargetSquare = isTabSelectedSpellTargetSquare(r, c);

                Color targetSquareColor;
                boolean immediateSquareColor = isBlinkingKing || isCheckFlashKing;
                if (isBlinkingKing && blinkVisible) {
                    targetSquareColor = baseSquareColor;
                } else if (isCheckFlashKing) {
                    Color flashColor = getCheckFlashColor();
                    targetSquareColor = flashColor != null ? flashColor : baseSquareColor;
                } else if (kingInCheckSquare) {
                    targetSquareColor = CHECK_SQUARE_COLOR;
                } else if (isBomberPrimedSquare) {
                    targetSquareColor = new Color(236, 139, 39);
                } else if (isTargetingSourceSquare) {
                    targetSquareColor = TAB_FOCUSED_SQUARE;
                } else if (tabSelectedSpellTargetSquare) {
                    targetSquareColor = TAB_FOCUSED_SQUARE;
                } else if (isTabMoveSourceSquare) {
                    targetSquareColor = TAB_FOCUSED_SQUARE;
                } else if (isTabFocusedPieceSquare) {
                    targetSquareColor = TAB_FOCUSED_SQUARE;
                } else if (isSelectedSquare) {
                    targetSquareColor = SELECTED_SQUARE;
                } else {
                    targetSquareColor = baseSquareColor;
                }

                if (!fogRulesActive && AppSettings.lastMoveHighlight() && ((r == lastMoveFromRow && c == lastMoveFromCol)
                        || (r == lastMoveToRow && c == lastMoveToCol))) {
                    // chess.com-style yellow on both the from- and to-square, in review too.
                    // Warm and strong enough to win over the blue: a pale see-through
                    // yellow mixed with blue squares and came out green.
                    Color lastMoveTint = new Color(255, 226, 92, 192);
                    targetSquareColor = overlayColor(targetSquareColor, lastMoveTint);
                }
                for (int i = 0; i < premoveQueue.size(); i++) {
                    Premove premove = premoveQueue.get(i);
                    if ((r == premove.fr && c == premove.fc)
                            || (r == premove.tr && c == premove.tc)) {
                        targetSquareColor = overlayColor(
                            targetSquareColor,
                            i == 0 ? PREMOVE_FIRST_COLOR : PREMOVE_CHAIN_COLOR
                        );
                    }
                }
                // (the analysis board rings its marked squares instead -- drawn with the arrows)
                if (!analysisMode) for (MarkedSquare marked : markedSquares) {
                    if (marked.row == r && marked.col == c) {
                        targetSquareColor = overlayColor(targetSquareColor, new Color(255, 0, 0, 120));
                        break;
                    }
                }

                g.setColor(animateSquareColor(r, c, targetSquareColor, immediateSquareColor));
                g.fillRect(displayCol * SQUARE_SIZE, displayRow * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
                if (isSelectedSquare) {
                    drawSelectedSquareGlow(
                        (Graphics2D) g,
                        displayCol * SQUARE_SIZE,
                        displayRow * SQUARE_SIZE
                    );
                }

                if (fogOverlayActive && fogRippleReached
                        && (visibilityMap == null || !visibilityMap[r][c])) {
                    drawFogSquare((Graphics2D) g, displayCol * SQUARE_SIZE, displayRow * SQUARE_SIZE, r, c, fogVisualAlpha);
                }

                boolean endermanSelectionPiece = isPendingEndermanPieceSquare(r, c);
                Piece sq = shown[r][c];
                if (sq != null &&
                    (!squareFogRulesActive || (sq.isWhite() == fogViewerIsWhite || (visibilityMap != null && visibilityMap[r][c]))) &&
                    sq != animPiece && 
                    sq != animRook && !(r == vibrateRow && c == vibrateCol) && 
                    (!dragging || sq != draggedPiece) &&
                    !isBomberCaptureSourcePiece(sq, r, c) &&
                    !isPieceInActivePreExplosionAt(r, c, sq) &&
                    !isPieceHandledByEndermanPop(sq, r, c, fogFrameTimeMs) &&
                    !isPieceHandledByEndermanPhasePoof(sq, r, c) &&
                    !isPieceInUrielReveal(sq, r, c)) {  // Don't draw pieces handled by an active effect.
                    
                    // Check if this is the rotating checkmated king
                    if (r == rotatingKingRow && c == rotatingKingCol && kingRotationAngle > 0) {
                        drawRotatedKing(g, sq, displayCol * SQUARE_SIZE, displayRow * SQUARE_SIZE, kingRotationAngle);
                    } else {
                        if (endermanPhaseActive && r == endermanPhaseRow && c == endermanPhaseCol) {
                            Graphics2D gp = (Graphics2D) g.create();
                            gp.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
                            sq.draw(gp, displayCol * SQUARE_SIZE, displayRow * SQUARE_SIZE);
                            gp.dispose();
                        } else {
                            sq.draw(g, displayCol * SQUARE_SIZE, displayRow * SQUARE_SIZE);
                        }
                        if (endermanSelectionPiece) {
                            drawEndermanPhaseOutline(
                                    (Graphics2D) g,
                                    sq,
                                    displayCol * SQUARE_SIZE,
                                    displayRow * SQUARE_SIZE,
                                    r,
                                    c,
                                    fogFrameTimeMs);
                        }
                    }
                }
            }
        }

        drawBomberCaptureFx((Graphics2D) g);
        drawPendingFireballVictims((Graphics2D) g);

        if (isDuckChessMode() && duckRow >= 0 && duckCol >= 0) {
            ensureDuckImageLoaded();
            if (duckImage != null) {
                boolean animDuck = isDuckPlacementAnimating();
                if (animDuck) {
                    float t = (float) (System.currentTimeMillis() - duckAnimStartMs) / (float) DUCK_MOVE_ANIM_MS;
                    t = Math.max(0f, Math.min(1f, t));
                    float eased = (float) (1.0 - Math.pow(1.0 - t, 3));
                    int fromDisplayRow = boardFlipped ? (7 - duckAnimFromRow) : duckAnimFromRow;
                    int fromDisplayCol = boardFlipped ? (7 - duckAnimFromCol) : duckAnimFromCol;
                    int toDisplayRow = boardFlipped ? (7 - duckAnimToRow) : duckAnimToRow;
                    int toDisplayCol = boardFlipped ? (7 - duckAnimToCol) : duckAnimToCol;
                    int x = Math.round((fromDisplayCol + (toDisplayCol - fromDisplayCol) * eased) * SQUARE_SIZE);
                    int y = Math.round((fromDisplayRow + (toDisplayRow - fromDisplayRow) * eased) * SQUARE_SIZE);
                    g.drawImage(duckImage, x, y, SQUARE_SIZE, SQUARE_SIZE, null);
                } else if (!duckDragging) {
                    int displayDuckRow = boardFlipped ? (7 - duckRow) : duckRow;
                    int displayDuckCol = boardFlipped ? (7 - duckCol) : duckCol;
                    int x = displayDuckCol * SQUARE_SIZE;
                    int y = displayDuckRow * SQUARE_SIZE;
                    if (pendingDuckPlacement && duckSelected) {
                        Graphics2D gd = (Graphics2D) g.create();
                        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 110.0));
                        gd.setColor(new Color(255, 215, 96, (int) (70 + pulse * 55)));
                        gd.fillRoundRect(x + 4, y + 4, SQUARE_SIZE - 8, SQUARE_SIZE - 8, 14, 14);
                        gd.dispose();
                    }
                    g.drawImage(duckImage, x, y, SQUARE_SIZE, SQUARE_SIZE, null);
                }
            }
        }

        // Draw coordinates
        if (showCoordinates) {
            drawCoordinates(g);
        }
        
        if ((pendingSpellTargetingId != null || pendingEndermanPiecePick || endermanPhaseActive)
                && !pendingSpellHighlightSquares.isEmpty()) {
            Graphics2D gSpell = (Graphics2D) g.create();
            gSpell.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int[] sq : pendingSpellHighlightSquares) {
                int r = sq[0], c = sq[1];
                if (!inBounds(r, c)) continue;
                int displayRow = boardFlipped ? (7 - r) : r;
                int displayCol = boardFlipped ? (7 - c) : c;
                int x = displayCol * SQUARE_SIZE;
                int y = displayRow * SQUARE_SIZE;
                if (isTabSelectedSpellTargetSquare(r, c)) continue;
                if (!pendingEndermanPiecePick) {
                    gSpell.setColor(new Color(145, 145, 145, 230));
                    gSpell.setStroke(new BasicStroke(3f));
                    gSpell.drawRoundRect(x + 5, y + 5, SQUARE_SIZE - 10, SQUARE_SIZE - 10, 12, 12);
                }
            }
            gSpell.dispose();
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g2d.setColor(Color.DARK_GRAY);
        
        // Show legal move dots for selected piece, or for pending Uriel placement.
        ArrayList<int[]> movesToShow = new ArrayList<>();
        if (pendingSpellTargetingId != null) {
            for (int[] sq : pendingSpellHighlightSquares) {
                if (sq == null || sq.length < 2) continue;
                int row = sq[0], col = sq[1];
                if (!inBounds(row, col)) continue;
                movesToShow.add(new int[]{-1, -1, row, col});
            }
            movesToShow.addAll(possibleMoves);
        } else if (pendingDuckPlacement || pendingUrielPlacement || SpellManager.ZOGLIN.equals(pendingSpellTargetingId)) {
            movesToShow = possibleMoves;
        } else if (!fogRulesActive && showLegalMovesEnabled && selectedPiece != null) {
            movesToShow = possibleMoves;
        }
        final boolean premoveSelectionShown = isPremoveSelection();
        
        for (int[] move : movesToShow) {
            int toRow = move[2];
            int toCol = move[3];
            int displayRow = boardFlipped ? (7 - toRow) : toRow;
            int displayCol = boardFlipped ? (7 - toCol) : toCol;
            boolean captureMove = premoveSelectionShown
                    ? inBounds(move[2], move[3]) && shown[move[2]][move[3]] != null
                    : isCaptureIndicatorMove(move);
            boolean showFireIcon = shouldShowFireCaptureIcon(captureMove);
	            boolean tabSelectedSpellTarget = isTabSelectedSpellTargetSquare(toRow, toCol);
	            
	            if (captureMove) {
	                g2d.setStroke(new BasicStroke(4));
	                g2d.drawRoundRect(displayCol * SQUARE_SIZE + 4, displayRow * SQUARE_SIZE + 4,
	                                  SQUARE_SIZE - 8, SQUARE_SIZE - 8, 10, 10);
                if (showFireIcon) {
                    int iconSize = 14;
                    int iconX = displayCol * SQUARE_SIZE + SQUARE_SIZE - iconSize - 5;
                    int iconY = displayRow * SQUARE_SIZE + 3;
                    drawFireIcon(g2d, iconX, iconY, iconSize);
                }
            } else if (!tabSelectedSpellTarget && !pendingUrielPlacement) {
                int dotSize = 20;
                g2d.fillOval(displayCol * SQUARE_SIZE + SQUARE_SIZE/2 - dotSize/2, 
                            displayRow * SQUARE_SIZE + SQUARE_SIZE/2 - dotSize/2, dotSize, dotSize);
            }
        }

        if (pendingUrielPlacement) {
            drawUrielPlacementTargets(g2d, movesToShow);
            drawUrielGhostPiece(g2d);
        }

        ArrayList<int[]> tabMoves = new ArrayList<>(movesToShow);
        tabMoves.sort(this::compareMovesForTabOrder);
        if (tabFocusedLegalMoveIndex >= tabMoves.size()) {
            tabFocusedLegalMoveIndex = tabMoves.isEmpty() ? -1 : 0;
        }
        if (tabFocusedLegalMoveIndex >= 0 && tabFocusedLegalMoveIndex < tabMoves.size()) {
            int[] focused = tabMoves.get(tabFocusedLegalMoveIndex);
            int toRow = focused[2];
            int toCol = focused[3];
            int displayRow = boardFlipped ? (7 - toRow) : toRow;
            int displayCol = boardFlipped ? (7 - toCol) : toCol;
            boolean captureMove = isCaptureIndicatorMove(focused);
            boolean pawnTarget = inBounds(toRow, toCol) && (board[toRow][toCol] instanceof Pawn);
            if (!isTabSelectedSpellTargetSquare(toRow, toCol)) {
                Graphics2D gFocus = (Graphics2D) g.create();
                gFocus.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                gFocus.setColor(new Color(0, 0, 0, 230));
                gFocus.setStroke(new BasicStroke(3f));
                if (captureMove) {
                    if (!pawnTarget) {
                        gFocus.drawRoundRect(displayCol * SQUARE_SIZE + 8, displayRow * SQUARE_SIZE + 8,
                            SQUARE_SIZE - 16, SQUARE_SIZE - 16, 8, 8);
                    }
                } else {
                    int dotSize = 20;
                    int cx = displayCol * SQUARE_SIZE + SQUARE_SIZE / 2;
                    int cy = displayRow * SQUARE_SIZE + SQUARE_SIZE / 2;
                    int ring = dotSize + 10;
                    gFocus.drawOval(cx - ring / 2, cy - ring / 2, ring, ring);
                }
                gFocus.dispose();
            }
        }


        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        // the move-classification badge sits on top of the legal-move dots and capture rings
        drawReviewBadge(g2d);
        // the rings are drawn on a layer above the board so they can spill past its edge
        if (!ensureRingOverlay()) drawThreeCheckAmbientRings(g2d);
        drawPieceExplosionLighting(g2d);
        drawAtomicExplosionFx(g2d);
        if (fireballOverlayPanel == null || !fireballOverlayPanel.isShowing()) {
            drawFireballImpactFx(g2d);
        }
        drawFreezeVisualFx(g2d, fogRulesActive, fogViewerIsWhite, visibilityMap);
        drawShieldVisualFx(g2d, fogRulesActive, fogViewerIsWhite, visibilityMap);
        drawUrielResurrectionFx(g2d);
        drawPieceExplosionFx(g2d);
        drawFogDarknessEnvelope(g2d, fogFrameTimeMs);
        drawFogRipple(g2d, fogFrameTimeMs);
        drawEndermanPhasePoof(g2d, fogFrameTimeMs);
        drawEndermanPickupFx(g2d, fogFrameTimeMs);
        drawEndermanOverlayFadeOut(g2d, fogFrameTimeMs);

        if (vibratePiece != null && vibrateRow >= 0 && vibrateCol >= 0 &&
            (!fogRulesActive || vibratePiece.isWhite() == fogViewerIsWhite || (visibilityMap != null && visibilityMap[vibrateRow][vibrateCol]))) {
            int displayRow = boardFlipped ? (7 - vibrateRow) : vibrateRow;
            int displayCol = boardFlipped ? (7 - vibrateCol) : vibrateCol;
            vibratePiece.draw(g, displayCol * SQUARE_SIZE + vibrateOffsetX, displayRow * SQUARE_SIZE + vibrateOffsetY);
        }

        // Draw animated piece with enhanced visual effects
        if (animPiece != null && (!fogRulesActive || animPiece.isWhite() == fogViewerIsWhite)) {
            animPiece.draw(g, animX, animY);
        }
        
        // Draw animated rook during castling
        if (animRook != null && (!fogRulesActive || animRook.isWhite() == fogViewerIsWhite)) {
            animRook.draw(g, animRookX, animRookY);
        }
        
        if (dragging && draggedPiece != null && (!fogRulesActive || draggedPiece.isWhite() == fogViewerIsWhite)) {
            int pieceX = dragX - SQUARE_SIZE / 2;
            int pieceY = dragY - SQUARE_SIZE / 2;
            draggedPiece.draw(g, pieceX, pieceY);
        }

        if (pendingDuckPlacement && duckDragging && duckImage != null) {
            Graphics2D gd = (Graphics2D) g.create();
            gd.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
            gd.drawImage(duckImage, duckDragX - SQUARE_SIZE / 2, duckDragY - SQUARE_SIZE / 2, SQUARE_SIZE, SQUARE_SIZE, null);
            gd.dispose();
        }
        // ===== DRAW ARROWS LAST (on top of everything) =====
Graphics2D g2 = (Graphics2D) g.create();
g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

if (analysisMode) {
    // analysis: right-click marks are rings around the square, in the arrows' colour
    g2.setStroke(new BasicStroke(Math.max(4f, SQUARE_SIZE * 0.075f)));
    for (MarkedSquare m : markedSquares) {
        int dr = boardFlipped ? 7 - m.row : m.row, dc = boardFlipped ? 7 - m.col : m.col;
        float inset = SQUARE_SIZE * 0.08f;
        g2.setColor(getArrowColor(m.green));
        g2.draw(new java.awt.geom.Ellipse2D.Float(dc * SQUARE_SIZE + inset, dr * SQUARE_SIZE + inset,
                SQUARE_SIZE - inset * 2, SQUARE_SIZE - inset * 2));
    }
}
// the best-move hint (under your own arrows), only on the live position
if (!viewingPast || analysisMode) {
    if (moveHintFade.prev != null) paintMoveHint(g2, moveHintFade.prev, moveHintFade.ease(moveHintFade.prevA));
    if (moveHintFade.cur != null && !animating) paintMoveHint(g2, moveHintFade.cur, moveHintFade.ease(moveHintFade.curA));
}
for (Arrow a : arrows) {
    drawArrow(g2, a.fromRow, a.fromCol, a.toRow, a.toCol, a.green);
}

g2.dispose();

        // Draw confetti particles (non-poop) on the board
        if (!particles.isEmpty()) {
            Graphics2D gc = (Graphics2D) g.create();
            gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            synchronized (particles) {
                for (Particle p : particles) {
                    if (p.isPoop) continue; // poop is drawn on the glass pane overlay
                    java.awt.geom.AffineTransform at = gc.getTransform();
                    gc.translate(p.x, p.y);
                    gc.rotate(p.angle);
                    gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, p.alpha));
                    gc.setColor(p.color);
                    if (p.isCircle) {
                        gc.fillOval(-p.size / 2, -p.size / 2, p.size, p.size);
                    } else {
                        gc.fillRect(-p.size / 2, -p.size / 4, p.size, p.size / 2);
                    }
                    gc.setTransform(at);
                }
            }
            gc.dispose();
        }

        drawUrielChooser((Graphics2D) g);
        drawSpellCastCardFx((Graphics2D) g);

        if (lockWash > 0.002f) {
            Graphics2D lockOverlay = (Graphics2D) g.create();
            lockOverlay.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f * lockWash));
            lockOverlay.setColor(Color.WHITE);
            lockOverlay.fillRect(0, 0, BOARD_SIZE, BOARD_SIZE);
            lockOverlay.dispose();
        }

        if (boardShakeX != 0 || boardShakeY != 0) {
            gRoot.translate(-boardShakeX, -boardShakeY);
        }
        gRoot.dispose();
    }

    // The badge cross-fades (with a small pop) whenever it changes square or class:
    // the old one fades out while the new one fades in.
    private static final long BADGE_IN_MS = 160, BADGE_OUT_MS = 120;
    private Review.MoveClass badgeCls = null, badgeOldCls = null;
    private int badgeRow = -1, badgeCol = -1, badgeOldRow = -1, badgeOldCol = -1;
    private long badgeAt = 0, badgeOldAt = 0;
    private Timer badgeFadeTimer;

    private void drawReviewBadge(Graphics2D g2) {
        Review.MoveClass cls = (lastMoveToRow < 0 || lastMoveToCol < 0) ? null : reviewBadgeClass;
        int row = cls == null ? -1 : lastMoveToRow, col = cls == null ? -1 : lastMoveToCol;
        long now = System.currentTimeMillis();
        if (cls != badgeCls || row != badgeRow || col != badgeCol) {
            badgeOldCls = badgeCls; badgeOldRow = badgeRow; badgeOldCol = badgeCol; badgeOldAt = now;
            badgeCls = cls; badgeRow = row; badgeCol = col; badgeAt = now;
            if (badgeFadeTimer == null) {
                badgeFadeTimer = new Timer(AnimationTiming.FRAME_DELAY_MS, e -> {
                    long t = System.currentTimeMillis();
                    repaint();
                    if (t - badgeAt > BADGE_IN_MS && t - badgeOldAt > BADGE_OUT_MS) {
                        ((Timer) e.getSource()).stop();
                    }
                });
            }
            badgeFadeTimer.restart();
        }
        float out = 1f - Math.min(1f, (now - badgeOldAt) / (float) BADGE_OUT_MS);
        if (badgeOldCls != null && out > 0f) paintBadge(g2, badgeOldCls, badgeOldRow, badgeOldCol, out, 1f);
        if (badgeCls != null) {
            float in = Math.min(1f, (now - badgeAt) / (float) BADGE_IN_MS);
            float ease = 1f - (1f - in) * (1f - in) * (1f - in);
            paintBadge(g2, badgeCls, badgeRow, badgeCol, ease, 0.7f + 0.3f * ease);
        }
    }

    private void paintBadge(Graphics2D g2, Review.MoveClass cls, int row, int col, float alpha, float scale) {
        BufferedImage img = reviewBadgeImages.get(cls);
        if (img == null || alpha <= 0f) return;
        int displayRow = boardFlipped ? (7 - row) : row;
        int displayCol = boardFlipped ? (7 - col) : col;
        int squareX = displayCol * SQUARE_SIZE;
        int squareY = displayRow * SQUARE_SIZE;
        int badgeSize = Math.max(18, Math.round(SQUARE_SIZE * 0.42f * scale));
        int radius = badgeSize / 2;
        // Keep edge-square badges fully inside the board instead of clipping the
        // top/left/right parts of their symbols at the board's outer bounds.
        int centerX = Math.max(radius, Math.min(BOARD_SIZE - radius, squareX + SQUARE_SIZE - 1));
        int centerY = Math.max(radius, Math.min(BOARD_SIZE - radius, squareY));
        Graphics2D b = (Graphics2D) g2.create();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
        b.drawImage(img, centerX - badgeSize / 2, centerY - badgeSize / 2, badgeSize, badgeSize, null);
        b.dispose();
    }

    private void drawCoordinates(Graphics g) {
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.setColor(new Color(100, 100, 100));
        
        // Draw file letters (a-h)
        for (int c = 0; c < 8; c++) {
            int displayCol = boardFlipped ? (7 - c) : c;
            char file = (char) ('a' + c);
            
            // Bottom right of each square
            g.drawString(String.valueOf(file), 
                displayCol * SQUARE_SIZE + SQUARE_SIZE - 12, 
                7 * SQUARE_SIZE + SQUARE_SIZE - 4);
        }
        
        // Draw rank numbers (1-8)
        for (int r = 0; r < 8; r++) {
            int displayRow = boardFlipped ? (7 - r) : r;
            int rank = 8 - r;
            
            // Top left of each square
            g.drawString(String.valueOf(rank), 
                4, 
                displayRow * SQUARE_SIZE + 14);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        clickedSelectedOnPress = false;
        // any click on the board ends the Tab piece picker (the mouse takes over)
        if (tabFocusedPieceRow != -1 || tabFocusedPieceCol != -1 || tabFocusedLegalMoveIndex != -1
                || tabSelectedSpellTargetRow != -1 || tabSelectedSpellTargetCol != -1) {
            tabFocusedPieceRow = -1;
            tabFocusedPieceCol = -1;
            tabFocusedLegalMoveIndex = -1;
            clearTabSelectedSpellTarget();
            repaint();
        }

        if (urielChooserActive) {
            if (SwingUtilities.isRightMouseButton(e)) {
                clearUrielChooser();
                repaint();
                return;
            }
            if (SwingUtilities.isLeftMouseButton(e) && urielChooserSelectedIndex < 0) {
                selectUrielChooserPiece(findUrielChooserCardAt(logicalMouseX(e), logicalMouseY(e)));
            }
            return;
        }

        // === LEFT CLICK CLEARS ALL MARKS ===
        if (SwingUtilities.isLeftMouseButton(e)) {
            arrows.clear();
            markedSquares.clear();
            drawingArrow = false;
            repaint();
        }

        // === RIGHT CLICK with premoves pending (or a premove piece in hand): cancel them ===
        if (SwingUtilities.isRightMouseButton(e) && (!premoveQueue.isEmpty() || isPremoveSelection())) {
            clearPremoves();
            dropSelection();
            drawingArrow = false;
            repaint();
            return;
        }

        // === RIGHT CLICK: start arrow & mark square ===
        if (SwingUtilities.isRightMouseButton(e)) {
            int col = mouseCol(e);
            int row = mouseRow(e);

            if (row < 0 || row > 7 || col < 0 || col > 7) return;

            if (boardFlipped) {
                row = 7 - row;
                col = 7 - col;
            }

            drawingArrow = true;
            arrowFromRow = row;
            arrowFromCol = col;
            arrowToRow = row;
            arrowToCol = col;

            repaint();
            return; // IMPORTANT: stop chess logic
        }

        // While the opponent's move is gliding in you can already start a premove;
        // every other animation still blocks input as before.
        boolean opponentAnimating = animating && animPiece != null && bomberCaptureFx == null
                && isPremoveModeActive() && !isPremoverPiece(animPiece);
        if (animating && !opponentAnimating) return;
        if (!inputEnabled) return;
        if (viewingPast && analysisMode) {
            branchFromHere();   // analysis: playing from an earlier move starts a new line
        } else if (viewingPast) {
            animateToState(getLatestStateIndex());
            viewingPast = false;
            return;
        }
        if (pendingEndermanPiecePick) return;
        if (pendingBomberRookPick) return;
        if (pendingSpellTargetingId != null || pendingUrielPlacement) return;
        if (isDuckPlacementAnimating()) return;

        int col = mouseCol(e);
        int row = mouseRow(e);
        if (row < 0 || row > 7 || col < 0 || col > 7) return;
        if (boardFlipped) { row = 7 - row; col = 7 - col; }

        final int r = row, c = col;

        if (pendingDuckPlacement) {
            pressX = logicalMouseX(e);
            pressY = logicalMouseY(e);
            duckPressedOnDuck = isDuckSquare(r, c);
            if (duckPressedOnDuck) {
                duckSelected = true;
                duckDragging = false;
                duckDragX = pressX;
                duckDragY = pressY;
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            } else {
                setCursor(Cursor.getDefaultCursor());
            }
            repaint();
            return;
        }

        clickedSelectedOnPress = (selectedPiece != null && selectedRow == r && selectedCol == c);

        // Record press origin for drag-threshold check
        pressX     = logicalMouseX(e);
        pressY     = logicalMouseY(e);
        wasDragged = false;
        dragging   = false;

        boolean isOwn = board[r][c] != null
                && board[r][c].isWhite() == whiteTurn
                && isLocalPlayerTurn()
                && isLocalPlayerPiece(board[r][c]);
        if (isOwn && endermanPhaseActive && whiteTurn == endermanPhaseSideWhite) {
            if (r == endermanPhaseRow && c == endermanPhaseCol) {
                isOwn = false;
            } else if (!isEndermanChangedSourceSquare(r, c)) {
                isOwn = false;
            }
        }
        // Premoves work on the "after my premoves" picture of the board.
        boolean premoveMode = isPremoveModeActive();
        Piece[][] premoveBoard = premoveMode ? premoveView() : board;
        boolean canPremoveFrom = premoveMode
                && premoveBoard[r][c] != null
                && isPremoverPiece(premoveBoard[r][c])
                && !(animating && r == animToRow && c == animToCol);   // about to be captured
        boolean holdingPremove = premoveMode && selectedPiece != null && isPremoverPiece(selectedPiece);
        if (holdingPremove && isPassAndPlay() && isOwn) {
            // two players: the side to move takes a piece -- the waiting side lets go of theirs
            dropSelection();
            holdingPremove = false;
        }

        // (two players: the side to move holding a piece and clicking an enemy piece is a capture)
        boolean holdingNormal = selectedPiece != null && !isPremoverPiece(selectedPiece);
        if (premoveMode && (!isPassAndPlay() || holdingPremove || (canPremoveFrom && !holdingNormal))) {
            if (selectedPiece == null) {
                if (canPremoveFrom) {
                    selectForPremove(premoveBoard, r, c);
                } else {
                    // a click on an empty square or an enemy piece cancels all premoves
                    clearPremoves();
                    dropSelection();
                    repaint();
                }
                return;
            }
            if (canPremoveFrom) {
                // another of your pieces: switch to it (dragging onto your own piece
                // is still a premove -- that is decided on release)
                selectForPremove(premoveBoard, r, c);
                return;
            }
            // empty or enemy square while holding a piece: the release decides
            draggedPiece = selectedPiece;
            dragX = pressX;
            dragY = pressY;
            return;
        }

        if (animating) return;     // only premoves may start while a piece is still moving
        canPremoveFrom = false;    // from here on it's an ordinary move by the side to move

        if (selectedPiece == null) {
            // --- nothing selected yet: select if own piece or start premove ---
            if (isOwn || canPremoveFrom) {
                selectedPiece = board[r][c];
                selectedRow   = r;
                selectedCol   = c;
                draggedPiece  = selectedPiece;
                tabPieceSelectionConfirmed = false;
                dragX = pressX;
                dragY = pressY;
                

                possibleMoves.clear();
                if (canPremoveFrom) {
                    possibleMoves.addAll(getPseudoMovesForPiece(r, c));
                } else {
                    for (int[] m : getActiveLegalMoves(whiteTurn))
                        if (m[0] == r && m[1] == c) possibleMoves.add(m);
                }
                repaint();
            }
            // In online/bot mode, don't allow selecting opponent pieces at all
            else if ((onlineMode || isBotGame) && board[r][c] != null) {
                // Clicked on opponent piece - do nothing
                return;
            }
            return;   // nothing more to do on first press
        }

        // --- something already selected ---
        // Chess960: with the king selected, its own castling rook is a move, not a new selection
        boolean castleOntoRook = false;
        if (isOwn && selectedPiece instanceof King && isCastle960(selectedRow, selectedCol, r, c)) {
            for (int[] m : possibleMoves) {
                if (m[0] == selectedRow && m[1] == selectedCol && m[2] == r && m[3] == c) { castleOntoRook = true; break; }
            }
        }
        if (castleOntoRook) {
            draggedPiece = selectedPiece;
            dragX = pressX;
            dragY = pressY;
            return;
        }
        if (isOwn || canPremoveFrom) {
            // pressed another own piece â†’ switch selection
            selectedPiece = board[r][c];
            selectedRow   = r;
            selectedCol   = c;
            draggedPiece  = selectedPiece;
            tabPieceSelectionConfirmed = false;
            dragX = pressX;
            dragY = pressY;
            

            possibleMoves.clear();
            if (canPremoveFrom) {
                possibleMoves.addAll(getPseudoMovesForPiece(r, c));
            } else {
                for (int[] m : getActiveLegalMoves(whiteTurn))
                    if (m[0] == r && m[1] == c) possibleMoves.add(m);
            }
            repaint();
            return;
        }
        
        // In online/bot mode, clicking opponent piece while piece selected should not grab it
        if ((onlineMode || isBotGame) && board[r][c] != null && 
            board[r][c].isWhite() != (isBotGame ? playerIsWhite : localIsWhite)) {
            // Attempting to grab opponent piece - treat as move attempt instead
            draggedPiece = selectedPiece;
            dragX = pressX;
            dragY = pressY;
            return;
        }

        // pressed on empty/enemy square while piece is selected â†’
        // keep draggedPiece ready (in case user drags instead of clicking).
        // Actual move attempt happens in mouseReleased.
        draggedPiece = selectedPiece;
        dragX = pressX;
        dragY = pressY;
    }

    private void blinkKingInCheck() {
        Timer blinkTimer = new Timer(150, null);
        blinkTimer.addActionListener(e -> {
            blinkVisible = !blinkVisible;
            blinkCount++;
            repaint();
            
            if (blinkCount >= 6) {
                blinkTimer.stop();
                blinkKingRow = -1;
                blinkKingCol = -1;
                blinkVisible = false;
                repaint();
            }
        });
        blinkTimer.start();
    }
    
    private void rotateCheckmatedKing(int kingRow, int kingCol) {
        rotatingKingRow = kingRow;
        rotatingKingCol = kingCol;
        kingRotating = true;
        kingRotationAngle = 0.0;
        
        final long durationMs = 500L;
        final long startedAtNanos = System.nanoTime();
        Timer rotateTimer = new Timer(AnimationTiming.FRAME_DELAY_MS, null);
        
        rotateTimer.addActionListener(e -> {
            double elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000.0;
            double progress = Math.min(1.0, elapsedMs / durationMs);
            
            // Use ease-out for natural falling motion
            double t = 1 - Math.pow(1 - progress, 3);
            kingRotationAngle = t * 90.0; // Rotate from 0 to 90 degrees
            
            repaint();
            
            if (progress >= 1.0) {
                rotateTimer.stop();
                kingRotating = false;
                // Keep the king at 90 degrees permanently
                kingRotationAngle = 90.0;
            }
        });
        rotateTimer.start();
    }
    
    private void drawRotatedKing(Graphics g, Piece king, int x, int y, double angleDegrees) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        
        // Calculate rotation point (center of the piece)
        int centerX = x + SQUARE_SIZE / 2;
        int centerY = y + SQUARE_SIZE / 2;
        
        // Rotate around the center
        g2d.rotate(Math.toRadians(angleDegrees), centerX, centerY);
        
        // Draw the king at its position
        king.draw(g2d, x, y);
        
        g2d.dispose();
    }

    private void vibrateIllegalMove() {
        final long startedAtNanos = System.nanoTime();
        final long durationMs = 300L;
        Timer vibrateTimer = new Timer(AnimationTiming.FRAME_DELAY_MS, null);
        vibrateTimer.addActionListener(e -> {
            long elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
            vibrateCount = Math.min(10, (int) (elapsedMs / 30L) + 1);
            
            if (vibrateCount % 2 == 0) {
                vibrateOffsetX = 5;
            } else {
                vibrateOffsetX = -5;
            }
            
            repaint();
            
            if (elapsedMs >= durationMs) {
                vibrateTimer.stop();
                vibrateRow = -1;
                vibrateCol = -1;
                vibratePiece = null;
                vibrateOffsetX = 0;
                vibrateOffsetY = 0;
                repaint();
            }
        });
        vibrateTimer.start();
    }

    // Easing function for smooth animation (ease-in-out cubic)
    private double easeInOutCubic(double t) {
        if (t < 0.5) {
            return 4 * t * t * t;
        } else {
            double f = (2 * t) - 2;
            return 0.5 * f * f * f + 1;
        }
    }
    
    // Easing function for bouncy effect (ease-out-back)
    private double easeOutBack(double t) {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }
    
    private void animateMove(Piece p, int sr, int sc, int tr, int tc, boolean isCastling, Runnable done) {
        animateMove(p, sr, sc, tr, tc, isCastling, done, false);
    }
    
    private void animateMove(Piece p, int sr, int sc, int tr, int tc, boolean isCastling, Runnable done, boolean forceAnimate) {
        if (!animationsEnabled && !forceAnimate) {
            // Skip animation, just run the completion callback
            done.run();
            return;
        }
        
        // Store source and destination for visual effects
        animFromRow = sr;
        animFromCol = sc;
        animToRow = tr;
        animToCol = tc;
        
        // Keep history navigation quick; let user settings control live move speed.
        final int steps = forceAnimate ? 7 : moveAnimationSteps;
        final int delay = forceAnimate ? 7 : moveAnimationDelayMs;
        final long durationNanos = (overrideAnimMs > 0 && !forceAnimate)
                ? overrideAnimMs * 1_000_000L
                : Math.max(1L, (long) steps * delay) * 1_000_000L;
        
        final int displaySr = boardFlipped ? (7 - sr) : sr;
        final int displaySc = boardFlipped ? (7 - sc) : sc;
        final int displayTr = boardFlipped ? (7 - tr) : tr;
        final int displayTc = boardFlipped ? (7 - tc) : tc;
        
        int sx = displaySc * SQUARE_SIZE, sy = displaySr * SQUARE_SIZE;
        int ex = displayTc * SQUARE_SIZE, ey = displayTr * SQUARE_SIZE;
        
        int rookSx = 0, rookEx = 0;
        if (isCastling) {
            int rookCol = (tc == 6) ? 7 : 0;
            int newRookCol = (tc == 6) ? 5 : 3;
            
            int displayRookCol = boardFlipped ? (7 - rookCol) : rookCol;
            int displayNewRookCol = boardFlipped ? (7 - newRookCol) : newRookCol;
            
            rookSx = displayRookCol * SQUARE_SIZE;
            rookEx = displayNewRookCol * SQUARE_SIZE;
        }
        
        final int finalRookSx = rookSx;
        final int finalRookEx = rookEx;

        Timer timer = new Timer(AnimationTiming.FRAME_DELAY_MS, null);
        final long startedAtNanos = System.nanoTime();

        timer.addActionListener(e -> {
            double linearT = Math.min(
                    1.0,
                    (System.nanoTime() - startedAtNanos) / (double) durationNanos);
            
            // Apply easing function for smooth acceleration/deceleration
            double t = easeInOutCubic(linearT);
            
            // Store animation progress for visual effects
            animProgress = linearT;
            
            animX = (int) (sx + t * (ex - sx));
            animY = (int) (sy + t * (ey - sy));
            
            if (isCastling) {
                animRookX = (int) (finalRookSx + t * (finalRookEx - finalRookSx));
                animRookY = (int) (displaySr * SQUARE_SIZE);
            }
            
            repaint();

            if (linearT >= 1.0) {
                timer.stop();
                animProgress = 0.0; // Reset progress
                animFromRow = animFromCol = animToRow = animToCol = -1; // Reset animation squares
                done.run();
            }
        });
        timer.start();
    }

    @Override 
    public void mouseReleased(MouseEvent e) {
        if (urielChooserActive) return;

        // === RIGHT RELEASE: finalize arrow or toggle red square ===
        if (SwingUtilities.isRightMouseButton(e) && drawingArrow) {
            int col = mouseCol(e);
            int row = mouseRow(e);

            // Handle out of bounds
            if (row < 0 || row > 7 || col < 0 || col > 7) {
                drawingArrow = false;
                arrowFromRow = arrowFromCol = arrowToRow = arrowToCol = -1;
                repaint();
                return;
            }

            if (boardFlipped) {
                row = 7 - row;
                col = 7 - col;
            }

            // Check if this is a drag (different square) or just a click (same square)
            if (arrowFromRow == row && arrowFromCol == col) {
                // Just a click - toggle red square only
                boolean removed = false;
                for (int i = 0; i < markedSquares.size(); i++) {
                    MarkedSquare ms = markedSquares.get(i);
                    if (ms.row == row && ms.col == col) {
                        markedSquares.remove(i);
                        removed = true;
                        break;
                    }
                }
                if (!removed) {
                    MarkedSquare ms = new MarkedSquare(row, col);
                    ms.green = e.isControlDown();
                    markedSquares.add(ms);
                }
            } else {
                // This is a drag - toggle same arrow (same from/to)
                boolean greenArrow = e.isControlDown();
                boolean removed = false;
                for (int i = 0; i < arrows.size(); i++) {
                    Arrow a = arrows.get(i);
                    if (a.fromRow == arrowFromRow && a.fromCol == arrowFromCol
                            && a.toRow == row && a.toCol == col
                            && a.green == greenArrow) {
                        arrows.remove(i);
                        removed = true;
                        break;
                    }
                }
                if (!removed) {
                    arrows.add(new Arrow(
                        arrowFromRow,
                        arrowFromCol,
                        row,
                        col,
                        greenArrow
                    ));
                }
            }

            drawingArrow = false;
            arrowFromRow = arrowFromCol = arrowToRow = arrowToCol = -1;
            repaint();
            return;
        }
        if (SwingUtilities.isRightMouseButton(e)) return;

        if (!inputEnabled) {
            selectedPiece = null; dragging = false; draggedPiece = null;
            wasDragged = false; possibleMoves.clear(); repaint();
            return;
        }
        if (viewingPast && analysisMode) {
            branchFromHere();
        } else if (viewingPast) {
            animateToState(getLatestStateIndex());
            viewingPast = false;
            selectedPiece = null; dragging = false; draggedPiece = null;
            wasDragged = false; possibleMoves.clear(); repaint();
            return;
        }
        if (pendingEndermanPiecePick) {
            int col = mouseCol(e);
            int row = mouseRow(e);
            if (row < 0 || row > 7 || col < 0 || col > 7) {
                repaint();
                return;
            }
            if (boardFlipped) { row = 7 - row; col = 7 - col; }
            Piece p = board[row][col];
            if (p == null) {
                repaint();
                return;
            }
            boolean endermanCasterWhite = pendingEndermanSideWhite;
            activateEndermanPhase(endermanCasterWhite, row, col);
            if (gameController != null) {
                gameController.onLocalSpellPhase(SPELL_PHASE_ENDERMAN, endermanCasterWhite, row, col);
            }
            repaint();
            return;
        }
        if (pendingBomberRookPick) {
            int col = mouseCol(e);
            int row = mouseRow(e);
            if (row < 0 || row > 7 || col < 0 || col > 7) {
                clearBomberState(true);
                repaint();
                return;
            }
            if (boardFlipped) { row = 7 - row; col = 7 - col; }
            Piece p = board[row][col];
            boolean validRook = p instanceof Rook
                    && p.isWhite() == pendingBomberSideWhite
                    && p.isWhite() == whiteTurn
                    && p.getFrozenTurnsRemaining() <= 0
                    && p.getResurrectCooldownTurnsRemaining() <= 0;
            if (!validRook) {
                clearBomberState(true);
                repaint();
                return;
            }
            pendingBomberRookPick = false;
            bomberPrimedRookRow = row;
            bomberPrimedRookCol = col;
            bomberPrimedSideWhite = p.isWhite();
            if (gameController != null) {
                gameController.onLocalSpellPhase(SPELL_PHASE_BOMBER_PRIME, bomberPrimedSideWhite, row, col);
            }
            selectedPiece = p;
            selectedRow = row;
            selectedCol = col;
            draggedPiece = p;
            possibleMoves.clear();
            for (int[] m : getActiveLegalMoves(whiteTurn)) {
                if (m[0] == row && m[1] == col) possibleMoves.add(m);
            }
            if (gameController != null) {
                gameController.cancelPendingSpellSelection(bomberPrimedSideWhite, SpellManager.BOMBER);
            }
            repaint();
            return;
        }
        if (pendingSpellTargetingId != null) {
            int col = mouseCol(e);
            int row = mouseRow(e);
            if (row < 0 || row > 7 || col < 0 || col > 7) {
                repaint();
                return;
            }
            if (boardFlipped) { row = 7 - row; col = 7 - col; }
            if (tryResolvePendingSpellTargeting(row, col)) return;
        }
        if (pendingDuckPlacement) {
            int col = mouseCol(e);
            int row = mouseRow(e);
            if (row < 0 || row > 7 || col < 0 || col > 7) {
                duckDragging = false;
                duckPressedOnDuck = false;
                wasDragged = false;
                setCursor(Cursor.getDefaultCursor());
                repaint();
                return;
            }
            if (boardFlipped) { row = 7 - row; col = 7 - col; }

            boolean isCurrentDuckSquare = isDuckSquare(row, col);
            if (duckPressedOnDuck && !wasDragged && isCurrentDuckSquare) {
                duckSelected = !duckSelected;
                duckDragging = false;
                duckPressedOnDuck = false;
                setCursor(Cursor.getDefaultCursor());
                repaint();
                return;
            }

            boolean shouldPlace = board[row][col] == null && !isCurrentDuckSquare;
            if (shouldPlace && (duckSelected || duckPressedOnDuck || !wasDragged)) {
                completeDuckPlacementAndEndTurn(row, col);
            } else {
                repaint();
            }
            duckDragging = false;
            duckPressedOnDuck = false;
            wasDragged = false;
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        if (pendingUrielPlacement) {
            int col = mouseCol(e);
            int row = mouseRow(e);
            if (row < 0 || row > 7 || col < 0 || col > 7) {
                repaint();
                return;
            }
            if (boardFlipped) { row = 7 - row; col = 7 - col; }
            tryResolvePendingUrielPlacement(row, col);
            return;
        }
        if (selectedPiece == null) { dragging = false; draggedPiece = null; wasDragged = false; return; }

        // stop ghost
        dragging     = false;
        draggedPiece = null;

        int col = mouseCol(e);
        int row = mouseRow(e);

        // --- released outside the board ---
        if (row < 0 || row > 7 || col < 0 || col > 7) {
            if (wasDragged) {
                // drag that went off-board â†’ cancel move, deselect
                selectedPiece = null; possibleMoves.clear();
            }
            // pure click off-board while piece selected â†’ keep selection
            wasDragged = false;
            setCursor(Cursor.getDefaultCursor());
            repaint();
            return;
        }

        if (boardFlipped) { row = 7 - row; col = 7 - col; }

        // --- holding a piece while it's the opponent's turn: this is a premove ---
        if (isPremoveSelection()) {
            if (row == selectedRow && col == selectedCol) {
                if (!wasDragged && clickedSelectedOnPress) dropSelection();
                wasDragged = false;
                clickedSelectedOnPress = false;
                setCursor(Cursor.getDefaultCursor());
                repaint();
                return;
            }
            queuePremoveFromSelection(row, col);
            return;
        }
        // (a premove-picked piece whose turn has since come: only keep it if it's really there)
        if (selectedPiece != null && board[selectedRow][selectedCol] != selectedPiece) {
            dropSelection();
            repaint();
            return;
        }
        final int toRow = row, toCol = castleDropTarget(selectedRow, selectedCol, row, col);

        // --- released on the same square as the selected piece ---
        if (toRow == selectedRow && toCol == selectedCol) {
            if (!wasDragged) {
                // Keep first single-click selection; only toggle off if the press
                // started on the currently-selected square.
                if (clickedSelectedOnPress) {
                    selectedPiece = null;
                    possibleMoves.clear();
                }
            }
            // if it WAS dragged but landed back â†’ keep selection (no deselect)
            wasDragged = false;
            clickedSelectedOnPress = false;
            
            repaint();
            return;
        }

        // --- check legality ---
        boolean moveLegal = false;
        for (int[] m : getActiveLegalMoves(whiteTurn)) {
            if (m[0] == selectedRow && m[1] == selectedCol && m[2] == toRow && m[3] == toCol) {
                moveLegal = true; break;
            }
        }

        if (!moveLegal) {
            Piece illegalTarget = board[toRow][toCol];
            boolean triedShieldCapture = illegalTarget != null
                    && illegalTarget.isWhite() != whiteTurn
                    && illegalTarget.getShieldedTurnsRemaining() > 0;
            if (!triedShieldCapture && selectedPiece instanceof Pawn && board[toRow][toCol] == null
                    && Math.abs(toCol - selectedCol) == 1 && toRow - selectedRow == (whiteTurn ? -1 : 1)) {
                Piece epTarget = board[selectedRow][toCol];
                triedShieldCapture = epTarget != null
                        && epTarget.isWhite() != whiteTurn
                        && epTarget.getShieldedTurnsRemaining() > 0;
            }
            if (triedShieldCapture) {
                SoundManager.playExtraSound("shield_block.wav");
            }
            // illegal target: feedback
            IsLegal legalEval = new IsLegal(board);
            if (!fogOfWarEnabled && !isDuckChessMode() && legalEval.isInCheck(whiteTurn)) {
                for (int r2 = 0; r2 < 8; r2++)
                    for (int c2 = 0; c2 < 8; c2++)
                        if (board[r2][c2] instanceof King && board[r2][c2].isWhite() == whiteTurn) {
                            blinkKingRow = r2; blinkKingCol = c2; blinkCount = 0;
                            blinkKingInCheck(); break;
                        }
            }
            vibrateRow = selectedRow;
            vibrateCol = selectedCol;
            vibratePiece = board[selectedRow][selectedCol];
            vibrateCount = 0;
            vibrateIllegalMove();
            selectedPiece = null; possibleMoves.clear();
            wasDragged = false;
            setCursor(Cursor.getDefaultCursor());
            repaint();
            return;
        }

        // Settings > Gameplay > Confirm moves (games against a bot or online)
        if ((isBotGame || onlineMode) && AppSettings.confirmMoves()) {
            dragging = false;
            draggedPiece = null;
            repaint();
            String move = (char) ('a' + selectedCol) + "" + (8 - selectedRow)
                    + " \u2192 " + (char) ('a' + toCol) + (8 - toRow);
            if (!GameDialogs.confirm(this, "Confirm move", "Play " + move + "?", "Play", "Cancel", false)) {
                selectedPiece = null;
                possibleMoves.clear();
                wasDragged = false;
                setCursor(Cursor.getDefaultCursor());
                repaint();
                return;
            }
        }

        // ====== LEGAL MOVE ======
        int fromRow = selectedRow, fromCol = selectedCol;
        Piece movePiece = board[fromRow][fromCol];
        if (endermanPhaseActive && whiteTurn == endermanPhaseSideWhite) {
            endermanConsumePending = true;
            endermanConsumeSideWhite = whiteTurn;
        }
        boolean bomberMove = movePiece instanceof Rook && isBomberPrimedForSquare(fromRow, fromCol, whiteTurn);
        if (bomberMove) {
            Piece dest = board[toRow][toCol];
            boolean capture = dest != null && dest.isWhite() != whiteTurn
                    && !(dest instanceof King)
                    && dest.getShieldedTurnsRemaining() <= 0;
            if (capture) {
                movePiece.setBombRookTurnsRemaining(Math.max(1, movePiece.getBombRookTurnsRemaining()));
                bomberConsumePending = true;
                bomberConsumeSideWhite = whiteTurn;
            } else {
                movePiece.setBombRookTurnsRemaining(0);
                bomberConsumePending = false;
                bomberConsumeSideWhite = false;
            }
            bomberPrimedRookRow = -1;
            bomberPrimedRookCol = -1;
            bomberPrimedSideWhite = false;
        } else if (bomberPrimedRookRow != -1) {
            clearBomberState(false);
        }
        boolean endermanTeleportMove = endermanPhaseActive && whiteTurn == endermanPhaseSideWhite;
        boolean draggedMove = wasDragged;
        boolean animate = animationsEnabled && !endermanTeleportMove && !draggedMove;
        wasDragged = false;
        selectedPiece = null;
        possibleMoves.clear();

        if (endermanTeleportMove) {
            queueEndermanTeleportAfterCard(fromRow, fromCol, toRow, toCol);
        } else if (animate) {
            executeAnimated(fromRow, fromCol, toRow, toCol);
        } else {
            executeInstant(fromRow, fromCol, toRow, toCol);
        }
    }
    
    // â”€â”€â”€ instant (drag-drop) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void executeInstant(int fromRow, int fromCol, int toRow, int toCol) {
        if (isCastle960(fromRow, fromCol, toRow, toCol)) { executeCastle960(fromRow, fromCol, toCol); return; }
        Piece movingPiece = board[fromRow][fromCol];
        if (!completingBomberCapture
                && shouldAnimateBomberCapture(movingPiece, toRow, toCol)) {
            startBomberCaptureAnimation(movingPiece, fromRow, fromCol, toRow, toCol);
            return;
        }
        SpellSnapshot spellSnapshot = isSpellChessMode() ? createSpellSnapshot() : null;

        Piece[][] boardBeforeMove = new Piece[8][8];
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) boardBeforeMove[r][c] = board[r][c];

        Piece capturedPiece = board[toRow][toCol];

        // en passant
        boolean isEnPassant = false;
        if (movingPiece instanceof Pawn && board[toRow][toCol] == null && Math.abs(toCol - fromCol) == 1) {
            capturedPiece = board[fromRow][toCol];
            board[fromRow][toCol] = null;
            isEnPassant = true;
        }

        // castling â€“ move rook instantly
        boolean isCastling = movingPiece instanceof King && Math.abs(toCol - fromCol) == 2;
        if (isCastling) {
            int rookCol = (toCol == 6) ? 7 : 0;
            int newRookCol = (toCol == 6) ? 5 : 3;
            board[fromRow][newRookCol] = board[fromRow][rookCol];
            board[fromRow][rookCol] = null;
            if (board[fromRow][newRookCol] instanceof Rook)
                ((Rook) board[fromRow][newRookCol]).setMoved(true);
        }

        board[fromRow][fromCol] = null;
        board[toRow][toCol]     = movingPiece;

        if (movingPiece instanceof King) ((King) movingPiece).setMoved(true);
        if (movingPiece instanceof Rook) ((Rook) movingPiece).setMoved(true);
        maybeBreakShieldOnMove(movingPiece);

        boolean bombTriggered = isSpellChessMode() && movingPiece instanceof Rook
                && capturedPiece != null && movingPiece.getBombRookTurnsRemaining() > 0;
        if (!applyBombExplosionIfNeeded(movingPiece, capturedPiece, toRow, toCol)) {
            for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) board[r][c] = boardBeforeMove[r][c];
            if (spellSnapshot != null) restoreSpellSnapshot(spellSnapshot);
            bomberConsumePending = false;
            bomberConsumeSideWhite = false;
            endermanConsumePending = false;
            endermanConsumeSideWhite = false;
            setCursor(Cursor.getDefaultCursor());
            repaint();
            return;
        }
        if (!applyAtomicExplosionIfNeeded(movingPiece, capturedPiece, toRow, toCol)) {
            for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) board[r][c] = boardBeforeMove[r][c];
            if (spellSnapshot != null) restoreSpellSnapshot(spellSnapshot);
            bomberConsumePending = false;
            bomberConsumeSideWhite = false;
            endermanConsumePending = false;
            endermanConsumeSideWhite = false;
            setCursor(Cursor.getDefaultCursor());
            repaint();
            return;
        }
        if (bombTriggered) capturedPiece = null;

        String promotionSymbol = handlePromotion(movingPiece, fromRow, fromCol, toRow, toCol);

        commitMove(movingPiece, fromRow, fromCol, toRow, toCol, boardBeforeMove,
                   capturedPiece, isCastling, isEnPassant, promotionSymbol);

        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    // â”€â”€â”€ animated (click-click) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void executeAnimated(int fromRow, int fromCol, int toRow, int toCol) {
        if (isCastle960(fromRow, fromCol, toRow, toCol)) { executeCastle960(fromRow, fromCol, toCol); return; }
        Piece movingPiece = board[fromRow][fromCol];
        if (!completingBomberCapture
                && shouldAnimateBomberCapture(movingPiece, toRow, toCol)) {
            startBomberCaptureAnimation(movingPiece, fromRow, fromCol, toRow, toCol);
            return;
        }
        SpellSnapshot spellSnapshot = isSpellChessMode() ? createSpellSnapshot() : null;

        Piece[][] boardBeforeMove = new Piece[8][8];
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) boardBeforeMove[r][c] = board[r][c];

        Piece capturedPiece = board[toRow][toCol];

        // en passant
        boolean isEnPassant = false;
        if (movingPiece instanceof Pawn && board[toRow][toCol] == null && Math.abs(toCol - fromCol) == 1) {
            capturedPiece = board[fromRow][toCol];
            board[fromRow][toCol] = null;
            isEnPassant = true;
        }

        boolean isCastling = movingPiece instanceof King && Math.abs(toCol - fromCol) == 2;

        animPiece = movingPiece;
        animating = true;
        board[fromRow][fromCol] = null;

        if (isCastling) {
            int rookCol = (toCol == 6) ? 7 : 0;
            animRook = board[fromRow][rookCol];
            board[fromRow][rookCol] = null;
        }

        final Piece[] capturedHolder = new Piece[]{capturedPiece};
        final boolean fEnPassant   = isEnPassant;
        final boolean fCastling    = isCastling;

        animateMove(movingPiece, fromRow, fromCol, toRow, toCol, isCastling, () -> {
            board[toRow][toCol] = movingPiece;

            if (fCastling) {
                int newRookCol = (toCol == 6) ? 5 : 3;
                board[fromRow][newRookCol] = animRook;
                ((Rook) animRook).setMoved(true);
                animRook = null;
            }

            if (movingPiece instanceof King) ((King) movingPiece).setMoved(true);
            if (movingPiece instanceof Rook) ((Rook) movingPiece).setMoved(true);
            maybeBreakShieldOnMove(movingPiece);

            boolean bombTriggered = isSpellChessMode() && movingPiece instanceof Rook
                    && capturedHolder[0] != null && movingPiece.getBombRookTurnsRemaining() > 0;
            if (!applyBombExplosionIfNeeded(movingPiece, capturedHolder[0], toRow, toCol)) {
                for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) board[r][c] = boardBeforeMove[r][c];
                if (spellSnapshot != null) restoreSpellSnapshot(spellSnapshot);
                bomberConsumePending = false;
                bomberConsumeSideWhite = false;
                endermanConsumePending = false;
                endermanConsumeSideWhite = false;
                animating = false;
                animPiece = null;
                repaint();
                return;
            }
            if (!applyAtomicExplosionIfNeeded(movingPiece, capturedHolder[0], toRow, toCol)) {
                for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) board[r][c] = boardBeforeMove[r][c];
                if (spellSnapshot != null) restoreSpellSnapshot(spellSnapshot);
                bomberConsumePending = false;
                bomberConsumeSideWhite = false;
                endermanConsumePending = false;
                endermanConsumeSideWhite = false;
                animating = false;
                animPiece = null;
                repaint();
                return;
            }
            if (bombTriggered) capturedHolder[0] = null;

            String promotionSymbol = handlePromotion(movingPiece, fromRow, fromCol, toRow, toCol);

            animating = false;
            animPiece = null;

            commitMove(movingPiece, fromRow, fromCol, toRow, toCol, boardBeforeMove,
                       capturedHolder[0], fCastling, fEnPassant, promotionSymbol);
            repaint();
        });
    }

    // â”€â”€â”€ shared helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private boolean shouldAnimateBomberCapture(Piece movingPiece, int toRow, int toCol) {
        if (!isSpellChessMode() || !(movingPiece instanceof Rook)) return false;
        if (movingPiece.getBombRookTurnsRemaining() <= 0) return false;
        Piece target = inBounds(toRow, toCol) ? board[toRow][toCol] : null;
        return target != null && target.isWhite() != movingPiece.isWhite()
                && !(target instanceof King) && target.getShieldedTurnsRemaining() <= 0;
    }

    private void startBomberCaptureAnimation(
            Piece rook,
            int fromRow,
            int fromCol,
            int toRow,
            int toCol) {
        if (bomberCaptureTimer != null) bomberCaptureTimer.stop();
        BomberCaptureFx fx = new BomberCaptureFx();
        fx.rook = rook;
        fx.fromRow = fromRow;
        fx.fromCol = fromCol;
        fx.toRow = toRow;
        fx.toCol = toCol;
        fx.startMs = System.currentTimeMillis();
        bomberCaptureFx = fx;
        animating = true;

        bomberCaptureTimer = new Timer(AnimationTiming.FRAME_DELAY_MS, e -> {
            long elapsed = System.currentTimeMillis() - fx.startMs;
            if (elapsed < BOMBER_CHARGE_MS) {
                repaint();
                return;
            }
            fx.rushing = true;
            fx.rushProgress = Math.min(1f,
                    (elapsed - BOMBER_CHARGE_MS) / (float) BOMBER_RUSH_MS);
            repaint();
            if (fx.rushProgress < 1f) return;

            ((Timer) e.getSource()).stop();
            bomberCaptureTimer = null;
            completingBomberCapture = true;
            bomberCaptureFx = null;
            animating = false;
            executeInstant(fromRow, fromCol, toRow, toCol);
            completingBomberCapture = false;
        });
        bomberCaptureTimer.setCoalesce(true);
        bomberCaptureTimer.start();
        repaint();
    }

    private boolean isBomberCaptureSourcePiece(Piece piece, int row, int col) {
        BomberCaptureFx fx = bomberCaptureFx;
        return fx != null && fx.rook == piece && fx.fromRow == row && fx.fromCol == col;
    }

    private void drawBomberCaptureFx(Graphics2D source) {
        BomberCaptureFx fx = bomberCaptureFx;
        if (fx == null || fx.rook == null) return;

        long elapsed = System.currentTimeMillis() - fx.startMs;
        float chargeT = Math.max(0f, Math.min(1f, elapsed / (float) BOMBER_CHARGE_MS));
        float glowT = chargeT * chargeT * (3f - 2f * chargeT);
        float moveT = fx.rushing ? fx.rushProgress : 0f;

        int sourceRow = boardFlipped ? 7 - fx.fromRow : fx.fromRow;
        int sourceCol = boardFlipped ? 7 - fx.fromCol : fx.fromCol;
        int targetRow = boardFlipped ? 7 - fx.toRow : fx.toRow;
        int targetCol = boardFlipped ? 7 - fx.toCol : fx.toCol;
        float x = (sourceCol + (targetCol - sourceCol) * moveT) * SQUARE_SIZE;
        float y = (sourceRow + (targetRow - sourceRow) * moveT) * SQUARE_SIZE;

        Graphics2D g = (Graphics2D) source.create();
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(0, 0, 0, Math.round(168f * glowT)));
        g.fillRect(0, 0, SQUARE_SIZE * 8, SQUARE_SIZE * 8);

        BufferedImage rookImage = new BufferedImage(
                SQUARE_SIZE, SQUARE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pieceGraphics = rookImage.createGraphics();
        fx.rook.draw(pieceGraphics, 0, 0);
        pieceGraphics.dispose();

        RescaleOp whiteTint = new RescaleOp(
                new float[]{0f, 0f, 0f, 1f},
                new float[]{255f, 255f, 255f, 0f},
                null);
        float scale = 1f + (BOMBER_ROOK_MAX_SCALE - 1f) * glowT;
        int drawSize = Math.max(1, Math.round(SQUARE_SIZE * scale));
        int drawX = Math.round(x + (SQUARE_SIZE - drawSize) / 2f);
        int drawY = Math.round(y + (SQUARE_SIZE - drawSize) / 2f);

        if (glowT > 0.02f) {
            int halo = Math.max(4, Math.round(SQUARE_SIZE * (0.10f + 0.12f * glowT)));
            g.setColor(new Color(255, 255, 255, Math.round(105f * glowT)));
            g.fillOval(drawX - halo, drawY - halo, drawSize + halo * 2, drawSize + halo * 2);
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        Graphics2D whiteRook = (Graphics2D) g.create();
        whiteRook.translate(x + SQUARE_SIZE / 2f, y + SQUARE_SIZE / 2f);
        whiteRook.scale(scale, scale);
        whiteRook.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glowT));
        whiteRook.drawImage(
                rookImage,
                whiteTint,
                -SQUARE_SIZE / 2,
                -SQUARE_SIZE / 2);
        whiteRook.dispose();
        if (glowT < 1f) {
            Graphics2D normalRook = (Graphics2D) g.create();
            normalRook.translate(x + SQUARE_SIZE / 2f, y + SQUARE_SIZE / 2f);
            normalRook.scale(scale, scale);
            normalRook.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, 1f - glowT));
            normalRook.drawImage(
                    rookImage,
                    -SQUARE_SIZE / 2,
                    -SQUARE_SIZE / 2,
                    null);
            normalRook.dispose();
        }
        g.dispose();
    }

    private void maybeBreakShieldOnMove(Piece movingPiece) {
        if (!isSpellChessMode()) return;
        if (!spellShieldBreaksOnMove) return;
        if (movingPiece != null && movingPiece.getShieldedTurnsRemaining() > 0) {
            int[] pos = findPiecePosition(movingPiece);
            if (pos != null) onShieldRemoved(movingPiece, pos[0], pos[1]);
            movingPiece.setShieldedTurnsRemaining(0);
        }
    }

    private boolean applyBombExplosionIfNeeded(Piece movingPiece, Piece capturedPiece, int captureRow, int captureCol) {
        if (!isSpellChessMode()) return true;
        if (!(movingPiece instanceof Rook)) return true;
        if (capturedPiece == null) return true;
        if (movingPiece.getBombRookTurnsRemaining() <= 0) return true;

        addCapturedPieceToOwner(capturedPiece.isWhite(), capturedPiece);
        movingPiece.setBombRookTurnsRemaining(0);

        for (int r = captureRow - 1; r <= captureRow + 1; r++) {
            for (int c = captureCol - 1; c <= captureCol + 1; c++) {
                if (!inBounds(r, c)) continue;
                Piece p = board[r][c];
                if (p == null || p instanceof King) continue;
                addCapturedPieceToOwner(p.isWhite(), p);
                board[r][c] = null;
            }
        }
        boolean valid = !isKingInCheckFor(movingPiece.isWhite());
        if (valid) startBomberCloudFx(captureRow, captureCol);
        return valid;
    }

    private boolean applyAtomicExplosionIfNeeded(Piece movingPiece, Piece capturedPiece, int captureRow, int captureCol) {
        if (!isAtomicMode()) return true;
        if (capturedPiece == null) return true;

        startAtomicExplosionFx(captureRow, captureCol);
        SoundManager.playExtraSound("explosion.mp3");
        applyAtomicExplosionAt(captureRow, captureCol);
        return findKing(movingPiece.isWhite()) != null;
    }

    private void applyAtomicExplosionAt(int centerRow, int centerCol) {
        for (int r = centerRow - 1; r <= centerRow + 1; r++) {
            for (int c = centerCol - 1; c <= centerCol + 1; c++) {
                if (!inBounds(r, c)) continue;
                Piece p = board[r][c];
                if (p == null) continue;

                boolean isCenter = (r == centerRow && c == centerCol);
                if (!isCenter && p instanceof Pawn) continue;
                board[r][c] = null;
            }
        }
    }

    /** Handles pawn promotion dialog; returns the symbol string (or ""). */
    private String handlePromotion(Piece movingPiece, int fromRow, int fromCol, int toRow, int toCol) {
        if (!(movingPiece instanceof Pawn)) return "";
        ((Pawn) movingPiece).onMove(fromRow, fromCol, toRow, toCol);
        if ((movingPiece.isWhite() && toRow == 0) || (!movingPiece.isWhite() && toRow == 7)) {
            if (premovePromotionSymbol != null) {          // a premove: chosen when it was queued
                String sym = premovePromotionSymbol;
                premovePromotionSymbol = null;
                board[toRow][toCol] = promotionPiece(sym, movingPiece.isWhite());
                return board[toRow][toCol] instanceof Queen ? "Q" : sym;
            }
            if (autoQueenEnabled) {
                board[toRow][toCol] = new Queen(movingPiece.isWhite());
                return "Q";
            }
            String symbol = showPromotionPicker(movingPiece.isWhite(), toRow, toCol);
            if (symbol == null || symbol.isEmpty()) symbol = "Q";
            switch (symbol) {
                case "R": board[toRow][toCol] = new Rook(movingPiece.isWhite());   return "R";
                case "B": board[toRow][toCol] = new Bishop(movingPiece.isWhite()); return "B";
                case "N": board[toRow][toCol] = new Knight(movingPiece.isWhite()); return "N";
                default:  board[toRow][toCol] = new Queen(movingPiece.isWhite());  return "Q";
            }
        }
        return "";
    }

    private String showPromotionPicker(boolean whitePiece, int promotionRow, int promotionCol) {
        final String[] picked = { "Q" };
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Pawn Promotion", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.getRootPane().putClientProperty(PopupSound.SILENT, Boolean.TRUE);   // part of the move, not a popup
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(Color.BLACK, 1, false));

        JPanel holder = new JPanel(new GridLayout(4, 1, 0, 0));
        holder.setBackground(new Color(229, 229, 229));
        int displayRow = boardFlipped ? (7 - promotionRow) : promotionRow;
        int displayCol = boardFlipped ? (7 - promotionCol) : promotionCol;
        // the board is drawn scaled to fit its space: the picker matches the real squares
        double scale = renderScale();
        int tile = Math.max(40, (int) Math.round(SQUARE_SIZE * scale));
        int squareX = (int) Math.round(displayCol * SQUARE_SIZE * scale);
        int squareY = (int) Math.round(displayRow * SQUARE_SIZE * scale);
        Point screenPoint = getLocationOnScreen();
        int panelHeight = tile * 4;
        Rectangle screenBounds = getGraphicsConfiguration().getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        int screenTop = screenBounds.y + insets.top;
        int screenBottom = screenBounds.y + screenBounds.height - insets.bottom;
        int squareScreenY = screenPoint.y + squareY;
        boolean openDown = (squareScreenY + panelHeight <= screenBottom);

        if (openDown) {
            holder.add(createPromotionOptionButton(new Queen(whitePiece), "Q", dialog, picked, tile));
            holder.add(createPromotionOptionButton(new Knight(whitePiece), "N", dialog, picked, tile));
            holder.add(createPromotionOptionButton(new Rook(whitePiece), "R", dialog, picked, tile));
            holder.add(createPromotionOptionButton(new Bishop(whitePiece), "B", dialog, picked, tile));
        } else {
            // Flip upward so the queen still overlaps the promotion square.
            holder.add(createPromotionOptionButton(new Bishop(whitePiece), "B", dialog, picked, tile));
            holder.add(createPromotionOptionButton(new Rook(whitePiece), "R", dialog, picked, tile));
            holder.add(createPromotionOptionButton(new Knight(whitePiece), "N", dialog, picked, tile));
            holder.add(createPromotionOptionButton(new Queen(whitePiece), "Q", dialog, picked, tile));
        }

        dialog.setContentPane(holder);
        dialog.pack();
        dialog.setResizable(false);

        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "promotionEscape");
        dialog.getRootPane().getActionMap().put("promotionEscape", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        int dialogX = screenPoint.x + squareX;
        int dialogY = openDown ? squareScreenY : (squareScreenY - (tile * 3));
        if (dialogY < screenTop) dialogY = screenTop;
        if (dialogY + dialog.getHeight() > screenBottom) dialogY = Math.max(screenTop, screenBottom - dialog.getHeight());
        dialog.setLocation(dialogX, dialogY);
        dialog.setVisible(true);
        return picked[0];
    }

    private JButton createPromotionOptionButton(Piece piece, String symbol, JDialog dialog, String[] picked, int tileSize) {
        JButton btn = new JButton() {
            private boolean hover;
            {
                setPreferredSize(new Dimension(tileSize, tileSize));
                setMinimumSize(new Dimension(tileSize, tileSize));
                setMaximumSize(new Dimension(tileSize, tileSize));
                setFocusPainted(false);
                setBorderPainted(false);
                setContentAreaFilled(false);
                setOpaque(true);
                setBackground(new Color(244, 244, 244));
                setCursor(new Cursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = true;
                        repaint();
                    }
                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(231, 231, 231));
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Framed tile style like a piece slot.
                g2.setColor(Color.BLACK);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.setColor(Color.BLACK);
                g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                g2.setColor(new Color(20, 20, 20));
                g2.drawRect(2, 2, getWidth() - 5, getHeight() - 5);
                g2.setColor(new Color(206, 206, 206));
                g2.fillRect(4, 4, getWidth() - 8, getHeight() - 8);

                // Gray hover effect.
                if (hover) {
                    g2.setColor(new Color(120, 120, 120, 55));
                    g2.fillRect(4, 4, getWidth() - 8, getHeight() - 8);
                    g2.setColor(new Color(25, 25, 25, 180));
                    g2.drawRect(4, 4, getWidth() - 9, getHeight() - 9);
                }

                Graphics2D gp = (Graphics2D) g2.create();
                int target = Math.min(getWidth(), getHeight()) - 16;
                double scale = Math.max(0.55, target / 70.0);
                int px = (int) ((getWidth() - (70 * scale)) / 2.0);
                int py = (int) ((getHeight() - (70 * scale)) / 2.0);
                gp.translate(px, py);
                gp.scale(scale, scale);
                piece.draw(gp, 0, 0);
                gp.dispose();

                g2.setColor(Color.BLACK);
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.dispose();
            }
        };
        btn.addActionListener(e -> {
            picked[0] = symbol;
            dialog.dispose();
        });
        return btn;
    }

    /** Everything that happens after the piece is on the board: notation, turn, save, endgame. */
    private void commitMove(Piece movingPiece, int fromRow, int fromCol, int toRow, int toCol,
                            Piece[][] boardBeforeMove, Piece capturedPiece,
                            boolean isCastling, boolean isEnPassant, String promotionSymbol) {
        committedMoveCount++;
        if (hintMove != null) { hintMove = null; moveHintFade.show(null); }   // the hint was for the position before this move
        commitMoveInner(movingPiece, fromRow, fromCol, toRow, toCol, boardBeforeMove,
                capturedPiece, isCastling, isEnPassant, promotionSymbol);
        afterMoveCommitted();
    }

    /**
     * The captured piece's exit animation (see CaptureFx), drawn above the board so a
     * piece taken on the top rank isn't cut off. Off when move animations are turned off,
     * and in Atomic, which has its own explosion.
     */
    private void playCaptureFx(Piece captured, int row, int col) {
        playCaptureFx(captured, row, col, null, -1, -1);
    }

    /** {@code capturer} standing on (capRow, capCol) is drawn in front of the vanishing piece. */
    private void playCaptureFx(Piece captured, int row, int col, Piece capturer, int capRow, int capCol) {
        if (!animationsEnabled || isAtomicMode() || captured == null || !inBounds(row, col)) return;
        try {
            int dr = boardFlipped ? 7 - row : row, dc = boardFlipped ? 7 - col : col;
            double scale = renderScale();
            int x0 = (int) Math.round(dc * SQUARE_SIZE * scale), y0 = (int) Math.round(dr * SQUARE_SIZE * scale);
            int side = (int) Math.round(SQUARE_SIZE * scale);
            boolean sameSquare = capturer != null && capRow == row && capCol == col;
            CaptureFx.play(this, captured, new Rectangle(x0, y0, side, side),
                    sameSquare ? capturer : null,
                    sameSquare ? () -> inBounds(capRow, capCol) && board[capRow][capCol] == capturer : null);
        } catch (Exception ignored) {
            // a missing animation must never get in the way of the move itself
        }
    }

    private void commitMoveInner(Piece movingPiece, int fromRow, int fromCol, int toRow, int toCol,
                            Piece[][] boardBeforeMove, Piece capturedPiece,
                            boolean isCastling, boolean isEnPassant, String promotionSymbol) {
        if (gameController != null && gameController.getMoveHistoryPanel() != null) {
            String notation = MoveNotation.getMoveNotation(
                    movingPiece, fromRow, fromCol, toRow, toCol,
                    boardBeforeMove, capturedPiece != null, isCastling, isEnPassant);
            if (!promotionSymbol.isEmpty())
                notation = MoveNotation.addPromotion(notation, promotionSymbol);
            gameController.getMoveHistoryPanel().addMove(notation, whiteTurn);
        } else if (analysisMode && analysisGame != null) {
            // The analysis screen keeps a variation tree rather than a flat list, so it
            // takes the notation and decides for itself whether this move continues a
            // line or starts a new branch.
            String notation = MoveNotation.getMoveNotation(
                    movingPiece, fromRow, fromCol, toRow, toCol,
                    boardBeforeMove, capturedPiece != null, isCastling, isEnPassant);
            if (!promotionSymbol.isEmpty())
                notation = MoveNotation.addPromotion(notation, promotionSymbol);
            analysisGame.onMovePlayed(notation);
        }

        // online send
        if (gameController != null)
            gameController.onLocalMove(fromRow, fromCol, toRow, toCol, promotionSymbol);

        // special moves by a human player in a real game count towards achievements
        if (gameController != null && !analysisMode && (!isBotGame || movingPiece.isWhite() == playerIsWhite)) {
            try {
                Achievements.onSpecialMove(gameController.isOnlineMode(), isEnPassant,
                        isCastling && toCol < fromCol, promotionSymbol);
            } catch (Exception ignored) { }
        }

        if (capturedPiece != null) {
            addCapturedPieceToOwner(capturedPiece.isWhite(), capturedPiece);
            // the taken piece floats up and fades away (en passant: from the pawn's own square)
            playCaptureFx(capturedPiece, isEnPassant ? fromRow : toRow, toCol, board[toRow][toCol], toRow, toCol);   // (the promoted piece, after a promotion)
        }

        if (isSpellChessMode() && movingPiece instanceof Zoglin) {
            movingPiece.incrementZoglinMoveCount();
            if (movingPiece.getZoglinMoveCount() >= 4 && board[toRow][toCol] == movingPiece) {
                board[toRow][toCol] = null;
            }
        }

        if (isSpellChessMode() && bomberConsumePending && movingPiece instanceof Rook
                && movingPiece.isWhite() == bomberConsumeSideWhite) {
            PlayerState caster = getPlayerState(bomberConsumeSideWhite);
            caster.setElixir(caster.getElixir() - BOMBER_COST);
            if (gameController != null) {
                gameController.onBoardSpellCastResolved(SpellManager.BOMBER, bomberConsumeSideWhite);
            }
            bomberConsumePending = false;
            bomberConsumeSideWhite = false;
        }
        if (isSpellChessMode() && endermanConsumePending && movingPiece.isWhite() == endermanConsumeSideWhite) {
            PlayerState caster = getPlayerState(endermanConsumeSideWhite);
            caster.setElixir(caster.getElixir() - ENDERMAN_COST);
            if (!completingEndermanTeleport && gameController != null) {
                gameController.onBoardSpellCastResolved(SpellManager.ENDERMAN, endermanConsumeSideWhite);
            }
            endermanConsumePending = false;
            endermanConsumeSideWhite = false;
        }
        if (endermanPhaseActive || pendingEndermanPiecePick) {
            clearEndermanState(false);
        }

        // clear en-passant flags
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                if (board[r][c] instanceof Pawn && board[r][c].isWhite() != whiteTurn)
                    ((Pawn) board[r][c]).clearJustMovedTwo();
        // ...and mark a pawn that just went two squares, whoever moved it. Your own moves
        // already did this on the way in, but the bot's and an online opponent's arrive
        // through other doors -- without this, their two-square pushes could never be
        // taken en passant.
        if (movingPiece instanceof Pawn) {
            ((Pawn) movingPiece).onMove(fromRow, fromCol, toRow, toCol);
        }

        lastMoveFromRow = fromRow; lastMoveFromCol = fromCol;
        lastMoveToRow   = toRow;   lastMoveToCol   = toCol;

        if (isAtomicMode()) {
            King whiteKing = findKing(true);
            King blackKing = findKing(false);
            if (whiteKing == null || blackKing == null) {
                if (gameController != null) gameController.stopTimer();
                saveBoardState();
                recordCurrentPosition();
                setInputEnabled(false);

                if (whiteKing == null && blackKing == null) {
                    if (gameController != null) {
                        gameController.showInGameInfoPopup("Draw", "Both kings exploded. Draw.");
                    } else {
                        GameDialogs.info(Board.this, "Game over", String.valueOf("Both kings exploded. Draw."));
                    }
                    return;
                }

                String winner = (whiteKing != null) ? "White" : "Black";
                if (gameController != null) {
                    gameController.showInGameInfoPopup("King Captured", winner + " wins by atomic explosion.");
                } else {
                    GameDialogs.info(Board.this, "Game over", String.valueOf(winner + " wins by atomic explosion."));
                }
                return;
            }
        }

        if ((fogOfWarEnabled || isSpellChessMode() || isDuckChessMode()) && capturedPiece instanceof King) {
            if (gameController != null) gameController.stopTimer();
            saveBoardState();
            recordCurrentPosition();
            setInputEnabled(false);
            String winner = movingPiece.isWhite() ? "White" : "Black";
            if (gameController != null) {
                gameController.showInGameInfoPopup("King Captured", winner + " wins by capturing the king.");
            } else {
                GameDialogs.info(Board.this, "Game over", String.valueOf(winner + " wins by capturing the king."));
            }
            return;
        }

        if (isKingOfTheHillMode()) {
            int[] whiteKingPos = findKingPosition(true);
            int[] blackKingPos = findKingPosition(false);
            boolean whiteInCenter = whiteKingPos != null && isCenterSquareForKingHill(whiteKingPos[0], whiteKingPos[1]);
            boolean blackInCenter = blackKingPos != null && isCenterSquareForKingHill(blackKingPos[0], blackKingPos[1]);
            if (whiteInCenter || blackInCenter) {
                if (gameController != null) gameController.stopTimer();
                saveBoardState();
                recordCurrentPosition();
                setInputEnabled(false);

                if (whiteInCenter && blackInCenter) {
                    if (gameController != null) {
                        gameController.showInGameInfoPopup("King of the Hill", "Both kings reached the hill. Draw.");
                    } else {
                        GameDialogs.info(Board.this, "Game over", String.valueOf("Both kings reached the hill. Draw."));
                    }
                    return;
                }

                String winner = whiteInCenter ? "White" : "Black";
                String msg = winner + " has won the race!";
                if (gameController != null) {
                    gameController.showInGameInfoPopup("King of the Hill", msg);
                } else {
                    GameDialogs.info(Board.this, "Game over", String.valueOf(msg));
                }
                return;
            }
        }

        boolean isPawnMove = movingPiece instanceof Pawn;
        if (isPawnMove || capturedPiece != null) {
            halfmoveClock = 0;
        } else {
            halfmoveClock++;
        }
        if (!whiteTurn) {
            fullmoveNumber++;
        }

        if (isDuckChessMode()) {
            if (!completingEndermanTeleport) {
                SoundManager.playMoveSound(
                        capturedPiece != null, isCastling, !promotionSymbol.isEmpty(), false);
            }
            beginDuckPlacementPhase();
            return;
        }

        whiteTurn = !whiteTurn;
        processSpellTurnStart(whiteTurn);
        if (autoFlipEnabled) {
            boardFlipped = !boardFlipped;
        }
        saveBoardState();
        recordCurrentPosition();
        boolean givesCheck = !fogOfWarEnabled && new IsLegal(board).isInCheck(whiteTurn);
        cachedWhiteInCheck = givesCheck && whiteTurn;
        cachedBlackInCheck = givesCheck && !whiteTurn;
        lastCheckEvalMs = System.currentTimeMillis();
        if (!completingEndermanTeleport) {
            SoundManager.playMoveSound(
                    capturedPiece != null, isCastling, !promotionSymbol.isEmpty(), givesCheck);
        }
        if (isThreeCheckMode() && givesCheck) {
            boolean checkingSideWhite = !whiteTurn;
            if (checkingSideWhite) {
                whiteChecksDelivered++;
            } else {
                blackChecksDelivered++;
            }
            int checksDelivered = checkingSideWhite ? whiteChecksDelivered : blackChecksDelivered;
            int[] checkedKingPos = findKingPosition(whiteTurn);
            if (checkedKingPos != null) {
                Color orange = new Color(255, 166, 58);
                Color red = new Color(232, 66, 66);
                if (checksDelivered == 1) {
                    startCheckFlashSequence(checkedKingPos[0], checkedKingPos[1], orange, red);
                } else if (checksDelivered == 2) {
                    startCheckFlashSequence(checkedKingPos[0], checkedKingPos[1], red, red);
                }
            }
            if (checksDelivered >= 3) {
                stopThreeCheckRingTimer();
                if (gameController != null) gameController.stopTimer();
                setInputEnabled(false);
                if (gameController != null && gameController.getMoveHistoryPanel() != null) {
                    gameController.getMoveHistoryPanel().updateLastMove("#");
                }
                int[] explodedKingPos = findKingPosition(whiteTurn);
                Piece explodedKing = null;
                if (explodedKingPos != null) {
                    explodedKing = board[explodedKingPos[0]][explodedKingPos[1]];
                    if (explodedKing != null) {
                        SoundManager.playExtraSound(THREE_CHECK_PRE_EXPLOSION_SOUND);
                        Timer explosionSoundDelay = new Timer(THREE_CHECK_EXPLOSION_SOUND_DELAY_MS, ev -> {
                            ((Timer) ev.getSource()).stop();
                            SoundManager.playExtraSound(THREE_CHECK_KING_EXPLOSION_SOUND);
                        });
                        explosionSoundDelay.setRepeats(false);
                        explosionSoundDelay.start();
                        startPieceExplosionFx(explodedKing, explodedKingPos[0], explodedKingPos[1], "");
                    }
                }
                String winner = checkingSideWhite ? "White" : "Black";
                String message = winner + " wins by giving 3 checks.";
                Runnable showResult = () -> {
                    if (gameController != null) {
                        gameController.showInGameInfoPopup("Three-check", message);
                    } else {
                        GameDialogs.info(Board.this, "Game over", String.valueOf(message));
                    }
                    repaint();
                };
                if (explodedKing != null) {
                    Timer delay = new Timer((int) getTotalPieceExplosionDurationMs() + THREE_CHECK_RESULT_EXTRA_DELAY_MS, ev -> {
                        ((Timer) ev.getSource()).stop();
                        showResult.run();
                    });
                    delay.setRepeats(false);
                    delay.start();
                } else {
                    showResult.run();
                }
                return;
            }
        }
        // opening name (always refresh; unknown openings should not remain "Start Position")
        if (gameController != null && gameController.getOpeningDetector() != null &&
                gameController.getOpeningNamePanel() != null) {
            String name = gameController.getOpeningDetector().getOpeningName(generateFEN());
            gameController.getOpeningNamePanel().setOpeningName(name);
        }

        if (analysisMode && analysisGame != null) analysisGame.onPositionChanged();

        // endgame
        // NOTE: the turn is handed over at the END of this method, once the endgame
        // checks below have run. There used to be a second switchTurn() call here as
        // well, which flipped the turn twice per move: the clock stayed on whoever
        // moved first, and a bot opponent kept concluding it was not its turn and
        // silently doing nothing.
        IsLegal next = new IsLegal(board);
        ArrayList<int[]> nextMoves = getActiveLegalMoves(whiteTurn);
        if (nextMoves.isEmpty()) {
            if (gameController != null) gameController.stopTimer();
            if (next.isInCheck(whiteTurn)) {
                if (gameController != null && gameController.getMoveHistoryPanel() != null)
                    gameController.getMoveHistoryPanel().updateLastMove("#");
                
                // Find and rotate the checkmated king
                for (int r = 0; r < 8; r++) {
                    for (int c = 0; c < 8; c++) {
                        if (board[r][c] instanceof King && board[r][c].isWhite() == whiteTurn) {
                            rotateCheckmatedKing(r, c);
                            break;
                        }
                    }
                }

                // Play the checkmate celebration sound

                
                // Show the custom image popup after a short delay (let the king rotate first)
                boolean whiteWins = !whiteTurn;
                if (gameController != null) {
                    gameController.recordBoardWin(whiteWins);
                }

                // Play case-based sound and keep handle to stop it
                // (not in analysis or a review: there the mate is just another position)
                SoundManager.SoundHandle checkmateSound = analysisMode ? null : SoundManager.playSound(whiteWins
                        ? SoundManager.SoundType.CHECKMATE_WHITE
                        : SoundManager.SoundType.CHECKMATE_BLACK);

                // Start celebration animation
                if (whiteWins) {
                    startConfetti();
                } else {
                    startPoopRain();
                }

                // Show popup after short delay so king rotation plays first
                Timer popupDelay = new Timer(600, ev -> showCheckmatePopup(whiteWins, checkmateSound));
                popupDelay.setRepeats(false);
                popupDelay.start();
            } else {
                if (gameController != null) {
                    gameController.showInGameInfoPopup("Draw", "Stalemate! Game is a draw.");
                } else {
                    GameDialogs.info(Board.this, "Game over", String.valueOf("Stalemate! Game is a draw."));
                }
            }
            return;
        } else if (!fogOfWarEnabled && next.isInCheck(whiteTurn)) {
            if (gameController != null && gameController.getMoveHistoryPanel() != null)
                gameController.getMoveHistoryPanel().updateLastMove("+");
        }

        if (!nextMoves.isEmpty()) {
            String drawReason = getAutomaticDrawReason();
            if (drawReason != null) {
                if (gameController != null) gameController.stopTimer();
                if (gameController != null) {
                    gameController.showInGameInfoPopup("Draw", drawReason);
                } else {
                    GameDialogs.info(Board.this, "Game over", String.valueOf(drawReason));
                }
                return;
            }
        }
        if (gameController != null) gameController.switchTurn();
    }

    public void finishSpellCastTurn() {
        halfmoveClock++;
        if (!whiteTurn) fullmoveNumber++;

        whiteTurn = !whiteTurn;
        processSpellTurnStart(whiteTurn);
        if (autoFlipEnabled) boardFlipped = !boardFlipped;

        saveBoardState();
        recordCurrentPosition();

        if (analysisMode && analysisGame != null) analysisGame.onPositionChanged();

        IsLegal next = new IsLegal(board);
        ArrayList<int[]> nextMoves = getActiveLegalMoves(whiteTurn);
        if (nextMoves.isEmpty()) {
            if (gameController != null) gameController.stopTimer();
            if (next.isInCheck(whiteTurn)) {
                if (gameController != null && gameController.getMoveHistoryPanel() != null)
                    gameController.getMoveHistoryPanel().updateLastMove("#");
            } else {
                if (gameController != null) {
                    gameController.showInGameInfoPopup("Draw", "Stalemate! Game is a draw.");
                } else {
                    GameDialogs.info(Board.this, "Game over", String.valueOf("Stalemate! Game is a draw."));
                }
            }
            repaint();
            return;
        }
        if (gameController != null) gameController.switchTurn();
        repaint();
    }

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {
        if (urielChooserActive) {
            urielChooserHoverIndex = -1;
            setCursor(Cursor.getDefaultCursor());
            repaint();
            return;
        }
        urielHoverRow = -1;
        urielHoverCol = -1;
        setCursor(Cursor.getDefaultCursor());
        if (pendingUrielPlacement) repaint();
    }
    
    @Override
    public void mouseDragged(MouseEvent e) {
        if (urielChooserActive) return;

        // === RIGHT DRAG: update arrow target ===
        if (drawingArrow && SwingUtilities.isRightMouseButton(e)) {
            int col = mouseCol(e);
            int row = mouseRow(e);

            if (row >= 0 && row < 8 && col >= 0 && col < 8) {
                if (boardFlipped) {
                    row = 7 - row;
                    col = 7 - col;
                }
                arrowToRow = row;
                arrowToCol = col;
                repaint();
            }
            return;
        }

        if (pendingDuckPlacement && duckSelected && duckPressedOnDuck) {
            duckDragX = logicalMouseX(e);
            duckDragY = logicalMouseY(e);
            if (!wasDragged) {
                int dx = duckDragX - pressX, dy = duckDragY - pressY;
                if (dx * dx + dy * dy >= DRAG_THRESHOLD * DRAG_THRESHOLD) {
                    wasDragged = true;
                    duckDragging = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }
            if (duckDragging) repaint();
            return;
        }

        if (SwingUtilities.isRightMouseButton(e)) return;
        if (!inputEnabled || draggedPiece == null) return;

        dragX = logicalMouseX(e);
        dragY = logicalMouseY(e);

        if (!wasDragged) {
            int dx = dragX - pressX, dy = dragY - pressY;
            if (dx * dx + dy * dy >= DRAG_THRESHOLD * DRAG_THRESHOLD) {
                wasDragged = true;
                dragging   = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
        }
        if (dragging) repaint();
    }
    
    @Override
    public void mouseMoved(MouseEvent e) {
        if (!inputEnabled) { setCursor(Cursor.getDefaultCursor()); return; }
        if (animating || dragging || isDuckPlacementAnimating()) return;

        if (urielChooserActive) {
            int hovered = urielChooserSelectedIndex < 0
                ? findUrielChooserCardAt(logicalMouseX(e), logicalMouseY(e))
                : -1;
            if (hovered != urielChooserHoverIndex) {
                urielChooserHoverIndex = hovered;
                repaint();
            }
            setCursor(hovered >= 0
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getDefaultCursor());
            return;
        }
        
        int col = mouseCol(e);
        int row = mouseRow(e);
        
        if (row < 0 || row > 7 || col < 0 || col > 7) {
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        
        if (boardFlipped) {
            row = 7 - row;
            col = 7 - col;
        }

        if (pendingDuckPlacement) {
            if (isDuckSquare(row, col) || board[row][col] == null) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            } else {
                setCursor(Cursor.getDefaultCursor());
            }
            return;
        }

        if (pendingUrielPlacement) {
            boolean legal = board[row][col] == null && isUrielPlacementSquare(row, col);
            urielHoverRow = legal ? row : -1;
            urielHoverCol = legal ? col : -1;
            setCursor(legal ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            repaint();
            return;
        }
        
        Piece premovePiece = isPremoveModeActive() ? premoveView()[row][col] : null;
        if (board[row][col] != null && board[row][col].isWhite() == whiteTurn
            && (!onlineMode || whiteTurn == localIsWhite)
            && (!isBotGame || board[row][col].isWhite() == playerIsWhite)) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else if (premovePiece != null && isPremoverPiece(premovePiece)) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    // Apply a move from the bot (no user input needed)
    public void applyBotMove(int fromRow, int fromCol, int toRow, int toCol) {
        applyBotMove(fromRow, fromCol, toRow, toCol, 0);
    }

    private void applyBotMove(int fromRow, int fromCol, int toRow, int toCol, int attempt) {
        if (fromRow < 0 || fromRow > 7 || fromCol < 0 || fromCol > 7) return;
        if (toRow < 0 || toRow > 7 || toCol < 0 || toCol > 7) return;
        // Something is still moving on the board (e.g. you were stepping through the
        // game's history): wait for it instead of losing the bot's move.
        if (animating || isDuckPlacementAnimating()) {
            if (attempt < 400) {
                pendingHistoryTargetIndex = -1;
                Timer retry = new Timer(15, e -> applyBotMove(fromRow, fromCol, toRow, toCol, attempt + 1));
                retry.setRepeats(false);
                retry.start();
            }
            return;
        }
        if (viewingPast) snapToLatestPosition();   // the move is played on the live position
        
        Piece movingPiece = board[fromRow][fromCol];
        SpellSnapshot spellSnapshot = isSpellChessMode() ? createSpellSnapshot() : null;
        if (movingPiece == null || movingPiece.isWhite() != whiteTurn) return;
        
        // Verify it's a legal move
        ArrayList<int[]> legalMoves = getActiveLegalMoves(whiteTurn);
        
        boolean moveLegal = false;
        for (int[] m : legalMoves) {
            if (m[0] == fromRow && m[1] == fromCol && m[2] == toRow && m[3] == toCol) {
                moveLegal = true;
                break;
            }
        }
        
        if (!moveLegal) return;
        if (isCastle960(fromRow, fromCol, toRow, toCol)) { executeCastle960(fromRow, fromCol, toCol); return; }
        
        // Save board state before move for notation
        Piece[][] boardBeforeMove = new Piece[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                boardBeforeMove[r][c] = board[r][c];
            }
        }
        
        Piece capturedPiece = board[toRow][toCol];
        
        // Handle en passant
        boolean isEnPassant = false;
        if (movingPiece instanceof Pawn && board[toRow][toCol] == null && 
            Math.abs(toCol - fromCol) == 1) {
            capturedPiece = board[fromRow][toCol];
            board[fromRow][toCol] = null;
            isEnPassant = true;
        }
        
        boolean isCastling = movingPiece instanceof King && Math.abs(toCol - fromCol) == 2;
        
        animPiece = movingPiece;
        animating = true;
        board[fromRow][fromCol] = null;
        
        if (isCastling) {
            int rookCol = (toCol == 6) ? 7 : 0;
            animRook = board[fromRow][rookCol];
            board[fromRow][rookCol] = null;
        }
        
        final Piece[] capturedHolder = new Piece[]{capturedPiece};
        final boolean fEnPassant = isEnPassant;
        final boolean fCastling = isCastling;
        
        animateMove(movingPiece, fromRow, fromCol, toRow, toCol, isCastling, () -> {
            board[toRow][toCol] = movingPiece;
            
            if (fCastling) {
                int newRookCol = (toCol == 6) ? 5 : 3;
                board[fromRow][newRookCol] = animRook;
                ((Rook) animRook).setMoved(true);
                animRook = null;
            }
            
            if (movingPiece instanceof King) ((King) movingPiece).setMoved(true);
            if (movingPiece instanceof Rook) ((Rook) movingPiece).setMoved(true);
            maybeBreakShieldOnMove(movingPiece);

            boolean bombTriggered = isSpellChessMode() && movingPiece instanceof Rook
                    && capturedHolder[0] != null && movingPiece.getBombRookTurnsRemaining() > 0;
            if (!applyBombExplosionIfNeeded(movingPiece, capturedHolder[0], toRow, toCol)) {
                for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) board[r][c] = boardBeforeMove[r][c];
                if (spellSnapshot != null) restoreSpellSnapshot(spellSnapshot);
                animating = false;
                animPiece = null;
                repaint();
                return;
            }
            if (!applyAtomicExplosionIfNeeded(movingPiece, capturedHolder[0], toRow, toCol)) {
                for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) board[r][c] = boardBeforeMove[r][c];
                if (spellSnapshot != null) restoreSpellSnapshot(spellSnapshot);
                animating = false;
                animPiece = null;
                repaint();
                return;
            }
            if (bombTriggered) capturedHolder[0] = null;
            
            String promotionSymbol = "";
            if (movingPiece instanceof Pawn && (toRow == 0 || toRow == 7)) {
                board[toRow][toCol] = new Queen(movingPiece.isWhite());
                promotionSymbol = "Q";
            }
            
            animating = false;
            animPiece = null;
            
            commitMove(movingPiece, fromRow, fromCol, toRow, toCol, boardBeforeMove,
                       capturedHolder[0], fCastling, fEnPassant, promotionSymbol);
            repaint();
        });
    }
    
    // Apply a move from the online opponent (no user input needed)
    public void applyOnlineMove(int fromRow, int fromCol, int toRow, int toCol, String promotionPiece) {
        applyOnlineMove(fromRow, fromCol, toRow, toCol, promotionPiece, 0);
    }

    private void applyOnlineMove(int fromRow, int fromCol, int toRow, int toCol, String promotionPiece, int attempt) {
        if (fromRow < 0 || fromRow > 7 || fromCol < 0 || fromCol > 7) return;
        if (toRow < 0 || toRow > 7 || toCol < 0 || toCol > 7) return;
        if (animating || isDuckPlacementAnimating()) {
            if (attempt < 400) {
                pendingHistoryTargetIndex = -1;
                Timer retry = new Timer(15, e -> applyOnlineMove(fromRow, fromCol, toRow, toCol, promotionPiece, attempt + 1));
                retry.setRepeats(false);
                retry.start();
            }
            return;
        }
        if (viewingPast && !analysisMode) snapToLatestPosition();
        
        Piece movingPiece = board[fromRow][fromCol];
        SpellSnapshot spellSnapshot = isSpellChessMode() ? createSpellSnapshot() : null;
        if (movingPiece == null || movingPiece.isWhite() != whiteTurn) return;
        
        // Verify it's a legal move
        ArrayList<int[]> legalMoves = getActiveLegalMoves(whiteTurn);
        
        boolean moveLegal = false;
        for (int[] m : legalMoves) {
            if (m[0] == fromRow && m[1] == fromCol && m[2] == toRow && m[3] == toCol) {
                moveLegal = true;
                break;
            }
        }
        
        if (!moveLegal) return;
        if (isCastle960(fromRow, fromCol, toRow, toCol)) { executeCastle960(fromRow, fromCol, toCol); return; }
        
        // Save board state before move for notation
        Piece[][] boardBeforeMove = new Piece[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                boardBeforeMove[r][c] = board[r][c];
            }
        }
        
        Piece capturedPiece = board[toRow][toCol];
        
        // Handle en passant
        boolean isEnPassant = false;
        if (movingPiece instanceof Pawn && board[toRow][toCol] == null && 
            Math.abs(toCol - fromCol) == 1) {
            capturedPiece = board[fromRow][toCol];
            board[fromRow][toCol] = null;
            isEnPassant = true;
        }
        
        boolean isCastling = movingPiece instanceof King && Math.abs(toCol - fromCol) == 2;
        
        animPiece = movingPiece;
        animating = true;
        board[fromRow][fromCol] = null;
        
        if (isCastling) {
            int rookCol = (toCol == 6) ? 7 : 0;
            animRook = board[fromRow][rookCol];
            board[fromRow][rookCol] = null;
        }
        
        final Piece[] capturedHolder = new Piece[]{capturedPiece};
        final boolean fEnPassant = isEnPassant;
        final boolean fCastling = isCastling;
        
        animateMove(movingPiece, fromRow, fromCol, toRow, toCol, isCastling, () -> {
            board[toRow][toCol] = movingPiece;
            
            if (fCastling) {
                int newRookCol = (toCol == 6) ? 5 : 3;
                board[fromRow][newRookCol] = animRook;
                ((Rook) animRook).setMoved(true);
                animRook = null;
            }
            
            if (movingPiece instanceof King) ((King) movingPiece).setMoved(true);
            if (movingPiece instanceof Rook) ((Rook) movingPiece).setMoved(true);
            maybeBreakShieldOnMove(movingPiece);

            boolean bombTriggered = isSpellChessMode() && movingPiece instanceof Rook
                    && capturedHolder[0] != null && movingPiece.getBombRookTurnsRemaining() > 0;
            if (!applyBombExplosionIfNeeded(movingPiece, capturedHolder[0], toRow, toCol)) {
                for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) board[r][c] = boardBeforeMove[r][c];
                if (spellSnapshot != null) restoreSpellSnapshot(spellSnapshot);
                animating = false;
                animPiece = null;
                repaint();
                return;
            }
            if (!applyAtomicExplosionIfNeeded(movingPiece, capturedHolder[0], toRow, toCol)) {
                for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) board[r][c] = boardBeforeMove[r][c];
                if (spellSnapshot != null) restoreSpellSnapshot(spellSnapshot);
                animating = false;
                animPiece = null;
                repaint();
                return;
            }
            if (bombTriggered) capturedHolder[0] = null;
            
            String promotionSymbol = "";
            if (movingPiece instanceof Pawn && (toRow == 0 || toRow == 7)) {
                if (promotionPiece != null && !promotionPiece.isEmpty()) {
                    switch (promotionPiece.toUpperCase()) {
                        case "Q": board[toRow][toCol] = new Queen(movingPiece.isWhite());  promotionSymbol = "Q"; break;
                        case "R": board[toRow][toCol] = new Rook(movingPiece.isWhite());   promotionSymbol = "R"; break;
                        case "B": board[toRow][toCol] = new Bishop(movingPiece.isWhite()); promotionSymbol = "B"; break;
                        case "N": board[toRow][toCol] = new Knight(movingPiece.isWhite()); promotionSymbol = "N"; break;
                        default:  board[toRow][toCol] = new Queen(movingPiece.isWhite());  promotionSymbol = "Q"; break;
                    }
                } else {
                    board[toRow][toCol] = new Queen(movingPiece.isWhite());
                    promotionSymbol = "Q";
                }
            }
            
            animating = false;
            animPiece = null;
            
            commitMove(movingPiece, fromRow, fromCol, toRow, toCol, boardBeforeMove,
                       capturedHolder[0], fCastling, fEnPassant, promotionSymbol);
            repaint();
        });
    }
    
    // Alias for compatibility
    public void applyRemoteMove(int fromRow, int fromCol, int toRow, int toCol, String promotionPiece) {
        applyOnlineMove(fromRow, fromCol, toRow, toCol, promotionPiece);
    }

    public void applyRemoteSpellCast(String spellId, boolean casterWhite, SpellTarget target) {
        if (!isSpellChessMode() || spellId == null) return;
        if (animating || whiteTurn != casterWhite) return;
        beginDeferredSpellVisualEffects();
        String result = spellManager.castSpell(this, spellId, casterWhite, target);
        if (result == null) {
            if (gameController != null) {
                gameController.onBoardSpellCastResolved(spellId, casterWhite);
            }
            repaint();
        } else {
            cancelDeferredSpellVisualEffects();
        }
    }

    public void applyRemoteSpellPhase(String phaseId, boolean casterWhite, int row, int col) {
        if (!isSpellChessMode() || phaseId == null) return;
        if (animating || whiteTurn != casterWhite || !inBounds(row, col)) return;
        if (SPELL_PHASE_BOMBER_PRIME.equals(phaseId)) {
            Piece p = board[row][col];
            if (!(p instanceof Rook) || p.isWhite() != casterWhite) return;
            pendingBomberRookPick = false;
            pendingBomberSideWhite = false;
            bomberPrimedRookRow = row;
            bomberPrimedRookCol = col;
            bomberPrimedSideWhite = casterWhite;
            return;
        }
        if (SPELL_PHASE_ENDERMAN.equals(phaseId)) {
            Piece p = board[row][col];
            if (p == null) return;
            activateEndermanPhase(casterWhite, row, col);
            repaint();
        }
    }
    
    /**
     * Applies a move given directly as from/to coordinates rather than algebraic
     * notation -- the same synchronous, non-animated commit {@link #applyMoveFromNotation}
     * uses under the hood, just skipping SAN parsing for a caller that already has UCI-style
     * coordinates on hand (e.g. replaying a reviewed game's UCI move list onto a board).
     * Goes through the exact same legality check and commit path, so a caller in analysis
     * mode gets the same SAN recording, tree notification and turn switch a hand-played or
     * notation-driven move would.
     *
     * @param promotionPiece "Q"/"R"/"B"/"N" (case-insensitive), or null for a non-promoting move
     */
    public boolean applyMoveByCoordinates(int fromRow, int fromCol, int toRow, int toCol, String promotionPiece) {
        return applyMoveProgrammatically(fromRow, fromCol, toRow, toCol, promotionPiece);
    }

    public boolean applyMoveFromNotation(String notation) {

    String promotionPiece = null;

    // Handle promotion (e.g. e8=Q)
    if (notation.contains("=")) {
        String[] parts = notation.split("=");
        notation = parts[0];
        promotionPiece = parts[1];
    }

    // Handle castling
    if (notation.equals("O-O")) {
        return applyCastling(true);
    }
    if (notation.equals("O-O-O")) {
        return applyCastling(false);
    }

    int[] move = parseNotation(notation);
    if (move == null) {
        return false;
    }

    int fromRow = move[0];
    int fromCol = move[1];
    int toRow   = move[2];
    int toCol   = move[3];

    return applyMoveProgrammatically(
        fromRow, fromCol, toRow, toCol, promotionPiece
    );
}


    
    private int[] parseNotation(String notation) {
        ArrayList<int[]> legalMoves = getActiveLegalMoves(whiteTurn);
        
        // Remove capture indicator 'x'
        notation = notation.replace("x", "");
        
        // Get destination square (last 2 characters)
        if (notation.length() < 2) return null;
        String destSquare = notation.substring(notation.length() - 2);
        int toCol = destSquare.charAt(0) - 'a';
        int toRow = 8 - (destSquare.charAt(1) - '0');
        
        if (toRow < 0 || toRow > 7 || toCol < 0 || toCol > 7) return null;
        
        // Determine piece type
        char firstChar = notation.charAt(0);
        boolean isPawn = Character.isLowerCase(firstChar);
        String pieceType = isPawn ? "Pawn" : "";
        
        if (!isPawn) {
            switch (firstChar) {
                case 'K': pieceType = "King"; break;
                case 'Q': pieceType = "Queen"; break;
                case 'R': pieceType = "Rook"; break;
                case 'B': pieceType = "Bishop"; break;
                case 'N': pieceType = "Knight"; break;
                default: return null;
            }
        }
        
        // Find all candidate moves
        ArrayList<int[]> candidates = new ArrayList<>();
        for (int[] move : legalMoves) {
            if (move[2] != toRow || move[3] != toCol) continue;
            
            Piece piece = board[move[0]][move[1]];
            if (piece == null) continue;
            
            String className = piece.getClass().getSimpleName();
            if (!className.equals(pieceType)) continue;
            
            candidates.add(move);
        }
        
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);
        
        // Disambiguate using file or rank
        if (notation.length() > 2) {
            char disambiguator = notation.charAt(isPawn ? 0 : 1);
            
            if (disambiguator >= 'a' && disambiguator <= 'h') {
                // File disambiguation
                int expectedCol = disambiguator - 'a';
                for (int[] move : candidates) {
                    if (move[1] == expectedCol) return move;
                }
            } else if (disambiguator >= '1' && disambiguator <= '8') {
                // Rank disambiguation
                int expectedRow = 8 - (disambiguator - '0');
                for (int[] move : candidates) {
                    if (move[0] == expectedRow) return move;
                }
            }
        }
        
        // If still ambiguous, try rank disambiguation
        if (notation.length() > 3 && !isPawn) {
            char rankChar = notation.charAt(2);
            if (rankChar >= '1' && rankChar <= '8') {
                int expectedRow = 8 - (rankChar - '0');
                for (int[] move : candidates) {
                    if (move[0] == expectedRow) return move;
                }
            }
        }
        
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        
        return null; // ambiguous or no match
    }
    
    
    private boolean applyCastling(boolean kingside) {
        int row = whiteTurn ? 7 : 0;
        int kingCol = 4;
        int rookCol = kingside ? 7 : 0;
        int newKingCol = kingside ? 6 : 2;
        int newRookCol = kingside ? 5 : 3;
        
        Piece king = board[row][kingCol];
        Piece rook = board[row][rookCol];
        
        if (!(king instanceof King) || !(rook instanceof Rook)) return false;
        if (king.isWhite() != whiteTurn) return false;
        
        // Verify this is a legal move
        ArrayList<int[]> legalMoves = getActiveLegalMoves(whiteTurn);
        
        boolean isLegal = false;
        for (int[] move : legalMoves) {
            if (move[0] == row && move[1] == kingCol && 
                move[2] == row && move[3] == newKingCol) {
                isLegal = true;
                break;
            }
        }
        
        if (!isLegal) return false;
        
        return applyMoveProgrammatically(row, kingCol, row, newKingCol, null);
    }
    
    private boolean applyMoveProgrammatically(int fromRow, int fromCol, int toRow, int toCol, String promotionPiece) {
        Piece movingPiece = board[fromRow][fromCol];
        if (movingPiece == null) return false;
        SpellSnapshot spellSnapshot = isSpellChessMode() ? createSpellSnapshot() : null;
        
        // Verify legality
        ArrayList<int[]> legalMoves = getActiveLegalMoves(whiteTurn);
        
        boolean isLegal = false;
        for (int[] move : legalMoves) {
            if (move[0] == fromRow && move[1] == fromCol && 
                move[2] == toRow && move[3] == toCol) {
                isLegal = true;
                break;
            }
        }
        
        if (!isLegal) return false;
        if (isCastle960(fromRow, fromCol, toRow, toCol)) { executeCastle960(fromRow, fromCol, toCol); return true; }
        
        // Save board state for notation
        Piece[][] boardBeforeMove = new Piece[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                boardBeforeMove[r][c] = board[r][c];
            }
        }
        
        Piece capturedPiece = board[toRow][toCol];
        
        // Handle en passant
        boolean isEnPassant = false;
        if (movingPiece instanceof Pawn && board[toRow][toCol] == null && 
            Math.abs(toCol - fromCol) == 1) {
            capturedPiece = board[fromRow][toCol];
            board[fromRow][toCol] = null;
            isEnPassant = true;
        }
        
        // Handle castling
        boolean isCastling = movingPiece instanceof King && Math.abs(toCol - fromCol) == 2;
        if (isCastling) {
            int rookCol = (toCol == 6) ? 7 : 0;
            int newRookCol = (toCol == 6) ? 5 : 3;
            board[fromRow][newRookCol] = board[fromRow][rookCol];
            board[fromRow][rookCol] = null;
            if (board[fromRow][newRookCol] instanceof Rook) {
                ((Rook) board[fromRow][newRookCol]).setMoved(true);
            }
        }
        
        // Move the piece
        board[fromRow][fromCol] = null;
        board[toRow][toCol] = movingPiece;
        
        // Update piece state
        if (movingPiece instanceof King) ((King) movingPiece).setMoved(true);
        if (movingPiece instanceof Rook) ((Rook) movingPiece).setMoved(true);
        if (movingPiece instanceof Pawn) {
            ((Pawn) movingPiece).onMove(fromRow, fromCol, toRow, toCol);
        }
        maybeBreakShieldOnMove(movingPiece);

        boolean bombTriggered = isSpellChessMode() && movingPiece instanceof Rook
                && capturedPiece != null && movingPiece.getBombRookTurnsRemaining() > 0;
        if (!applyBombExplosionIfNeeded(movingPiece, capturedPiece, toRow, toCol)) {
            for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) board[r][c] = boardBeforeMove[r][c];
            if (spellSnapshot != null) restoreSpellSnapshot(spellSnapshot);
            return false;
        }
        if (!applyAtomicExplosionIfNeeded(movingPiece, capturedPiece, toRow, toCol)) {
            for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) board[r][c] = boardBeforeMove[r][c];
            if (spellSnapshot != null) restoreSpellSnapshot(spellSnapshot);
            return false;
        }
        if (bombTriggered) capturedPiece = null;
        
        // Handle promotion
        String promotionSymbol = "";
        if (movingPiece instanceof Pawn && (toRow == 0 || toRow == 7)) {
            if (promotionPiece != null && !promotionPiece.isEmpty()) {
                switch (promotionPiece.toUpperCase()) {
                    case "Q": board[toRow][toCol] = new Queen(movingPiece.isWhite());  promotionSymbol = "Q"; break;
                    case "R": board[toRow][toCol] = new Rook(movingPiece.isWhite());   promotionSymbol = "R"; break;
                    case "B": board[toRow][toCol] = new Bishop(movingPiece.isWhite()); promotionSymbol = "B"; break;
                    case "N": board[toRow][toCol] = new Knight(movingPiece.isWhite()); promotionSymbol = "N"; break;
                    default:  board[toRow][toCol] = new Queen(movingPiece.isWhite());  promotionSymbol = "Q"; break;
                }
            } else {
                board[toRow][toCol] = new Queen(movingPiece.isWhite());
                promotionSymbol = "Q";
            }
        }
        
        // Commit the move (notation, turn switch, etc.)
        commitMoveProgrammatically(movingPiece, fromRow, fromCol, toRow, toCol, 
                                   boardBeforeMove, capturedPiece, isCastling, 
                                   isEnPassant, promotionSymbol);
        
        return true;
    }
    
    private void commitMoveProgrammatically(Piece movingPiece, int fromRow, int fromCol, 
                                           int toRow, int toCol, Piece[][] boardBeforeMove,
                                           Piece capturedPiece, boolean isCastling, 
                                           boolean isEnPassant, String promotionSymbol) {
        // Same as above: the analysis screen records the move into its tree.
        if (analysisMode && analysisGame != null) {
            String notation = MoveNotation.getMoveNotation(
                    movingPiece, fromRow, fromCol, toRow, toCol,
                    boardBeforeMove, capturedPiece != null, isCastling, isEnPassant);
            if (!promotionSymbol.isEmpty()) {
                notation = MoveNotation.addPromotion(notation, promotionSymbol);
            }
            analysisGame.onMovePlayed(notation);
        }
        
        // Clear en-passant flags for opponent pawns
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] instanceof Pawn && board[r][c].isWhite() != whiteTurn) {
                    ((Pawn) board[r][c]).clearJustMovedTwo();
                }
            }
        }
        
        // Update last move highlighting
        lastMoveFromRow = fromRow;
        lastMoveFromCol = fromCol;
        lastMoveToRow = toRow;
        lastMoveToCol = toCol;

        if (capturedPiece != null) {
            addCapturedPieceToOwner(capturedPiece.isWhite(), capturedPiece);
        }
        if (isSpellChessMode() && movingPiece instanceof Zoglin) {
            movingPiece.incrementZoglinMoveCount();
            if (movingPiece.getZoglinMoveCount() >= 4 && board[toRow][toCol] == movingPiece) {
                board[toRow][toCol] = null;
            }
        }

        boolean isPawnMove = movingPiece instanceof Pawn;
        if (isPawnMove || capturedPiece != null) {
            halfmoveClock = 0;
        } else {
            halfmoveClock++;
        }
        if (!whiteTurn) {
            fullmoveNumber++;
        }
        
        // Switch turn
        whiteTurn = !whiteTurn;
        processSpellTurnStart(whiteTurn);
        if (autoFlipEnabled) {
            boardFlipped = !boardFlipped;
        }
        saveBoardState();
        recordCurrentPosition();
        
        // Same notification a hand-played move sends. This used to write the opening
        // name straight into OpeningNamePanel -- a component the analysis screen builds
        // but never puts on screen -- so a position reached by loading a PGN showed a
        // blank opening title and left the engine analysing the position before it.
        if (analysisMode && analysisGame != null) {
            analysisGame.onPositionChanged();
        }

        repaint();
    }

    public boolean hasSufficientMaterialToMate(boolean whiteSide) {
        int bishops = 0;
        int knights = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (p == null || p.isWhite() != whiteSide) continue;
                if (p instanceof King) continue;
                if (p instanceof Pawn || p instanceof Rook || p instanceof Queen) return true;
                if (p instanceof Bishop) bishops++;
                else if (p instanceof Knight) knights++;
            }
        }
        if (bishops >= 2) return true;
        if (bishops >= 1 && knights >= 1) return true;
        return false;
    }

    private boolean isInsufficientMaterialDraw() {
        return !hasSufficientMaterialToMate(true) && !hasSufficientMaterialToMate(false);
    }

    private String getAutomaticDrawReason() {
        if (isInsufficientMaterialDraw()) {
            return "Draw by insufficient material.";
        }
        int repetitionCount = positionCounts.getOrDefault(getPositionKey(), 0);
        if (repetitionCount >= 5) {
            return "Draw by fivefold repetition.";
        }
        if (repetitionCount >= 3) {
            return "Draw by threefold repetition.";
        }
        if (halfmoveClock >= 150) {
            return "Draw by 75-move rule.";
        }
        if (halfmoveClock >= 100) {
            return "Draw by 50-move rule.";
        }
        return null;
    }

    private String getCastlingAvailability() {
        StringBuilder castling = new StringBuilder();
        King whiteKing = findKing(true);
        King blackKing = findKing(false);

        if (whiteKing != null && !whiteKing.hasMoved()) {
            Piece kingsideRook = board[7][castleRookColKingside];
            Piece queensideRook = board[7][castleRookColQueenside];
            if (kingsideRook instanceof Rook && !((Rook) kingsideRook).hasMoved()) castling.append('K');
            if (queensideRook instanceof Rook && !((Rook) queensideRook).hasMoved()) castling.append('Q');
        }
        if (blackKing != null && !blackKing.hasMoved()) {
            Piece kingsideRook = board[0][castleRookColKingside];
            Piece queensideRook = board[0][castleRookColQueenside];
            if (kingsideRook instanceof Rook && !((Rook) kingsideRook).hasMoved()) castling.append('k');
            if (queensideRook instanceof Rook && !((Rook) queensideRook).hasMoved()) castling.append('q');
        }
        return castling.length() > 0 ? castling.toString() : "-";
    }

    private String getEnPassantTargetSquare() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board[r][c];
                if (!(p instanceof Pawn)) continue;
                Pawn pawn = (Pawn) p;
                if (!pawn.justMovedTwo()) continue;
                if (pawn.isWhite() == whiteTurn) continue;
                if (!hasAdjacentCapturingPawn(r, c, whiteTurn)) continue;
                int targetRow = pawn.isWhite() ? r + 1 : r - 1;
                if (targetRow < 0 || targetRow > 7) continue;
                return toSquare(targetRow, c);
            }
        }
        return "-";
    }

    private boolean hasAdjacentCapturingPawn(int row, int col, boolean capturerIsWhite) {
        int left = col - 1;
        int right = col + 1;
        if (left >= 0 && board[row][left] instanceof Pawn && board[row][left].isWhite() == capturerIsWhite) return true;
        if (right < 8 && board[row][right] instanceof Pawn && board[row][right].isWhite() == capturerIsWhite) return true;
        return false;
    }

    private String toSquare(int row, int col) {
        char file = (char) ('a' + col);
        int rank = 8 - row;
        return "" + file + rank;
    }

    private String getPositionKey() {
        return getPiecePlacementOnly() + " " + (whiteTurn ? "w" : "b") + " " +
                getCastlingAvailability() + " " + getEnPassantTargetSquare();
    }

    private String getPiecePlacementOnly() {
        StringBuilder fen = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            int emptyCount = 0;
            for (int c = 0; c < 8; c++) {
                if (board[r][c] == null) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) fen.append(emptyCount);
                    emptyCount = 0;
                    fen.append(getPieceFENChar(board[r][c]));
                }
            }
            if (emptyCount > 0) fen.append(emptyCount);
            if (r < 7) fen.append('/');
        }
        return fen.toString();
    }

    private void recordCurrentPosition() {
        String key = getPositionKey();
        positionCounts.put(key, positionCounts.getOrDefault(key, 0) + 1);
    }

    private void rebuildPositionCountsFromHistory() {
        positionCounts.clear();
        for (String fen : moveHistoryFEN) {
            String key = fenToPositionKey(fen);
            positionCounts.put(key, positionCounts.getOrDefault(key, 0) + 1);
        }
        if (positionCounts.isEmpty()) {
            recordCurrentPosition();
        }
    }

    private String fenToPositionKey(String fen) {
        if (fen == null || fen.isEmpty()) return getPositionKey();
        String[] parts = fen.split(" ");
        if (parts.length < 4) return fen;
        return parts[0] + " " + parts[1] + " " + parts[2] + " " + parts[3];
    }

    /**
     * Put the side to move and the move clocks back to what they were at a saved position.
     *
     * <p>Navigating the game restored the pieces but left {@code whiteTurn} and the
     * clocks wherever the last move had put them. The board looked right and reported a
     * FEN that said the wrong side was to play -- so stepping back to the start of a game
     * had the engine analysing Black's reply to a move that was no longer on the board.
     *
     * <p>Each saved position has its FEN recorded alongside it, which is the authority
     * here -- including the en-passant square, which has to be rebuilt because the flag
     * behind it lives on the piece objects the saved states share. Without that, browsing
     * back and returning to the live position would quietly lose the right to capture en
     * passant.
     */
    private void restoreTurnStateAt(int index) {
        if (index < 0 || index >= moveHistoryFEN.size()) return;
        String[] parts = moveHistoryFEN.get(index).split(" ");
        if (parts.length >= 2) {
            whiteTurn = "w".equals(parts[1]);
        }
        if (parts.length >= 6) {
            try { halfmoveClock = Integer.parseInt(parts[4]); }
            catch (NumberFormatException e) { halfmoveClock = 0; }
            try { fullmoveNumber = Integer.parseInt(parts[5]); }
            catch (NumberFormatException e) { fullmoveNumber = 1; }
        }

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c] instanceof Pawn) {
                    ((Pawn) board[r][c]).clearJustMovedTwo();
                }
            }
        }
        // "e3" means a white pawn stands on e4 having just come from e2; the pawn to mark
        // is the one on the square the capture would pass through, one rank further on.
        if (parts.length >= 4 && !"-".equals(parts[3]) && parts[3].length() == 2) {
            int col = parts[3].charAt(0) - 'a';
            int targetRank = parts[3].charAt(1) - '1';
            int pawnRank = targetRank == 2 ? 3 : (targetRank == 5 ? 4 : -1);
            if (col >= 0 && col < 8 && pawnRank >= 0) {
                int row = 7 - pawnRank;
                if (board[row][col] instanceof Pawn) {
                    ((Pawn) board[row][col]).setJustMovedTwo();
                }
            }
        }
    }

    private void syncMoveClocksFromHistory() {
        if (moveHistoryFEN.isEmpty()) {
            halfmoveClock = 0;
            fullmoveNumber = 1;
            return;
        }
        String fen = moveHistoryFEN.get(moveHistoryFEN.size() - 1);
        String[] parts = fen.split(" ");
        if (parts.length >= 6) {
            try { halfmoveClock = Integer.parseInt(parts[4]); }
            catch (NumberFormatException e) { halfmoveClock = 0; }
            try { fullmoveNumber = Integer.parseInt(parts[5]); }
            catch (NumberFormatException e) { fullmoveNumber = 1; }
        } else {
            halfmoveClock = 0;
            fullmoveNumber = 1;
        }
    }

private void drawArrow(Graphics2D g2, int fr, int fc, int tr, int tc, boolean green) {
    int fromX = squareCenterX(fr, fc);
    int fromY = squareCenterY(fr, fc);
    int toX   = squareCenterX(tr, tc);
    int toY   = squareCenterY(tr, tc);

    int dr = Math.abs(tr - fr);
    int dc = Math.abs(tc - fc);
    if (dr * dc == 2) {
        int bendRow = fr;
        int bendCol = fc;
        if (dr == 2) {
            bendRow = fr + Integer.signum(tr - fr) * 2;
        } else {
            bendCol = fc + Integer.signum(tc - fc) * 2;
        }
        int bendX = squareCenterX(bendRow, bendCol);
        int bendY = squareCenterY(bendRow, bendCol);
        drawKnightArrow(g2, fromX, fromY, bendX, bendY, toX, toY, green);
    } else {
        drawArrowLine(g2, fromX, fromY, toX, toY, green, Math.max(18, (int) (SQUARE_SIZE * 0.36)));
    }
}


private void drawArrowLine(Graphics2D g2, int x1, int y1, int x2, int y2, boolean green, int tailInset) {
    int dx = x2 - x1;
    int dy = y2 - y1;
    double len = Math.hypot(dx, dy);
    if (len < 1.0) return;

    double ux = dx / len;
    double uy = dy / len;
    double px = -uy;
    double py = ux;

    int shaftThickness = Math.max(10, (int) (SQUARE_SIZE * 0.18));
    int headLength = Math.max(18, (int) (SQUARE_SIZE * 0.36));
    int headWidth = Math.max(20, (int) (SQUARE_SIZE * 0.40));

    int startX = (int) Math.round(x1 + ux * tailInset);
    int startY = (int) Math.round(y1 + uy * tailInset);

    int tipX = x2;
    int tipY = y2;
    int shaftEndX = (int) Math.round(tipX - ux * headLength);
    int shaftEndY = (int) Math.round(tipY - uy * headLength);

    Graphics2D g = (Graphics2D) g2.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(getArrowColor(green));
    g.setStroke(new BasicStroke(shaftThickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
    g.drawLine(startX, startY, shaftEndX, shaftEndY);

    int leftX = (int) Math.round(shaftEndX + px * (headWidth * 0.5));
    int leftY = (int) Math.round(shaftEndY + py * (headWidth * 0.5));
    int rightX = (int) Math.round(shaftEndX - px * (headWidth * 0.5));
    int rightY = (int) Math.round(shaftEndY - py * (headWidth * 0.5));

    Polygon head = new Polygon();
    head.addPoint(tipX, tipY);
    head.addPoint(leftX, leftY);
    head.addPoint(rightX, rightY);
    g.fillPolygon(head);
    g.dispose();
}

private void drawKnightArrow(Graphics2D g2, int fromX, int fromY, int bendX, int bendY, int toX, int toY, boolean green) {
    int d1x = bendX - fromX;
    int d1y = bendY - fromY;
    int d2x = toX - bendX;
    int d2y = toY - bendY;
    double len1 = Math.hypot(d1x, d1y);
    double len2 = Math.hypot(d2x, d2y);
    if (len1 < 1.0 || len2 < 1.0) return;

    double u1x = d1x / len1;
    double u1y = d1y / len1;
    double u2x = d2x / len2;
    double u2y = d2y / len2;
    double p2x = -u2y;
    double p2y = u2x;

    int tailInset = Math.max(18, (int) (SQUARE_SIZE * 0.36));
    int shaftThickness = Math.max(10, (int) (SQUARE_SIZE * 0.18));
    int headLength = Math.max(18, (int) (SQUARE_SIZE * 0.36));
    int headWidth = Math.max(20, (int) (SQUARE_SIZE * 0.40));

    int startX = (int) Math.round(fromX + u1x * tailInset);
    int startY = (int) Math.round(fromY + u1y * tailInset);
    int tipX = toX;
    int tipY = toY;
    int shaftEndX = (int) Math.round(tipX - u2x * headLength);
    int shaftEndY = (int) Math.round(tipY - u2y * headLength);

    Graphics2D g = (Graphics2D) g2.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(getArrowColor(green));
    g.setStroke(new BasicStroke(shaftThickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

    java.awt.geom.Path2D path = new java.awt.geom.Path2D.Double();
    path.moveTo(startX, startY);
    path.lineTo(bendX, bendY);
    path.lineTo(shaftEndX, shaftEndY);
    g.draw(path);

    int leftX = (int) Math.round(shaftEndX + p2x * (headWidth * 0.5));
    int leftY = (int) Math.round(shaftEndY + p2y * (headWidth * 0.5));
    int rightX = (int) Math.round(shaftEndX - p2x * (headWidth * 0.5));
    int rightY = (int) Math.round(shaftEndY - p2y * (headWidth * 0.5));

    Polygon head = new Polygon();
    head.addPoint(tipX, tipY);
    head.addPoint(leftX, leftY);
    head.addPoint(rightX, rightY);
    g.fillPolygon(head);
    g.dispose();
}

private Color getArrowColor(boolean green) {
    if (arrowColorOverride != null) return arrowColorOverride;
    return green ? new Color(124, 201, 92, 185) : new Color(244, 188, 52, 185);
}

/** While set, arrows are drawn in this colour (the best-move hint's blue). */
private Color arrowColorOverride;
/** Settings > Best move hint: Argus's suggested move {fr, fc, tr, tc}, or null. */
private int[] hintMove;
private static final Color HINT_ARROW_COLOR = new Color(76, 150, 235, 175);

public void setHintMove(int[] move) {
    int[] next = move == null || move.length < 4 ? null : move.clone();
    if (java.util.Arrays.equals(next, hintMove)) return;
    hintMove = next;
    moveHintFade.show(next);
}

public void clearHintMove() {
    if (hintMove == null) return;
    hintMove = null;
    moveHintFade.show(null);
}

private final ArrowFade moveHintFade = new ArrowFade();

private void paintMoveHint(Graphics2D g2, int[] m, float fade) {
    if (fade <= 0.001f) return;
    arrowColorOverride = new Color(HINT_ARROW_COLOR.getRed(), HINT_ARROW_COLOR.getGreen(),
            HINT_ARROW_COLOR.getBlue(), Math.round(HINT_ARROW_COLOR.getAlpha() * fade));
    try {
        drawArrow(g2, m[0], m[1], m[2], m[3], false);
    } finally {
        arrowColorOverride = null;
    }
}
private int squareCenterX(int row, int col) {
    int displayCol = boardFlipped ? (7 - col) : col;
    return displayCol * SQUARE_SIZE + SQUARE_SIZE / 2;
}

private int squareCenterY(int row, int col) {
    int displayRow = boardFlipped ? (7 - row) : row;
    return displayRow * SQUARE_SIZE + SQUARE_SIZE / 2;
}

    // Confetti: continuous party poppers from both bottom corners
    private void startConfetti() {
        stopConfetti();
        int w = getWidth(), h = getHeight();
        spawnBurst(0, h, -1);
        spawnBurst(w, h, 1);
        // Re-burst every 1100ms â€” runs forever until stopConfetti()
        rebursterTimer = new Timer(1100, ev -> {
            spawnBurst(0, getHeight(), -1);
            spawnBurst(getWidth(), getHeight(), 1);
        });
        rebursterTimer.start();
        // Physics loop
        confettiTimer = new Timer(AnimationTiming.FRAME_DELAY_MS, ev -> {
            int bh = getHeight();
            double dt = AnimationTiming.FRAME_SCALE_FROM_16_MS;
            synchronized (particles) {
                for (Particle p : particles) {
                    p.x += p.vx * dt;
                    p.y += p.vy * dt;
                    p.vy += p.gravity * dt;
                    p.vx *= Math.pow(0.99, dt);
                    p.angle += p.spin * dt;
                }
                particles.removeIf(pp -> pp.y > bh + 60);
            }
            repaint();
        });
        confettiTimer.start();
    }

    private void spawnBurst(int ox, int oy, int side) {
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < 70; i++) {
            Color c = CONFETTI_COLORS[rnd.nextInt(CONFETTI_COLORS.length)];
            double base  = (side < 0) ? Math.toRadians(-115) : Math.toRadians(-155);
            double angle = base + rnd.nextDouble() * Math.toRadians(80);
            double speed = 4.5 + rnd.nextDouble() * 8.5;
            int size = 6 + rnd.nextInt(8);
            Particle p = new Particle(ox, oy,
                Math.cos(angle) * speed, Math.sin(angle) * speed,
                c, size, (rnd.nextDouble() - 0.5) * 0.25, rnd.nextBoolean(), false);
            p.alpha = 1.0f;
            particles.add(p);
        }
    }

    // Poop overlay panel â€” installed on the JFrame glass pane so it paints above dialogs
    private javax.swing.JPanel poopOverlayPanel = null;
    // Fireball overlay panel on layered pane so impact flash can render above top bars.
    private javax.swing.JPanel fireballOverlayPanel = null;
    private JLayeredPane fireballOverlayLayer = null;

    private JLayeredPane getVisibleFireballOverlayLayer() {
        JRootPane visibleRoot = SwingUtilities.getRootPane(this);
        if (visibleRoot != null && visibleRoot.isShowing()) {
            return visibleRoot.getLayeredPane();
        }
        if (gameController != null && gameController.isShowing()) {
            return gameController.getLayeredPane();
        }
        return null;
    }

    private void ensureFireballOverlayPanel() {
        if (fireballOverlayPanel != null && fireballOverlayPanel.isShowing()) return;
        if (fireballOverlayPanel != null && fireballOverlayLayer != null) {
            fireballOverlayLayer.remove(fireballOverlayPanel);
            fireballOverlayPanel = null;
            fireballOverlayLayer = null;
        }

        JLayeredPane layered = getVisibleFireballOverlayLayer();
        if (layered == null) return;
        fireballOverlayLayer = layered;
        fireballOverlayPanel = new javax.swing.JPanel(null) {
            @Override public boolean isOpaque() { return false; }
            @Override public boolean contains(int x, int y) { return false; }
            @Override protected void paintComponent(Graphics g) {
                if (fireballImpactFx.isEmpty()) return;
                Graphics2D gc = (Graphics2D) g.create();
                gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                java.awt.Point boardOrigin = javax.swing.SwingUtilities.convertPoint(
                    Board.this, new java.awt.Point(0, 0), this);
                gc.translate(boardOrigin.x, boardOrigin.y);
                gc.scale(renderScale(), renderScale());
                drawFireballImpactFx(gc, 0, 0, BOARD_SIZE, BOARD_SIZE);
                gc.dispose();
            }
        };
        fireballOverlayPanel.setOpaque(false);
        fireballOverlayPanel.setBounds(0, 0, layered.getWidth(), layered.getHeight());
        layered.add(fireballOverlayPanel, JLayeredPane.DRAG_LAYER);
        layered.moveToFront(fireballOverlayPanel);
        layered.revalidate();
        layered.repaint();
    }

    // Poop rain: continuous falling from random x positions when white loses
    private void startPoopRain() {
        stopConfetti();

        // Install a transparent overlay on the root pane glass pane of the parent frame
        // so poop renders above the JDialog popup
        if (gameController != null) {
            poopOverlayPanel = new javax.swing.JPanel(null) {
                @Override public boolean isOpaque() { return false; }
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D gc = (Graphics2D) g.create();
                    gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Translate from frame coords to board coords
                    java.awt.Point boardOrigin = javax.swing.SwingUtilities.convertPoint(
                        Board.this, new java.awt.Point(0, 0), this);
                    synchronized (particles) {
                        for (Particle p : particles) {
                            if (!p.isPoop) continue;
                            java.awt.geom.AffineTransform at = gc.getTransform();
                            gc.translate(boardOrigin.x + p.x, boardOrigin.y + p.y);
                            gc.rotate(p.angle);
                            gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, p.alpha));
                            gc.setFont(new Font("Segoe UI Emoji", Font.PLAIN, p.size));
                            gc.setColor(new Color(101, 55, 0));
                            gc.drawString(new String(Character.toChars(0x1F4A9)), -p.size / 2, p.size / 2);
                            gc.setTransform(at);
                        }
                    }
                    gc.dispose();
                }
            };
            poopOverlayPanel.setOpaque(false);
            javax.swing.JRootPane root = gameController.getRootPane();
            root.setGlassPane(poopOverlayPanel);
            poopOverlayPanel.setVisible(true);
        }

        int w = getWidth();
        spawnPoopWave(w);
        // Spawn a new wave of droppings every 380ms continuously
        poopSpawnTimer = new Timer(380, ev -> spawnPoopWave(getWidth()));
        poopSpawnTimer.start();
        // Physics loop â€” repaint the overlay panel each frame
        confettiTimer = new Timer(AnimationTiming.FRAME_DELAY_MS, ev -> {
            int bh = getHeight();
            double dt = AnimationTiming.FRAME_SCALE_FROM_16_MS;
            synchronized (particles) {
                for (Particle p : particles) {
                    if (!p.isPoop) continue;
                    p.x += p.vx * dt;
                    p.y += p.vy * dt;
                    p.vy += p.gravity * 0.5 * dt;
                    p.angle += p.spin * dt;
                }
                particles.removeIf(pp -> pp.isPoop && pp.y > bh + 60);
            }
            if (poopOverlayPanel != null) poopOverlayPanel.repaint();
        });
        confettiTimer.start();
    }

    private void spawnPoopWave(int w) {
        java.util.Random rnd = new java.util.Random();
        int count = 5 + rnd.nextInt(4);
        for (int i = 0; i < count; i++) {
            double x  = 10 + rnd.nextDouble() * (w - 20);
            double vx = (rnd.nextDouble() - 0.5) * 1.2;
            double vy = 1.5 + rnd.nextDouble() * 2.5;
            int size = 28 + rnd.nextInt(18);
            Particle p = new Particle(x, -size, vx, vy,
                new Color(101, 55, 0), size, (rnd.nextDouble() - 0.5) * 0.08, false, true);
            p.gravity = 0.05 + rnd.nextDouble() * 0.05;
            p.alpha   = 1.0f;
            particles.add(p);
        }
    }

    private void stopConfetti() {
        if (confettiTimer  != null) { confettiTimer.stop();   confettiTimer  = null; }
        if (rebursterTimer != null) { rebursterTimer.stop();  rebursterTimer = null; }
        if (poopSpawnTimer != null) { poopSpawnTimer.stop();  poopSpawnTimer = null; }
        particles.clear();
        // Remove the poop glass pane overlay if present
        if (poopOverlayPanel != null) {
            poopOverlayPanel.setVisible(false);
            if (gameController != null) {
                javax.swing.JPanel blank = new javax.swing.JPanel();
                blank.setOpaque(false);
                gameController.getRootPane().setGlassPane(blank);
            }
            poopOverlayPanel = null;
        }
        repaint();
    }


    // â”€â”€â”€ Custom checkmate popup with image â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void showCheckmatePopup(boolean whiteWins, SoundManager.SoundHandle soundHandle) {
        // the game's own result window (centred on the board); sound and confetti stop when it closes
        Runnable stop = () -> {
            SoundManager.stopSound(soundHandle);
            stopConfetti();
        };
        if (gameController != null) {
            gameController.showCheckmateResult(whiteWins, stop);
        } else {
            GameDialogs.info(this, "Checkmate", whiteWins ? "White wins!" : "Black wins!");
            stop.run();
        }
    }

    private void applyRoundedDialogShape(JDialog dialog, int arc) {
        try {
            dialog.setShape(new RoundRectangle2D.Double(0, 0, dialog.getWidth(), dialog.getHeight(), arc, arc));
        } catch (UnsupportedOperationException ignored) {
            // Keep painted rounded card if shaped windows are unsupported.
        }
    }

}
