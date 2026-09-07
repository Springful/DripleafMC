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
