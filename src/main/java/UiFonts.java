import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class UiFonts {
    private static final Font CINZEL = loadFont("/assets/fonts/Cinzel.ttf", "src/assets/fonts/Cinzel.ttf", "Serif");
    private static final Font INTER = loadFont("/assets/fonts/Inter.ttf", "src/assets/fonts/Inter.ttf", "SansSerif");

    private UiFonts() {
    }

    public static Font title(float size) {
        return derive(CINZEL, size, TextAttribute.WEIGHT_BOLD);
    }

    public static Font titleSemiBold(float size) {
        return derive(CINZEL, size, TextAttribute.WEIGHT_SEMIBOLD);
    }

    public static Font trackedTitle(float size, float tracking) {
        Map<TextAttribute, Object> attributes = new HashMap<>();
        attributes.put(TextAttribute.SIZE, size);
        attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
        attributes.put(TextAttribute.TRACKING, tracking);
        return CINZEL.deriveFont(attributes);
    }

    public static Font subtext(float size) {
        return derive(INTER, size, TextAttribute.WEIGHT_MEDIUM);
    }

    public static Font uiRegular(float size) {
        return derive(INTER, size, TextAttribute.WEIGHT_REGULAR);
    }

    private static Font derive(Font base, float size, Float weight) {
        Map<TextAttribute, Object> attributes = new HashMap<>();
        attributes.put(TextAttribute.SIZE, size);
        attributes.put(TextAttribute.WEIGHT, weight);
        return base.deriveFont(attributes);
    }

    private static Font loadFont(String resourcePath, String filePath, String fallback) {
        try (InputStream stream = UiFonts.class.getResourceAsStream(resourcePath)) {
            if (stream != null) {
                return register(Font.createFont(Font.TRUETYPE_FONT, stream));
            }
        } catch (Exception ignored) {
        }

        try {
            File file = new File(filePath);
            if (file.isFile()) {
                return register(Font.createFont(Font.TRUETYPE_FONT, file));
            }
        } catch (Exception ignored) {
        }
        return new Font(fallback, Font.PLAIN, 12);
    }

    private static Font register(Font font) {
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
        return font;
    }
}
