package net.dripleaf.core.common.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;

/**
 * Range-checked reads over a {@link ConfigurationSection}.
 *
 * <p>Every accessor takes a default and a {@link ValidationLog}. A value that is
 * the wrong type or out of range logs one specific line and returns the default,
 * rather than throwing halfway through a load and leaving the plugin
 * half-configured.
 */
public final class Cfg {

    private final ConfigurationSection section;
    private final String file;
    private final String basePath;
    private final ValidationLog log;

    public Cfg(ConfigurationSection section, String file, ValidationLog log) {
        this(section, file, "", log);
    }

    private Cfg(ConfigurationSection section, String file, String basePath, ValidationLog log) {
        this.section = section;
        this.file = file;
        this.basePath = basePath;
        this.log = log;
    }

    /** {@code null} when the child does not exist. */
    public Cfg child(String path) {
        ConfigurationSection child = section == null ? null : section.getConfigurationSection(path);
        return child == null ? null : new Cfg(child, file, qualify(path), log);
    }

    /** An always-present child, empty when absent — for optional blocks. */
    public Cfg childOrEmpty(String path) {
        Cfg child = child(path);
        return child != null ? child : new Cfg(null, file, qualify(path), log);
    }

    public boolean exists() {
        return section != null;
    }

    public ConfigurationSection raw() {
        return section;
    }

    public java.util.Set<String> keys() {
        return section == null ? java.util.Set.of() : section.getKeys(false);
    }

    public boolean contains(String path) {
        return section != null && section.contains(path);
    }

    public String string(String path, String fallback) {
        if (section == null) {
            return fallback;
        }
        String value = section.getString(path);
        return value == null ? fallback : value;
    }

    public List<String> stringList(String path) {
        return section == null ? List.of() : section.getStringList(path);
    }

    public boolean bool(String path, boolean fallback) {
        return section == null ? fallback : section.getBoolean(path, fallback);
    }

    public int integer(String path, int fallback, int min, int max) {
        if (section == null || !section.contains(path)) {
            return fallback;
        }
        if (!section.isInt(path)) {
            log.add(file, qualify(path), "expected a whole number, using " + fallback);
            return fallback;
        }
        int value = section.getInt(path);
        if (value < min || value > max) {
            log.add(file, qualify(path),
                    value + " is outside " + min + ".." + max + ", using " + fallback);
            return fallback;
        }
        return value;
    }

    public int integer(String path, int fallback) {
        return integer(path, fallback, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public long duration(String path, long fallbackSeconds) {
        return (long) number(path, fallbackSeconds, 0d, Double.MAX_VALUE);
    }

    public double number(String path, double fallback, double min, double max) {
        if (section == null || !section.contains(path)) {
            return fallback;
        }
        Object value = section.get(path);
        if (!(value instanceof Number number)) {
            log.add(file, qualify(path), "expected a number, using " + fallback);
            return fallback;
        }
        double result = number.doubleValue();
        if (Double.isNaN(result) || Double.isInfinite(result) || result < min || result > max) {
            log.add(file, qualify(path), result + " is not usable here, using " + fallback);
            return fallback;
        }
        return result;
    }

    public double number(String path, double fallback) {
        return number(path, fallback, -Double.MAX_VALUE, Double.MAX_VALUE);
    }

    public <E extends Enum<E>> E enumValue(String path, Class<E> type, E fallback) {
        String raw = string(path, "");
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            log.add(file, qualify(path), '"' + raw + "\" is not a valid "
                    + type.getSimpleName() + ", using " + fallback);
            return fallback;
        }
    }

    public ValidationLog log() {
        return log;
    }

    public String file() {
        return file;
    }

    /** Fully qualified path of a child key, for log lines. */
    public String qualify(String path) {
        return basePath.isEmpty() ? path : basePath + '.' + path;
    }
}
