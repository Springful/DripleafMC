package net.dripleaf.core.common.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.dripleaf.core.common.text.MessageService;
import net.dripleaf.core.common.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Draws a {@link Screen} as a native Paper dialog.
 *
 * <p>This is the default surface: it scrolls, it takes real text input, it does
 * not consume an inventory slot, and it is legible at any GUI scale.
 *
 * <p>Buttons use {@link DialogAction#customClick} with a single use and a short
 * lifetime, so no listener has to demultiplex keys and no callback outlives the
 * screen it belongs to. That is also why a screen is rebuilt on every open
 * rather than cached — a cached dialog would hold spent callbacks.
 */
public final class DialogRenderer {

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(5))
            .build();

    private final MessageService messages;

    public DialogRenderer(MessageService messages) {
        this.messages = messages;
    }

    public void open(Player player, Screen screen) {
        List<DialogBody> body = body(screen);
        List<DialogInput> inputs = inputs(screen);
        List<ActionButton> buttons = buttons(screen);

        ActionButton exit = screen.closeable()
                ? ActionButton.builder(Text.parse(messages.raw("ui.close")))
                .action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player clicker) {
                        screen.onClose().accept(clicker);
                    }
                }, ONCE))
                .build()
                : null;

        DialogBase base = DialogBase.builder(Text.parse(screen.title()))
                .canCloseWithEscape(screen.closeable())
                .body(body)
                .inputs(inputs)
                .build();

        int width = screen.dialogButtonWidth();
        List<ActionButton> sized = new ArrayList<>(buttons.size());
        for (ActionButton button : buttons) {
            sized.add(ActionButton.create(button.label(), button.tooltip(), width,
                    button.action()));
        }

        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(base)
                .type(io.papermc.paper.registry.data.dialog.type.DialogType
                        .multiAction(sized)
                        .exitAction(exit)
                        .columns(screen.dialogColumns())
                        .build())));
    }

    private List<DialogBody> body(Screen screen) {
        List<String> lines = screen.body();
        List<DialogBody> out = new ArrayList<>(lines.size() + 1);
        if (screen.heroItem() != null) {
            out.add(DialogBody.item(screen.heroItem())
                    .showTooltip(true)
                    .showDecorations(false)
                    .width(32)
                    .height(32)
                    .build());
        }
        for (String line : lines) {
            out.add(DialogBody.plainMessage(Text.parse(line)));
        }
        return out;
    }

    private List<DialogInput> inputs(Screen screen) {
        List<ScreenInput> specs = screen.inputs();
        if (specs.isEmpty()) {
            return List.of();
        }
        List<DialogInput> out = new ArrayList<>(specs.size());
        for (ScreenInput spec : specs) {
            out.add(DialogInput.text(spec.key(), Text.parse(spec.label()))
                    .initial(spec.initial())
                    .maxLength(spec.maxLength())
                    .width(200)
                    .build());
        }
        return out;
    }

    private List<ActionButton> buttons(Screen screen) {
        List<ScreenButton> specs = screen.buttons();
        List<ActionButton> out = new ArrayList<>(specs.size() + 1);

        if (screen.hasInputs() && screen.onSubmit() != null) {
            out.add(button(
                    screen.submitLabel().isBlank()
                            ? messages.raw("ui.submit")
                            : screen.submitLabel(),
                    List.of(),
                    true,
                    "",
                    null,
                    screen));
        }

        for (ScreenButton spec : specs) {
            out.add(button(spec.label(), spec.description(), spec.enabled(),
                    spec.lockedReason(), spec.action(), null));
        }
        return out;
    }

    /**
     * @param screen non-null only for the submit button, whose callback needs
     *              the response view rather than a plain player consumer
     */
    private ActionButton button(String label, List<String> description, boolean enabled,
                                String lockedReason, Consumer<Player> action, Screen screen) {
        ActionButton.Builder builder = ActionButton.builder(Text.parse(label));

        List<String> tooltipLines = new ArrayList<>(description);
        if (!enabled && !lockedReason.isBlank()) {
            tooltipLines.add(lockedReason);
        }
        if (!tooltipLines.isEmpty()) {
            builder.tooltip(joinLines(tooltipLines));
        }

        if (screen != null) {
            builder.action(DialogAction.customClick((response, audience) -> {
                if (!(audience instanceof Player clicker)) {
                    return;
                }
                Map<String, String> values = new HashMap<>(screen.inputs().size());
                for (ScreenInput input : screen.inputs()) {
                    String value = response.getText(input.key());
                    values.put(input.key(), value == null ? "" : value);
                }
                screen.onSubmit().accept(clicker, values);
            }, ONCE));
            return builder.build();
        }

        // A disabled button still renders and still says why; it simply does nothing.
        Consumer<Player> effective = enabled ? action : player -> {
        };
        builder.action(DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player clicker) {
                effective.accept(clicker);
            }
        }, ONCE));
        return builder.build();
    }

    private static Component joinLines(List<String> lines) {
        Component out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out = out.append(Component.newline());
            }
            out = out.append(Text.item(lines.get(i)));
        }
        return out;
    }
}
