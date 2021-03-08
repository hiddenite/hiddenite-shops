package eu.hiddenite.shops.commands;

import eu.hiddenite.shops.ShopsPlugin;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SellCommand implements CommandExecutor, TabCompleter {
    private final ShopsPlugin plugin;

    public SellCommand(ShopsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@Nonnull final CommandSender sender,
                             @Nonnull final Command command,
                             @Nonnull final String alias,
                             @Nonnull final String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }
        Player player = (Player)sender;

        if (args.length != 1) {
            plugin.sendMessage(player, "market.messages.sell-usage");
            return true;
        }

        long price;
        try {
            price = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            price = -1;
        }

        if (price < 0 || price > 1000000000) {
            plugin.sendMessage(player, "market.messages.invalid-price", "{PRICE}", args[0]);
            return true;
        }

        if (player.getInventory().getItemInMainHand().getAmount() == 0
                || player.getInventory().getItemInMainHand().getType() == Material.AIR) {
            plugin.sendMessage(player, "market.messages.invalid-item");
            return true;
        }

        plugin.getMarketManager().sellItem(player, price);
        return true;
    }

    @Override
    public List<String> onTabComplete(@Nonnull final CommandSender sender,
                                      @Nonnull final Command command,
                                      @Nonnull final String alias,
                                      @Nonnull final String[] args) {
        if (args.length == 1) {
            return Arrays.asList("100", "1000", "10000", "100000");
        }
        return Collections.emptyList();
    }
}
