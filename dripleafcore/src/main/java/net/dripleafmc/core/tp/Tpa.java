package net.dripleafmc.core.tp;

import net.dripleafmc.core.config.Cfg;
import net.dripleafmc.core.config.Lang;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Teleport requests, expiring lazily on read. */
public final class Tpa {

    public record Request(UUID from, long expiresAt) {}

    private final Map<UUID, Request> inbox = new HashMap<>(16);
    private final Cfg cfg;

    public Tpa(Cfg cfg, Lang lang) {
        this.cfg = cfg;
    }

    public void send(UUID from, UUID to) {
        inbox.put(to, new Request(from, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cfg.tpaExpiry)));
    }

    public Request peek(UUID to) {
        Request request = inbox.get(to);
        if (request == null) return null;
        if (request.expiresAt() < System.currentTimeMillis()) {
            inbox.remove(to);
            return null;
        }
        return request;
    }

    public Request take(UUID to) {
        Request request = peek(to);
        if (request != null) inbox.remove(to);
        return request;
    }

    public void forget(UUID uuid) {
        inbox.remove(uuid);
        inbox.values().removeIf(r -> r.from().equals(uuid));
    }
}
