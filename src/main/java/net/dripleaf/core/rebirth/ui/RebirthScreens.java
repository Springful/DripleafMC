package net.dripleaf.core.rebirth.ui;

import net.dripleaf.core.api.CurrencyType;
import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.sound.SoundService;
import net.dripleaf.core.common.text.Ctx;
import net.dripleaf.core.common.text.Glyphs;
import net.dripleaf.core.common.text.Palette;
import net.dripleaf.core.common.text.ProgressBar;
import net.dripleaf.core.common.ui.ButtonStyle;
import net.dripleaf.core.common.ui.ChestLayout;
import net.dripleaf.core.common.ui.Screen;
import net.dripleaf.core.common.ui.ScreenButton;
import net.dripleaf.core.common.ui.ScreenInput;
import net.dripleaf.core.rebirth.CheckResult;
import net.dripleaf.core.rebirth.RebirthPath;
import net.dripleaf.core.rebirth.RebirthService;
import net.dripleaf.core.rebirth.RebirthTier;
import net.dripleaf.core.rebirth.requirement.RequirementState;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Every rebirth screen, built once and rendered either way.
 *
 * <p>The path menu keeps the old hopper shape when it is drawn as a chest —
 * slot 0 Standard, slot 2 the info book, slot 4 Soul Ascension — because it is
 * compact and it works. Drawn as a dialog it becomes three labelled buttons
 * with their requirement breakdowns in the body, which is what the extra room
 * is for.
 *
 * <p>Two things the Skript version got wrong and this does not:
 * <ul>
 *   <li>a locked path says <em>which</em> requirement is missing, in its title,
 *       not just "(Locked)";</li>
 *   <li>costs are shown with {@code formatExact} — a player about to spend
 *       48,750,000 sees that number in full, never {@code $48.75M}.</li>
 * </ul>
 */
public final class RebirthScreens {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault());
    private static final int BAR_CELLS = 10;

    private final Services services;
    private final RebirthService rebirth;

    public RebirthScreens(Services services, RebirthService rebirth) {
        this.services = services;
        this.rebirth = rebirth;
    }

    // ------------------------------------------------------------ path menu

    public void openMain(Player player) {
        if (rebirth.maxed(player)) {
            openMaxed(player);
            return;
        }
        CheckResult standard = rebirth.check(player, RebirthPath.STANDARD);
        CheckResult soul = rebirth.check(player, RebirthPath.SOUL);
        RebirthTier tier = standard.tier();

        Screen screen = Screen.of("rebirth", Palette.brand(
                        line("rebirth.menu-title", tierCtx(player, tier))))
                .hero(new ItemStack(tier.icon()))
                .lines(overview(player, tier, standard))
                .layout(ChestLayout.hopper())
                .dialogColumns(1)
                .button(pathButton(player, standard, RebirthPath.STANDARD, 0))
                .button(infoButton(2))
                .button(pathButton(player, soul, RebirthPath.SOUL, 4))
                .build();

        services.sounds().play(player, SoundService.MENU_OPEN);
        services.ui().open(player, screen);
    }

    private List<String> overview(Player player, RebirthTier tier, CheckResult result) {
        List<String> body = new ArrayList<>(16);
        body.add(header("rebirth.section-progress"));
        body.add(item(line("rebirth.current-tier", new Ctx()
                .put("tier", rebirth.tier(player))
                .put("numeral", numeralOf(rebirth.tier(player))))));
        body.add(item(line("rebirth.next-tier", tierCtx(player, tier))));
        body.add("");
        body.add(header("rebirth.section-requirements"));
        body.addAll(requirementLines(result));
        body.add("");
        body.add(header("rebirth.section-rewards"));
        body.addAll(rewardLines(tier));
        return body;
    }

    /** {@code ➥ ✗ Stone Mined: ▰▰▰▰▰▰▱▱▱▱ 1,847 / 2,500 (73%)} */
    public List<String> requirementLines(CheckResult result) {
        List<String> out = new ArrayList<>(result.requirements().size() + 1);

        boolean costMet = result.costMet();
        out.add(item((costMet
                ? Palette.colour(Palette.SUCCESS, Glyphs.MET)
                : Palette.colour(Palette.FAILURE, Glyphs.UNMET))
                + " " + line(costMet ? "rebirth.cost-met" : "rebirth.cost-unmet", new Ctx()
                .put("cost", services.amounts().formatExact(CurrencyType.MONEY, result.cost()))
                .put("balance", services.amounts()
                        .formatExact(CurrencyType.MONEY, result.balance())))));

        for (RequirementState state : result.requirements()) {
            boolean met = state.met();
            Ctx ctx = new Ctx()
                    .put("name", state.requirement().display())
                    .put("bar", ProgressBar.coloured(state.progress(), state.target(),
                            BAR_CELLS, met ? Palette.SUCCESS : Palette.BRAND_TO))
                    .put("progress", services.amounts().formatWhole(state.progress()))
                    .put("target", services.amounts().formatWhole(state.target()))
                    .put("percent", String.valueOf(
                            ProgressBar.percent(state.progress(), state.target())));
            out.add(item((met
                    ? Palette.colour(Palette.SUCCESS, Glyphs.MET)
                    : Palette.colour(Palette.FAILURE, Glyphs.UNMET))
                    + " " + line("rebirth.requirement-line", ctx)));
        }
        return out;
    }

    private List<String> rewardLines(RebirthTier tier) {
        Ctx ctx = new Ctx()
                .put("cash", services.amounts().formatExact(CurrencyType.MONEY, tier.cash()))
                .put("keys", tier.keys())
                .put("souls", tier.souls(RebirthPath.SOUL))
                .put("multiplier", trim(tier.multiplier()));
        List<String> out = new ArrayList<>(8);
        out.add(item(line("rebirth.reward-cash", ctx)));
        out.add(item(line("rebirth.reward-keys", ctx)));
        out.add(item(line("rebirth.reward-multiplier", ctx)));
        out.add(item(Palette.soul(line("rebirth.reward-souls", ctx))));
        for (String unlock : rebirth.unlocks().displays(tier.number())) {
            out.add(item(Palette.colour(Palette.MUTED, unlock)));
        }
        return out;
    }

    private ScreenButton pathButton(Player player, CheckResult result, RebirthPath path,
                                    int slot) {
        boolean soul = path == RebirthPath.SOUL;
        ButtonStyle style = soul ? ButtonStyle.SOUL : ButtonStyle.PRIMARY;
        RebirthTier tier = result.tier();

        Ctx ctx = new Ctx()
                .put("numeral", tier.numeral())
                .put("cost", services.amounts()
                        .formatExact(CurrencyType.MONEY, result.cost()))
                .put("souls", tier.souls(path));

        ScreenButton.Builder button = ScreenButton.of("path-" + path.id(),
                        style.paint(line("rebirth.path-" + path.id(), ctx)))
                .style(style)
                .material(soul ? Material.PURPLE_DYE : Material.LIME_DYE)
                .slot(slot)
                .line(item(line("rebirth.path-cost", ctx)))
                .lines(requirementLines(result));

        if (result.allowed()) {
            button.line("")
                    .line(item(Palette.colour(Palette.SUCCESS,
                            Glyphs.ACTION + " " + services.messages().raw("rebirth.click-to"))))
                    .action(clicker -> openConfirm(clicker, path));
            return button.build();
        }

        // Say which requirement is missing, not merely "(Locked)".
        button.locked(lockReason(result));
        return button.build();
    }

    private String lockReason(CheckResult result) {
        return switch (result.status()) {
            case COOLDOWN -> line("rebirth.locked-cooldown", Ctx.of("time",
                    net.dripleaf.core.common.command.DripleafCommand
                            .formatDuration(result.cooldownRemaining())));
            case INSUFFICIENT_FUNDS -> line("rebirth.locked-funds", new Ctx()
                    .put("cost", services.amounts()
                            .formatExact(CurrencyType.MONEY, result.cost()))
                    .put("short", services.amounts().formatExact(CurrencyType.MONEY,
                            result.cost().subtract(result.balance()).max(BigDecimal.ZERO))));
            case ECONOMY_UNAVAILABLE -> services.messages().raw("rebirth.locked-economy");
            case MAXED -> services.messages().raw("rebirth.maxed-short");
            default -> {
                RequirementState unmet = result.firstUnmet();
                yield unmet == null
                        ? services.messages().raw("rebirth.locked-generic")
                        : line("rebirth.locked-requirement", new Ctx()
                        .put("name", unmet.requirement().display())
                        .put("progress", services.amounts().formatWhole(unmet.progress()))
                        .put("target", services.amounts().formatWhole(unmet.target())));
            }
        };
    }

    private ScreenButton infoButton(int slot) {
        return ScreenButton.of("info",
                        Palette.brand(services.messages().raw("rebirth.info-button")))
                .style(ButtonStyle.NEUTRAL)
                .material(Material.BOOK)
                .slot(slot)
                .line(item(services.messages().raw("rebirth.info-button-lore")))
                .action(this::openBrowser)
                .build();
    }

    // -------------------------------------------------------------- confirm

    /**
     * The point of no return.
     *
     * <p>Above {@code type-to-confirm-above} the player must type the tier
     * numeral. At these costs an accidental click is hours of play, and a
     * two-button dialog is one misclick away from spending 48 million.
     */
    public void openConfirm(Player player, RebirthPath path) {
        CheckResult result = rebirth.checkFresh(player, path);
        if (!result.allowed()) {
            deny(player, result);
            return;
        }
        RebirthTier tier = result.tier();
        boolean typed = tier.number() > rebirth.settings().typeToConfirmAbove();

        Ctx ctx = new Ctx()
                .put("numeral", tier.numeral())
                .put("tier", tier.number())
                .put("path", services.messages().raw("rebirth.path-name-" + path.id()))
                .put("cost", services.amounts()
                        .formatExact(CurrencyType.MONEY, result.cost()))
                .put("balance", services.amounts()
                        .formatExact(CurrencyType.MONEY, result.balance()))
                .put("after", services.amounts().formatExact(CurrencyType.MONEY,
                        rebirth.settings().sacrificeMoney()
                                ? BigDecimal.ZERO
                                : result.balance().subtract(result.cost())));

        Screen.Builder builder = Screen.of("rebirth-confirm",
                        Palette.colour(Palette.DANGER,
                                line("rebirth.confirm-title", ctx)))
                .hero(new ItemStack(tier.icon()))
                .lines(services.messages().rawList("rebirth.confirm-body").stream()
                        .map(ctx::applyRaw).toList())
                .blank()
                .line(header("rebirth.section-sacrifice"))
                .lines(sacrificeLines());

        if (typed) {
            builder.blank()
                    .line(item(Palette.colour(Palette.WARNING, Glyphs.WARNING + " "
                            + line("rebirth.confirm-type-warning", ctx))))
                    .input(ScreenInput.text("numeral",
                            services.messages().raw("rebirth.confirm-type-label"),
                            tier.numeral()))
                    .onSubmit(line("rebirth.confirm-yes", ctx), (clicker, values) -> {
                        String typedValue = values.getOrDefault("numeral", "").trim();
                        if (!typedValue.equalsIgnoreCase(tier.numeral())) {
                            services.messages().send(clicker, "rebirth.confirm-type-mismatch",
                                    Ctx.of("numeral", tier.numeral()));
                            openConfirm(clicker, path);
                            return;
                        }
                        perform(clicker, path);
                    });
        } else {
            builder.button(ScreenButton.of("confirm", line("rebirth.confirm-yes", ctx))
                    .style(path == RebirthPath.SOUL ? ButtonStyle.SOUL : ButtonStyle.PRIMARY)
                    .material(Material.LIME_DYE)
                    .action(clicker -> perform(clicker, path))
                    .build());
        }

        builder.button(ScreenButton.of("cancel",
                        services.messages().raw("rebirth.confirm-no"))
                .style(ButtonStyle.NEUTRAL)
                .material(Material.ARROW)
                .action(clicker -> {
                    services.messages().send(clicker, "rebirth.cancelled");
                    openMain(clicker);
                })
                .build());

        services.ui().open(player, builder.build());
    }

    private List<String> sacrificeLines() {
        List<String> out = new ArrayList<>(2);
        if (rebirth.settings().sacrificeMoney()) {
            out.add(item(Palette.colour(Palette.DANGER,
                    services.messages().raw("rebirth.sacrifice-money"))));
        }
        if (rebirth.settings().sacrificeMcMmo()) {
            out.add(item(Palette.colour(Palette.DANGER,
                    services.messages().raw("rebirth.sacrifice-mcmmo"))));
        }
        if (out.isEmpty()) {
            out.add(item(Palette.colour(Palette.MUTED,
                    services.messages().raw("rebirth.sacrifice-none"))));
        }
        return out;
    }

    private void perform(Player player, RebirthPath path) {
        services.ui().close(player);
        CheckResult.Status status = rebirth.perform(player, path);
        if (status != CheckResult.Status.OK) {
            deny(player, rebirth.checkFresh(player, path));
        }
    }

    private void deny(Player player, CheckResult result) {
        services.sounds().play(player, SoundService.PURCHASE_FAILURE);
        player.sendMessage(net.dripleaf.core.common.text.Text.parse(
                services.messages().prefix()
                        + Palette.colour(Palette.FAILURE, lockReason(result))));
    }

    // -------------------------------------------------------- tier browser

    /**
     * {@code /rebirth info} — the tier browser.
     *
     * <p>Paginated rather than a fixed 25-slot grid, so it scales past 25 tiers
     * with no code change. Tiers beyond the player's visibility block are shown
     * as locked silhouettes rather than hidden: a visible goal is motivating, an
     * empty grid is just an empty grid.
     */
    public void openBrowser(Player player) {
        int current = rebirth.tier(player);
        int ceiling = rebirth.visibleCeiling(player);
        int max = rebirth.tiers().max();

        Screen.Builder builder = Screen.of("rebirth-info",
                        Palette.brand(services.messages().raw("rebirth.browser-title")))
                .line(line("rebirth.browser-subtitle", new Ctx()
                        .put("tier", current)
                        .put("visible", Math.min(ceiling, max))
                        .put("max", max)))
                .blank()
                .layout(ChestLayout.chest(6));

        for (RebirthTier tier : rebirth.tiers().all()) {
            builder.button(tier.number() <= ceiling
                    ? tierButton(player, tier, current)
                    : silhouette(tier, ceiling));
        }
        builder.button(ScreenButton.of("back",
                        services.messages().raw("ui.back"))
                .style(ButtonStyle.NEUTRAL)
                .material(Material.ARROW)
                .action(this::openMain)
                .build());

        services.sounds().play(player, SoundService.MENU_OPEN);
        services.ui().open(player, builder.build());
    }

    private ScreenButton tierButton(Player player, RebirthTier tier, int current) {
        boolean completed = tier.number() <= current;
        boolean isNext = tier.number() == current + 1;

        String marker = completed
                ? Palette.colour(Palette.SUCCESS, Glyphs.MET)
                : isNext ? Palette.colour(Palette.WARNING, Glyphs.SOUL)
                : Palette.colour(Palette.STRUCTURE, Glyphs.ITEM);

        ScreenButton.Builder button = ScreenButton.of("tier-" + tier.number(),
                        marker + " " + (completed ? Palette.colour(Palette.SUCCESS,
                                title(tier)) : Palette.brand(title(tier))))
                .material(tier.icon())
                .style(completed ? ButtonStyle.PRIMARY
                        : isNext ? ButtonStyle.SOUL : ButtonStyle.NEUTRAL)
                .glint(completed)
                .line(item(line("rebirth.browser-cost", new Ctx()
                        .put("cost", services.amounts()
                                .formatExact(CurrencyType.MONEY, tier.cost()))
                        .put("soul_cost", services.amounts().formatExact(CurrencyType.MONEY,
                                tier.cost(RebirthPath.SOUL))))))
                .line("")
                .line(header("rebirth.section-requirements"));

        for (var requirement : tier.requirements()) {
            var state = requirement.evaluate(player);
            button.line(item((state.met()
                    ? Palette.colour(Palette.SUCCESS, Glyphs.MET)
                    : Palette.colour(Palette.FAILURE, Glyphs.UNMET))
                    + " " + line("rebirth.requirement-line", new Ctx()
                    .put("name", requirement.display())
                    .put("bar", ProgressBar.coloured(state.progress(), state.target(),
                            BAR_CELLS, state.met() ? Palette.SUCCESS : Palette.BRAND_TO))
                    .put("progress", services.amounts().formatWhole(state.progress()))
                    .put("target", services.amounts().formatWhole(state.target()))
                    .put("percent", String.valueOf(
                            ProgressBar.percent(state.progress(), state.target()))))));
        }

        button.line("").line(header("rebirth.section-rewards")).lines(rewardLines(tier));

        if (completed) {
            Long achieved = services.players().get(player).tierDates().get(tier.number());
            if (achieved != null) {
                button.line("").line(item(Palette.colour(Palette.DEEP_MUTED,
                        line("rebirth.browser-achieved", Ctx.of("date",
                                DATE.format(Instant.ofEpochMilli(achieved)))))));
            }
        }
        return button.build();
    }

    private ScreenButton silhouette(RebirthTier tier, int ceiling) {
        return ScreenButton.of("tier-locked-" + tier.number(),
                        Palette.colour(Palette.MUTED, title(tier)))
                .material(Material.GRAY_DYE)
                .style(ButtonStyle.LOCKED)
                .locked(line("rebirth.browser-locked", new Ctx()
                        .put("numeral", numeralOf(ceiling))
                        .put("tier", ceiling)))
                .build();
    }

    // ---------------------------------------------------------------- stats

    public void openStats(Player player, Player subject) {
        var data = services.players().get(subject);
        int current = rebirth.tier(subject);
        RebirthTier next = rebirth.tiers().get(current + 1);

        Screen.Builder builder = Screen.of("rebirth-stats",
                        Palette.brand(line("rebirth.stats-title",
                                Ctx.of("player", subject.getName()))))
                .line(header("rebirth.section-lifetime"))
                .line(item(line("rebirth.stats-tier", new Ctx()
                        .put("tier", current).put("numeral", numeralOf(current)))))
                .line(item(line("rebirth.stats-total",
                        Ctx.of("total", String.valueOf(data.rebirthTotal())))))
                .line(item(line("rebirth.stats-souls", Ctx.of("souls",
                        data.soulsEarned().toPlainString()))))
                .line(item(line("rebirth.stats-spent", Ctx.of("spent",
                        services.amounts().formatExact(CurrencyType.MONEY,
                                data.rebirthSpent())))))
                .line(item(line("rebirth.stats-multiplier", Ctx.of("multiplier",
                        trim((rebirth.sellMultiplier(subject) - 1d) * 100d)))));

        if (data.rebirthFirst() > 0L) {
            builder.line(item(line("rebirth.stats-first", Ctx.of("date",
                    DATE.format(Instant.ofEpochMilli(data.rebirthFirst()))))));
        }
        if (data.rebirthLast() > 0L) {
            builder.line(item(line("rebirth.stats-last", Ctx.of("date",
                    DATE.format(Instant.ofEpochMilli(data.rebirthLast()))))));
        }

        if (next != null) {
            builder.blank().line(header("rebirth.section-next"))
                    .lines(requirementLines(rebirth.check(subject, RebirthPath.STANDARD)));
        }

        builder.button(ScreenButton.of("back", services.messages().raw("ui.back"))
                .style(ButtonStyle.NEUTRAL)
                .material(Material.ARROW)
                .action(this::openMain)
                .build());
        services.ui().open(player, builder.build());
    }

    private void openMaxed(Player player) {
        var data = services.players().get(player);
        Screen screen = Screen.of("rebirth-maxed",
                        Palette.soul(services.messages().raw("rebirth.maxed-title")))
                .lines(services.messages().rawList("rebirth.maxed-body"))
                .blank()
                .line(header("rebirth.section-lifetime"))
                .line(item(line("rebirth.stats-total",
                        Ctx.of("total", String.valueOf(data.rebirthTotal())))))
                .line(item(line("rebirth.stats-souls",
                        Ctx.of("souls", data.soulsEarned().toPlainString()))))
                .line(item(line("rebirth.stats-spent", Ctx.of("spent",
                        services.amounts().formatExact(CurrencyType.MONEY,
                                data.rebirthSpent())))))
                .button(ScreenButton.of("info",
                                Palette.brand(services.messages().raw("rebirth.info-button")))
                        .style(ButtonStyle.NEUTRAL)
                        .material(Material.BOOK)
                        .action(this::openBrowser)
                        .build())
                .build();
        services.ui().open(player, screen);
    }

    // --------------------------------------------------------------- helpers

    private String title(RebirthTier tier) {
        return line("rebirth.browser-entry", new Ctx()
                .put("tier", tier.number()).put("numeral", tier.numeral()));
    }

    private Ctx tierCtx(Player player, RebirthTier tier) {
        Ctx ctx = new Ctx().put("player", player.getName());
        if (tier != null) {
            ctx.put("tier", tier.number()).put("numeral", tier.numeral());
        }
        return ctx;
    }

    private String numeralOf(int tier) {
        RebirthTier found = rebirth.tiers().get(tier);
        return found == null
                ? services.messages().raw("rebirth.numeral-none")
                : found.numeral();
    }

    private String line(String key, Ctx ctx) {
        return ctx.applyRaw(services.messages().raw(key));
    }

    private String header(String key) {
        return Palette.colour(Palette.STRUCTURE, Glyphs.HEADER + " ")
                + Palette.colour(Palette.BODY, services.messages().raw(key));
    }

    private String item(String text) {
        return Palette.colour(Palette.STRUCTURE, Glyphs.ITEM + " ") + text;
    }

    private static String trim(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.format(java.util.Locale.US, "%.1f", value);
    }
}
