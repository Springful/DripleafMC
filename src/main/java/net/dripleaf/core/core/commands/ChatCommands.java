package net.dripleaf.core.core.commands;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.Text;
import net.dripleaf.core.core.CoreModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Private messaging, ignores, social spy and nicknames. */
public final class ChatCommands {

    private ChatCommands() {
    }

    public static final class Msg extends DripleafCommand {

        private final CoreModule core;

        public Msg(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length < 2) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/msg <player> <message>"));
                return false;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                target = core.social().byNickname(args[0]);
            }
            if (target == null) {
                services.messages().send(sender, "errors.player-not-found",
                        Ctx.of("player", args[0]));
                return false;
            }
            return core.social().message(player, target,
                    String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? onlineNames(args) : List.of();
        }
    }

    public static final class Reply extends DripleafCommand {

        private final CoreModule core;

        public Reply(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/r <message>"));
                return false;
            }
            Player target = core.social().replyTarget(player);
            if (target == null) {
                services.messages().send(sender, "social.no-reply-target");
                return false;
            }
            return core.social().message(player, target, String.join(" ", args));
        }
    }

    public static final class Ignore extends DripleafCommand {

        private final CoreModule core;

        public Ignore(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/ignore <player>"));
                return false;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                services.messages().send(sender, "errors.player-not-found",
                        Ctx.of("player", args[0]));
                return false;
            }
            boolean now = core.social().toggleIgnore(player, target.getUniqueId());
            services.messages().send(sender,
                    now ? "social.ignore-added" : "social.ignore-removed",
                    Ctx.of("player", target.getName()));
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? onlineNames(args) : List.of();
        }
    }

    public static final class SocialSpy extends DripleafCommand {

        private final CoreModule core;

        public SocialSpy(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            boolean now = core.social().toggleSocialSpy(player);
            services.messages().send(sender,
                    now ? "social.spy-on" : "social.spy-off");
            return true;
        }
    }

    /**
     * {@code /nick}.
     *
     * <p>Colour support is a separate node because it is a Rebirth XX unlock:
     * without {@code dripleaf.nick.colour} the tags are stripped rather than the
     * command being refused, so a player who tries it still gets a nickname.
     */
    public static final class Nick extends DripleafCommand {

        private final CoreModule core;

        public Nick(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/nick <name|off> [player]"));
                return false;
            }
            Player target = player;
            if (args.length > 1) {
                if (!sender.hasPermission("dripleaf.nick.other")) {
                    services.messages().send(sender, "errors.no-permission",
                            Ctx.of("permission", "dripleaf.nick.other"));
                    return false;
                }
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    services.messages().send(sender, "errors.player-not-found",
                            Ctx.of("player", args[1]));
                    return false;
                }
            }

            if (args[0].equalsIgnoreCase("off")) {
                services.players().get(target).nickname("");
                target.displayName(Text.parse(target.getName()));
                services.messages().send(sender, "social.nick-cleared",
                        Ctx.of("player", target.getName()));
                return true;
            }

            String nickname = sender.hasPermission("dripleaf.nick.colour")
                    ? args[0]
                    : Text.plain(args[0]);
            services.players().get(target).nickname(nickname);
            target.displayName(Text.parse(nickname));
            services.messages().send(sender, "social.nick-set", new Ctx()
                    .put("player", target.getName()).put("nick", nickname));
            return true;
        }
    }

    public static final class RealName extends DripleafCommand {

        private final CoreModule core;

        public RealName(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean playerOnly() {
            return false;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/realname <nick>"));
                return false;
            }
            Player target = core.social().byNickname(args[0]);
            if (target == null) {
                services.messages().send(sender, "social.no-such-nick",
                        Ctx.of("nick", args[0]));
                return false;
            }
            services.messages().send(sender, "social.realname", new Ctx()
                    .put("nick", args[0]).put("player", target.getName()));
            return true;
        }
    }
}
