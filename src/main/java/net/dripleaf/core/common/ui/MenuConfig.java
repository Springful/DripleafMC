package net.dripleaf.core.common.ui;

import net.dripleaf.core.common.config.Cfg;
import net.dripleaf.core.common.config.ConfigManager;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code menus.yml} — the appearance of every screen and every button in the
 * plugin, in one file.
 *
 * <p>This is what makes "every dialog option and button is configurable"
 * true rather than aspirational. Staff can retitle a screen, relabel a button,
 * change an icon, move a chest slot, reorder a grid, add lore, or hide an entry
 * entirely, for any menu the plugin has — without a rebuild and without
 * touching code.
 *
 * <p>Templates are parsed once per reload and held immutably. Lookups are a
 * hash hit, which matters because a screen is rebuilt on every open.
 */
public final class MenuConfig {

    public static final String RESOURCE = "menus.yml";

    private final ConfigManager configs;
    private final Map<String, MenuTemplate> screens = new HashMap<>(32);

    public MenuConfig(ConfigManager configs) {
        this.configs = configs;
    }

    /** Re-reads the file. Safe at runtime — nothing caches a template across a reload. */
    public void load() {
        screens.clear();
        configs.load(RESOURCE);
        Cfg root = configs.view(RESOURCE, "screens");
        for (String id : root.keys()) {
            Cfg node = root.child(id);
            if (node != null) {
                screens.put(id, new MenuTemplate(id, node));
            }
        }
    }

    /** Never {@code null}: an unknown id yields a visible placeholder screen. */
    public MenuTemplate template(String screenId) {
        return screens.getOrDefault(screenId, MenuTemplate.missing(screenId));
    }

    public boolean has(String screenId) {
        return screens.containsKey(screenId);
    }

    public int size() {
        return screens.size();
    }

    /** Every configured screen id, for tab completion and diagnostics. */
    public java.util.Set<String> screenIds() {
        return screens.keySet();
    }
}
