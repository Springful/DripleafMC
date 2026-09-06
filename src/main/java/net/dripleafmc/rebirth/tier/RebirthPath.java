package net.dripleafmc.rebirth.tier;

import java.util.Locale;

public enum RebirthPath {

    STANDARD,
    SOUL;

    private final String id = name().toLowerCase(Locale.ROOT);

    public String id() {
        return id;
    }

    public static RebirthPath parse(String input) {
        if (input == null) {
            return null;
        }
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "standard", "normal" -> STANDARD;
            case "soul", "ascension", "soulascension" -> SOUL;
            default -> null;
        };
    }
}
