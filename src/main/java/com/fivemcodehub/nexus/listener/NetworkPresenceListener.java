package com.fivemcodehub.nexus.listener;

import com.fivemcodehub.nexus.bus.MessageBus;
import com.fivemcodehub.nexus.bus.NexusMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class NetworkPresenceListener implements Listener {

    private final JavaPlugin plugin;
    private final MessageBus bus;

    public NetworkPresenceListener(JavaPlugin plugin, MessageBus bus) {
        this.plugin = plugin;
        this.bus = bus;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("publish-presence", true)) return;
        bus.publish(NexusMessage.of("player-switch", bus.serverId(),
                event.getPlayer().getName() + " -> " + bus.serverId()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("publish-presence", true)) return;
        bus.publish(NexusMessage.of("player-switch", bus.serverId(),
                event.getPlayer().getName() + " left " + bus.serverId()));
    }
}
