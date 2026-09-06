package net.dripleafmc.core.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Renders the neutral menu model as Paper Dialogs.
 *
 * Every button carries a server-side callback with a bounded lifetime, so a player who
 * walks away from an open dialog can't pin handlers in memory forever. Callbacks are
 * bounced to the main thread before touching any Bukkit state.
 */
public final class DialogRenderer {

    private static final int BUTTON_WIDTH = 150;

    private final Plugin plugin;
    private final ClickCallback.Options options;

    public DialogRenderer(Plugin plugin, int lifetimeMinutes) {
        this.plugin = plugin;
        this.options = ClickCallback.Options.builder()
                .uses(ClickCallback.UNLIMITED_USES)
                .lifetime(Duration.ofMinutes(Math.max(1, lifetimeMinutes)))
                .build();
    }

    private DialogAction action(Consumer<Player> handler) {
        return DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player player) {
                Bukkit.getScheduler().runTask(plugin, () -> handler.accept(player));
            }
        }, options);
    }

    public void open(Player player, Menu menu) {
        List<DialogBody> body = new ArrayList<>(menu.body.size());
        for (Component line : menu.body) body.add(DialogBody.plainMessage(line));

        List<DialogInput> inputs = new ArrayList<>(1);
        if (menu.inputKey != null) {
            inputs.add(DialogInput.text(menu.inputKey, 300, menu.inputLabel, true,
                    menu.inputInitial, menu.inputMaxLength, null));
        }

        List<ActionButton> actions = new ArrayList<>(menu.buttons.size());
        for (MenuButton b : menu.buttons) {
            DialogAction buttonAction = b.onSubmit() == null
                    ? action(b.onClick())
                    : DialogAction.customClick((response, audience) -> {
                        if (!(audience instanceof Player clicker)) return;
                        Map<String, Object> values = new HashMap<>(2);
                        if (menu.inputKey != null) {
                            String typed = response.getText(menu.inputKey);
                            if (typed != null) values.put(menu.inputKey, typed);
                        }
                        MenuValues wrapped = new MenuValues(values);
                        Bukkit.getScheduler().runTask(plugin, () -> b.onSubmit().accept(clicker, wrapped));
                    }, options);
            actions.add(ActionButton.create(b.label(), b.tooltip(), BUTTON_WIDTH, buttonAction));
        }

        ActionButton exit = menu.onBack == null
                ? ActionButton.create(Component.text("Close"), null, BUTTON_WIDTH, null)
                : ActionButton.create(menu.backLabel == null ? Component.text("Back") : menu.backLabel,
                        null, BUTTON_WIDTH, action(menu.onBack));

        DialogBase base = DialogBase.builder(menu.title)
                .body(body)
                .inputs(inputs)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(actions, exit, menu.columns)));

        player.showDialog(dialog);
    }

    public void open(Player player, MenuForm form) {
        List<DialogBody> body = new ArrayList<>(form.body.size() + 1);
        if (form.icon != null) body.add(DialogBody.item(form.icon).build());
        for (Component line : form.body) body.add(DialogBody.plainMessage(line));

        List<DialogInput> inputs = new ArrayList<>(2 + form.bools.size());
        if (form.numberKey != null) {
            inputs.add(DialogInput.numberRange(form.numberKey, 200, form.numberLabel,
                    "%s", form.min, form.max, form.initial, form.step));
        }
        if (form.textKey != null) {
            inputs.add(DialogInput.text(form.textKey, 200, form.textLabel, true,
                    form.textInitial, form.textMaxLength, null));
        }
        for (MenuForm.Bool b : form.bools) {
            inputs.add(DialogInput.bool(b.key(), b.label(), b.initial(), "true", "false"));
        }

        DialogBase base = DialogBase.builder(form.title)
                .body(body)
                .inputs(inputs)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();

        ActionButton confirm = ActionButton.create(form.confirmLabel, null, BUTTON_WIDTH,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player player2)) return;
                    Map<String, Object> values = new HashMap<>(4);
                    if (form.numberKey != null) {
                        Float f = response.getFloat(form.numberKey);
                        if (f != null) values.put(form.numberKey, f);
                    }
                    if (form.textKey != null) {
                        String s = response.getText(form.textKey);
                        if (s != null) values.put(form.textKey, s);
                    }
                    for (MenuForm.Bool b : form.bools) {
                        Boolean v = response.getBoolean(b.key());
                        values.put(b.key(), v != null ? v : b.initial());
                    }
                    MenuValues wrapped = new MenuValues(values);
                    Bukkit.getScheduler().runTask(plugin, () -> form.onConfirm.accept(player2, wrapped));
                }, options));

        ActionButton cancel = ActionButton.create(form.cancelLabel, null, BUTTON_WIDTH,
                form.onCancel == null ? null : action(form.onCancel));

        Dialog dialog = Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(confirm, cancel)));

        player.showDialog(dialog);
    }

    /** Plain informational notice with a single dismiss button. */
    public void notice(Player player, Component title, List<Component> lines, Component buttonLabel,
                       Consumer<Player> onDismiss) {
        List<DialogBody> body = new ArrayList<>(lines.size());
        for (Component line : lines) body.add(DialogBody.plainMessage(line));

        DialogBase base = DialogBase.builder(title)
                .body(body)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();

        ActionButton button = ActionButton.create(buttonLabel, null, BUTTON_WIDTH,
                onDismiss == null ? null : action(onDismiss));

        player.showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.notice(button))));
    }
}
