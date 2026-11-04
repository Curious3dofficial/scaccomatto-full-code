import java.util.LinkedHashMap;
import java.util.Map;

final class AccountJson {
    private AccountJson() {
    }

    static Map<String, String> parseObject(String source) {
        Parser parser = new Parser(source == null ? "" : source);
        return parser.parseObject();
    }

    static String object(Map<String, ?> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append(quote(entry.getKey())).append(':');
            Object value = entry.getValue();
            if (value == null) {
                json.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append(quote(String.valueOf(value)));
            }
        }
        return json.append('}').toString();
    }

    private static String quote(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default: escaped.append(c);
            }
        }
        return escaped.append('"').toString();
    }

    private static final class Parser {
        private final String source;
        private int index;

        Parser(String source) {
            this.source = source;
        }

        Map<String, String> parseObject() {
            skipWhitespace();
            expect('{');
            Map<String, String> values = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return values;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                values.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return values;
                }
                expect(',');
            }
        }

        private String parseValue() {
            if (peek('"')) return parseString();
            if (source.startsWith("null", index)) {
                index += 4;
                return null;
            }
            int start = index;
            while (index < source.length()) {
                char c = source.charAt(index);
                if (c == ',' || c == '}' || Character.isWhitespace(c)) break;
                index++;
            }
            if (start == index) throw new IllegalArgumentException("Expected JSON value");
            return source.substring(start, index);
        }

        private String parseString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < source.length()) {
                char c = source.charAt(index++);
                if (c == '"') return value.toString();
                if (c != '\\') {
                    value.append(c);
                    continue;
                }
                if (index >= source.length()) throw new IllegalArgumentException("Invalid JSON escape");
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"': value.append('"'); break;
                    case '\\': value.append('\\'); break;
                    case '/': value.append('/'); break;
                    case 'b': value.append('\b'); break;
                    case 'f': value.append('\f'); break;
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    case 'u':
                        if (index + 4 > source.length()) {
                            throw new IllegalArgumentException("Invalid unicode escape");
                        }
                        value.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                        index += 4;
                        break;
                    default: throw new IllegalArgumentException("Invalid JSON escape");
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private void expect(char expected) {
            if (index >= source.length() || source.charAt(index) != expected) {
                throw new IllegalArgumentException("Invalid JSON object");
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < source.length() && source.charAt(index) == expected;
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }
    }
}
