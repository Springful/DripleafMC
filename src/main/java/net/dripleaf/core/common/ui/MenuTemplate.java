package net.dripleaf.core.common.ui;

import net.dripleaf.core.common.config.Cfg;
import net.dripleaf.core.common.text.Ctx;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One screen's appearance, read from {@code menus.yml}.
 *
 * <p>Supplies the title, the body lines, the chest layout and the dialog
 * options, plus a {@link ButtonTemplate} per button. A screen builder starts
 * from {@link #screen(Ctx)} and only ever adds behaviour and live data.
 *
 * <p>An unknown screen id yields a loud placeholder rather than a crash, so a
 * typo in config is visible in game instead of taking a menu down.
 */
public final class MenuTemplate {

    private final String id;
    private final String title;
    private final List<String> body;

    private final InventoryType chestType;
    private final int rows;
    private final boolean decorated;
    private final Material filler;
    private final int headerSlot;

    private final int columns;
    private final int buttonWidth;
    private final boolean closeable;

    private final Map<String, ButtonTemplate> buttons = new HashMap<>(16);

    MenuTemplate(String id, Cfg cfg) {
        this.id = id;
        this.title = cfg.string("title", id);
        this.body = List.copyOf(cfg.stringList("body"));

        Cfg chest = cfg.childOrEmpty("chest");
        this.chestType = "HOPPER".equalsIgnoreCase(chest.string("type", "CHEST"))
                ? InventoryType.HOPPER : InventoryType.CHEST;
        this.rows = chest.integer("rows", 0, 0, 6);
        this.decorated = chest.bool("decorated", true);
        this.filler = net.dripleaf.core.common.icon.IconService.material(
                chest.string("filler", ""), Material.GRAY_STAINED_GLASS_PANE);
        this.headerSlot = chest.integer("header-slot", 4, -1, 53);

        Cfg dialog = cfg.childOrEmpty("dialog");
        this.columns = dialog.integer("columns", 1, 1, 4);
        this.buttonWidth = dialog.integer("button-width", 220, 1, 1024);
        this.closeable = cfg.bool("closeable", true);

        Cfg buttonSection = cfg.childOrEmpty("buttons");
        for (String key : buttonSection.keys()) {
            Cfg node = buttonSection.child(key);
            if (node != null) {
                buttons.put(key, new ButtonTemplate(key, node, null));
            }
        }
    }

    /** Placeholder for a screen id that {@code menus.yml} does not define. */
    static MenuTemplate missing(String id) {
        return new MenuTemplate(id, true);
    }

    private MenuTemplate(String id, boolean missing) {
        this.id = id;
        this.title = "<#FF5555>Missing screen: " + id + "</#FF5555>";
        this.body = List.of(
                "<#AAAAAA>This screen has no entry in menus.yml.</#AAAAAA>",
                "<#555555>Add one under screens." + id + "</#555555>");
        this.chestType = InventoryType.CHEST;
        this.rows = 3;
        this.decorated = true;
        this.filler = Material.GRAY_STAINED_GLASS_PANE;
        this.headerSlot = 4;
        this.columns = 1;
        this.buttonWidth = 220;
        this.closeable = true;
    }

    public String id() {
        return id;
    }

    public String title(Ctx ctx) {
        return ctx == null ? title : ctx.applyRaw(title);
    }

    public List<String> body(Ctx ctx) {
        if (ctx == null) {
            return body;
        }
        List<String> out = new ArrayList<>(body.size());
        for (String line : body) {
            out.add(ctx.applyRaw(line));
        }
        return out;
    }

    /** Never {@code null}: an unconfigured button id yields a visible placeholder. */
    public ButtonTemplate button(String buttonId) {
        return buttons.getOrDefault(buttonId, ButtonTemplate.missing(buttonId));
    }

    public boolean hasButton(String buttonId) {
        return buttons.containsKey(buttonId);
    }

    /** Every configured button id, in no particular order. */
    public java.util.Set<String> buttonIds() {
        return buttons.keySet();
    }

    public ChestLayout layout() {
        ChestLayout layout = chestType == InventoryType.HOPPER
                ? ChestLayout.hopper()
                : rows > 0 ? ChestLayout.chest(rows) : ChestLayout.auto();
        layout = layout.withFiller(filler).withHeaderSlot(headerSlot);
        return decorated ? layout : layout.undecorated();
    }

    /**
     * A {@link Screen.Builder} pre-filled with everything config owns. Callers
     * add buttons and any dynamic body lines.
     */
    public Screen.Builder screen(Ctx ctx) {
        return Screen.of(id, title(ctx))
                .lines(body(ctx))
                .layout(layout())
                .dialogColumns(columns)
                .dialogButtonWidth(buttonWidth)
                .closeable(closeable);
    }

    /** As {@link #screen(Ctx)}, with a hero item shown large in the dialog. */
    public Screen.Builder screen(Ctx ctx, ItemStack hero) {
        return screen(ctx).hero(hero);
    }
}
