package net.dripleafmc.rebirth.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.dripleafmc.rebirth.RebirthPlugin;
import net.dripleafmc.rebirth.data.Profile;
import net.dripleafmc.rebirth.tier.RebirthPath;
import net.dripleafmc.rebirth.tier.RebirthTier;
import net.dripleafmc.rebirth.util.Ctx;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /rebirth, plus the admin subtree. Registered through Paper's Brigadier
 * lifecycle rather than a plugin.yml commands block.
 */
public final class RebirthCommand {

    private RebirthCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(RebirthPlugin plugin) {
        return Commands.literal("rebirth")
                .requires(source -> source.getSender().hasPermission("dripleafrebirth.use"))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        plugin.lang().send(sender, "general.players-only");
                        return 0;
                    }
                    plugin.ui(player).openMain(player);
                    return 1;
                })
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission("dripleafrebirth.admin"))
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();
                            long start = System.currentTimeMillis();
                            try {
                                plugin.reloadEverything();
                                plugin.lang().send(sender, "general.reloaded", new Ctx()
                                        .put("files", 5)
                                        .put("ms", System.currentTimeMillis() - start));
                            } catch (RuntimeException ex) {
                                plugin.lang().send(sender, "general.reload-failed",
                                        new Ctx().put("error", String.valueOf(ex.getMessage())));
                                plugin.getLogger().severe("Reload failed: " + ex);
                            }
                            return 1;
                        }))
                .then(Commands.literal("info")
                        .requires(source -> source.getSender().hasPermission("dripleafrebirth.admin"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> {
                                    CommandSender sender = context.getSource().getSender();
                                    Player target = resolve(plugin, sender,
                                            StringArgumentType.getString(context, "player"));
                                    if (target == null) {
                                        return 0;
                                    }
                                    RebirthTier tier = plugin.tiers()
                                            .get(plugin.service().currentTier(target));
                                    plugin.lang().send(sender, "admin.tier-info", new Ctx()
                                            .put("target", target.getName())
                                            .put("tier", plugin.service().currentTier(target))
                                            .put("tier_roman", tier == null
                                                    ? plugin.settings().noneRoman() : tier.roman())
                                            .put("multiplier", (long) plugin.service().multiplier(target)));
                                    return 1;
                                })))
                .then(Commands.literal("set")
                        .requires(source -> source.getSender().hasPermission("dripleafrebirth.admin"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("tier", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            CommandSender sender = context.getSource().getSender();
                                            Player target = resolve(plugin, sender,
                                                    StringArgumentType.getString(context, "player"));
                                            if (target == null) {
                                                return 0;
                                            }
                                            int tier = IntegerArgumentType.getInteger(context, "tier");
                                            if (tier != 0 && !plugin.tiers().exists(tier)) {
                                                plugin.lang().send(sender, "general.invalid-tier",
                                                        new Ctx().put("tier", tier));
                                                return 0;
                                            }
                                            Profile profile = plugin.store().get(target.getUniqueId());
                                            plugin.store().set(target.getUniqueId(), profile.withTier(tier));
                                            plugin.lang().send(sender, "admin.tier-set", new Ctx()
                                                    .put("target", target.getName())
                                                    .put("tier", tier));
                                            return 1;
                                        }))))
                .then(Commands.literal("force")
                        .requires(source -> source.getSender().hasPermission("dripleafrebirth.admin"))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("path", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("standard");
                                            builder.suggest("soul");
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            CommandSender sender = context.getSource().getSender();
                                            Player target = resolve(plugin, sender,
                                                    StringArgumentType.getString(context, "player"));
                                            if (target == null) {
                                                return 0;
                                            }
                                            RebirthPath path = RebirthPath.parse(
                                                    StringArgumentType.getString(context, "path"));
                                            if (path == null) {
                                                return 0;
                                            }
                                            plugin.service().perform(target, path);
                                            plugin.lang().send(sender, "admin.force-run", new Ctx()
                                                    .put("target", target.getName())
                                                    .put("path", path.id()));
                                            return 1;
                                        }))))
                .build();
    }

    private static Player resolve(RebirthPlugin plugin, CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            plugin.lang().send(sender, "general.unknown-player", new Ctx().put("input", name));
        }
        return target;
    }
}
