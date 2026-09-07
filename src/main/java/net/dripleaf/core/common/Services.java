package net.dripleaf.core.common;

import net.dripleaf.core.api.CurrencyRegistry;
import net.dripleaf.core.api.RebirthApi;
import net.dripleaf.core.common.config.ConfigManager;
import net.dripleaf.core.common.cooldown.CooldownService;
import net.dripleaf.core.common.cooldown.WarmupService;
import net.dripleaf.core.common.hook.Hooks;
import net.dripleaf.core.common.icon.IconService;
import net.dripleaf.core.common.log.AuditLog;
import net.dripleaf.core.common.money.AmountParser;
import net.dripleaf.core.common.scheduler.Schedulers;
import net.dripleaf.core.common.sound.SoundService;
import net.dripleaf.core.common.storage.PlayerDataStore;
import net.dripleaf.core.common.text.MessageService;
import net.dripleaf.core.common.ui.ChatPrompt;
import net.dripleaf.core.common.ui.UiService;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The shared foundations, built once in a fixed order and handed to both
 * modules.
 *
 * <p>Deliberately a plain aggregate rather than a framework. Everything in here
 * has exactly one instance for the life of the server, and a module holding a
 * reference to it is holding the same objects everyone else is — which is the
 * property that makes {@code /dripleafcore reload} a data swap instead of a
 * re-wiring exercise.
 */
public final class Services {

    private final JavaPlugin plugin;
    private final Schedulers schedulers;
    private final ConfigManager configs;
    private final MessageService messages;
    private final PlayerDataStore players;
    private final Hooks hooks;
    private final SoundService sounds;
    private final IconService icons;
    private final AmountParser amounts;
    private final AuditLog audit;
    private final CooldownService cooldowns;
    private final WarmupService warmups;
    private final ChatPrompt prompts;
    private final UiService ui;

    private CurrencyRegistry currencies;
    private RebirthApi rebirth;

    public Services(JavaPlugin plugin, Schedulers schedulers, ConfigManager configs,
                    MessageService messages, PlayerDataStore players, Hooks hooks,
                    SoundService sounds, IconService icons, AmountParser amounts,
                    AuditLog audit, CooldownService cooldowns, WarmupService warmups,
                    ChatPrompt prompts, UiService ui) {
        this.plugin = plugin;
        this.schedulers = schedulers;
        this.configs = configs;
        this.messages = messages;
        this.players = players;
        this.hooks = hooks;
        this.sounds = sounds;
        this.icons = icons;
        this.amounts = amounts;
        this.audit = audit;
        this.cooldowns = cooldowns;
        this.warmups = warmups;
        this.prompts = prompts;
        this.ui = ui;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public Schedulers schedulers() {
        return schedulers;
    }

    public ConfigManager configs() {
        return configs;
    }

    public MessageService messages() {
        return messages;
    }

    public PlayerDataStore players() {
        return players;
    }

    public Hooks hooks() {
        return hooks;
    }

    public SoundService sounds() {
        return sounds;
    }

    public IconService icons() {
        return icons;
    }

    public AmountParser amounts() {
        return amounts;
    }

    public AuditLog audit() {
        return audit;
    }

    public CooldownService cooldowns() {
        return cooldowns;
    }

    public WarmupService warmups() {
        return warmups;
    }

    public ChatPrompt prompts() {
        return prompts;
    }

    public UiService ui() {
        return ui;
    }

    /** Published by the core module during its enable. */
    public CurrencyRegistry currencies() {
        return currencies;
    }

    public void currencies(CurrencyRegistry registry) {
        this.currencies = registry;
    }

    /** Published by the rebirth module during its enable; {@code null} when it is off. */
    public RebirthApi rebirth() {
        return rebirth;
    }

    public void rebirth(RebirthApi api) {
        this.rebirth = api;
    }
}
