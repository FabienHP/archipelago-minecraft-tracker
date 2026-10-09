package aptracker.core.apworld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the Python literals the apworld's tests are written with: lists, dicts, strings, booleans
 * and integers, with comments and trailing commas. Anything else is rejected, so that a test file
 * using a construct this reader does not understand fails loudly instead of being skipped.
 */
final class PythonLiterals {

    /** A parsed value and the index of the first character after it. */
    record Parsed(Object value, int end) {
    }

    private final String text;
    private int position;

    private PythonLiterals(String text, int position) {
        this.text = text;
        this.position = position;
    }

    /** Parses the literal that starts at {@code start}, ignoring leading whitespace and comments. */
    static Parsed parse(String text, int start) {
        PythonLiterals reader = new PythonLiterals(text, start);
        Object value = reader.value();
        return new Parsed(value, reader.position);
    }

    private Object value() {
        skipBlank();
        char c = peek();
        if (c == '[') {
            return list();
        } else if (c == '{') {
            return dict();
        } else if (c == '\'' || c == '"') {
            return string();
        } else if (c == '-' || Character.isDigit(c)) {
            return integer();
        } else if (accept("True")) {
            return Boolean.TRUE;
        } else if (accept("False")) {
            return Boolean.FALSE;
        }
        throw error("Unsupported Python expression");
    }

    private List<Object> list() {
        position++;
        List<Object> values = new ArrayList<>();
        while (true) {
            skipBlank();
            if (peek() == ']') {
                position++;
                return values;
            }
            values.add(value());
            skipBlank();
            if (peek() == ',') {
                position++;
            } else if (peek() != ']') {
                throw error("Expected ',' or ']'");
            }
        }
    }

    private Map<String, Object> dict() {
        position++;
        Map<String, Object> values = new LinkedHashMap<>();
        while (true) {
            skipBlank();
            if (peek() == '}') {
                position++;
                return values;
            }
            String key = string();
            skipBlank();
            if (peek() != ':') {
                throw error("Expected ':'");
            }
            position++;
            values.put(key, value());
            skipBlank();
            if (peek() == ',') {
                position++;
            } else if (peek() != '}') {
                throw error("Expected ',' or '}'");
            }
        }
    }

    private String string() {
        char quote = peek();
        if (quote != '\'' && quote != '"') {
            throw error("Expected a string");
        }
        position++;
        StringBuilder result = new StringBuilder();
        while (true) {
            char c = peek();
            position++;
            if (c == quote) {
                return result.toString();
            } else if (c == '\\') {
                char escaped = peek();
                position++;
                if (escaped != '\\' && escaped != '\'' && escaped != '"') {
                    throw error("Unsupported escape sequence");
                }
                result.append(escaped);
            } else if (c == '\n') {
                throw error("Unterminated string");
            } else {
                result.append(c);
            }
        }
    }

    private Long integer() {
        int start = position;
        if (peek() == '-') {
            position++;
        }
        while (position < text.length() && Character.isDigit(text.charAt(position))) {
            position++;
        }
        return Long.parseLong(text.substring(start, position));
    }

    private boolean accept(String word) {
        if (!text.startsWith(word, position)) {
            return false;
        }
        int end = position + word.length();
        if (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
            return false;
        }
        position = end;
        return true;
    }

    private void skipBlank() {
        while (position < text.length()) {
            char c = text.charAt(position);
            if (Character.isWhitespace(c)) {
                position++;
            } else if (c == '#') {
                while (position < text.length() && text.charAt(position) != '\n') {
                    position++;
                }
            } else {
                return;
            }
        }
    }

    private char peek() {
        if (position >= text.length()) {
            throw error("Unexpected end of file");
        }
        return text.charAt(position);
    }

    private IllegalArgumentException error(String message) {
        int line = 1;
        for (int i = 0; i < Math.min(position, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        int end = Math.min(text.length(), position + 40);
        String excerpt = text.substring(Math.min(position, text.length()), end).replace("\n", "\\n");
        return new IllegalArgumentException(message + " at line " + line + ": " + excerpt);
    }
}
