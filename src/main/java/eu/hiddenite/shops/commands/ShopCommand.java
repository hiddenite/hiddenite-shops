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
        if (!(sender instanceof Player)) {
            return true;
        }
        Player player = (Player)sender;

        if (args.length < 1) {
            sender.sendMessage("/shop <command>");
            return true;
        }

        if (args[0].equals("create-shipping-box")) {
            createShippingBox(player);
        } else if (args[0].equals("create-bank-chest")) {
            createBankChest(player, args);
        } else {
            sender.sendMessage("Invalid subcommand: " + args[0]);
        }

        return true;
    }

    private void createShippingBox(Player player) {
        plugin.getShippingBoxManager().createShippingBox(player);
    }

    private void createBankChest(Player player, String[] args) {
        if (args.length != 4) {
            player.sendMessage("/shop create-bank-chest <id> <size> <price>");
            return;
        }

        String id = args[1];
        int size = Integer.parseInt(args[2]);
        int price = Integer.parseInt(args[3]);

        if (size < 0 || size > 54 || size % 9 != 0) {
            player.sendMessage("Invalid size: " + size);
            return;
        }

        if (price <= 0 || price > 1000000000) {
            player.sendMessage("Invalid price: " + price);
            return;
        }

        plugin.getItemBankManager().createBankChest(player, id, size, price);
    }

    @Override
    public List<String> onTabComplete(@Nonnull final CommandSender sender,
                                      @Nonnull final Command command,
                                      @Nonnull final String alias,
                                      @Nonnull final String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create-shipping-box", "create-bank-chest");
        }
        if (args.length > 1 && args[0].equals("create-bank-chest")) {
            if (args.length == 2) {
                return Arrays.asList("A", "B", "C", "D");
            }
            if (args.length == 3) {
                return Arrays.asList("9", "18", "27", "36", "45", "54");
            }
            if (args.length == 4) {
                return Arrays.asList("100", "1000", "10000", "100000");
            }
        }
        return Collections.emptyList();
    }
}
