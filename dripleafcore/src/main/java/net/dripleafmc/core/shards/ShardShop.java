package net.dripleafmc.core.shards;

import net.dripleafmc.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** shard-shop.yml. Items are pre-built at load; buying is a clone, not a rebuild. */
public final class ShardShop {

    public enum Kind { ITEM, BOOSTER }

    public record Entry(String id, Component display, List<Component> lore, Material icon,
                        long price, Kind kind, int durationHours, ItemStack stack) {}

    private final List<Entry> entries = new ArrayList<>(16);
    private Component title = Component.text("Shard Shop");
    private final Logger log;

    public ShardShop(Logger log) {
        this.log = log;
    }

    public void load(File file) {
        entries.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        title = Text.mm(yml.getString("title", "<dark_gray>Shard Shop"));
        ConfigurationSection root = yml.getConfigurationSection("items");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            Material icon = Material.matchMaterial(sec.getString("icon", "STONE"));
            if (icon == null) {
                log.warning("[DripleafCore] shard-shop.yml: unknown icon for " + id);
                continue;
            }
            Component display = Text.mm(sec.getString("display", id));
            List<Component> lore = new ArrayList<>();
            for (String line : sec.getStringList("lore")) lore.add(Text.mm(line));

            Kind kind = "BOOSTER".equalsIgnoreCase(sec.getString("type", "ITEM")) ? Kind.BOOSTER : Kind.ITEM;
            long price = sec.getLong("price", 0);
            int hours = sec.getInt("duration-hours", 24);

            ItemStack stack = null;
            if (kind == Kind.ITEM) {
                stack = new ItemStack(icon);
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.displayName(display);
                    if (!lore.isEmpty()) meta.lore(new ArrayList<>(lore));
                    stack.setItemMeta(meta);
                }
                for (String raw : sec.getStringList("enchantments")) {
                    int split = raw.lastIndexOf(':');
                    if (split <= 0) continue;
                    String name = raw.substring(0, split).toLowerCase(Locale.ROOT);
                    int level;
                    try {
                        level = Integer.parseInt(raw.substring(split + 1));
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name));
                    if (enchantment == null) {
                        log.warning("[DripleafCore] shard-shop.yml: unknown enchantment " + name);
                        continue;
                    }
                    stack.addUnsafeEnchantment(enchantment, level);
                }
            }
            entries.add(new Entry(id, display, List.copyOf(lore), icon, price, kind, hours, stack));
        }
        log.info("[DripleafCore] shard shop: " + entries.size() + " entries.");
    }

    public List<Entry> entries() {
        return entries;
    }

    public Component title() {
        return title;
    }

    public Map<String, Entry> byId() {
        Map<String, Entry> map = new LinkedHashMap<>(entries.size());
        for (Entry e : entries) map.put(e.id(), e);
        return map;
    }
}
