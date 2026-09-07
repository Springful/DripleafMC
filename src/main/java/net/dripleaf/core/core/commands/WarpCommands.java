package net.dripleaf.core.core.commands;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.Palette;
import net.dripleaf.core.common.ui.ButtonStyle;
import net.dripleaf.core.common.ui.Screen;
import net.dripleaf.core.common.ui.ScreenButton;
import net.dripleaf.core.core.CoreModule;
import net.dripleaf.core.core.warps.Warp;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/** {@code /warp}, {@code /warps}, {@code /setwarp}, {@code /delwarp}. */
public final class WarpCommands {

    private WarpCommands() {
    }

    public static final class WarpTo extends DripleafCommand {

        private final CoreModule core;

        public WarpTo(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                core.warps().openMenu(player);
                return true;
            }
            Warp warp = core.warps().warp(args[0]);
            if (warp == null) {
                services.messages().send(sender, "warps.unknown", Ctx.of("name", args[0]));
                return false;
            }
            return core.warps().teleport(player, warp);
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? filter(core.warps().names(), args) : List.of();
        }
    }

    public static final class Warps extends DripleafCommand {

        private final CoreModule core;

        public Warps(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            core.warps().openMenu(player);
            return true;
        }
    }

    public static final class SetWarp extends DripleafCommand {

        private final CoreModule core;

        public SetWarp(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/setwarp <name> [category]"));
                return false;
            }
            String category = args.length > 1 ? args[1] : "general";
            core.warps().save(args[0], player.getLocation(), category);
            services.messages().send(sender, "warps.saved", Ctx.of("name", args[0]));
            services.audit().admin(sender.getName() + " created warp " + args[0]);
            return true;
        }
    }

    public static final class DelWarp extends DripleafCommand {

        private final CoreModule core;

        public DelWarp(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/delwarp <name>"));
                return false;
            }
            String name = args[0];
            if (core.warps().warp(name) == null) {
                services.messages().send(sender, "warps.unknown", Ctx.of("name", name));
                return false;
            }
            Screen screen = Screen.of("delwarp",
                            Palette.colour(Palette.DANGER,
                                    services.messages().raw("warps.delete-title")))
                    .line(Ctx.of("name", name)
                            .applyRaw(services.messages().raw("warps.delete-body")))
                    .button(ScreenButton.of("confirm", services.messages().raw("ui.confirm"))
                            .style(ButtonStyle.DANGER)
                            .material(Material.REDSTONE)
                            .action(clicker -> {
                                if (core.warps().delete(name)) {
                                    services.messages().send(clicker, "warps.deleted",
                                            Ctx.of("name", name));
                                    services.audit().admin(clicker.getName()
                                            + " deleted warp " + name);
                                }
                                services.ui().close(clicker);
                            })
                            .build())
                    .button(ScreenButton.of("cancel", services.messages().raw("ui.cancel"))
                            .style(ButtonStyle.NEUTRAL)
                            .material(Material.ARROW)
                            .action(clicker -> services.ui().close(clicker))
                            .build())
                    .build();
            services.ui().open(player, screen);
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? filter(core.warps().names(), args) : List.of();
        }
    }
}
