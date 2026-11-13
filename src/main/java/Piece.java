import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public abstract class Piece {
    protected boolean white;
    protected static BufferedImage[][] pieceImages = new BufferedImage[2][6];
    protected int imageIndex;
    private int frozenTurnsRemaining = 0;
    private int shieldedTurnsRemaining = 0;
    private int bombRookTurnsRemaining = 0;
    private int resurrectCooldownTurnsRemaining = 0;
    private int zoglinMoveCount = 0;
    private boolean zoglin = false;

    // ── piece sets ─────────────────────────────────────────────────────────────
    // Classic: /assets/pieces/wk.png ...; the others in their own folder
    // (/assets/pieces/neo/wk.png ...). Every board draws through pieceImages, so
    // swapping the pictures here changes every board at once.

    static final String[] SETS = {"Classic", "Neo", "Minimal", "Chessnut", "Spatial", "Papercut", "Cburnett", "Eyes", "Skulls", "Prmi"};
    private static volatile String loadedSet = "Classic";

    static {
        loadSet(currentSetName());          // after SETS / loadedSet exist
    }

    private static String currentSetName() {
        try { return AppSettings.pieceSet(); } catch (Throwable t) { return "Classic"; }
    }

    /** Where a piece picture of the current set is, e.g. path("wk.png"). */
    static String path(String file) {
        String set = loadedSet;
        return "Classic".equals(set) ? "/assets/pieces/" + file : "/assets/pieces/" + set.toLowerCase() + "/" + file;
    }

    /** The current set's name ("Classic", "Neo", "Minimal", "Chessnut"...). */
    static String setName() { return loadedSet; }

    /** Switches every board to another piece set (missing pictures fall back to Classic). */
    static synchronized void loadSet(String set) {
        if (set == null) set = "Classic";
        boolean known = false;
        for (String s : SETS) if (s.equalsIgnoreCase(set)) { set = s; known = true; }
        if (!known) set = "Classic";
        String[] names = {"k", "q", "r", "b", "n", "p"};
        BufferedImage[][] fresh = new BufferedImage[2][6];
        for (int c = 0; c < 2; c++) {
            for (int i = 0; i < 6; i++) {
                String file = (c == 1 ? "w" : "b") + names[i] + ".png";
                String dir = "Classic".equals(set) ? "/assets/pieces/" : "/assets/pieces/" + set.toLowerCase() + "/";
                try {
                    java.net.URL url = Piece.class.getResource(dir + file);
                    if (url == null) url = Piece.class.getResource("/assets/pieces/" + file);
                    fresh[c][i] = url == null ? null : ImageIO.read(url);
                } catch (Exception e) {
                    CrashLog.record(Thread.currentThread(), e);
                }
            }
        }
        pieceImages = fresh;
        loadedSet = set;
    }

    public Piece(boolean white, int imageIndex) {
    this.white = white;
    this.imageIndex = imageIndex;
    }


    public boolean isWhite() {
        return white;
    }

    public abstract boolean isValidMove(
        int sr, int sc, int er, int ec, Piece[][] board
    );

    public abstract Piece copy();

    protected void copyStatusTo(Piece target) {
        target.frozenTurnsRemaining = this.frozenTurnsRemaining;
        target.shieldedTurnsRemaining = this.shieldedTurnsRemaining;
        target.bombRookTurnsRemaining = this.bombRookTurnsRemaining;
        target.resurrectCooldownTurnsRemaining = this.resurrectCooldownTurnsRemaining;
        target.zoglinMoveCount = this.zoglinMoveCount;
        target.zoglin = this.zoglin;
    }

    public int getFrozenTurnsRemaining() {
        return frozenTurnsRemaining;
    }

    public void setFrozenTurnsRemaining(int frozenTurnsRemaining) {
        this.frozenTurnsRemaining = Math.max(0, frozenTurnsRemaining);
    }

    public int getShieldedTurnsRemaining() {
        return shieldedTurnsRemaining;
    }

    public void setShieldedTurnsRemaining(int shieldedTurnsRemaining) {
        this.shieldedTurnsRemaining = Math.max(0, shieldedTurnsRemaining);
    }

    public int getBombRookTurnsRemaining() {
        return bombRookTurnsRemaining;
    }

    public void setBombRookTurnsRemaining(int bombRookTurnsRemaining) {
        this.bombRookTurnsRemaining = Math.max(0, bombRookTurnsRemaining);
    }

    public int getResurrectCooldownTurnsRemaining() {
        return resurrectCooldownTurnsRemaining;
    }

    public void setResurrectCooldownTurnsRemaining(int resurrectCooldownTurnsRemaining) {
        this.resurrectCooldownTurnsRemaining = Math.max(0, resurrectCooldownTurnsRemaining);
    }

    public int getZoglinMoveCount() {
        return zoglinMoveCount;
    }

    public void incrementZoglinMoveCount() {
        zoglinMoveCount++;
    }

    public void setZoglinMoveCount(int zoglinMoveCount) {
        this.zoglinMoveCount = Math.max(0, zoglinMoveCount);
    }

    public boolean isZoglin() {
        return zoglin;
    }

    public void setZoglin(boolean zoglin) {
        this.zoglin = zoglin;
    }

    public void draw(Graphics g, int x, int y) {
    int colorIndex = white ? 1 : 0;
    BufferedImage img = pieceImages[colorIndex][imageIndex];

    if (img != null) {
        g.drawImage(img, x, y, 70, 70, null);
    }
}
}   
