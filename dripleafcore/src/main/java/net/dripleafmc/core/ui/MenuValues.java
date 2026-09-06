package net.dripleafmc.core.ui;

import java.util.Map;

/** Whatever the viewer typed or dragged, normalised across both renderers. */
public record MenuValues(Map<String, Object> raw) {

    public float number(String key, float fallback) {
        Object v = raw.get(key);
        return v instanceof Number n ? n.floatValue() : fallback;
    }

    public int intVal(String key, int fallback) {
        return Math.round(number(key, fallback));
    }

    public String text(String key, String fallback) {
        Object v = raw.get(key);
        return v instanceof String s && !s.isBlank() ? s : fallback;
    }

    public boolean bool(String key, boolean fallback) {
        Object v = raw.get(key);
        return v instanceof Boolean b ? b : fallback;
    }
}
