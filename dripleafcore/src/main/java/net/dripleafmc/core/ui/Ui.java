package net.dripleafmc.core.ui;

import net.dripleafmc.core.config.Cfg;
import net.dripleafmc.core.hook.Floodgate;
import net.dripleafmc.core.profile.Profile;
import net.dripleafmc.core.profile.Profiles;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.Consumer;

/**
 * The one place that decides dialog vs chest. Screens never ask this question
 * themselves — they build a Menu or MenuForm and hand it here.
 */
public final class Ui {

    private final Cfg cfg;
    private final Profiles profiles;
    private final DialogRenderer dialogs;
    private final Prompt prompt;

    public Ui(Plugin plugin, Cfg cfg, Profiles profiles, Prompt prompt) {
        this.cfg = cfg;
        this.profiles = profiles;
        this.prompt = prompt;
        this.dialogs = new DialogRenderer(plugin, cfg.callbackLifetimeMinutes);
    }

    public Prompt prompt() {
        return prompt;
    }

    public boolean dialogs(Player player) {
        if (!cfg.dialogsEnabled) return false;
        if (cfg.chestForBedrock && Floodgate.isBedrock(player.getUniqueId())) return false;
        Profile p = profiles.get(player.getUniqueId());
        return p == null || p.flag(Profile.FLAG_DIALOGS);
    }

    public void open(Player player, Menu menu) {
        if (dialogs(player)) {
            dialogs.open(player, menu);
        } else {
            ChestRenderer.open(player, menu);
        }
    }

    public void open(Player player, MenuForm form) {
        if (dialogs(player)) {
            dialogs.open(player, form);
        } else {
            ChestRenderer.open(player, form, prompt);
        }
    }

    public void notice(Player player, Component title, List<Component> lines, Component button,
                       Consumer<Player> onDismiss) {
        if (dialogs(player)) {
            dialogs.notice(player, title, lines, button, onDismiss);
            return;
        }
        Menu menu = new Menu(title);
        for (Component line : lines) menu.body(line);
        if (onDismiss != null) menu.back(button, onDismiss);
        ChestRenderer.open(player, menu);
    }
}
