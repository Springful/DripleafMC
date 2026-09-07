package net.dripleaf.core.core.commands;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.core.CoreModule;
import net.dripleaf.core.core.kits.Kit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/** {@code /kit} and {@code /kits}. */
public final class KitCommands {

    private KitCommands() {
    }

    public static final class KitClaim extends DripleafCommand {

        private final CoreModule core;

        public KitClaim(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                core.kits().openMenu(player);
                return true;
            }
            Kit kit = core.kits().kit(args[0]);
            if (kit == null) {
                services.messages().send(sender, "kits.unknown", Ctx.of("name", args[0]));
                return false;
            }
            return core.kits().claim(player, kit);
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? filter(core.kits().names(), args) : List.of();
        }
    }

    public static final class Kits extends DripleafCommand {

        private final CoreModule core;

        public Kits(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            core.kits().openMenu(player);
            return true;
        }
    }
}
