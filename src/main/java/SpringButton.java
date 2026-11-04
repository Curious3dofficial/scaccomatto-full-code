import java.awt.*;
import javax.swing.*;

/**
 * Shared base for every custom-painted button on the meadow screens
 * (MainMenuScreen's Play/account/settings/quit, ModeSelectScreen's six mode
 * circles).
 *
 * This used to also drive a damped-spring hover/press animation (grow on
 * hover, bounce on press) -- that's been stripped out for now, per request
 * ("delete all button effect code, we'll see that later"). Buttons painted
 * through this base currently render a fixed, non-animated look; clicking
 * still works exactly as before (Swing's normal ActionEvent firing doesn't
 * depend on any of the removed code).
 *
 * The one thing that stayed: fadeAlpha / fadeTo() / setFadeAlpha(), the
 * whole-button fade in/out used for screen-transition choreography (Play's
 * buttons fading out, the six mode circles fading in). That's a
 * screen-transition effect, not a hover/click effect, so it wasn't part of
 * what got deleted here.
 *
 * entryScale is the same idea for size: a plain 0..1+ multiplier a subclass
 * can apply around its own center while it's animating in (a subtle bouncy
 * pop alongside the fade-in), separate from -- and not to be confused with
 * -- the deleted hover/press grow-and-shrink. It doesn't animate on its
 * own; whoever drives the entrance (e.g. ModeSelectScreen.fadeInButtons())
 * computes the curve and calls setEntryScale() each tick, same as it does
 * with setFadeAlpha().
 */
abstract class SpringButton extends JButton {

    protected float fadeAlpha = 1f;
    protected float entryScale = 1f;
    private Timer fadeTimer;

    protected SpringButton() {
        // Every subclass paints a shape that doesn't fill its full
        // rectangular bounds (a circle, a rounded pill) and, since fadeTo()
        // was added, sometimes paints it partially transparent. A JButton
        // defaults to opaque=true, which tells Swing this component covers
        // every pixel of its own bounds itself -- so on a repaint Swing
        // reuses whatever was already in the buffer there instead of first
        // repainting the parent underneath. That's invisible at full opacity
        // (the corners were already correct from the last full layout
        // paint), but during a fade each new partially-transparent frame
        // then composites onto the STALE previous frame instead of the live
        // background, smearing frame over frame. setOpaque(false) makes
        // Swing repaint the real background behind this button first, every
        // time.
        setOpaque(false);
    }

    /**
     * Eases fadeAlpha to `target` over `durationMs`, repainting every frame,
     * then runs `onComplete` (if given) once it lands exactly on target.
     */
    protected void fadeTo(float target, int durationMs, Runnable onComplete) {
        if (fadeTimer != null && fadeTimer.isRunning()) fadeTimer.stop();
        float start = fadeAlpha;
        float clampedTarget = Math.max(0f, Math.min(1f, target));
        long startNanos = System.nanoTime();
        double durationSec = Math.max(1, durationMs) / 1000.0;

        fadeTimer = new Timer(16, null);
        fadeTimer.addActionListener(e -> {
            double t = (System.nanoTime() - startNanos) / 1_000_000_000.0 / durationSec;
            t = Math.max(0.0, Math.min(1.0, t));
            float eased = (float) (1.0 - Math.pow(1.0 - t, 3)); // ease-out cubic
            fadeAlpha = start + (clampedTarget - start) * eased;
            repaint();
            if (t >= 1.0) {
                fadeAlpha = clampedTarget;
                fadeTimer.stop();
                repaint();
                if (onComplete != null) onComplete.run();
            }
        });
        fadeTimer.start();
    }

    protected void fadeTo(float target, int durationMs) {
        fadeTo(target, durationMs, null);
    }

    /** Snaps fadeAlpha to a value with no animation -- for resetting state, not for transitions. */
    protected void setFadeAlpha(float alpha) {
        if (fadeTimer != null && fadeTimer.isRunning()) fadeTimer.stop();
        fadeAlpha = Math.max(0f, Math.min(1f, alpha));
        repaint();
    }

    /**
     * Like setFadeAlpha() but without asking for a repaint -- for screens that
     * drive a fade themselves and paint the whole frame in one go each tick.
     */
    void setFadeAlphaQuiet(float alpha) {
        if (fadeTimer != null && fadeTimer.isRunning()) fadeTimer.stop();
        fadeAlpha = Math.max(0f, Math.min(1f, alpha));
    }

    /** setEntryScale() without the repaint request (see setFadeAlphaQuiet()). */
    void setEntryScaleQuiet(float scale) {
        entryScale = scale;
    }

    private java.awt.image.BufferedImage fadeBuffer;

    /**
     * Part-faded buttons are drawn whole into a picture first, and that picture
     * is faded as one. Fading each shape separately let the shadow show through
     * the half-transparent white circle (a grey, muddy middle of the fade);
     * this way the button just becomes more and more see-through, cleanly.
     */
    @Override
    public void paint(Graphics g) {
        float a = fadeAlpha;
        if (a >= 0.999f) { super.paint(g); return; }
        if (a <= 0.001f) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        Graphics2D g2 = (Graphics2D) g;
        java.awt.geom.AffineTransform at = g2.getTransform();
        double sx = Math.abs(at.getScaleX()) < 1e-6 ? 1 : Math.abs(at.getScaleX());
        double sy = Math.abs(at.getScaleY()) < 1e-6 ? 1 : Math.abs(at.getScaleY());
        int bw = (int) Math.ceil(w * sx), bh = (int) Math.ceil(h * sy);
        if (fadeBuffer == null || fadeBuffer.getWidth() != bw || fadeBuffer.getHeight() != bh) {
            GraphicsConfiguration gc = getGraphicsConfiguration();
            fadeBuffer = gc != null
                    ? gc.createCompatibleImage(bw, bh, Transparency.TRANSLUCENT)
                    : new java.awt.image.BufferedImage(bw, bh, java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
        }
        Graphics2D bg = fadeBuffer.createGraphics();
        try {
            bg.setComposite(AlphaComposite.Clear);
            bg.fillRect(0, 0, bw, bh);
            bg.setComposite(AlphaComposite.SrcOver);
            bg.scale(sx, sy);
            bg.setFont(g.getFont());
            fadeAlpha = 1f;
            try {
                super.paint(bg);
            } finally {
                fadeAlpha = a;
            }
        } finally {
            bg.dispose();
        }
        Graphics2D out = (Graphics2D) g.create();
        try {
            out.scale(1 / sx, 1 / sy);
            out.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            out.drawImage(fadeBuffer, 0, 0, null);
        } finally {
            out.dispose();
        }
    }

    /** Snaps entryScale to a value with no animation of its own -- the caller drives the curve. */
    protected void setEntryScale(float scale) {
        entryScale = scale;
        repaint();
    }
}
