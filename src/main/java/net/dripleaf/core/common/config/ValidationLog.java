package net.dripleaf.core.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Collects config problems instead of throwing them.
 *
 * <p>A staff member mistyping a number in {@code tiers.yml} should produce one
 * warning line and a working server, not a plugin that fails to enable. Issues
 * gathered here are logged once at the end of a load and surfaced again on the
 * admin Diagnostics screen, so they do not scroll away unnoticed.
 */
public final class ValidationLog {

    /**
     * @param file    the yml the problem is in
     * @param path    the config path, e.g. {@code tiers.7.cost}
     * @param message what is wrong and what was used instead
     */
    public record Issue(String file, String path, String message) {

        @Override
        public String toString() {
            return file + " → " + path + ": " + message;
        }
    }

    private final List<Issue> issues = new ArrayList<>();

    public void add(String file, String path, String message) {
        issues.add(new Issue(file, path, message));
    }

    public List<Issue> issues() {
        return List.copyOf(issues);
    }

    public boolean isEmpty() {
        return issues.isEmpty();
    }

    public int size() {
        return issues.size();
    }

    public void clear() {
        issues.clear();
    }

    /** One summary line plus the detail, so a clean start-up stays quiet. */
    public void report(Logger logger) {
        if (issues.isEmpty()) {
            return;
        }
        logger.warning("Config validation found " + issues.size()
                + " problem" + (issues.size() == 1 ? "" : "s")
                + ". The affected values fell back to their defaults:");
        for (Issue issue : issues) {
            logger.warning("  " + issue);
        }
    }
}
