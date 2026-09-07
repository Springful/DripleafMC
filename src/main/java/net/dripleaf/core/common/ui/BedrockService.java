package net.dripleaf.core.common.ui;

import net.dripleaf.core.common.config.Cfg;
import net.dripleaf.core.common.hook.FloodgateBridge;
import net.dripleaf.core.common.hook.PapiBridge;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Is this player on Bedrock?"
 *
 * <p>Four signals, checked in descending order of trustworthiness. The first
 * one that gives a definite answer wins, and the answer is cached for the
 * session — a player does not change platform mid-login, and this is asked on
 * every single menu open.
 *
 * <ol>
 *   <li><b>Floodgate or Geyser.</b> Authoritative when installed. Nothing else
 *       comes close, so nothing else is consulted if this answers.</li>
 *   <li><b>The {@code %bedrock%} placeholder.</b> Configurable, because the
 *       server already publishes this and a plugin that disagrees with the
 *       scoreboard is a support ticket. Any of the configured truthy values
 *       counts as yes.</li>
 *   <li><b>The username prefix.</b> Every Bedrock account on this network
 *       carries a {@code .} in front of its name. Cheap, offline-safe, and the
 *       signal that still works when Floodgate's API moves.</li>
 *   <li><b>The Floodgate UUID shape.</b> Floodgate mints UUIDs whose most
 *       significant bits are zero. A last structural fallback rather than a
 *       primary check, since a cracked or offline-mode server can produce
 *       lookalikes.</li>
 * </ol>
 *
 * <p>The cache is bounded: entries are dropped on quit, like every other
 * per-player map in this plugin.
 */
public final class BedrockService {

    private final FloodgateBridge floodgate;
    private final PapiBridge papi;
    private final Map<UUID, Boolean> cache = new ConcurrentHashMap<>();

    private boolean useFloodgate = true;
    private String placeholder = "%bedrock%";
    private List<String> truthy = List.of("true", "yes", "1", "bedrock");
    private String usernamePrefix = ".";
    private boolean useUuidShape = true;

    public BedrockService(FloodgateBridge floodgate, PapiBridge papi) {
        this.floodgate = floodgate;
        this.papi = papi;
    }

    /** Re-reads {@code ui.bedrock-detection}. Clears the cache — detection may have changed. */
    public void configure(Cfg cfg) {
        this.useFloodgate = cfg.bool("floodgate", true);
        this.placeholder = cfg.string("placeholder", "%bedrock%");
        List<String> configured = cfg.stringList("placeholder-true-values");
        if (!configured.isEmpty()) {
            this.truthy = configured.stream()
                    .map(value -> value.toLowerCase(Locale.ROOT).trim())
                    .toList();
        }
        this.usernamePrefix = cfg.string("username-prefix", ".");
        this.useUuidShape = cfg.bool("floodgate-uuid-shape", true);
        cache.clear();
    }

    public boolean isBedrock(Player player) {
        if (player == null) {
            return false;
        }
        Boolean cached = cache.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }
        boolean result = detect(player);
        cache.put(player.getUniqueId(), result);
        return result;
    }

    private boolean detect(Player player) {
        if (useFloodgate) {
            Boolean answer = floodgate.isBedrock(player.getUniqueId());
            if (answer != null) {
                return answer;
            }
        }
        if (!placeholder.isBlank() && papi.available()) {
            String value = papi.resolve(player, placeholder);
            if (value != null && !value.isBlank() && !value.equals(placeholder)) {
                return truthy.contains(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (!usernamePrefix.isEmpty() && player.getName().startsWith(usernamePrefix)) {
            return true;
        }
        return useUuidShape && player.getUniqueId().getMostSignificantBits() == 0L;
    }

    /** Which signal answered, for {@code /uimode} and the Diagnostics screen. */
    public String explain(Player player) {
        if (useFloodgate && floodgate.isBedrock(player.getUniqueId()) != null) {
            return floodgate.detail();
        }
        if (!placeholder.isBlank() && papi.available()) {
            String value = papi.resolve(player, placeholder);
            if (value != null && !value.isBlank() && !value.equals(placeholder)) {
                return placeholder + " = " + value;
            }
        }
        if (!usernamePrefix.isEmpty() && player.getName().startsWith(usernamePrefix)) {
            return "username starts with \"" + usernamePrefix + '"';
        }
        if (useUuidShape && player.getUniqueId().getMostSignificantBits() == 0L) {
            return "Floodgate-shaped UUID";
        }
        return "no Bedrock signal";
    }

    public void forget(UUID uuid) {
        cache.remove(uuid);
    }

    public void clear() {
        cache.clear();
    }

    public String placeholder() {
        return placeholder;
    }

    public String usernamePrefix() {
        return usernamePrefix;
    }
}
