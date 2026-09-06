package net.dripleafmc.core.shop;

import net.dripleafmc.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** shop.yml, parsed once into immutable records. Nothing here is read at click time. */
public final class Shop {

    public record Entry(Material material, Component display, double price) {}

    public record Category(String id, Component display, Component lore, Material icon, List<Entry> items) {}

    private final List<Category> categories = new ArrayList<>(8);
    private final List<Entry> allItems = new ArrayList<>(64);
    private final java.util.Map<Material, Entry> byMaterial = new java.util.EnumMap<>(Material.class);
    private Component title = Component.text("Shop");
    private final Logger log;

    public Shop(Logger log) {
        this.log = log;
    }

    public void load(File file, java.util.function.ToDoubleFunction<Material> fallbackPrice) {
        categories.clear();
        allItems.clear();
        byMaterial.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        title = Text.mm(yml.getString("title", "<dark_gray>Shop"));
        ConfigurationSection root = yml.getConfigurationSection("categories");
        if (root == null) {
            log.warning("[DripleafCore] shop.yml has no 'categories' section.");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            Material icon = Material.matchMaterial(sec.getString("icon", "STONE"));
            if (icon == null) icon = Material.STONE;

            List<Entry> entries = new ArrayList<>();
            for (Map<?, ?> raw : sec.getMapList("items")) {
                Object materialName = raw.get("material");
                if (materialName == null) continue;
                Material material = Material.matchMaterial(String.valueOf(materialName));
                if (material == null) {
                    log.warning("[DripleafCore] shop.yml: unknown material " + materialName);
                    continue;
                }
                Object priceRaw = raw.get("price");
                double price = priceRaw instanceof Number n
                        ? n.doubleValue()
                        : fallbackPrice.applyAsDouble(material);
                if (price <= 0) continue;
                entries.add(new Entry(material, Component.text(Text.pretty(material)), price));
            }
            categories.add(new Category(
                    id,
                    Text.mm(sec.getString("display", id)),
                    Text.mm(sec.getString("lore", "")),
                    icon,
                    List.copyOf(entries)));
        }
        for (Category category : categories) {
            for (Entry entry : category.items()) {
                if (byMaterial.putIfAbsent(entry.material(), entry) == null) allItems.add(entry);
            }
        }
        allItems.sort(java.util.Comparator.comparing(e -> Text.pretty(e.material())));
        log.info("[DripleafCore] shop: " + categories.size() + " categories, "
                + allItems.size() + " purchasable items.");
    }

    public List<Category> categories() {
        return categories;
    }

    /** Every purchasable entry, flattened. Rebuilt only on load, never per menu open. */
    public List<Entry> allItems() {
        return allItems;
    }

    /** Null when the server does not sell this material — the quick buy guard. */
    public Entry find(Material material) {
        return byMaterial.get(material);
    }

    public Component title() {
        return title;
    }
}
