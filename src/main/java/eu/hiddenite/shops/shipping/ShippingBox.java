package eu.hiddenite.shops.shipping;

import org.bukkit.*;
import org.bukkit.configuration.Configuration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Objects;

public class ShippingBox {
    public final Inventory inventory;
    public final Scoreboard scoreboard;
    public final Location location;
    private final ShippingBoxManager manager;
    private final Configuration config;

    public ShippingBox(ShippingBoxManager manager, Configuration config, Location location) {
        this.manager = manager;
        this.config = config;
        this.location = location;

        inventory = Bukkit.createInventory(null,9,
                Objects.toString(config.getString("shipping-box.messages.inventory-title"), "")
        );

        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        updateScoreboard();
    }

    public void updateScoreboard() {
        Objective oldObjective = scoreboard.getObjective("box");
        if (oldObjective != null) {
            oldObjective.unregister();
        }

        Objective objective = scoreboard.registerNewObjective("box", "",
                Objects.toString(config.getString("shipping-box.messages.scoreboard-title"), " ")
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int itemCount = 0;
        int totalPrice = 0;
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null && itemStack.getType() != Material.AIR) {
                itemCount += itemStack.getAmount();
                totalPrice += manager.getPrice(itemStack.getType()) * itemStack.getAmount();
            }
        }

        String separatorText = config.getString("shipping-box.messages.scoreboard-separator");
        if (separatorText != null) {
            Score score = objective.getScore(separatorText);
            score.setScore(3);
        }
        String itemCountText = config.getString("shipping-box.messages.scoreboard-item-count");
        if (itemCountText != null) {
            itemCountText = itemCountText.replace("{ITEM_COUNT}", String.valueOf(itemCount));
            itemCountText = itemCountText.replace("{ITEM_COUNT_PLURAL_1}", itemCount != 1 ? "s" : "");
            itemCountText = itemCountText.replace("{ITEM_COUNT_PLURAL_01}", itemCount > 1 ? "s" : "");
            Score score = objective.getScore(itemCountText);
            score.setScore(2);
        }
        String totalPriceText = config.getString("shipping-box.messages.scoreboard-total-price");
        if (totalPriceText != null) {
            String formattedTotalPrice = manager.getPlugin().getEconomy().format(totalPrice);
            totalPriceText = totalPriceText.replace("{TOTAL_PRICE}", formattedTotalPrice);
            Score score = objective.getScore(totalPriceText);
            score.setScore(1);
        }
    }

    public void spawnParticles(int nbParticles) {
        location.getWorld().spawnParticle(Particle.SPELL_MOB,
                location,
                nbParticles,
                0.1, 0.0, 0.1
        );
        location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.05f);
    }
}
