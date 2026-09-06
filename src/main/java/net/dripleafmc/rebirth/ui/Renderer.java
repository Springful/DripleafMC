package net.dripleafmc.rebirth.ui;

import net.dripleafmc.rebirth.RebirthPlugin;
import net.dripleafmc.rebirth.core.CheckResult;
import net.dripleafmc.rebirth.core.RequirementState;
import net.dripleafmc.rebirth.util.Ctx;
import net.dripleafmc.rebirth.util.Text;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared line building for both front ends.
 * <p>
 * A template line that is exactly a token - {@code {requirements}},
 * {@code {rewards}}, {@code {unlocks}}, {@code {click}} - is replaced by the
 * matching block. Everything else is parsed once with the render context.
 */
public final class Renderer {

    private final RebirthPlugin plugin;

    public Renderer(RebirthPlugin plugin) {
        this.plugin = plugin;
    }

    /** Cost line followed by one line per requirement, already ticked or crossed. */
    public List<Component> requirements(CheckResult result) {
        List<RequirementState> states = result.requirements();
        List<Component> out = new ArrayList<>(states.size() + 1);

        Ctx costCtx = new Ctx()
                .put("cost", plugin.settings().numbers().display(result.cost()))
                .put("balance", plugin.settings().numbers().display(result.balance()));
        out.add(Text.item(plugin.lang().raw(result.costMet()
                ? "components.cost-met"
                : "components.cost-unmet"), costCtx.resolver()));

        String met = plugin.lang().raw("components.requirement-met");
        String unmet = plugin.lang().raw("components.requirement-unmet");

        for (RequirementState state : states) {
            Ctx ctx = new Ctx()
                    .put("name", state.requirement().display())
                    .put("progress", plugin.settings().numbers().progress(state.progress()))
                    .put("target", plugin.settings().numbers().progress(state.target()))
                    .put("percent", String.valueOf(Math.round(
                            state.target() <= 0d ? 100d
                                    : Math.min(100d, state.progress() / state.target() * 100d))));
            out.add(Text.item(state.met() ? met : unmet, ctx.resolver()));
        }
        return out;
    }

    public List<Component> parseAll(List<String> raw, Ctx ctx) {
        List<Component> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(Text.item(line, ctx.resolver()));
        }
        return out;
    }

    /**
     * Expands a template: token-only lines are spliced, everything else parsed.
     *
     * @param expansions token name (without braces) to replacement block
     */
    public List<Component> expand(List<String> template, Ctx ctx,
                                  Map<String, List<Component>> expansions) {
        List<Component> out = new ArrayList<>(template.size() + 12);
        for (String line : template) {
            String trimmed = line.trim();
            if (trimmed.length() > 2 && trimmed.charAt(0) == '{' && trimmed.endsWith("}")) {
                List<Component> block = expansions.get(trimmed.substring(1, trimmed.length() - 1));
                if (block != null) {
                    out.addAll(block);
                }
                continue;
            }
            out.add(Text.item(line, ctx.resolver()));
        }
        return out;
    }
}
