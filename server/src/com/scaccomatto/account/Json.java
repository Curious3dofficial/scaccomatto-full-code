package com.scaccomatto.account;

import java.util.LinkedHashMap;
import java.util.Map;

final class Json {
    private Json() {
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
            appendValue(json, entry.getValue());
        }
        return json.append('}').toString();
    }

    static String quote(String value) {
        if (value == null) return "null";
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\b': escaped.append("\\b"); break;
                case '\f': escaped.append("\\f"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.append('"').toString();
    }

    private static void appendValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
        } else {
            json.append(quote(String.valueOf(value)));
        }
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
                    break;
                }
                expect(',');
            }
            skipWhitespace();
            if (index != source.length()) throw error("Unexpected trailing JSON");
            return values;
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
            if (start == index) throw error("Expected JSON value");
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
                if (index >= source.length()) throw error("Incomplete escape sequence");
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
                        if (index + 4 > source.length()) throw error("Incomplete unicode escape");
                        value.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                        index += 4;
                        break;
                    default: throw error("Unsupported escape sequence");
                }
            }
            throw error("Unterminated string");
        }

        private void expect(char expected) {
            if (index >= source.length() || source.charAt(index) != expected) {
                throw error("Expected '" + expected + "'");
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < source.length() && source.charAt(index) == expected;
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at character " + index);
        }
    }
}
