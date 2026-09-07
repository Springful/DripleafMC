package net.dripleaf.core.core.commands;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.common.storage.AtomicYaml;
import net.dripleaf.core.common.storage.PlayerDataStore;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.core.CoreModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Teleportation: {@code /spawn}, the {@code /tpa} family, {@code /back},
 * {@code /rtp}, {@code /tp}, {@code /tphere} and {@code /top}.
 *
 * <p>Warmups and cooldowns are declared in {@code core/commands.yml} and
 * applied by {@link DripleafCommand}; nothing here reimplements them.
 */
public final class TeleportCommands {

    private TeleportCommands() {
    }

    /** Spawn is stored in the plugin folder, not in a world's level.dat. */
    private static Location spawn(Services services) {
        Path path = services.plugin().getDataFolder().toPath().resolve("data/spawn.yml");
        YamlConfiguration yaml = AtomicYaml.read(path, services.plugin().getLogger());
        Location stored = PlayerDataStore.readLocation(
                yaml.getConfigurationSection("spawn"));
        if (stored != null) {
            return stored;
        }
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return world == null ? null : world.getSpawnLocation();
    }

    public static final class Spawn extends DripleafCommand {

        private final CoreModule core;

        public Spawn(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            Location target = spawn(services);
            if (target == null) {
                services.messages().send(sender, "teleport.no-spawn");
                return false;
            }
            core.teleports().move(player, target, "teleport.spawn");
            return true;
        }
    }

    public static final class SetSpawn extends DripleafCommand {

        public SetSpawn(Services services, CommandSpec spec) {
            super(services, spec);
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            Path path = services.plugin().getDataFolder().toPath().resolve("data/spawn.yml");
            YamlConfiguration yaml = new YamlConfiguration();
            PlayerDataStore.writeLocation(yaml, "spawn", player.getLocation());
            services.schedulers().async(() -> {
                try {
                    AtomicYaml.write(path, yaml);
                } catch (Exception ex) {
                    services.plugin().getLogger().warning(
                            "Could not save spawn: " + ex.getMessage());
                }
            });
            services.messages().send(sender, "teleport.spawn-set");
            services.audit().admin(sender.getName() + " set spawn to "
                    + describe(player.getLocation()));
            return true;
        }
    }

    /** {@code /tpa} and {@code /tpahere} differ by one boolean. */
    public static final class TpaRequest extends DripleafCommand {

        private final CoreModule core;
        private final boolean here;

        public TpaRequest(Services services, CommandSpec spec, CoreModule core, boolean here) {
            super(services, spec);
            this.core = core;
            this.here = here;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length < 1) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/" + id() + " <player>"));
                return false;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                services.messages().send(sender, "errors.player-not-found",
                        Ctx.of("player", args[0]));
                return false;
            }
            return core.teleports().request(player, target, here);
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? onlineNames(args) : List.of();
        }
    }

    public static final class TpAccept extends DripleafCommand {

        private final CoreModule core;

        public TpAccept(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            return core.teleports().accept(player);
        }
    }

    public static final class TpDeny extends DripleafCommand {

        private final CoreModule core;

        public TpDeny(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            return core.teleports().deny(player);
        }
    }

    public static final class TpaCancel extends DripleafCommand {

        private final CoreModule core;

        public TpaCancel(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            return core.teleports().cancel(player);
        }
    }

    public static final class Back extends DripleafCommand {

        private final CoreModule core;

        public Back(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            Location target = core.teleports().backTarget(player);
            if (target == null) {
                services.messages().send(sender, "teleport.no-back");
                return false;
            }
            core.teleports().move(player, target, "teleport.back");
            return true;
        }
    }

    public static final class Rtp extends DripleafCommand {

        private final CoreModule core;

        public Rtp(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            services.messages().send(sender, "teleport.rtp-searching");
            core.teleports().randomTeleport(player, found -> {
                if (!Boolean.TRUE.equals(found)) {
                    services.messages().send(sender, "teleport.rtp-failed");
                    services.cooldowns().clear(player, id());
                }
            });
            return true;
        }
    }

    /** {@code /tp <player> [player]} — staff-only, no warmup by design. */
    public static final class Tp extends DripleafCommand {

        private final CoreModule core;

        public Tp(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/tp <player> [player]"));
                return false;
            }
            Player first = Bukkit.getPlayerExact(args[0]);
            if (first == null) {
                services.messages().send(sender, "errors.player-not-found",
                        Ctx.of("player", args[0]));
                return false;
            }
            if (args.length == 1) {
                core.teleports().move(player, first.getLocation(), "teleport.moved");
                return true;
            }
            Player second = Bukkit.getPlayerExact(args[1]);
            if (second == null) {
                services.messages().send(sender, "errors.player-not-found",
                        Ctx.of("player", args[1]));
                return false;
            }
            core.teleports().move(first, second.getLocation(), "teleport.moved");
            services.messages().send(sender, "teleport.moved-other", new Ctx()
                    .put("player", first.getName())
                    .put("target", second.getName()));
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 2 ? onlineNames(args) : List.of();
        }
    }

    public static final class TpHere extends DripleafCommand {

        private final CoreModule core;

        public TpHere(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            if (args.length == 0) {
                services.messages().send(sender, "errors.usage",
                        Ctx.of("usage", "/tphere <player>"));
                return false;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                services.messages().send(sender, "errors.player-not-found",
                        Ctx.of("player", args[0]));
                return false;
            }
            core.teleports().move(target, player.getLocation(), "teleport.summoned");
            return true;
        }

        @Override
        protected Collection<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? onlineNames(args) : List.of();
        }
    }

    /** Straight up to the highest block in the current column. */
    public static final class Top extends DripleafCommand {

        private final CoreModule core;

        public Top(Services services, CommandSpec spec, CoreModule core) {
            super(services, spec);
            this.core = core;
        }

        @Override
        protected boolean run(CommandSender sender, Player player, String[] args) {
            Location at = player.getLocation();
            int y = player.getWorld().getHighestBlockYAt(at.getBlockX(), at.getBlockZ());
            if (y <= player.getWorld().getMinHeight()) {
                services.messages().send(sender, "teleport.no-top");
                return false;
            }
            Location target = at.clone();
            target.setY(y + 1);
            core.teleports().move(player, target, "teleport.top");
            return true;
        }
    }

    private static String describe(Location location) {
        return (location.getWorld() == null ? "?" : location.getWorld().getName())
                + " " + location.getBlockX() + "," + location.getBlockY()
                + "," + location.getBlockZ();
    }
}
