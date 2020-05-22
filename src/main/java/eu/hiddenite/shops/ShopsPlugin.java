package eu.hiddenite.shops;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ShopsPlugin extends JavaPlugin {
    private Database database;
    private ShippingBoxManager shippingBoxManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        database = new Database(getConfig(), getLogger());
        if (!database.open()) {
            getLogger().warning("Could not connect to the database. Plugin not enabled.");
            return;
        }

        shippingBoxManager = new ShippingBoxManager(this);
    }

    @Override
    public void onDisable() {
        database.close();
    }

    public Database getDatabase() {
        return database;
    }

    public void updateCurrency(final Player player, final int delta) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try (PreparedStatement ps = database.prepareStatement("INSERT INTO currency" +
                    " (player_id, amount)" +
                    " VALUES (?, ?)" +
                    " ON DUPLICATE KEY UPDATE amount = amount + ?"
            )) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setInt(2, delta);
                ps.setInt(3, delta);
                ps.executeUpdate();
            } catch (SQLException e) {
                getLogger().warning("Could not update currency, player " + player.getName() + ", delta " + delta);
                e.printStackTrace();
            }
        });
    }
}
