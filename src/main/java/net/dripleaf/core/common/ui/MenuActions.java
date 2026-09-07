package net.dripleaf.core.common.ui;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.text.Ctx;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Turns an {@code action:} string in {@code menus.yml} into something a button
 * actually does.
 *
 * <p>This is what lets staff add, remove and reorder entries on a menu without
 * a code change: a button is a label, an icon, a slot and an action string.
 *
 * <table>
 *   <caption>Supported forms</caption>
 *   <tr><td>{@code command:home}</td><td>runs {@code /home} as the player</td></tr>
 *   <tr><td>{@code console:give <player> diamond 1}</td><td>runs on the console; {@code <player>} is substituted</td></tr>
 *   <tr><td>{@code screen:my-screen}</td><td>opens another screen defined in {@code menus.yml}</td></tr>
 *   <tr><td>{@code message:some.key}</td><td>sends a {@code messages.yml} key</td></tr>
 *   <tr><td>{@code close}</td><td>closes the menu</td></tr>
 *   <tr><td>{@code none}</td><td>does nothing — decorative</td></tr>
 * </table>
 *
 * <p>Anything else is looked up in the {@link #register registry}, which is how
 * Java-backed entries — the shop, the rebirth menu, the UI-mode picker — are
 * reachable from config by a stable name rather than by class.
 */
public final class MenuActions {

    private final Services services;
    private final Map<String, Consumer<Player>> registry = new HashMap<>(32);

    public MenuActions(Services services) {
        this.services = services;
    }

    /**
     * Publishes a named action for config to reference.
     *
     * @param id what {@code menus.yml} writes in {@code action:}, e.g. {@code shop}
     */
    public void register(String id, Consumer<Player> action) {
        registry.put(id.toLowerCase(Locale.ROOT), action);
    }

    public boolean has(String id) {
        return registry.containsKey(id.toLowerCase(Locale.ROOT));
    }

    /** Never {@code null}: an unresolvable action reports itself in chat. */
    public Consumer<Player> resolve(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("none")) {
            return player -> {
            };
        }
        String action = raw.trim();
        int colon = action.indexOf(':');
        String prefix = colon < 0 ? action : action.substring(0, colon);
        String value = colon < 0 ? "" : action.substring(colon + 1).trim();

        switch (prefix.toLowerCase(Locale.ROOT)) {
            case "command", "cmd", "player" -> {
                return player -> {
                    services.ui().close(player);
                    player.performCommand(stripSlash(value));
                };
            }
            case "console" -> {
                return player -> {
                    services.ui().close(player);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            Ctx.of("player", player.getName())
                                    .applyRaw(stripSlash(value)));
                };
            }
            case "screen", "menu" -> {
                return player -> open(player, value);
            }
            case "message", "msg" -> {
                return player -> services.messages().send(player, value);
            }
            case "close" -> {
                return player -> services.ui().close(player);
            }
            default -> {
                Consumer<Player> registered = registry.get(action.toLowerCase(Locale.ROOT));
                if (registered != null) {
                    return registered;
                }
                return player -> services.messages().send(player, "ui.unknown-action",
                        Ctx.of("action", action));
            }
        }
    }

    /**
     * Builds and opens a screen defined entirely in {@code menus.yml}.
     *
     * <p>Used for the player menu and any nested menu staff add. Buttons are
     * ordered by their configured slot where one is set, so the chest and
     * dialog forms agree on reading order.
     */
    public void open(Player player, String screenId) {
        MenuTemplate menu = services.menus().template(screenId);
        Ctx ctx = new Ctx().put("player", player.getName());
        Screen.Builder builder = menu.screen(ctx);

        menu.buttonIds().stream()
                .map(menu::button)
                .filter(template -> !template.hidden())
                .sorted(java.util.Comparator.comparingInt(
                        template -> template.slot() < 0 ? Integer.MAX_VALUE : template.slot()))
                .forEach(template -> {
                    ButtonTemplate.Builder button = template.builder(ctx);
                    if (button == null) {
                        return;
                    }
                    if (!template.permission().isBlank()
                            && !player.hasPermission(template.permission())) {
                        button.locked(Ctx.of("permission", template.permission())
                                .applyRaw(services.messages().raw("ui.button-locked")));
                    } else {
                        button.action(resolve(template.action()));
                    }
                    builder.button(button.build());
                });

        services.sounds().play(player,
                net.dripleaf.core.common.sound.SoundService.MENU_OPEN);
        services.ui().open(player, builder.build());
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
