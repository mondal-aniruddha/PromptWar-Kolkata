package com.mindmate.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Json {
    public static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof Map<?, ?> map) return objectToJson(map);
        if (value instanceof Collection<?> collection) return arrayToJson(collection);
        return beanToJson(value);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        Object parsed = new Parser(json).parseValue();
        try {
            T instance = type.getDeclaredConstructor().newInstance();
            if (!(parsed instanceof Map<?, ?> map)) {
                return instance;
            }
            for (Field field : type.getFields()) {
                Object raw = map.get(field.getName());
                if (raw == null) continue;
                if (field.getType() == String.class) field.set(instance, String.valueOf(raw));
                if (field.getType() == int.class) field.setInt(instance, ((Number) raw).intValue());
                if (field.getType() == double.class) field.setDouble(instance, ((Number) raw).doubleValue());
                if (field.getType() == boolean.class) field.setBoolean(instance, Boolean.TRUE.equals(raw));
                if (List.class.isAssignableFrom(field.getType()) && raw instanceof List<?>) field.set(instance, raw);
            }
            return instance;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Cannot parse JSON for " + type.getSimpleName(), exception);
        }
    }

    private static String objectToJson(Map<?, ?> map) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            parts.add(quote(String.valueOf(entry.getKey())) + ":" + toJson(entry.getValue()));
        }
        return "{" + String.join(",", parts) + "}";
    }

    private static String arrayToJson(Collection<?> values) {
        return "[" + String.join(",", values.stream().map(Json::toJson).toList()) + "]";
    }

    private static String beanToJson(Object bean) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Field field : bean.getClass().getFields()) {
            try {
                values.put(field.getName(), field.get(bean));
            } catch (IllegalAccessException ignored) {
            }
        }
        return objectToJson(values);
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private static class Parser {
        private final String text;
        private int index = 0;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        Object parseValue() {
            skipWhitespace();
            if (peek('{')) return parseObject();
            if (peek('[')) return parseArray();
            if (peek('"')) return parseString();
            if (startsWith("true")) {
                index += 4;
                return true;
            }
            if (startsWith("false")) {
                index += 5;
                return false;
            }
            if (startsWith("null")) {
                index += 4;
                return null;
            }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            index++;
            skipWhitespace();
            while (!peek('}')) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                map.put(key, parseValue());
                skipWhitespace();
                if (peek(',')) index++;
                skipWhitespace();
            }
            index++;
            return map;
        }

        private List<Object> parseArray() {
            List<Object> values = new ArrayList<>();
            index++;
            skipWhitespace();
            while (!peek(']')) {
                values.add(parseValue());
                skipWhitespace();
                if (peek(',')) index++;
                skipWhitespace();
            }
            index++;
            return values;
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == '"') break;
                if (current == '\\' && index < text.length()) {
                    char escaped = text.charAt(index++);
                    result.append(switch (escaped) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case '"' -> '"';
                        case '\\' -> '\\';
                        default -> escaped;
                    });
                } else {
                    result.append(current);
                }
            }
            return result.toString();
        }

        private Number parseNumber() {
            int start = index;
            while (index < text.length() && "-0123456789.".indexOf(text.charAt(index)) >= 0) index++;
            String number = text.substring(start, index);
            return number.contains(".") ? Double.parseDouble(number) : Integer.parseInt(number);
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
        }

        private boolean startsWith(String expected) {
            return text.startsWith(expected, index);
        }

        private void expect(char expected) {
            if (!peek(expected)) throw new IllegalArgumentException("Expected '" + expected + "' at " + index);
            index++;
        }
    }
}
