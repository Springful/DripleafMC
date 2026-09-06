package net.dripleafmc.rebirth.reward;

import net.dripleafmc.rebirth.RebirthPlugin;
import net.dripleafmc.rebirth.util.Ctx;
import net.dripleafmc.rebirth.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Runs reward-sets. Everything is dispatched on the main thread; a
 * {@code delay:} entry splits the remainder of the set into a scheduled task
 * rather than sleeping.
 */
public final class RewardExecutor {

    private final RebirthPlugin plugin;

    public RewardExecutor(RebirthPlugin plugin) {
        this.plugin = plugin;
    }

    public void run(Player player, List<RewardAction> actions, Ctx ctx) {
        runFrom(player, actions, ctx, 0);
    }

    private void runFrom(Player player, List<RewardAction> actions, Ctx ctx, int index) {
        for (int i = index; i < actions.size(); i++) {
            RewardAction action = actions.get(i);

            if (action.kind() == RewardAction.Kind.DELAY) {
                int ticks = action.delayTicks();
                int next = i + 1;
                if (ticks > 0 && next < actions.size()) {
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> runFrom(player, actions, ctx, next), ticks);
                    return;
                }
                continue;
            }

            String resolved = ctx.applyRaw(action.value());
            dispatch(player, action.kind(), resolved);
        }
    }

    private void dispatch(Player player, RewardAction.Kind kind, String value) {
        switch (kind) {
            case CONSOLE -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
            case PLAYER -> player.performCommand(value);
            case OP -> {
                boolean wasOp = player.isOp();
                try {
                    player.setOp(true);
                    player.performCommand(value);
                } finally {
                    player.setOp(wasOp);
                }
            }
            case MESSAGE -> player.sendMessage(Text.parse(plugin.papi().text(player, value)));
            case BROADCAST -> Bukkit.broadcast(Text.parse(value));
            case DELAY -> {
                // handled in runFrom
            }
        }
    }
}
