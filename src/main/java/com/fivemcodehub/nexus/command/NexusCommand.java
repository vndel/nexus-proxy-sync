package com.fivemcodehub.nexus.command;

import com.fivemcodehub.nexus.bus.MessageBus;
import com.fivemcodehub.nexus.bus.NexusMessage;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class NexusCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final MessageBus bus;

    public NexusCommand(JavaPlugin plugin, MessageBus bus) {
        this.plugin = plugin;
        this.bus = bus;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MM.deserialize(
                    "<gray>/nexus <status|broadcast <msg>|reload></gray>"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                sender.sendMessage(MM.deserialize("<gray>──── NexusProxySync ────</gray>"));
                sender.sendMessage(MM.deserialize("<gray>Node:</gray> <white><id></white>",
                        Placeholder.unparsed("id", bus.serverId())));
                sender.sendMessage(MM.deserialize("<gray>Broker:</gray> <state>",
                        Placeholder.parsed("state", bus.healthy()
                                ? "<green>connected</green>" : "<red>unreachable</red>")));
                sender.sendMessage(MM.deserialize(
                        "<gray>out:</gray> <white><p></white>  "
                                + "<gray>in:</gray> <white><r></white>  "
                                + "<gray>dropped:</gray> <white><d></white>",
                        Placeholder.unparsed("p", String.valueOf(bus.publishedCount())),
                        Placeholder.unparsed("r", String.valueOf(bus.receivedCount())),
                        Placeholder.unparsed("d", String.valueOf(bus.droppedCount()))));
            }
            case "broadcast" -> {
                if (args.length < 2) {
                    sender.sendMessage(MM.deserialize("<red>Provide a message.</red>"));
                    return true;
                }
                String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                bus.publish(NexusMessage.of("broadcast", bus.serverId(), text));
                sender.sendMessage(MM.deserialize("<green>Published to the network.</green>"));
            }
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(MM.deserialize(
                        "<green>Config reloaded. Restart to re-dial Redis.</green>"));
            }
            default -> sender.sendMessage(MM.deserialize("<red>Unknown subcommand.</red>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? List.of("status", "broadcast", "reload") : List.of();
    }
}
