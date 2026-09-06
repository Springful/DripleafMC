package net.dripleafmc.rebirth.tier;

import java.util.List;

/**
 * What one path costs and which reward-sets it fires.
 * Requirements are shared by both paths and live on {@link RebirthTier}.
 */
public record PathSpec(double cost, List<String> rewardSets) {
}
