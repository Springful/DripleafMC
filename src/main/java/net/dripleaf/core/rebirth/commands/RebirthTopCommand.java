package net.dripleaf.core.rebirth.commands;

import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.command.CommandSpec;
import net.dripleaf.core.common.command.DripleafCommand;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.core.leaderboard.LeaderboardService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /rebirthtop} — highest tier first, ties broken by who got there first.
 *
 * <p>Served from the same cached leaderboard as {@code /baltop}; nothing is
 * computed on request.
 */
public final class RebirthTopCommand extends DripleafCommand {

    private final LeaderboardService leaderboards;

    public RebirthTopCommand(Services services, CommandSpec spec,
                             LeaderboardService leaderboards) {
        super(services, spec);
        this.leaderboards = leaderboards;
    }

    @Override
    protected boolean playerOnly() {
        return false;
    }

    @Override
    protected boolean run(CommandSender sender, Player player, String[] args) {
        int page = 1;
        if (args.length > 0) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {
                // A bad page number reads as page one, not as an error.
            }
        }
        List<LeaderboardService.Entry> board = leaderboards.rebirths();
        services.messages().send(sender, "rebirth.top-header", new Ctx()
                .put("page", String.valueOf(page))
                .put("pages", String.valueOf(leaderboards.pages(board))));

        List<LeaderboardService.Entry> entries = leaderboards.page(board, page);
        int rank = (page - 1) * 10 + 1;
        for (LeaderboardService.Entry entry : entries) {
            services.messages().send(sender, "rebirth.top-entry", new Ctx()
                    .put("rank", String.valueOf(rank++))
                    .put("player", entry.name())
                    .put("tier", entry.value().toBigInteger().toString()));
        }
        if (entries.isEmpty()) {
            services.messages().send(sender, "rebirth.top-empty");
        }
        return true;
    }
}
