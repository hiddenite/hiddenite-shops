package eu.hiddenite.shops;

import eu.hiddenite.shops.bank.ItemBankManager;
import eu.hiddenite.shops.casino.CasinoManager;
import eu.hiddenite.shops.commands.SellCommand;
import eu.hiddenite.shops.commands.ShopCommand;
import eu.hiddenite.shops.market.MarketManager;
import eu.hiddenite.shops.shipping.ShippingBoxManager;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class ShopsPlugin extends JavaPlugin {
    private Database database;
    private Economy economy;

    private ShippingBoxManager shippingBoxManager;
    private ItemBankManager itemBankManager;
    private MarketManager marketManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        database = new Database(getConfig(), getLogger());
        if (!database.open()) {
            getLogger().warning("Could not connect to the database. Plugin not enabled.");
            return;
        }

        economy = new Economy(database, getLogger());

        shippingBoxManager = new ShippingBoxManager(this);
        itemBankManager = new ItemBankManager(this);
        marketManager = new MarketManager(this);
        new CasinoManager(this);

        PluginCommand shopCommand = getCommand("shop");
        if (shopCommand != null) {
            shopCommand.setExecutor(new ShopCommand(this));
        }

        PluginCommand sellCommand = getCommand("sell");
        if (sellCommand != null) {
            sellCommand.setExecutor(new SellCommand(this));
        }
    }

    public String getMessage(String configPath) {
        return Objects.toString(getConfig().getString(configPath), "");
    }

    public String formatMessage(String key, Object... parameters) {
        String msg = getMessage(key);
        for (int i = 0; i < parameters.length - 1; i += 2) {
            msg = msg.replace(parameters[i].toString(), parameters[i + 1].toString());
        }
        return msg;
    }

    public void sendMessage(Player player, String key, Object... parameters) {
        player.sendMessage(formatMessage(key, parameters));
    }

    public String getTranslatedName(Material material) {
        return shippingBoxManager.getTranslatedName(material);
    }

    public String getTranslatedNameLower(Material material) {
        return getTranslatedName(material).toLowerCase();
    }

    @Override
    public void onDisable() {
        itemBankManager.close();
        marketManager.close();
        database.close();
    }

    public Database getDatabase() {
        return database;
    }

    public Economy getEconomy() {
        return economy;
    }

    public ShippingBoxManager getShippingBoxManager() {
        return shippingBoxManager;
    }

    public ItemBankManager getItemBankManager() {
        return itemBankManager;
    }

    public MarketManager getMarketManager() {
        return marketManager;
    }
}
