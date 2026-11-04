import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * The game screen's time-control dropdown, in the Online screen's layout but dark and
 * purple: a header tile (clock badge, the current time, a chevron that turns over), a
 * 2x2 grid of quick picks under it, and a "More Settings" link that slides the quick
 * grid away and the full Bullet / Blitz / Rapid list in.
 *
 * <p>Only the pieces live here; ChessGame puts them together and owns the selection.
 */
final class TimeControlPicker {
    private TimeControlPicker() { }

    // dark + purple palette (AnalysisTheme's)
    static final Color TILE         = new Color(38, 37, 44);
    static final Color TILE_HOVER   = new Color(47, 45, 56);
    static final Color TILE_EDGE    = new Color(60, 58, 70);
    static final Color TILE_EDGE_HI = new Color(122, 92, 190);
    static final Color OPEN_TOP     = new Color(66, 48, 108);
    static final Color OPEN_BOTTOM  = new Color(52, 38, 88);
    static final Color OPEN_EDGE    = new Color(150, 110, 245);
    static final Color TEXT         = new Color(242, 240, 248);
    static final Color MUTED        = new Color(156, 152, 172);
    static final Color ACCENT       = new Color(186, 156, 255);
    static final Color SEL_TOP      = new Color(150, 106, 242);
    static final Color SEL_BOTTOM   = new Color(106, 64, 206);

    static Graphics2D quality(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    static Color mix(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    /** "10 min" -> "10 Min", "3 | 2" stays. */
    static String titleCase(String s) {
        StringBuilder out = new StringBuilder();
        boolean start = true;
        for (char c : s.toCharArray()) {
            out.append(start ? Character.toUpperCase(c) : c);
            start = Character.isWhitespace(c);
        }
        return out.toString();
    }

    /** A 0-1 value that eases toward on/off; repaints its owner as it moves. */
    static final class Fader {
        private final JComponent owner;
        private final float durationMs;
        private float value;
        private float from;
        private boolean target;
        private long start;
        private Timer timer;

        Fader(JComponent owner, float durationMs, boolean initial) {
            this.owner = owner;
            this.durationMs = durationMs;
            this.target = initial;
            this.value = initial ? 1f : 0f;
        }

        float value() { return value; }

        void set(boolean on) {
            if (on == target && (timer == null || !timer.isRunning())) return;
            target = on;
            from = value;
            start = System.nanoTime();
            if (timer == null) {
                timer = AnimationTiming.createUiTimer(e -> {
                    float goal = target ? 1f : 0f;
                    float t = AnimationTiming.progress(start, Math.max(90f, durationMs * Math.abs(goal - from)));
                    float eased = t * t * (3f - 2f * t);
                    value = from + (goal - from) * eased;
                    if (t >= 1f) {
                        value = goal;
                        timer.stop();
                    }
                    owner.repaint();
                });
            }
            timer.restart();
        }
    }

    // ── header ────────────────────────────────────────────────────────────────

    /**
     * The tile that shows the current time and opens the dropdown. Its text is the
     * preset's full display ("10 min (Rapid)"); it paints the short part big and the
     * category small beside it.
     */
    static final class Header extends JButton {
        private final Fader hover = new Fader(this, 170f, false);
        private final Fader open = new Fader(this, 300f, false);
        private final Fader press = new Fader(this, 110f, false);
        private boolean expanded;

        Header(String text, Runnable onClick) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(AnalysisTheme.display(AnalysisTheme.k(26f)));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { if (isEnabled()) hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); press.set(false); }
                @Override public void mousePressed(MouseEvent e) { if (isEnabled()) press.set(true); }
                @Override public void mouseReleased(MouseEvent e) { press.set(false); }
            });
            addActionListener(e -> {
                SoundManager.playExtraSound("tap.wav");
                if (onClick != null) onClick.run();
            });
        }

        void setExpanded(boolean expanded) {
            if (this.expanded == expanded) return;
            this.expanded = expanded;
            open.set(expanded);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            if (!enabled) hover.set(false);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = quality(g);
            float hv = hover.value();
            float op = open.value();
            float pr = press.value();
            int w = getWidth();
            int h = getHeight();
            float pad = Math.max(3f, h * 0.06f);
            float arc = h * 0.30f;
            float shrink = pr * h * 0.025f;
            RoundRectangle2D tile = new RoundRectangle2D.Float(
                    pad * 0.5f + shrink, pad * 0.3f + shrink,
                    w - pad - shrink * 2, h - pad * 1.3f - shrink * 2, arc, arc);

            // soft shadow, stronger while open
            for (int i = 4; i >= 1; i--) {
                g2.setColor(new Color(0, 0, 0, Math.round((10 + 14 * op) / i)));
                g2.fill(new RoundRectangle2D.Float((float) tile.getX(), (float) tile.getY() + i * 1.2f,
                        (float) tile.getWidth(), (float) tile.getHeight(), arc, arc));
            }

            AnalysisTheme.Accent ac = AnalysisTheme.accent(this);
            Color openTop = mix(new Color(36, 35, 42), ac.base, 0.38f), openBottom = mix(new Color(36, 35, 42), ac.base, 0.26f);
            Color top = mix(mix(TILE, TILE_HOVER, hv), openTop, op);
            Color bottom = mix(mix(TILE, TILE_HOVER, hv), openBottom, op);
            g2.setPaint(new GradientPaint(0, (float) tile.getY(), top, 0, (float) tile.getMaxY(), bottom));
            g2.fill(tile);
            g2.setStroke(new BasicStroke(Math.max(1.2f, h * 0.022f)));
            g2.setColor(mix(mix(TILE_EDGE, mix(TILE_EDGE, ac.base, 0.6f), hv), ac.topHi, op));
            g2.draw(tile);

            // round clock badge
            float cy = (float) tile.getCenterY();
            float badge = (float) tile.getHeight() * 0.62f;
            float bx = (float) tile.getX() + (float) tile.getHeight() * 0.22f;
            g2.setColor(alpha(mix(ac.base, ac.text, op), Math.round(46 + 30 * op)));
            g2.fill(new Ellipse2D.Float(bx, cy - badge / 2f, badge, badge));
            paintClock(g2, bx + badge / 2f, cy, badge * 0.24f, mix(ac.text, Color.WHITE, op * 0.35f), badge * 0.065f);

            // text: short label big, category small and muted
            String full = getText() == null ? "" : getText();
            int paren = full.lastIndexOf(" (");
            String main = titleCase(paren > 0 ? full.substring(0, paren) : full);
            String sub = paren > 0 && full.endsWith(")") ? full.substring(paren + 2, full.length() - 1) : "";
            float tx = bx + badge + (float) tile.getHeight() * 0.26f;
            Font f = getFont();
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            float base = cy + (fm.getAscent() - fm.getDescent()) / 2f;
            g2.setColor(isEnabled() ? TEXT : alpha(TEXT, 120));
            g2.drawString(main, tx, base);
            if (!sub.isEmpty()) {
                Font sf = UiFonts.subtext(f.getSize2D() * 0.56f);
                g2.setFont(sf);
                FontMetrics sm = g2.getFontMetrics();
                float sx = tx + fm.stringWidth(main) + f.getSize2D() * 0.45f;
                float pillH = sm.getHeight() * 1.05f;
                float pillW = sm.stringWidth(sub.toUpperCase()) + pillH * 0.8f;
                float chevronRoom = (float) tile.getHeight() * 0.9f;
                if (sx + pillW < tile.getMaxX() - chevronRoom) {
                    g2.setColor(alpha(AnalysisTheme.accent(this).base, Math.round(60 + 40 * op)));
                    g2.fill(new RoundRectangle2D.Float(sx, cy - pillH / 2f, pillW, pillH, pillH, pillH));
                    g2.setColor(mix(AnalysisTheme.accent(this).text, Color.WHITE, op * 0.5f));
                    g2.drawString(sub.toUpperCase(), sx + pillH * 0.4f,
                            cy + (sm.getAscent() - sm.getDescent()) / 2f);
                }
            }

            // chevron: points down closed, turns over to point up open
            float chx = (float) tile.getMaxX() - (float) tile.getHeight() * 0.45f;
            float cs = (float) tile.getHeight() * 0.12f;
            Graphics2D gc = (Graphics2D) g2.create();
            gc.translate(chx, cy);
            gc.rotate(Math.PI * op);
            gc.setColor(mix(MUTED, TEXT, Math.max(hv, op)));
            gc.setStroke(new BasicStroke(Math.max(2f, h * 0.045f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Float p = new Path2D.Float();
            p.moveTo(-cs, -cs * 0.45f);
            p.lineTo(0, cs * 0.55f);
            p.lineTo(cs, -cs * 0.45f);
            gc.draw(p);
            gc.dispose();
            g2.dispose();
        }
    }

    static void paintClock(Graphics2D g, float cx, float cy, float r, Color c, float stroke) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(c);
        g2.setStroke(new BasicStroke(Math.max(1.4f, stroke), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        g2.draw(new Line2D.Float(cx, cy, cx, cy - r * 0.6f));
        g2.draw(new Line2D.Float(cx, cy, cx + r * 0.45f, cy + r * 0.3f));
        g2.dispose();
    }

    // ── chips ─────────────────────────────────────────────────────────────────

    /**
     * One time choice. Dark tile with a thin edge; hover lifts it a shade with a purple
     * edge; the chosen one is solid purple with white text, fading in and out.
     */
    static final class Chip extends JButton {
        private final Fader hover = new Fader(this, 160f, false);
        private final Fader chosen = new Fader(this, 220f, false);
        private final java.util.function.BooleanSupplier selected;

        Chip(String label, java.util.function.BooleanSupplier selected) {
            super(label);
            this.selected = selected;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(AnalysisTheme.display(AnalysisTheme.k(24f)));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { if (isEnabled()) hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); }
            });
            addActionListener(e -> SoundManager.playExtraSound("tap.wav"));
            refresh(false);
        }

        /** Re-reads whether this chip is the chosen one (animated unless {@code instant} is false). */
        void refresh(boolean animate) {
            boolean on = selected != null && selected.getAsBoolean();
            if (animate) {
                chosen.set(on);
            } else if ((chosen.value() > 0.5f) != on) {
                chosen.set(on);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = quality(g);
            float hv = hover.value();
            float sel = chosen.value();
            int w = getWidth();
            int h = getHeight();
            float arc = Math.min(h * 0.26f, AnalysisTheme.k(14f));
            RoundRectangle2D r = new RoundRectangle2D.Float(1, 1, w - 2.5f, h - 2.5f, arc, arc);

            g2.setColor(mix(TILE, TILE_HOVER, hv));
            g2.fill(r);
            if (sel > 0.001f) {
                Composite old = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, sel));
                g2.setPaint(new GradientPaint(0, 0, mix(AnalysisTheme.accent(this).top, AnalysisTheme.accent(this).topHi, hv), 0, h,
                        mix(AnalysisTheme.accent(this).bottom, AnalysisTheme.accent(this).botHi, hv)));
                g2.fill(r);
                // glossy top band
                g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 46), 0, h * 0.55f, new Color(255, 255, 255, 0)));
                g2.fill(r);
                g2.setComposite(old);
            }
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(mix(mix(TILE_EDGE, mix(TILE_EDGE, AnalysisTheme.accent(this).base, 0.6f), hv), AnalysisTheme.accent(this).text, sel));
            g2.draw(r);

            Font f = getFont();
            String text = getText();
            FontMetrics fm = g2.getFontMetrics(f);
            float size = f.getSize2D();
            while (size > 9f && fm.stringWidth(text) > w - h * 0.4f) {
                size -= 1f;
                f = f.deriveFont(size);
                fm = g2.getFontMetrics(f);
            }
            g2.setFont(f);
            float tx = (w - fm.stringWidth(text)) / 2f;
            float ty = h / 2f + (fm.getAscent() - fm.getDescent()) / 2f;
            g2.setColor(mix(mix(new Color(222, 219, 232), TEXT, hv), Color.WHITE, sel));
            g2.drawString(text, tx, ty);
            g2.dispose();
        }
    }

    // ── section heading ───────────────────────────────────────────────────────

    static final int BULLET = 0, BLITZ = 1, RAPID = 2;

    static JComponent heading(int kind, String title) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        int s = AnalysisTheme.k(26);
        JComponent icon = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = quality(g);
                float u = getHeight() / 24f;
                g2.scale(u, u);
                g2.setColor(AnalysisTheme.accent(this).text);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = 12, cy = 12;
                if (kind == BULLET) {
                    Graphics2D gb = (Graphics2D) g2.create();
                    gb.rotate(Math.PI / 4, cx, cy);
                    gb.fill(new RoundRectangle2D.Float(cx - 4, cy - 9, 8, 15, 8, 8));
                    gb.fill(new Rectangle2D.Float(cx - 4, cy - 2, 8, 8));
                    gb.draw(new Line2D.Float(cx - 2, cy + 8, cx - 2, cy + 11));
                    gb.draw(new Line2D.Float(cx + 2, cy + 8, cx + 2, cy + 11));
                    gb.dispose();
                } else if (kind == BLITZ) {
                    Path2D.Float bolt = new Path2D.Float();
                    bolt.moveTo(cx + 3, cy - 10);
                    bolt.lineTo(cx - 6, cy + 1);
                    bolt.lineTo(cx - 1, cy + 1);
                    bolt.lineTo(cx - 3, cy + 10);
                    bolt.lineTo(cx + 6, cy - 1);
                    bolt.lineTo(cx + 1, cy - 1);
                    bolt.closePath();
                    g2.fill(bolt);
                } else {
                    g2.draw(new Ellipse2D.Float(cx - 8, cy - 6, 16, 16));
                    g2.draw(new Line2D.Float(cx, cy - 6, cx, cy - 9));
                    g2.draw(new Line2D.Float(cx - 3, cy - 10, cx + 3, cy - 10));
                    g2.draw(new Line2D.Float(cx, cy + 2, cx, cy - 2));
                }
                g2.dispose();
            }
        };
        icon.setPreferredSize(new Dimension(s, s));
        row.add(icon);
        row.add(Box.createHorizontalStrut(AnalysisTheme.k(8)));
        JLabel label = new JLabel(title);
        label.setFont(AnalysisTheme.display(AnalysisTheme.k(23f)));
        label.setForeground(TEXT);
        row.add(label);
        return row;
    }

    // ── "More Settings" link ──────────────────────────────────────────────────

    /** Muted centred text that brightens and underlines on hover, with a small chevron. */
    static final class TextLink extends JButton {
        private final Fader hover = new Fader(this, 160f, false);
        private final Fader flipped = new Fader(this, 280f, false);

        TextLink(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(UiFonts.subtext(AnalysisTheme.k(19f)));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.set(true); }
                @Override public void mouseExited(MouseEvent e) { hover.set(false); }
            });
        }

        void setFlipped(boolean on) { flipped.set(on); }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + fm.getHeight() * 2, Math.round(fm.getHeight() * 1.9f));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = quality(g);
            float hv = hover.value();
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String text = getText();
            float chevW = fm.getHeight() * 0.55f;
            float gap = fm.getHeight() * 0.3f;
            float total = fm.stringWidth(text) + gap + chevW;
            float x = (getWidth() - total) / 2f;
            float base = getHeight() / 2f + (fm.getAscent() - fm.getDescent()) / 2f;
            Color c = mix(MUTED, TEXT, hv);
            g2.setColor(c);
            g2.drawString(text, x, base);
            if (hv > 0.01f) {
                g2.setColor(alpha(c, Math.round(255 * hv)));
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(new Line2D.Float(x, base + 3, x + fm.stringWidth(text), base + 3));
            }
            float cx = x + fm.stringWidth(text) + gap + chevW / 2f;
            float cy = getHeight() / 2f;
            float cs = chevW * 0.45f;
            Graphics2D gc = (Graphics2D) g2.create();
            gc.translate(cx, cy);
            gc.rotate(Math.PI * flipped.value());
            gc.setColor(c);
            gc.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Float p = new Path2D.Float();
            p.moveTo(-cs, -cs * 0.45f);
            p.lineTo(0, cs * 0.55f);
            p.lineTo(cs, -cs * 0.45f);
            gc.draw(p);
            gc.dispose();
            g2.dispose();
        }
    }

    // ── slide + fade reveal ───────────────────────────────────────────────────

    /**
     * Holds a block that slides open/closed (height) and fades with it -- the same
     * motion as the Online screen's dropdown. {@code onTick} runs every frame so the
     * owner can follow the changing height.
     */
    static final class Reveal extends JPanel {
        static final float DURATION_MS = 300f;
        private final JComponent content;
        private final Runnable onTick;
        private float progress;
        private float from;
        private boolean open;
        private long startedAt;
        private Timer timer;

        Reveal(JComponent content, boolean open, Runnable onTick) {
            super(null);
            this.content = content;
            this.onTick = onTick;
            this.open = open;
            this.progress = open ? 1f : 0f;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            add(content);
        }

        boolean isOpen() { return open; }

        void setOpen(boolean open) {
            if (this.open == open) return;
            this.open = open;
            from = progress;
            startedAt = System.nanoTime();
            if (timer == null) timer = AnimationTiming.createUiTimer(e -> tick());
            timer.restart();
        }

        /** Jumps straight to open/closed. */
        void snap(boolean open) {
            if (timer != null) timer.stop();
            this.open = open;
            progress = open ? 1f : 0f;
            revalidate();
            repaint();
        }

        private void tick() {
            float target = open ? 1f : 0f;
            float duration = Math.max(120f, DURATION_MS * Math.abs(target - from));
            float t = AnimationTiming.progress(startedAt, duration);
            float eased = open
                    ? 1f - (float) Math.pow(1f - t, 3)
                    : (t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f);
            progress = from + (target - from) * eased;
            if (t >= 1f) {
                progress = target;
                timer.stop();
            }
            revalidate();
            repaint();
            if (onTick != null) onTick.run();
        }

        private int height() {
            return Math.round(content.getPreferredSize().height * progress);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(content.getPreferredSize().width, height()); }
        @Override public Dimension getMinimumSize() { return new Dimension(0, height()); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, height()); }

        @Override
        public void doLayout() {
            int full = content.getPreferredSize().height;
            // slides down from under whatever sits above it
            content.setBounds(0, getHeight() - full, getWidth(), full);
        }

        @Override
        public void paint(Graphics g) {
            if (progress >= 0.999f) { super.paint(g); return; }
            if (progress <= 0.001f) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, progress * progress));
            super.paint(g2);
            g2.dispose();
        }
    }
}
