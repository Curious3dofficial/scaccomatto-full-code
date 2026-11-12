import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;

/**
 * Ctrl+Backspace deletes the word before the cursor and Ctrl+Delete the word after it, in
 * every text box in the app (like everywhere else on the computer). In a password box,
 * where the words can't be seen, they clear everything before / after the cursor.
 * Installed once at start-up: each text box gets the keys the first time it's focused.
 */
final class TextEditKeys {
    private TextEditKeys() { }

    private static final String DONE = "textEditKeys.installed";

    static void install() {
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getID() != FocusEvent.FOCUS_GAINED) return;
            Object src = e.getSource();
            if (src instanceof JTextComponent) addTo((JTextComponent) src);
        }, AWTEvent.FOCUS_EVENT_MASK);
    }

    static void addTo(JTextComponent t) {
        if (Boolean.TRUE.equals(t.getClientProperty(DONE))) return;
        t.putClientProperty(DONE, Boolean.TRUE);
        InputMap in = t.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = t.getActionMap();
        in.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, KeyEvent.CTRL_DOWN_MASK), "app-delete-prev-word");
        in.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, KeyEvent.CTRL_DOWN_MASK), "app-delete-next-word");
        am.put("app-delete-prev-word", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { deleteWord(t, true); }
        });
        am.put("app-delete-next-word", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { deleteWord(t, false); }
        });
    }

    private static void deleteWord(JTextComponent t, boolean back) {
        if (!t.isEditable() || !t.isEnabled()) return;
        try {
            int start = t.getSelectionStart(), end = t.getSelectionEnd();
            if (start != end) {                       // a selection: just delete it
                t.getDocument().remove(start, end - start);
                return;
            }
            String s = t.getDocument().getText(0, t.getDocument().getLength());
            int caret = t.getCaretPosition();
            // (a password-style box that's showing its text, like the app's own fields, counts as normal)
            boolean hidden = t instanceof JPasswordField && ((JPasswordField) t).echoCharIsSet();
            if (back) {
                int i = hidden ? 0 : wordStart(s, caret);
                if (i < caret) t.getDocument().remove(i, caret - i);
            } else {
                int j = hidden ? s.length() : wordEnd(s, caret);
                if (j > caret) t.getDocument().remove(caret, j - caret);
            }
        } catch (BadLocationException ignored) {
        }
    }

    /** Back over spaces, then over one run of letters/digits (or one run of other symbols). */
    private static int wordStart(String s, int caret) {
        int i = caret;
        while (i > 0 && Character.isWhitespace(s.charAt(i - 1))) i--;
        if (i > 0 && isWord(s.charAt(i - 1))) {
            while (i > 0 && isWord(s.charAt(i - 1))) i--;
        } else {
            while (i > 0 && !isWord(s.charAt(i - 1)) && !Character.isWhitespace(s.charAt(i - 1))) i--;
        }
        return i;
    }

    private static int wordEnd(String s, int caret) {
        int j = caret, n = s.length();
        while (j < n && Character.isWhitespace(s.charAt(j))) j++;
        if (j < n && isWord(s.charAt(j))) {
            while (j < n && isWord(s.charAt(j))) j++;
        } else {
            while (j < n && !isWord(s.charAt(j)) && !Character.isWhitespace(s.charAt(j))) j++;
        }
        return j;
    }

    private static boolean isWord(char c) { return Character.isLetterOrDigit(c) || c == '_'; }
}
