package eu.hiddenite.shops.market;

import eu.hiddenite.shops.Database;
import eu.hiddenite.shops.ShopsPlugin;
import eu.hiddenite.shops.helpers.BukkitSerializer;
import eu.hiddenite.shops.helpers.ChestDataHelper;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
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
        private Material selectedMaterial = null;
        private List<Integer> shownItems = null;

        private int currentPage = 1;
        private int totalPages = 1;
    }

    private final ShopsPlugin plugin;
    private final List<MarketItem> itemsForSale = new ArrayList<>();
    private final Map<UUID, OpenMarket> openedMarkets = new HashMap<>();
    private final NamespacedKey marketChestKey;

    public MarketManager(ShopsPlugin plugin) {
        this.plugin = plugin;

        marketChestKey = new NamespacedKey(plugin, "market-chest");

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
            plugin.sendMessage(player, "market.messages.sell-error");
            return;
        }

        plugin.getLogger().info("[Market] Created market item " + marketItemId + ": " + item.getType() + "x" + item.getAmount());

        player.getInventory().setItemInMainHand(null);
        itemsForSale.add(new MarketItem(marketItemId, player.getUniqueId(), price, item));
    }

    public void createMarketChest(Player player) {
        Block block = player.getTargetBlock(10);
        if (ChestDataHelper.setBlockChestType(block, marketChestKey)) {
            player.sendMessage("Done, the chest is now a market chest.");
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
        if (block == null || !ChestDataHelper.isBlockChestType(block, marketChestKey)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        Inventory inventory = Bukkit.createInventory(player, 54, "Market");

        OpenMarket openMarket = new OpenMarket();
        openMarket.location = block.getLocation().add(0.5, 1.0, 0.5);
        openMarket.inventory = inventory;
        openedMarkets.put(player.getUniqueId(), openMarket);

        loadMarketHome(openMarket, 1);

        player.openInventory(inventory);
        player.playSound(openMarket.location, Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
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
                if (openMarket.selectedMaterial == null) {
                    loadMarketPage(openMarket, event.getCurrentItem().getType(), 1);
                } else {
                    int marketItemId = openMarket.shownItems.get(event.getSlot());
                    MarketItem marketItem = itemsForSale.stream().filter(x -> x.id == marketItemId).findAny().orElse(null);

                    if (marketItem != null) {
                        player.sendMessage("purchasing " + marketItem.itemStack.getType().name() + " at " + marketItem.price + " from " + marketItem.sellerId);
                    } else {
                        // Too late, no longer for sale.
                    }
                }
            } else if (event.getSlot() == 48) {
                if (openMarket.currentPage > 1) {
                    openMarket.currentPage -= 1;
                }
                if (openMarket.selectedMaterial == null) {
                    loadMarketHome(openMarket, openMarket.currentPage);
                } else {
                    loadMarketPage(openMarket, openMarket.selectedMaterial, openMarket.currentPage);
                }
            } else if (event.getSlot() == 50) {
                if (openMarket.currentPage < openMarket.totalPages) {
                    openMarket.currentPage += 1;
                }
                if (openMarket.selectedMaterial == null) {
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
        Set<Material> materialsForSale = getMaterialsForSale();

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

    private void createPageButtons(OpenMarket openMarket) {
        if (openMarket.totalPages > 1) {
            ItemStack leftArrow = new ItemStack(Material.ARROW);
            ItemMeta meta = leftArrow.getItemMeta();
            meta.setDisplayName("Left");
            leftArrow.setItemMeta(meta);
            openMarket.inventory.setItem(48, leftArrow);

            ItemStack currentPage = new ItemStack(Material.PAPER);
            meta = currentPage.getItemMeta();
            meta.setDisplayName("Page " + openMarket.currentPage + "/" + openMarket.totalPages);
            currentPage.setItemMeta(meta);
            openMarket.inventory.setItem(49, currentPage);

            ItemStack rightArrow = new ItemStack(Material.ARROW);
            meta = rightArrow.getItemMeta();
            meta.setDisplayName("Right");
            rightArrow.setItemMeta(meta);
            openMarket.inventory.setItem(50, rightArrow);
        }
    }

    private void loadMarketPage(OpenMarket openMarket, Material material, int page) {
        Inventory inventory = openMarket.inventory;
        inventory.clear();

        openMarket.selectedMaterial = material;

        List<MarketItem> itemsFromThisCategory = getItemsForSale(material);
        List<MarketItem> items = itemsFromThisCategory
                .stream()
                .sorted((a, b) -> b.id - a.id)
                .skip((page - 1) * 45)
                .limit(45)
                .collect(Collectors.toList());
        openMarket.shownItems = items.stream().map(x -> x.id).collect(Collectors.toList());

        inventory.setContents(items.stream().map(x -> {
            ItemStack stack = x.itemStack.clone();

            List<String> lore = new ArrayList<>();

            lore.add("Price: " + plugin.getEconomy().format(x.price) + " C$");

            stack.setLore(lore);
            return stack;
        }).toArray(ItemStack[]::new));

        openMarket.currentPage = page;
        openMarket.totalPages = (int)Math.ceil((double)itemsFromThisCategory.size() / 45.0);

        createPageButtons(openMarket);
    }

    private Set<Material> getMaterialsForSale() {
        return itemsForSale.stream().map(x -> x.itemStack.getType()).collect(Collectors.toSet());
    }

    private List<MarketItem> getItemsForSale(Material material) {
        return itemsForSale.stream().filter(x -> x.itemStack.getType() == material).collect(Collectors.toList());
    }

    private void loadMarketFromDatabase() {
        itemsForSale.clear();

        try (PreparedStatement ps = plugin.getDatabase().prepareStatement(
                "SELECT id, seller_id, price, item_data FROM market_items WHERE was_sold = 0"
        )) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    UUID sellerId = UUID.fromString(rs.getString(2));
                    long price = rs.getLong(3);
                    byte[] itemData = rs.getBytes(4);

                    try {
                        ItemStack item = BukkitSerializer.deserialize(itemData);
                        itemsForSale.add(new MarketItem(id, sellerId, price, item));
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
}
