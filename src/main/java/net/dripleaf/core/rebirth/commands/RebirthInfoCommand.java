package net.dripleaf.core.rebirth.commands;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.rebirth.RebirthModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /rebirthinfo} — a shortcut to the tier browser, kept from the Skript. */
public final class RebirthInfoCommand extends DripleafCommand {

    private final RebirthModule module;

    public RebirthInfoCommand(Services services, CommandSpec spec, RebirthModule module) {
        super(services, spec);
        this.module = module;
    }

    @Override
    protected boolean run(CommandSender sender, Player player, String[] args) {
        module.screens().openBrowser(player);
        return true;
    }
}
