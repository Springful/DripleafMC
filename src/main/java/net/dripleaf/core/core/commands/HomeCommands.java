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
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/** {@code /home}, {@code /sethome}, {@code /delhome}, {@code /homes}. */
public final class HomeCommands {

    private HomeCommands() {
    }

    /** No argument opens the picker; a name teleports straight there. */
    public static final class Home extends DripleafCommand {

        private final CoreModule core;

        public Home(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                List<String> names = core.homes().names(player);
                if (names.isEmpty()) {
                    services.messages().send(sender, "homes.none");
                    return false;
                }
                if (names.size() == 1) {
                    core.homes().teleport(player, names.get(0));
                    return true;
                }
                core.homes().openPicker(player);
                return true;
            }
            if (core.homes().home(player, args[0]) == null) {
                services.messages().send(sender, "homes.unknown", Ctx.of("name", args[0]));
                return false;
            }
            core.homes().teleport(player, args[0]);
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return sender instanceof Player player && args.length <= 1
                    ? filter(core.homes().names(player), args)
                    : List.of();
        }
    }

    public static final class SetHome extends DripleafCommand {

        private final CoreModule core;

        public SetHome(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            String name = args.length == 0 ? "home" : args[0];
            String result = core.homes().set(player, name);
            Ctx ctx = new Ctx()
                    .put("name", name)
                    .put("used", String.valueOf(core.homes().used(player)))
                    .put("max", String.valueOf(core.homes().limit(player)));
            services.messages().send(sender, result, ctx);
            return result.equals("homes.set") || result.equals("homes.replaced");
        }
    }

    /** Deleting a home is not undoable, so it goes through a confirmation screen. */
    public static final class DelHome extends DripleafCommand {

        private final CoreModule core;

        public DelHome(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/delhome <name>"));
                return false;
            }
            String name = args[0];
            if (core.homes().home(player, name) == null) {
                services.messages().send(sender, "homes.unknown", Ctx.of("name", name));
                return false;
            }

            Screen screen = Screen.of("delhome",
                            Palette.colour(Palette.DANGER,
                                    services.messages().raw("homes.delete-title")))
                    .line(Ctx.of("name", name)
                            .applyRaw(services.messages().raw("homes.delete-body")))
                    .button(ScreenButton.of("confirm",
                                    services.messages().raw("ui.confirm"))
                            .style(ButtonStyle.DANGER)
                            .material(Material.REDSTONE)
                            .action(clicker -> {
                                if (core.homes().delete(clicker, name)) {
                                    services.messages().send(clicker, "homes.deleted",
                                            Ctx.of("name", name));
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
            return sender instanceof Player player && args.length <= 1
                    ? filter(core.homes().names(player), args)
                    : List.of();
        }
    }

    public static final class Homes extends DripleafCommand {

        private final CoreModule core;

        public Homes(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            core.homes().openPicker(player);
            return true;
        }
    }
}
