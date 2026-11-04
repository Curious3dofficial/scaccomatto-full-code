import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Chess titles, as chess.com shows them next to a name: a small red rounded tag with
 * the letters in Baloo 2.
 *
 * <p>Each title carries the rating it needs. The FIDE titles use FIDE's own minimums
 * (a rating reached at some point, plus norms for GM/IM/WGM/WIM); NM is US Chess's
 * National Master at 2200. WNM has no single international standard -- it's a
 * national title -- so 2000 here mirrors WCM.
 */
enum ChessTitle {
    NONE("", "None", 0),
    GM("GM", "Grandmaster", 2500),
    IM("IM", "International Master", 2400),
    FM("FM", "FIDE Master", 2300),
    CM("CM", "Candidate Master", 2200),
    NM("NM", "National Master", 2200),
    WGM("WGM", "Woman Grandmaster", 2300),
    WIM("WIM", "Woman International Master", 2200),
    WFM("WFM", "Woman FIDE Master", 2100),
    WCM("WCM", "Woman Candidate Master", 2000),
    WNM("WNM", "Woman National Master", 2000);

    final String code;
    final String fullName;
    final int minRating;

    ChessTitle(String code, String fullName, int minRating) {
        this.code = code;
        this.fullName = fullName;
        this.minRating = minRating;
    }

    /** Whether a player with this Elo could hold the title (no Elo given = any title). */
    boolean allowedFor(Integer elo) {
        return this == NONE || elo == null || elo >= minRating;
    }

    static ChessTitle parse(String s) {
        if (s == null || s.isBlank()) return NONE;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    /** chess.com's title red. */
    static final Color RED = new Color(158, 38, 37);

    /**
     * Paints the tag with its left edge at {@code x}, vertically centred on {@code cy}.
     * Returns its width (0 for NONE).
     */
    int paint(Graphics2D g, float x, float cy, float height) {
        if (this == NONE) return 0;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        Font f = MainMenu.variantDisplayFont(height * 0.72f);
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics();
        float padX = height * 0.30f;
        float w = fm.stringWidth(code) + padX * 2;
        float y = cy - height / 2f;
        g2.setColor(RED);
        g2.fill(new RoundRectangle2D.Float(x, y, w, height, height * 0.42f, height * 0.42f));
        g2.setColor(Color.WHITE);
        float base = cy + (fm.getAscent() - fm.getDescent()) / 2f + height * 0.03f;
        g2.drawString(code, x + padX, base);
        g2.dispose();
        return Math.round(w);
    }

    /** The tag's width at this height, without painting. */
    int width(Graphics2D g, float height) {
        if (this == NONE) return 0;
        FontMetrics fm = g.getFontMetrics(MainMenu.variantDisplayFont(height * 0.72f));
        return Math.round(fm.stringWidth(code) + height * 0.60f);
    }
}
