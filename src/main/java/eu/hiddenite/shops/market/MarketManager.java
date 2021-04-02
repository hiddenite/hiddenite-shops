package eu.hiddenite.shops.market;

import eu.hiddenite.shops.Database;
import eu.hiddenite.shops.Economy;
import eu.hiddenite.shops.ShopsPlugin;
import eu.hiddenite.shops.helpers.BukkitSerializer;
import eu.hiddenite.shops.helpers.ChestDataHelper;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

public class MarketManager implements Listener {
    private static class MarketItem {
        private MarketItem(int id, UUID sellerId, long price, ItemStack itemStack) {
            this.id = id;
            this.sellerId = sellerId;
            this.price = price;
            this.itemStack = itemStack;
        }

        private final int id;
        private final UUID sellerId;
        private final long price;
        private final ItemStack itemStack;
    }

    private static class OpenMarket {
        private Location location;
        private Inventory inventory;

        private boolean isViewingCurrentSales = false;
        private Material selectedMaterial = null;
        private List<Integer> shownItems = null;

        private int currentPage = 1;
        private int totalPages = 1;
    }

    private final ShopsPlugin plugin;
    private final List<MarketItem> itemsForSale = new ArrayList<>();
    private final List<MarketItem> pendingNotifications = new ArrayList<>();
    private final Map<UUID, OpenMarket> openedMarkets = new HashMap<>();
    private final NamespacedKey marketChestKey;
    private final NamespacedKey marketCancelChestKey;

    public MarketManager(ShopsPlugin plugin) {
        this.plugin = plugin;

        marketChestKey = new NamespacedKey(plugin, "market-chest");
        marketCancelChestKey = new NamespacedKey(plugin, "market-cancel-chest");

        loadMarketFromDatabase();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void close() {
        for (Map.Entry<UUID, OpenMarket> entry : openedMarkets.entrySet()) {
            InventoryHolder holder = entry.getValue().inventory.getHolder();
            if (!(holder instanceof Player)) {
                continue;
            }
            ((Player)holder).closeInventory();
        }
        openedMarkets.clear();
    }

    public void sellItem(Player player, long price) {
        List<Double> pos = plugin.getConfig().getDoubleList("market.location.pos");
        Location marketLocation = new Location(
                Bukkit.getWorld(Objects.toString(plugin.getConfig().getString("market.location.world"), "world")),
                pos.get(0), pos.get(1), pos.get(2)
        );
        double marketLocationRadius = plugin.getConfig().getDouble("market.location.radius");

        if (player.getWorld() != marketLocation.getWorld() ||
                player.getLocation().distance(marketLocation) > marketLocationRadius
        ) {
            plugin.sendMessage(player, "market.messages.sell-too-far");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        int marketItemId;
        try (PreparedStatement ps = plugin.getDatabase().prepareStatement(
                "INSERT INTO market_items (seller_id, price, item_data) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
        )) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setLong(2, price);
            ps.setBytes(3, BukkitSerializer.serialize(item));
            ps.executeUpdate();
            marketItemId = Database.getGeneratedId(ps);
        } catch (Exception e) {
            e.printStackTrace();
            plugin.sendMessage(player, "market.messages.sql-error");
            return;
        }

        plugin.getLogger().info("[Market] Created market item " + marketItemId + ": " + item.getType() + "x" + item.getAmount());

        plugin.sendMessage(player, "market.messages.sell-success",
                "{QUANTITY}", item.getAmount(),
                "{MATERIAL}", plugin.getTranslatedNameLower(item.getType()),
                "{PRICE}", plugin.getEconomy().format(price));

        player.getInventory().setItemInMainHand(null);
        itemsForSale.add(new MarketItem(marketItemId, player.getUniqueId(), price, item));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 1.1f);
    }

    public void createMarketChest(Player player, boolean isCancel) {
        Block block = player.getTargetBlock(10);
        if (ChestDataHelper.setBlockChestType(block, isCancel ? marketCancelChestKey : marketChestKey)) {
            player.sendMessage("Done, the chest is now a " + (isCancel ? "cancellation" : "market") + " chest.");
        } else {
            player.sendMessage("Fail, please target a valid chest.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null ||
                (!ChestDataHelper.isBlockChestType(block, marketChestKey)
                        && !ChestDataHelper.isBlockChestType(block, marketCancelChestKey))) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        Inventory inventory = Bukkit.createInventory(player, 54, plugin.getMessage("market.title"));

        OpenMarket openMarket = new OpenMarket();
        openMarket.location = block.getLocation().add(0.5, 1.0, 0.5);
        openMarket.inventory = inventory;
        openedMarkets.put(player.getUniqueId(), openMarket);

        if (ChestDataHelper.isBlockChestType(block, marketChestKey)) {
            loadMarketHome(openMarket, 1);
        } else {
            loadMarketSales(player, openMarket, 1);
        }

        player.openInventory(inventory);
        player.playSound(openMarket.location, Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteract(final PlayerJoinEvent event) {
        sendPendingNotifications(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        OpenMarket openMarket = openedMarkets.get(event.getWhoClicked().getUniqueId());
        if (openMarket == null) {
            return;
        }

        Player player = (Player)event.getWhoClicked();
        event.setCancelled(true);

        if (event.getClickedInventory() != openMarket.inventory) {
            return;
        }

        if (event.getClick() == ClickType.LEFT) {
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
                return;
            }

            if (event.getSlot() < 45) {
                if (openMarket.selectedMaterial == null && !openMarket.isViewingCurrentSales) {
                    loadMarketPage(openMarket, event.getCurrentItem().getType(), 1);
                } else {
                    int marketItemId = openMarket.shownItems.get(event.getSlot());
                    MarketItem marketItem = itemsForSale.stream().filter(x -> x.id == marketItemId).findAny().orElse(null);

                    if (openMarket.isViewingCurrentSales) {
                        if (marketItem != null) {
                            cancelSale(player, marketItem);
                        }
                        loadMarketSales(player, openMarket, openMarket.currentPage);
                    } else {
                        if (marketItem != null) {
                            buyItem(player, marketItem);
                        } else {
                            plugin.sendMessage(player, "market.messages.buy-too-late");
                        }
                        loadMarketPage(openMarket, openMarket.selectedMaterial, openMarket.currentPage);
                    }
                }
            } else if (event.getSlot() == 48) {
                if (openMarket.currentPage > 1) {
                    openMarket.currentPage -= 1;
                }
                if (openMarket.isViewingCurrentSales) {
                    loadMarketSales(player, openMarket, openMarket.currentPage);
                } else if (openMarket.selectedMaterial == null) {
                    loadMarketHome(openMarket, openMarket.currentPage);
                } else {
                    loadMarketPage(openMarket, openMarket.selectedMaterial, openMarket.currentPage);
                }
            } else if (event.getSlot() == 50) {
                if (openMarket.currentPage < openMarket.totalPages) {
                    openMarket.currentPage += 1;
                }
                if (openMarket.isViewingCurrentSales) {
                    loadMarketSales(player, openMarket, openMarket.currentPage);
                } else if (openMarket.selectedMaterial == null) {
                    loadMarketHome(openMarket, openMarket.currentPage);
                } else {
                    loadMarketPage(openMarket, openMarket.selectedMaterial, openMarket.currentPage);
                }
            }
        } else if (event.getClick() == ClickType.RIGHT) {
            if (openMarket.selectedMaterial != null) {
                loadMarketHome(openMarket, 1);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (openedMarkets.containsKey(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(final InventoryCloseEvent event) {
        OpenMarket openMarket = openedMarkets.remove(event.getPlayer().getUniqueId());
        if (openMarket != null) {
            ((Player)event.getPlayer()).playSound(openMarket.location, Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);
        }
    }

    private void loadMarketHome(OpenMarket openMarket, int page) {
        List<Material> materialsForSale = getMaterialsForSale();

        ItemStack[] content = materialsForSale
                .stream()
                .skip((page - 1) * 45)
                .limit(45)
                .map(ItemStack::new)
                .toArray(ItemStack[]::new);
        openMarket.inventory.setContents(content);

        openMarket.currentPage = page;
        openMarket.totalPages = (int)Math.ceil((double)materialsForSale.size() / 45.0);
        openMarket.shownItems = null;
        openMarket.selectedMaterial = null;

        createPageButtons(openMarket);
    }

    private void loadMarketSales(Player player, OpenMarket openMarket, int page) {
        List<MarketItem> allItems = getItemsSoldByPlayer(player);

        while (page > 1 && (page - 1) * 45 >= allItems.size()) {
            page -= 1;
        }

        List<MarketItem> items = allItems.stream().skip((page - 1) * 45).limit(45).collect(Collectors.toList());

        openMarket.inventory.setContents(items.stream().map(x -> {
            ItemStack stack = x.itemStack.clone();

            List<String> lore = new ArrayList<>();
            lore.add(plugin.formatMessage("market.price-tag",
                    "{PRICE}", plugin.getEconomy().format(x.price))
            );
            stack.setLore(lore);

            return stack;
        }).toArray(ItemStack[]::new));

        openMarket.isViewingCurrentSales = true;
        openMarket.currentPage = page;
        openMarket.totalPages = (int)Math.ceil((double)allItems.size() / 45.0);
        openMarket.shownItems = items.stream().map(x -> x.id).collect(Collectors.toList());
        openMarket.selectedMaterial = null;

        createPageButtons(openMarket);
    }

    private void loadMarketPage(OpenMarket openMarket, Material material, int page) {
        Inventory inventory = openMarket.inventory;

        List<MarketItem> itemsFromThisCategory = getItemsForSale(material);

        if (itemsFromThisCategory.size() == 0) {
            loadMarketHome(openMarket, 1);
            return;
        }

        while (page > 1 && (page - 1) * 45 >= itemsFromThisCategory.size()) {
            page -= 1;
        }

        openMarket.selectedMaterial = material;
        List<MarketItem> items = itemsFromThisCategory
                .stream()
                .sorted((a, b) -> {
                    double pricePerItemA = (double)a.price / a.itemStack.getAmount();
                    double pricePerItemB = (double)b.price / b.itemStack.getAmount();
                    if (pricePerItemA != pricePerItemB) {
                        return Double.compare(pricePerItemA, pricePerItemB);
                    } else {
                        return b.id - a.id;
                    }
                })
                .skip((page - 1) * 45)
                .limit(45)
                .collect(Collectors.toList());
        openMarket.shownItems = items.stream().map(x -> x.id).collect(Collectors.toList());

        inventory.setContents(items.stream().map(x -> {
            ItemStack stack = x.itemStack.clone();

            List<String> lore = new ArrayList<>();
            lore.add(plugin.formatMessage("market.price-tag",
                    "{PRICE}", plugin.getEconomy().format(x.price))
            );
            stack.setLore(lore);

            return stack;
        }).toArray(ItemStack[]::new));

        openMarket.currentPage = page;
        openMarket.totalPages = (int)Math.ceil((double)itemsFromThisCategory.size() / 45.0);

        createPageButtons(openMarket);
    }

    private void createPageButtons(OpenMarket openMarket) {
        if (openMarket.totalPages > 1) {
            ItemStack leftArrow = new ItemStack(Material.ARROW);
            ItemMeta meta = leftArrow.getItemMeta();
            meta.setDisplayName(plugin.formatMessage("market.buttons.left"));
            leftArrow.setItemMeta(meta);
            openMarket.inventory.setItem(48, leftArrow);

            ItemStack currentPage = new ItemStack(Material.PAPER);
            meta = currentPage.getItemMeta();
            meta.setDisplayName(plugin.formatMessage(
                    "market.buttons.page",
                    "{PAGE}", openMarket.currentPage,
                    "{TOTAL}", openMarket.totalPages
            ));
            currentPage.setItemMeta(meta);
            openMarket.inventory.setItem(49, currentPage);

            ItemStack rightArrow = new ItemStack(Material.ARROW);
            meta = rightArrow.getItemMeta();
            meta.setDisplayName(plugin.formatMessage("market.buttons.right"));
            rightArrow.setItemMeta(meta);
            openMarket.inventory.setItem(50, rightArrow);
        }
    }

    private List<Material> getMaterialsForSale() {
        return itemsForSale.stream()
                .map(x -> x.itemStack.getType())
                .distinct()
                .sorted(Comparator.comparing(Enum::name))
                .collect(Collectors.toList());
    }

    private List<MarketItem> getItemsForSale(Material material) {
        return itemsForSale.stream()
                .filter(x -> x.itemStack.getType() == material)
                .collect(Collectors.toList());
    }

    private List<MarketItem> getItemsSoldByPlayer(Player player) {
        return itemsForSale.stream()
                .filter(x -> x.sellerId.equals(player.getUniqueId()))
                .collect(Collectors.toList());
    }

    private void buyItem(Player player, MarketItem marketItem) {
        int slot = player.getInventory().firstEmpty();
        if (slot == -1) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.5f, 1.2f);
            plugin.sendMessage(player, "market.messages.buy-no-space");
            return;
        }

        Economy.ResultType result = plugin.getEconomy().removeMoney(player.getUniqueId(), marketItem.price);
        if (result == Economy.ResultType.NOT_ENOUGH_MONEY) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.5f, 1.2f);
            plugin.sendMessage(player, "market.messages.buy-not-enough-money");
            return;
        }
        if (result != Economy.ResultType.SUCCESS) {
            plugin.sendMessage(player, "market.messages.sql-error");
            return;
        }

        result = plugin.getEconomy().addMoney(marketItem.sellerId, marketItem.price);
        if (result != Economy.ResultType.SUCCESS) {
            plugin.sendMessage(player, "market.messages.sql-error");
            return;
        }

        Player seller = Bukkit.getPlayer(marketItem.sellerId);

        try (PreparedStatement ps = plugin.getDatabase().prepareStatement(
                "UPDATE market_items SET was_sold = 1, was_notified = ? WHERE id = ?"
        )) {
            ps.setBoolean(1, seller != null);
            ps.setInt(2, marketItem.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            plugin.sendMessage(player, "market.messages.sql-error");
            return;
        }

        itemsForSale.remove(marketItem);
        pendingNotifications.add(marketItem);

        player.getInventory().setItem(slot, marketItem.itemStack);

        plugin.getLogger().info("[Market] Sold market item " + marketItem.id + ": " + marketItem.itemStack.getType() + "x" + marketItem.itemStack.getAmount());

        plugin.sendMessage(player, "market.messages.buy-success",
                "{QUANTITY}", marketItem.itemStack.getAmount(),
                "{MATERIAL}", plugin.getTranslatedNameLower(marketItem.itemStack.getType()),
                "{PRICE}", plugin.getEconomy().format(marketItem.price));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 1.1f);

        if (seller != null) {
            plugin.sendMessage(seller, "market.messages.sold-notification",
                    "{QUANTITY}", marketItem.itemStack.getAmount(),
                    "{MATERIAL}", plugin.getTranslatedNameLower(marketItem.itemStack.getType()),
                    "{PRICE}", plugin.getEconomy().format(marketItem.price));
        }
    }

    private void cancelSale(Player player, MarketItem marketItem) {
        int slot = player.getInventory().firstEmpty();
        if (slot == -1) {
            plugin.sendMessage(player, "market.messages.buy-no-space");
            return;
        }

        try (PreparedStatement ps = plugin.getDatabase().prepareStatement(
                "DELETE FROM market_items WHERE id = ?"
        )) {
            ps.setInt(1, marketItem.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            plugin.sendMessage(player, "market.messages.sql-error");
            return;
        }
        itemsForSale.remove(marketItem);

        player.getInventory().setItem(slot, marketItem.itemStack);

        plugin.getLogger().info("[Market] Deleted market item " + marketItem.id + ": " + marketItem.itemStack.getType() + "x" + marketItem.itemStack.getAmount());
    }

    private void loadMarketFromDatabase() {
        itemsForSale.clear();

        try (PreparedStatement ps = plugin.getDatabase().prepareStatement(
                "SELECT id, seller_id, price, item_data, was_sold, was_notified FROM market_items WHERE was_sold = 0 OR was_notified = 0"
        )) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    UUID sellerId = UUID.fromString(rs.getString(2));
                    long price = rs.getLong(3);
                    byte[] itemData = rs.getBytes(4);
                    boolean wasSold = rs.getBoolean(5);
                    boolean wasNotified = rs.getBoolean(6);

                    try {
                        ItemStack item = BukkitSerializer.deserialize(itemData);
                        MarketItem marketItem = new MarketItem(id, sellerId, price, item);
                        if (!wasSold) {
                            itemsForSale.add(marketItem);
                        } else if (!wasNotified) {
                            pendingNotifications.add(marketItem);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Market] Could not deserialize market item " + id);
                        e.printStackTrace();
                    }
                }
            }

            plugin.getLogger().info("[Market] Loaded " + itemsForSale.size() + " market items");
        } catch (SQLException e) {
            plugin.getLogger().warning("[Market] Could not load banks!");
            e.printStackTrace();
        }
    }

    private void sendPendingNotifications(Player player) {
        List<MarketItem> pending = pendingNotifications.stream()
                .filter(x -> x.sellerId.equals(player.getUniqueId()))
                .collect(Collectors.toList());

        if (pending.size() == 0) {
            return;
        }

        for (MarketItem marketItem : pending) {
            plugin.sendMessage(player, "market.messages.sold-notification",
                    "{QUANTITY}", marketItem.itemStack.getAmount(),
                    "{MATERIAL}", plugin.getTranslatedNameLower(marketItem.itemStack.getType()),
                    "{PRICE}", plugin.getEconomy().format(marketItem.price));
        }

        try (PreparedStatement ps = plugin.getDatabase().prepareStatement(
                "UPDATE market_items SET was_notified = 1 WHERE seller_id = ? AND was_sold = 1"
        )) {
            ps.setString(1, player.getUniqueId().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        pendingNotifications.removeAll(pending);
    }
}
