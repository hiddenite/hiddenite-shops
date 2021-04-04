package eu.hiddenite.shops.shipping;

import eu.hiddenite.shops.ShopsPlugin;
import eu.hiddenite.shops.helpers.ChestDataHelper;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ShippingBoxManager implements Listener {
    private final ShopsPlugin plugin;
    private final Configuration config;
    private final HashMap<UUID, ShippingBox> shippingBoxes = new HashMap<>();
    private final HashMap<Material, Integer> prices = new HashMap<>();
    private final NamespacedKey shippingBoxKey;

    private final HashMap<Material, Integer> pricesOfTheDay = new LinkedHashMap<>();
    private final HashMap<Material, String> translatedNames = new HashMap<>();
    private Material itemOfTheDay = null;

    public ShippingBoxManager(ShopsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();

        shippingBoxKey = new NamespacedKey(plugin, "shipping-box");

        reloadPrices(false);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> selectItemOfTheDay(false), 200, 200);
    }

    public ShopsPlugin getPlugin() {
        return plugin;
    }

    public String getTranslatedName(Material material) {
        return translatedNames.getOrDefault(material, material.name());
    }

    public void reloadPrices(boolean isReload) {
        loadPrices();
        selectItemOfTheDay(isReload);
    }

    private void loadPrices() {
        prices.clear();
        translatedNames.clear();
        pricesOfTheDay.clear();

        try (PreparedStatement ps = plugin.getDatabase().prepareStatement(
                "SELECT material_name, french_name, price, multiplier FROM shipping_box_prices ORDER BY material_name"
        )) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Material material = Material.valueOf(rs.getString(1));
                    String translatedName = rs.getString(2);
                    int price = rs.getInt(3);
                    int multiplier = rs.getInt(4);

                    prices.put(material, price);
                    translatedNames.put(material, translatedName);
                    if (multiplier > 0) {
                        pricesOfTheDay.put(material, price * multiplier);
                    }
                }
            }
            plugin.getLogger().info("[ShippingBox] Loaded " + prices.size() + " prices");
        } catch (SQLException e) {
            plugin.getLogger().warning("[ShippingBox] Could not retrieve prices");
            e.printStackTrace();
        }
    }

    private void selectItemOfTheDay(boolean isReload) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, 3);

        int day = cal.get(Calendar.DAY_OF_MONTH);
        int month = cal.get(Calendar.MONTH);
        int year = cal.get(Calendar.YEAR);
        int hash = hashcodeFromInteger(year * 31 * 12 + month * 12 + day);

        ArrayList<Material> availableItems = new ArrayList<>(pricesOfTheDay.keySet());
        Material newItemOfTheDay = availableItems.get(hash % availableItems.size());

        if (itemOfTheDay != newItemOfTheDay) {
            itemOfTheDay = newItemOfTheDay;
            plugin.getLogger().info("[ShippingBox] Item of the day: " + itemOfTheDay + " (hash " + hash + ")");
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this::updateItemOfTheDaySigns, isReload ? 20 : 200);
        }
    }

    private void updateItemOfTheDaySigns() {
        ConfigurationSection section = getPlugin().getConfig().getConfigurationSection("shipping-box.item-of-the-day.signs");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection signSection = section.getConfigurationSection(key);
                if (signSection == null) {
                    continue;
                }
                String worldName = signSection.getString("world");
                List<Integer> nameCoordinates = signSection.getIntegerList("name");
                List<Integer> priceCoordinates = signSection.getIntegerList("price");
                if (worldName == null || nameCoordinates.size() != 3 || priceCoordinates.size() != 3) {
                    continue;
                }
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    continue;
                }
                updateSignAtLocation(
                        new Location(world, nameCoordinates.get(0), nameCoordinates.get(1), nameCoordinates.get(2)),
                        new Location(world, priceCoordinates.get(0), priceCoordinates.get(1), priceCoordinates.get(2))
                );
            }
        }
    }

    private void updateSignAtLocation(Location nameLocation, Location priceLocation) {
        World world = nameLocation.getWorld();
        Block block = world.getBlockAt(nameLocation);

        if (!(block.getState() instanceof Sign)) {
            plugin.getLogger().warning("[ShippingBox] Item of the day sign not found: " + nameLocation);
            return;
        }

        Sign sign = (Sign)block.getState();

        String name = translatedNames.get(itemOfTheDay);
        String[] words = name.split(" ");
        ArrayList<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : words){
            if (line.length() + word.length() > 15) {
                lines.add(line.toString());
                line = new StringBuilder();
            } else {
                line.append(" ");
            }
            line.append(word);
        }
        lines.add(line.toString());

        sign.setLine(0, "");
        sign.setLine(1, "");
        sign.setLine(2, "");
        sign.setLine(3, "");

        if (lines.size() == 1) {
            sign.setLine(1, lines.get(0));
        } else if (lines.size() == 2) {
            sign.setLine(1, lines.get(0));
            sign.setLine(2, lines.get(1));
        } else if (lines.size() == 3) {
            sign.setLine(0, lines.get(0));
            sign.setLine(1, lines.get(1));
            sign.setLine(2, lines.get(2));
        } else {
            sign.setLine(0, lines.get(0));
            sign.setLine(1, lines.get(1));
            sign.setLine(2, lines.get(2));
            sign.setLine(3, lines.get(3));
        }

        sign.update();

        block = world.getBlockAt(priceLocation);

        if (!(block.getState() instanceof Sign)) {
            plugin.getLogger().warning("[ShippingBox] Item of the day sign not found: " + priceLocation);
            return;
        }

        String line1 = Objects.toString(config.getString("shipping-box.signs.item-of-the-day-price-1"), "");
        String line2 = Objects.toString(config.getString("shipping-box.signs.item-of-the-day-price-2"), "");

        String formattedPrice = plugin.getEconomy().format(getPrice(itemOfTheDay));
        line1 = line1.replace("{PRICE}", formattedPrice);
        line2 = line2.replace("{PRICE}", formattedPrice);

        sign = (Sign)block.getState();
        sign.setLine(0, "");
        sign.setLine(1, line1);
        sign.setLine(2, line2);
        sign.setLine(3, "");
        sign.update();
    }

    private int hashcodeFromInteger(int x) {
        x = ((x >> 16) ^ x) * 0x45d9f3b;
        x = ((x >> 16) ^ x) * 0x45d9f3b;
        x = (x >> 16) ^ x;
        return x;
    }

    public int getPrice(Material material) {
        if (itemOfTheDay == material) {
            return pricesOfTheDay.getOrDefault(material, 0);
        }
        return prices.getOrDefault(material, 0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !ChestDataHelper.isBlockChestType(block, shippingBoxKey)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!shippingBoxes.containsKey(player.getUniqueId())) {
            shippingBoxes.put(player.getUniqueId(),
                    new ShippingBox(this, config,
                            event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5)
                    )
            );
        }

        ShippingBox shippingBox = shippingBoxes.get(player.getUniqueId());

        String openMessage = config.getString("shipping-box.messages.open-message");
        if (openMessage != null) {
            player.sendMessage(openMessage);
        }

        player.openInventory(shippingBox.inventory);
        player.setScoreboard(shippingBox.scoreboard);
        player.playSound(shippingBox.location, Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        ShippingBox shippingBox = getShippingBoxFromEvent(event.getWhoClicked(), event.getInventory());
        if (shippingBox == null) {
            return;
        }
        Player player = (Player)event.getWhoClicked();

        if (event.getAction() == InventoryAction.PLACE_ALL ||
                event.getAction() == InventoryAction.PLACE_ONE ||
                event.getAction() == InventoryAction.PLACE_SOME) {
            if (event.getRawSlot() < event.getInventory().getSize()) {
                ItemStack stack = event.getCursor();
                if (stack != null) {
                    if (!checkIfItemForSale(player, stack)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (event.getRawSlot() >= event.getInventory().getSize()) {
                ItemStack stack = event.getCurrentItem();
                if (stack != null) {
                    if (!checkIfItemForSale(player, stack)) {
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

        plugin.getServer().getScheduler().runTask(plugin, shippingBox::updateScoreboard);
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        ShippingBox shippingBox = getShippingBoxFromEvent(event.getWhoClicked(), event.getInventory());
        if (shippingBox == null) {
            return;
        }
        Player player = (Player)event.getWhoClicked();

        for (int i : event.getRawSlots()) {
            if (i >= event.getInventory().getSize()) {
                continue;
            }
            ItemStack stack = event.getNewItems().get(i);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            if (!checkIfItemForSale(player, stack)) {
                event.setCancelled(true);
                return;
            }
        }

        plugin.getServer().getScheduler().runTask(plugin, shippingBox::updateScoreboard);
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        ShippingBox shippingBox = getShippingBoxFromEvent(event.getPlayer(), event.getInventory());
        if (shippingBox == null) {
            return;
        }
        Player player = (Player)event.getPlayer();

        int itemCount = 0;
        long totalPrice = 0;
        for (ItemStack itemStack : event.getInventory().getContents()) {
            if (itemStack != null && itemStack.getType() != Material.AIR) {
                long price = getPrice(itemStack.getType()) * itemStack.getAmount();
                itemCount += itemStack.getAmount();
                totalPrice += price;
                plugin.getLogger().info("[ShippingBox] " + itemStack.getType().name() + " x " + itemStack.getAmount() + " @ " + price);
            }
        }

        player.playSound(shippingBox.location, Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);

        if (itemCount > 0) {
            plugin.getLogger().info("[ShippingBox] " + player.getName() + " sold " + itemCount + " items for " + totalPrice);

            String soldMessage = config.getString("shipping-box.messages.sold-message");
            if (soldMessage != null) {
                String formattedTotalPrice = plugin.getEconomy().format(totalPrice);
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

        plugin.getEconomy().addMoney(player.getUniqueId(), totalPrice);
    }

    public void createShippingBox(Player player) {
        Block block = player.getTargetBlock(10);

        if (!ChestDataHelper.setBlockChestType(block, shippingBoxKey)) {
            player.sendMessage("Fail, please target a valid chest.");
            return;
        }

        player.sendMessage("Done, the chest is now a shipping box.");
    }

    private ShippingBox getShippingBoxFromEvent(HumanEntity humanEntity, Inventory inventory) {
        if (!(humanEntity instanceof Player)) {
            return null;
        }
        Player player = (Player)humanEntity;
        if (!shippingBoxes.containsKey(player.getUniqueId())) {
            return null;
        }
        ShippingBox shippingBox = shippingBoxes.get(player.getUniqueId());
        if (inventory != shippingBox.inventory) {
            return null;
        }
        return shippingBox;
    }

    private boolean checkIfItemForSale(Player player, ItemStack stack) {
        if (prices.getOrDefault(stack.getType(), 0) == 0) {
            plugin.sendMessage(
                    player, "shipping-box.messages.not-for-sale",
                    "{ITEM_NAME}", plugin.getTranslatedNameLower(stack.getType())
            );
            return false;
        }
        if (stack.getEnchantments().size() > 0) {
            plugin.sendMessage(player, "shipping-box.messages.cant-sell-enchant");
            return false;
        }
        return true;
    }
}
