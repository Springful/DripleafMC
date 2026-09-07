package net.dripleaf.core.rebirth;

import net.dripleaf.core.api.DripleafModule;
import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandRegistry;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.core.leaderboard.LeaderboardService;
import net.dripleaf.core.rebirth.commands.RebirthCommand;
import net.dripleaf.core.rebirth.commands.RebirthInfoCommand;
import net.dripleaf.core.rebirth.commands.RebirthTopCommand;
import net.dripleaf.core.rebirth.requirement.RequirementFactory;
import net.dripleaf.core.rebirth.reward.UnlockRegistry;
import net.dripleaf.core.rebirth.ui.RebirthScreens;

import java.util.List;

/**
 * The rebirth system: tiers, requirements, rewards, unlocks and the two paths.
 *
 * <p>Talks to the core module only through {@code api/}, and publishes
 * {@link RebirthService} back as the {@code RebirthApi} the shop reads its sell
 * multiplier from.
 */
public final class RebirthModule implements DripleafModule {

    private final Services services;
    private final RebirthSettings settings = new RebirthSettings();
    private final TierRegistry tiers;
    private final UnlockRegistry unlocks;
    private final RebirthService service;
    private final RebirthScreens screens;
    private final CommandRegistry commands;
    private final LeaderboardService leaderboards;

    private boolean enabled;

    public RebirthModule(Services services, LeaderboardService leaderboards) {
        this.services = services;
        this.leaderboards = leaderboards;
        this.tiers = new TierRegistry(services, new RequirementFactory(services));
        this.unlocks = new UnlockRegistry(services);
        this.service = new RebirthService(services, tiers, unlocks, settings);
        this.screens = new RebirthScreens(services, service);
        this.commands = new CommandRegistry(services, "rebirth/rebirth.yml");
    }

    @Override
    public String name() {
        return "rebirth";
    }

    @Override
    public void enable() {
        reload();
        services.rebirth(service);
        services.plugin().getServer().getPluginManager()
                .registerEvents(service, services.plugin());

        commands.declare(new CommandSpec("rebirth", true, "dripleaf.rebirth",
                        List.of("prestige", "ascend"), 0L, 0d,
                        "Open the rebirth menu"),
                (s, spec) -> new RebirthCommand(s, spec, this));
        commands.declare(new CommandSpec("rebirthinfo", true, "dripleaf.rebirth",
                        List.of("rbinfo"), 0L, 0d, "Browse the rebirth tiers"),
                (s, spec) -> new RebirthInfoCommand(s, spec, this));
        commands.declare(new CommandSpec("rebirthtop", true, "dripleaf.rebirth",
                        List.of("rbtop"), 0L, 0d, "Rebirth leaderboard"),
                (s, spec) -> new RebirthTopCommand(s, spec, leaderboards));
        commands.registerAll(services.plugin());

        services.plugin().getLogger().info("Rebirth: " + tiers.size() + " tiers, "
                + unlocks.size() + " unlock groups loaded.");
        enabled = true;
    }

    @Override
    public void disable() {
        enabled = false;
    }

    @Override
    public void reload() {
        services.configs().load("rebirth/rebirth.yml");
        settings.load(services.configs().view("rebirth/rebirth.yml"));
        tiers.load();
        unlocks.load();
        commands.reload();
    }

    public boolean enabled() {
        return enabled;
    }

    public RebirthService service() {
        return service;
    }

    public RebirthScreens screens() {
        return screens;
    }

    public RebirthSettings settings() {
        return settings;
    }

    public TierRegistry tiers() {
        return tiers;
    }

    public UnlockRegistry unlocks() {
        return unlocks;
    }

    public CommandRegistry commands() {
        return commands;
    }
}
