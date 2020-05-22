package eu.hiddenite.shops;

import org.bukkit.*;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class ShopsPlugin extends JavaPlugin implements Listener {
    private final HashMap<Material, Integer> prices = new HashMap<>();
    private Database database;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        database = new Database(getConfig(), getLogger());
        if (!database.open()) {
            getLogger().warning("Could not connect to the database. Plugin not enabled.");
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);

        ConfigurationSection pricesSection = getConfig().getConfigurationSection("shipping_box.prices");
        if (pricesSection != null) {
            pricesSection.getKeys(false).forEach(key -> {
                int price = pricesSection.getInt(key);
                prices.put(Material.valueOf(key.toUpperCase()), price);
            });
        }
    }

    @Override
    public void onDisable() {
    }

    private class ShippingBox {
        public Inventory inventory;
        public Scoreboard scoreboard;

        private final Configuration config;
        private final Location location;

        public ShippingBox(Configuration config, Location location) {
            this.config = config;
            this.location = location;

            inventory = Bukkit.createInventory(null,9,
                    Objects.toString(config.getString("shipping_box.messages.inventory_title"), "")
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
                    Objects.toString(config.getString("shipping_box.messages.scoreboard_title"), "")
            );
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            int itemCount = 0;
            int totalPrice = 0;
            for (ItemStack itemStack : inventory.getContents()) {
                if (itemStack != null && itemStack.getType() != Material.AIR) {
                    itemCount += itemStack.getAmount();
                    totalPrice += prices.get(itemStack.getType()) * itemStack.getAmount();
                }
            }

            String separatorText = config.getString("shipping_box.messages.scoreboard_separator");
            if (separatorText != null) {
                Score score = objective.getScore(separatorText);
                score.setScore(3);
            }
            String itemCountText = config.getString("shipping_box.messages.scoreboard_item_count");
            if (itemCountText != null) {
                itemCountText = itemCountText.replace("{ITEM_COUNT}", String.valueOf(itemCount));
                itemCountText = itemCountText.replace("{ITEM_COUNT_PLURAL_1}", itemCount != 1 ? "s" : "");
                itemCountText = itemCountText.replace("{ITEM_COUNT_PLURAL_01}", itemCount > 1 ? "s" : "");
                Score score = objective.getScore(itemCountText);
                score.setScore(2);
            }
            String totalPriceText = config.getString("shipping_box.messages.scoreboard_total_price");
            if (totalPriceText != null) {
                String formattedTotalPrice = String.format("%.2f", totalPrice / 100.0);
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

    private final HashMap<UUID, ShippingBox> shippingBoxes = new HashMap<>();

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.CHEST) {
                event.setCancelled(true);

                Player player = event.getPlayer();

                if (!shippingBoxes.containsKey(player.getUniqueId())) {
                    shippingBoxes.put(player.getUniqueId(),
                            new ShippingBox(getConfig(), event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5))
                    );
                }
                ShippingBox shippingBox = shippingBoxes.get(player.getUniqueId());

                String openMessage = getConfig().getString("shipping_box.messages.open_message");
                if (openMessage != null) {
                    player.sendMessage(openMessage);
                }

                player.openInventory(shippingBox.inventory);
                player.setScoreboard(shippingBox.scoreboard);
                player.playSound(shippingBox.location, Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player)event.getWhoClicked();
        if (!shippingBoxes.containsKey(player.getUniqueId())) {
            return;
        }
        ShippingBox shippingBox = shippingBoxes.get(player.getUniqueId());
        if (event.getInventory() != shippingBox.inventory) {
            return;
        }

        if (event.getAction() == InventoryAction.PLACE_ALL ||
                event.getAction() == InventoryAction.PLACE_ONE ||
                event.getAction() == InventoryAction.PLACE_SOME) {
            if (event.getRawSlot() < event.getInventory().getSize()) {
                ItemStack stack = event.getCursor();
                if (stack != null) {
                    if (!prices.containsKey(stack.getType())) {
                        sendNotForSaleMessage(player, stack.getType());
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (event.getRawSlot() >= event.getInventory().getSize()) {
                ItemStack stack = event.getCurrentItem();
                if (stack != null) {
                    if (!prices.containsKey(stack.getType())) {
                        sendNotForSaleMessage(player, stack.getType());
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        } else if (event.getAction() != InventoryAction.PICKUP_ALL &&
                event.getAction() != InventoryAction.PICKUP_HALF &&
                event.getAction() != InventoryAction.PICKUP_SOME &&
                event.getAction() != InventoryAction.PICKUP_ONE &&
                event.getAction() != InventoryAction.COLLECT_TO_CURSOR &&
                event.getAction() != InventoryAction.DROP_ALL_CURSOR &&
                event.getAction() != InventoryAction.DROP_ONE_CURSOR &&
                event.getAction() != InventoryAction.NOTHING) {
            event.setCancelled(true);
            return;
        }

        getServer().getScheduler().runTask(this, shippingBox::updateScoreboard);
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player)event.getWhoClicked();
        if (!shippingBoxes.containsKey(player.getUniqueId())) {
            return;
        }
        ShippingBox shippingBox = shippingBoxes.get(player.getUniqueId());
        if (event.getInventory() != shippingBox.inventory) {
            return;
        }

        for (int i : event.getRawSlots()) {
            if (i >= event.getInventory().getSize()) {
                continue;
            }
            ItemStack stack = event.getNewItems().get(i);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            if (!prices.containsKey(stack.getType())) {
                sendNotForSaleMessage(player, stack.getType());
                event.setCancelled(true);
                return;
            }
        }

        getServer().getScheduler().runTask(this, shippingBox::updateScoreboard);
    }

    private void sendNotForSaleMessage(Player player, Material material) {
        String notForSaleMessage = getConfig().getString("shipping_box.messages.not_for_sale");
        if (notForSaleMessage != null) {
            notForSaleMessage = notForSaleMessage.replace("{ITEM_NAME}", material.name());
            player.sendMessage(notForSaleMessage);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player)event.getPlayer();
        if (!shippingBoxes.containsKey(player.getUniqueId())) {
            return;
        }
        ShippingBox shippingBox = shippingBoxes.get(player.getUniqueId());
        if (event.getInventory() != shippingBox.inventory) {
            return;
        }

        int itemCount = 0;
        int totalPrice = 0;
        for (ItemStack itemStack : event.getInventory().getContents()) {
            if (itemStack != null && itemStack.getType() != Material.AIR) {
                itemCount += itemStack.getAmount();
                totalPrice += prices.getOrDefault(itemStack.getType(), 0) * itemStack.getAmount();
            }
        }

        player.playSound(shippingBox.location, Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);

        if (itemCount > 0) {
            String soldMessage = getConfig().getString("shipping_box.messages.sold_message");
            if (soldMessage != null) {
                String formattedTotalPrice = String.format("%.2f", totalPrice / 100.0);
                soldMessage = soldMessage.replace("{ITEM_COUNT}", String.valueOf(itemCount));
                soldMessage = soldMessage.replace("{ITEM_COUNT_PLURAL_1}", itemCount != 1 ? "s" : "");
                soldMessage = soldMessage.replace("{ITEM_COUNT_PLURAL_01}", itemCount > 1 ? "s" : "");
                soldMessage = soldMessage.replace("{TOTAL_PRICE}", formattedTotalPrice);
                player.sendMessage(soldMessage);
            }
            shippingBox.spawnParticles(itemCount);
        }

        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        shippingBoxes.remove(player.getUniqueId());

        updateCurrency(player, totalPrice);
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
