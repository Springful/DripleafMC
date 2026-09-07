package net.dripleaf.core.common.ui;

import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.MessageService;
import net.dripleaf.core.common.text.Palette;
import net.dripleaf.core.common.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws a {@link Screen} as a chest GUI.
 *
 * <p>Everything the dialog renderer can do, this does too — that is the
 * contract. Body text becomes the lore of a header item, buttons become items,
 * a locked button becomes a grey item that still states its reason, and a text
 * input becomes a {@link ChatPrompt}. Anything that does not fit on one page is
 * paginated rather than truncated.
 *
 * <p>Layout, when the screen does not specify one:
 * <pre>
 *   row 0        header item (screen title + body), centred
 *   rows 1..n-2  buttons, columns 1-7, left to right
 *   row n-1      ◀ previous · close · next ▶
 * </pre>
 * A screen that wants an exact arrangement — the rebirth path bar, for one —
 * gives its buttons explicit slots and a {@link ChestLayout} to match.
 */
public final class ChestRenderer {

    /** Inner columns of a chest row, leaving a one-slot border either side. */
    private static final int[] INNER_COLUMNS = { 1, 2, 3, 4, 5, 6, 7 };

    private final MessageService messages;
    private final ChatPrompt prompts;
    /** Current page per player per screen id, so paging survives a redraw. */
    private final Map<UUID, Map<String, Integer>> pages = new ConcurrentHashMap<>();

    public ChestRenderer(MessageService messages, ChatPrompt prompts) {
        this.messages = messages;
        this.prompts = prompts;
    }

    public void open(Player player, Screen screen) {
        open(player, screen, page(player, screen.id()));
    }

    private void open(Player player, Screen screen, int page) {
        ChestLayout layout = screen.layout();
        ChestHolder holder = new ChestHolder(screen.id());

        List<ScreenButton> pinned = new ArrayList<>(4);
        List<ScreenButton> flowing = new ArrayList<>(screen.buttons().size());
        for (ScreenButton button : screen.buttons()) {
            (button.slot() >= 0 ? pinned : flowing).add(button);
        }

        // A chest cannot host a text field, so every input becomes a button that
        // opens a chat prompt. The screen author never sees the difference.
        for (ScreenInput input : screen.inputs()) {
            flowing.add(inputButton(screen, input));
        }
        if (screen.hasInputs() && screen.onSubmit() != null) {
            flowing.add(submitButton(screen));
        }

        Inventory inventory = layout.isHopper()
                ? Bukkit.createInventory(holder, InventoryType.HOPPER, Text.parse(screen.title()))
                : Bukkit.createInventory(holder, rows(layout, flowing.size()) * 9,
                Text.parse(screen.title()));
        holder.bind(inventory);
        holder.onClose(screen.onClose());

        if (layout.isHopper()) {
            renderHopper(inventory, holder, pinned, flowing);
        } else {
            renderChest(player, inventory, holder, screen, layout, pinned, flowing, page);
        }

        player.openInventory(inventory);
    }

    // ------------------------------------------------------------ chest form

    private void renderChest(Player player, Inventory inventory, ChestHolder holder,
                             Screen screen, ChestLayout layout, List<ScreenButton> pinned,
                             List<ScreenButton> flowing, int page) {
        int size = inventory.getSize();
        int rows = size / 9;

        if (layout.decorated()) {
            ItemStack filler = Items.filler(layout.filler());
            for (int slot = 0; slot < size; slot++) {
                inventory.setItem(slot, filler);
            }
        }

        if (layout.headerSlot() >= 0 && layout.headerSlot() < size) {
            inventory.setItem(layout.headerSlot(), header(screen));
        }

        List<Integer> content = contentSlots(rows);
        int perPage = Math.max(1, content.size());
        int totalPages = Math.max(1, (flowing.size() + perPage - 1) / perPage);
        int current = Math.max(0, Math.min(page, totalPages - 1));
        setPage(player, screen.id(), current);

        int from = current * perPage;
        int to = Math.min(flowing.size(), from + perPage);
        for (int i = from; i < to; i++) {
            place(inventory, holder, content.get(i - from), flowing.get(i), screen);
        }

        for (ScreenButton button : pinned) {
            if (button.slot() < size) {
                place(inventory, holder, button.slot(), button, screen);
            }
        }

        renderNavigation(inventory, holder, screen, rows, current, totalPages);
    }

    private void renderNavigation(Inventory inventory, ChestHolder holder, Screen screen,
                                  int rows, int page, int totalPages) {
        int base = (rows - 1) * 9;

        if (totalPages > 1) {
            Ctx ctx = new Ctx().put("page", page + 1).put("pages", totalPages);
            if (page > 0) {
                bind(inventory, holder, base + 3,
                        Items.build(Material.ARROW,
                                Text.item(messages.raw("ui.previous-page"), ctx.resolver()),
                                List.of()),
                        player -> reopen(player, screen, page - 1));
            }
            if (page < totalPages - 1) {
                bind(inventory, holder, base + 5,
                        Items.build(Material.ARROW,
                                Text.item(messages.raw("ui.next-page"), ctx.resolver()),
                                List.of()),
                        player -> reopen(player, screen, page + 1));
            }
        }

        if (screen.closeable()) {
            bind(inventory, holder, base + 4,
                    Items.build(Material.BARRIER, Text.item(messages.raw("ui.close")), List.of()),
                    player -> {
                        player.closeInventory();
                        screen.onClose().accept(player);
                    });
        }
    }

    private void reopen(Player player, Screen screen, int page) {
        if (player.getOpenInventory().getTopInventory()
                .getHolder(false) instanceof ChestHolder holder) {
            holder.reopening();
        }
        open(player, screen, page);
    }

    // ----------------------------------------------------------- hopper form

    private void renderHopper(Inventory inventory, ChestHolder holder,
                              List<ScreenButton> pinned, List<ScreenButton> flowing) {
        for (ScreenButton button : pinned) {
            if (button.slot() < inventory.getSize()) {
                place(inventory, holder, button.slot(), button, null);
            }
        }
        int slot = 0;
        for (ScreenButton button : flowing) {
            while (slot < inventory.getSize() && inventory.getItem(slot) != null) {
                slot++;
            }
            if (slot >= inventory.getSize()) {
                break;
            }
            place(inventory, holder, slot++, button, null);
        }
    }

    // ---------------------------------------------------------------- pieces

    private ItemStack header(Screen screen) {
        List<Component> lore = new ArrayList<>(screen.body().size());
        for (String line : screen.body()) {
            lore.add(Text.item(line));
        }
        ItemStack hero = screen.heroItem();
        Component name = Text.item(screen.title());
        return hero != null
                ? Items.relabel(hero, name, lore, false)
                : Items.build(Material.PAPER, name, lore);
    }

    private void place(Inventory inventory, ChestHolder holder, int slot, ScreenButton button,
                       Screen screen) {
        List<Component> lore = new ArrayList<>(button.description().size() + 2);
        for (String line : button.description()) {
            lore.add(Text.item(line));
        }
        if (!button.enabled() && !button.lockedReason().isBlank()) {
            lore.add(Component.empty());
            lore.add(Text.item(Palette.colour(Palette.FAILURE, button.lockedReason())));
        }

        ItemStack stack = button.customItem() != null
                ? Items.relabel(button.customItem(), Text.item(button.label()), lore,
                button.glint())
                : Items.build(button.material(), Text.item(button.label()), lore,
                button.amount(), button.glint());

        inventory.setItem(slot, stack);
        if (button.enabled()) {
            holder.action(slot, button.action());
        } else {
            // Still clickable, still says no — silence reads as a broken menu.
            holder.action(slot, player -> {
                if (!button.lockedReason().isBlank()) {
                    player.sendMessage(Text.parse(messages.prefix()
                            + Palette.colour(Palette.FAILURE, button.lockedReason())));
                }
            });
        }
    }

    private void bind(Inventory inventory, ChestHolder holder, int slot, ItemStack stack,
                      java.util.function.Consumer<Player> action) {
        inventory.setItem(slot, stack);
        holder.action(slot, action);
    }

    private ScreenButton inputButton(Screen screen, ScreenInput input) {
        return ScreenButton.of("input-" + input.key(), input.label())
                .style(ButtonStyle.NEUTRAL)
                .material(Material.WRITABLE_BOOK)
                .line(messages.raw("ui.click-to-type"))
                .action(player -> prompts.ask(player, "ui.enter-value",
                        new Ctx().put("label", Text.plain(input.label()))
                                .put("hint", input.placeholder()),
                        answer -> {
                            Map<String, String> values = new HashMap<>(1);
                            values.put(input.key(), answer);
                            if (screen.onSubmit() != null) {
                                screen.onSubmit().accept(player, values);
                            }
                        },
                        () -> open(player, screen)))
                .build();
    }

    private ScreenButton submitButton(Screen screen) {
        String label = screen.submitLabel().isBlank()
                ? messages.raw("ui.submit")
                : screen.submitLabel();
        return ScreenButton.of("submit", label)
                .style(ButtonStyle.PRIMARY)
                .material(Material.LIME_DYE)
                .action(player -> {
                    Map<String, String> empty = new LinkedHashMap<>();
                    for (ScreenInput input : screen.inputs()) {
                        empty.put(input.key(), input.initial());
                    }
                    if (screen.onSubmit() != null) {
                        screen.onSubmit().accept(player, empty);
                    }
                })
                .build();
    }

    // ---------------------------------------------------------------- sizing

    private static int rows(ChestLayout layout, int flowingButtons) {
        if (!layout.autoSized()) {
            return Math.max(1, Math.min(6, layout.rows()));
        }
        // One header row, one navigation row, and as many content rows as fit.
        int contentRows = Math.max(1, (flowingButtons + INNER_COLUMNS.length - 1)
                / INNER_COLUMNS.length);
        return Math.max(3, Math.min(6, contentRows + 2));
    }

    private static List<Integer> contentSlots(int rows) {
        List<Integer> slots = new ArrayList<>((rows - 2) * INNER_COLUMNS.length);
        for (int row = 1; row <= rows - 2; row++) {
            for (int column : INNER_COLUMNS) {
                slots.add(row * 9 + column);
            }
        }
        if (slots.isEmpty()) {
            // A two-row screen has no inner band; use the whole first row.
            for (int column = 0; column < 9; column++) {
                slots.add(column);
            }
        }
        return slots;
    }

    // ------------------------------------------------------------ page state

    private int page(Player player, String screenId) {
        Map<String, Integer> perScreen = pages.get(player.getUniqueId());
        return perScreen == null ? 0 : perScreen.getOrDefault(screenId, 0);
    }

    private void setPage(Player player, String screenId, int page) {
        pages.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>(4))
                .put(screenId, page);
    }

    /** Bounded, like every per-player map here. */
    public void forget(UUID uuid) {
        pages.remove(uuid);
    }

    public void resetPage(Player player, String screenId) {
        setPage(player, screenId, 0);
    }
}
