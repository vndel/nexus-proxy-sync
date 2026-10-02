package com.fivemcodehub.nexus.bus;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Redis Pub/Sub transport with self-message filtering and reconnect backoff. */
public final class MessageBus {

    private static final Gson GSON = new Gson();
    private static final String CHANNEL = "nexus:bus";

    private final JavaPlugin plugin;
    private final String serverId;

    private final AtomicLong published = new AtomicLong();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    private JedisPool pool;
    private JedisPubSub subscriber;
    private Thread subscriberThread;
    private volatile boolean closing;

    public MessageBus(JavaPlugin plugin, String serverId) {
        this.plugin = plugin;
        this.serverId = serverId;
    }

    public boolean connect() {
        var cfg = plugin.getConfig();
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(cfg.getInt("redis.pool-size", 6));
        poolConfig.setMaxIdle(3);
        poolConfig.setTestOnBorrow(true);

        String host = cfg.getString("redis.host", "127.0.0.1");
        int port = cfg.getInt("redis.port", 6379);
        String password = cfg.getString("redis.password", "");
        int timeout = cfg.getInt("redis.timeout-ms", 2_000);

        try {
            this.pool = password == null || password.isEmpty()
                    ? new JedisPool(poolConfig, host, port, timeout)
                    : new JedisPool(poolConfig, host, port, timeout, password);

            // Fail fast at startup rather than on first publish.
            try (var jedis = pool.getResource()) {
                jedis.ping();
            }
            publish(NexusMessage.of("node-online", serverId, ""));
            return true;
        } catch (Exception ex) {
            plugin.getLogger().severe("Redis connection failed: " + ex.getMessage());
            return false;
        }
    }

    public void subscribe(Consumer<NexusMessage> handler) {
        long maxAge = plugin.getConfig().getLong("max-message-age-ms", 30_000L);

        this.subscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String raw) {
                NexusMessage message;
                try {
                    message = GSON.fromJson(raw, NexusMessage.class);
                } catch (JsonSyntaxException ex) {
                    dropped.incrementAndGet();
                    return;
                }
                if (message == null || message.type() == null) {
                    dropped.incrementAndGet();
                    return;
                }
                // Pub/Sub echoes to the publisher; ignore our own traffic.
                if (serverId.equals(message.origin())) return;
                // Guard against a backlog replayed after a reconnect.
                if (message.age() > maxAge) {
                    dropped.incrementAndGet();
                    return;
                }

                received.incrementAndGet();
                handler.accept(message);
            }
        };

        this.subscriberThread = new Thread(() -> {
            long backoff = 1_000L;
            while (!closing) {
                try (var jedis = pool.getResource()) {
                    backoff = 1_000L;              // reset after a clean connect
                    jedis.subscribe(subscriber, CHANNEL);
                } catch (Exception ex) {
                    if (closing) return;
                    plugin.getLogger().warning("Bus subscriber dropped: " + ex.getMessage());
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    backoff = Math.min(backoff * 2, 30_000L);   // exponential backoff
                }
            }
        }, "nexus-bus-sub");

        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    public void publish(NexusMessage message) {
        if (pool == null || pool.isClosed()) return;
        // Publishing performs network I/O, so keep it off the main thread.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (var jedis = pool.getResource()) {
                jedis.publish(CHANNEL, GSON.toJson(message));
                published.incrementAndGet();
            } catch (Exception ex) {
                dropped.incrementAndGet();
                plugin.getLogger().fine("Publish failed: " + ex.getMessage());
            }
        });
    }

    public String serverId() {
        return serverId;
    }

    public long publishedCount() {
        return published.get();
    }

    public long receivedCount() {
        return received.get();
    }

    public long droppedCount() {
        return dropped.get();
    }

    public boolean healthy() {
        if (pool == null || pool.isClosed()) return false;
        try (var jedis = pool.getResource()) {
            return "PONG".equalsIgnoreCase(jedis.ping());
        } catch (Exception ex) {
            return false;
        }
    }

    public void close() {
        this.closing = true;
        if (subscriber != null) {
            try {
                subscriber.unsubscribe();
            } catch (Exception ignored) {
                // Already disconnected.
            }
        }
        if (subscriberThread != null) subscriberThread.interrupt();
        if (pool != null && !pool.isClosed()) pool.close();
    }
}
