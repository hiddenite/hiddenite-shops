package eu.hiddenite.shops;

import eu.hiddenite.shops.commands.ShopCommand;
import eu.hiddenite.shops.shipping.ShippingBoxManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class ShopsPlugin extends JavaPlugin {
    private Database database;
    private Economy economy;

    private ShippingBoxManager shippingBoxManager;

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

        PluginCommand shopCommand = getCommand("shop");
        if (shopCommand != null) {
            shopCommand.setExecutor(new ShopCommand(this));
        }
    }

    @Override
    public void onDisable() {
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
}
