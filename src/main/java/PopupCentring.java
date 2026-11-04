import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.LayoutManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ContainerEvent;
import java.awt.event.WindowEvent;

/**
 * Every pop-up in the app lines its contents up down the middle: titles, messages,
 * field names and rows of buttons are centred, wherever the pop-up was built.
 *
 * <p>Installed once at start-up. It watches for pop-up windows opening (and for things
 * being added to a pop-up that's already open, like the Account pop-up switching from
 * Sign in to Create account) and centres what it finds:
 * <ul>
 *   <li>a label standing on its own line gets centred text;</li>
 *   <li>things stacked in a column are centred in that column;</li>
 *   <li>a row that was packed to the left is packed to the middle.</li>
 * </ul>
 * Text boxes, lists, tables and the insides of buttons are left alone (typing and
 * scrolling lists read best from the left), as is anything laid out by hand.
 */
final class PopupCentring {
    private PopupCentring() { }

    private static boolean installed;

    static synchronized void install() {
        if (installed) return;
        installed = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof WindowEvent && event.getID() == WindowEvent.WINDOW_OPENED) {
                Window w = ((WindowEvent) event).getWindow();
                if (w instanceof JDialog) centre((JDialog) w);
            } else if (event instanceof ContainerEvent && event.getID() == ContainerEvent.COMPONENT_ADDED) {
                Component child = ((ContainerEvent) event).getChild();
                Window w = SwingUtilities.getWindowAncestor(child);
                if (w instanceof JDialog && w.isShowing()) {
                    SwingUtilities.invokeLater(() -> centre((JDialog) w));
                }
            }
        }, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
    }

    /** Centres everything in this pop-up (also usable directly, before it's shown). */
    static void centre(JDialog dialog) {
        Container content = dialog.getContentPane();
        if (content == null) return;
        if (centreTree(content)) {
            dialog.getRootPane().validate();
            dialog.repaint();
        }
    }

    private static boolean centreTree(Container parent) {
        boolean changed = false;
        LayoutManager layout = parent.getLayout();
        if (layout instanceof FlowLayout) {
            FlowLayout flow = (FlowLayout) layout;
            int a = flow.getAlignment();
            if (a == FlowLayout.LEFT || a == FlowLayout.LEADING) {
                flow.setAlignment(FlowLayout.CENTER);
                changed = true;
            }
        }
        boolean column = layout instanceof BoxLayout && ((BoxLayout) layout).getAxis() == BoxLayout.Y_AXIS
                || layout instanceof BoxLayout && ((BoxLayout) layout).getAxis() == BoxLayout.PAGE_AXIS;
        boolean border = layout instanceof BorderLayout;
        for (Component c : parent.getComponents()) {
            if (column && c instanceof JComponent
                    && ((JComponent) c).getAlignmentX() != Component.CENTER_ALIGNMENT) {
                ((JComponent) c).setAlignmentX(Component.CENTER_ALIGNMENT);
                changed = true;
            }
            if (c instanceof JLabel && (column || border || layout instanceof FlowLayout)) {
                JLabel l = (JLabel) c;
                int h = l.getHorizontalAlignment();
                if (h == SwingConstants.LEFT || h == SwingConstants.LEADING) {
                    l.setHorizontalAlignment(SwingConstants.CENTER);
                    changed = true;
                }
                // wrapped (HTML) text lines up inside its own box: centre those lines too
                String text = l.getText();
                if (text != null && text.regionMatches(true, 0, "<html>", 0, 6)
                        && !text.toLowerCase().contains("text-align")) {
                    l.setText("<html><div style='text-align:center'>" + text.substring(6)
                            .replaceAll("(?i)</html>\\s*$", "") + "</div></html>");
                    changed = true;
                }
            }
            // scrolling content: centre what's inside, unless it's a list or table
            if (c instanceof JScrollPane) {
                Component view = ((JScrollPane) c).getViewport().getView();
                if (view instanceof Container && !(view instanceof JList) && !(view instanceof JTable)
                        && !(view instanceof JTextComponent)) {
                    changed |= centreTree((Container) view);
                }
                continue;
            }
            // leave typing, lists, tables and the insides of buttons as they are
            if (c instanceof JTextComponent || c instanceof JList || c instanceof JTable
                    || c instanceof AbstractButton) {
                continue;
            }
            if (c instanceof Container && !(c instanceof JLabel)) {
                changed |= centreTree((Container) c);
            }
        }
        // the layout keeps sizes from before: make it measure again
        if (changed) parent.invalidate();
        return changed;
    }
}
