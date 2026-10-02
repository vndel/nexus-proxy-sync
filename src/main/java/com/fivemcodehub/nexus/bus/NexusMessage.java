package com.fivemcodehub.nexus.bus;

/**
 * Wire format for bus traffic.
 *
 * <p>Carries the originating server id so a node can discard its own
 * broadcasts; Redis Pub/Sub echoes to every subscriber including the
 * publisher, which would otherwise double-deliver every message.
 */
public record NexusMessage(String type, String origin, String payload, long timestamp) {

    public static NexusMessage of(String type, String origin, String payload) {
        return new NexusMessage(type, origin, payload, System.currentTimeMillis());
    }

    /** Age in milliseconds; used to drop messages replayed after a reconnect. */
    public long age() {
        return System.currentTimeMillis() - timestamp;
    }
}
