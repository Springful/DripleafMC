package net.dripleaf.core.rebirth;

/**
 * The two ways up a tier.
 *
 * <p>Standard costs {@code cost} and grants cash, keys and the multiplier.
 * Soul Ascension costs {@code cost × soul-cost-multiplier} and grants
 * everything Standard does <em>plus</em> soul tokens.
 */
public enum RebirthPath {

    STANDARD("standard"),
    SOUL("soul");

    private final String id;

    RebirthPath(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
