package com.fivemcodehub.nexus;

import com.fivemcodehub.nexus.bus.MessageBus;
import com.fivemcodehub.nexus.bus.NexusMessage;
import com.fivemcodehub.nexus.command.NexusCommand;
import com.fivemcodehub.nexus.listener.NetworkPresenceListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Inter-server message bus for a Velocity network.
 *
 * <p>Why Redis Pub/Sub rather than plugin messaging: plugin messages are
 * tunnelled through a player connection, so they cannot be delivered to an
 * empty backend and are lost when nobody is online. A broker-backed bus lets
 * any node publish at any time, including during startup before a single
 * player has joined.
 *
 * <p>Delivery is fire-and-forget. Pub/Sub has no durability, so nothing that
 * must survive a broker restart should travel over it.
 */
public final class NexusPlugin extends JavaPlugin {

    private MessageBus bus;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String serverId = getConfig().getString("server-id", "");
        if (serverId == null || serverId.isBlank()) {
            getLogger().severe("server-id is not set. Each backend needs a unique id,");
            getLogger().severe("otherwise nodes cannot distinguish their own messages.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.bus = new MessageBus(this, serverId);
        if (!bus.connect()) {
            getLogger().severe("Redis unavailable; disabling to avoid a half-connected bus.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bus.subscribe(this::handle);
        getServer().getPluginManager().registerEvents(
                new NetworkPresenceListener(this, bus), this);

        var cmd = getCommand("nexus");
        if (cmd != null) {
            NexusCommand executor = new NexusCommand(this, bus);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("NexusProxySync online as '" + serverId + "'");
    }

    @Override
    public void onDisable() {
        if (bus != null) {
            bus.publish(NexusMessage.of("node-offline", bus.serverId(), ""));
            bus.close();
        }
    }

    /** Dispatches an inbound message. Always invoked off the main thread. */
    private void handle(NexusMessage message) {
        switch (message.type()) {
            case "broadcast" -> {
                var component = net.kyori.adventure.text.minimessage.MiniMessage
                        .miniMessage().deserialize(message.payload());
                // Hop back to the main thread before touching player state.
                getServer().getScheduler().runTask(this, () ->
                        getServer().getOnlinePlayers()
                                .forEach(p -> p.sendMessage(component)));
            }
            case "player-switch" -> {
                if (getConfig().getBoolean("announce-switches", false)) {
                    getLogger().info("Network switch: " + message.payload());
                }
            }
            case "node-online", "node-offline" ->
                    getLogger().info("Node " + message.origin() + " -> " + message.type());
            default -> getLogger().fine("Unhandled message type: " + message.type());
        }
    }

    public MessageBus bus() {
        return bus;
    }
}
