package net.dripleaf.core.common.command;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dripleaf.core.common.Services;
import net.dripleaf.core.common.config.Cfg;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Builds and registers every command, exactly once.
 *
 * <p>Commands are declared with a factory rather than instantiated eagerly, so
 * a command that is disabled in {@code core/commands.yml} is never constructed
 * and never registered — that is what keeps it from colliding with EssentialsX
 * rather than merely losing a race with it.
 *
 * <p>Registration happens inside Paper's {@code COMMANDS} lifecycle event and
 * never again. Reload swaps the {@link CommandSpec} data behind the live
 * instances; it does not re-register anything, because that path leaks.
 */
public final class CommandRegistry {

    /** A command that has not been built yet. */
    private record Declaration(CommandSpec defaults,
                               BiFunction<Services, CommandSpec, DripleafCommand> factory) {
    }

    private final Services services;
    private final String configResource;
    private final Map<String, Declaration> declarations = new LinkedHashMap<>(80);
    private final Map<String, DripleafCommand> registered = new LinkedHashMap<>(80);
    private final List<String> skipped = new ArrayList<>();

    public CommandRegistry(Services services, String configResource) {
        this.services = services;
        this.configResource = configResource;
    }

    /**
     * Declares a command.
     *
     * @param defaults shipped defaults, including whether it is enabled out of the box
     * @param factory  builds the instance, called only if the command is enabled
     */
    public CommandRegistry declare(CommandSpec defaults,
                                   BiFunction<Services, CommandSpec, DripleafCommand> factory) {
        declarations.put(defaults.id(), new Declaration(defaults, factory));
        return this;
    }

    /** Resolves every declaration against {@code commands.yml} and registers the enabled ones. */
    public void registerAll(Plugin plugin) {
        Cfg root = services.configs().view(configResource, "commands");

        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            for (Map.Entry<String, Declaration> entry : declarations.entrySet()) {
                Declaration declaration = entry.getValue();
                CommandSpec spec = CommandSpec.read(root.child(entry.getKey()),
                        declaration.defaults());
                if (!spec.enabled()) {
                    skipped.add(entry.getKey());
                    continue;
                }
                DripleafCommand command = declaration.factory().apply(services, spec);
                registered.put(entry.getKey(), command);
                commands.register(spec.id(), spec.description(), spec.aliases(), command);
            }
            plugin.getLogger().info("Registered " + registered.size() + " of "
                    + declarations.size() + " commands from " + configResource
                    + " (" + skipped.size() + " disabled).");
        });
    }

    /**
     * Re-reads {@code commands.yml} and pushes the new timings, permissions and
     * descriptions into the live command objects.
     *
     * <p>Cooldown, warmup and permission changes apply the instant this
     * returns. Whether a command <em>exists</em> is fixed at start-up — Paper's
     * command registrar is only valid inside its lifecycle event, and
     * re-registering outside it is exactly the leak this class exists to avoid
     * — so a change to {@code enabled} is reported by
     * {@link #pendingRegistrationChanges()} rather than silently ignored.
     *
     * @return how many live commands were retuned
     */
    public int reload() {
        Cfg root = services.configs().view(configResource, "commands");
        int updated = 0;
        for (Map.Entry<String, DripleafCommand> entry : registered.entrySet()) {
            Declaration declaration = declarations.get(entry.getKey());
            if (declaration == null) {
                continue;
            }
            CommandSpec fresh = CommandSpec.read(root.child(entry.getKey()),
                    declaration.defaults());
            if (!fresh.equals(entry.getValue().spec())) {
                entry.getValue().spec(fresh);
                updated++;
            }
        }
        return updated;
    }

    /**
     * Command ids whose {@code enabled:} in config no longer matches what was
     * registered at start-up — that is, the ones that need a restart to appear
     * or disappear.
     *
     * <p>Surfaced by the reload output so "I enabled it and nothing happened"
     * never has to be guessed at.
     */
    public List<String> pendingRegistrationChanges() {
        Cfg root = services.configs().view(configResource, "commands");
        List<String> pending = new ArrayList<>();
        for (Map.Entry<String, Declaration> entry : declarations.entrySet()) {
            CommandSpec fresh = CommandSpec.read(root.child(entry.getKey()),
                    entry.getValue().defaults());
            boolean live = registered.containsKey(entry.getKey());
            if (fresh.enabled() != live) {
                pending.add(entry.getKey() + (fresh.enabled() ? " (+)" : " (-)"));
            }
        }
        return pending;
    }

    public Map<String, DripleafCommand> registered() {
        return Map.copyOf(registered);
    }

    /** Ids present in the catalogue but switched off — shown on the admin Commands screen. */
    public List<String> skipped() {
        return List.copyOf(skipped);
    }

    public List<CommandSpec> catalogue() {
        List<CommandSpec> out = new ArrayList<>(declarations.size());
        Cfg root = services.configs().view(configResource, "commands");
        for (Map.Entry<String, Declaration> entry : declarations.entrySet()) {
            out.add(CommandSpec.read(root.child(entry.getKey()), entry.getValue().defaults()));
        }
        return out;
    }

    public int declaredCount() {
        return declarations.size();
    }

    public int registeredCount() {
        return registered.size();
    }
}
