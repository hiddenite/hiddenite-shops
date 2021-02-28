package eu.hiddenite.shops.commands;

import eu.hiddenite.shops.ShopsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ShopCommand implements CommandExecutor, TabCompleter {
    private final ShopsPlugin plugin;

    public ShopCommand(ShopsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@Nonnull final CommandSender sender,
                             @Nonnull final Command command,
                             @Nonnull final String alias,
                             @Nonnull final String[] args) {
        if (args.length < 1) {
            sender.sendMessage("/shop <create-shipping-box>");
            return true;
        }

        if (args[0].equalsIgnoreCase("create-shipping-box")) {
            if (!(sender instanceof Player)) {
                return true;
            }
            plugin.getShippingBoxManager().createShippingBox((Player)sender);
            return true;
        } else {
            sender.sendMessage("Invalid subcommand: " + args[0]);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@Nonnull final CommandSender sender,
                                      @Nonnull final Command command,
                                      @Nonnull final String alias,
                                      @Nonnull final String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create-shipping-box", "something-else");
        }
        return Collections.emptyList();
    }
}
