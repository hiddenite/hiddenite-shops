package eu.hiddenite.shops;

import eu.hiddenite.shops.bank.ItemBankManager;
import eu.hiddenite.shops.commands.ShopCommand;
import eu.hiddenite.shops.shipping.ShippingBoxManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class ShopsPlugin extends JavaPlugin {
    private Database database;
    private Economy economy;

    private ShippingBoxManager shippingBoxManager;
    private ItemBankManager itemBankManager;

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

        PluginCommand shopCommand = getCommand("shop");
        if (shopCommand != null) {
            shopCommand.setExecutor(new ShopCommand(this));
        }
    }

    public String getMessage(String configPath) {
        return Objects.toString(getConfig().getString(configPath), "");
    }

    @Override
    public void onDisable() {
        itemBankManager.close();
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
}
