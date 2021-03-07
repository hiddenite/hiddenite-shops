package eu.hiddenite.shops.bank;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import eu.hiddenite.shops.Economy;
import eu.hiddenite.shops.ShopsPlugin;
import eu.hiddenite.shops.helpers.ItemStackSerializer;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class ItemBankManager implements Listener {
    private static class OpenedBank {
        private OpenedBank(String bankId, Inventory inventory, Location location) {
            this.bankId = bankId;
            this.inventory = inventory;
            this.location = location;
        }
        private final String bankId;
        private final Inventory inventory;
        private final Location location;
    }

    private final ShopsPlugin plugin;

    private final NamespacedKey bankIdKey;
    private final NamespacedKey bankSizeKey;
    private final NamespacedKey bankPriceKey;

    private final HashMap<UUID, HashMap<String, ItemStack[]>> playerBanks = new HashMap<>();
    private final HashMap<UUID, String> purchaseConfirmations = new HashMap<>();
    private final HashMap<UUID, OpenedBank> openBanks = new HashMap<>();

    public ItemBankManager(ShopsPlugin plugin) {
        this.plugin = plugin;

        bankIdKey = new NamespacedKey(plugin, "bank-chest-id");
        bankSizeKey = new NamespacedKey(plugin, "bank-chest-size");
        bankPriceKey = new NamespacedKey(plugin, "bank-chest-price");

        loadAllBanksFromDatabase();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void close() {
        for (Map.Entry<UUID, OpenedBank> entry : openBanks.entrySet()) {
            playerBanks.get(entry.getKey()).put(entry.getValue().bankId, entry.getValue().inventory.getContents());
            saveBankToDatabase(entry.getKey(), entry.getValue().bankId);
        }
        openBanks.clear();
    }

    public void createBankChest(Player player, String id, int size, int price) {
        Block block = player.getTargetBlock(10);

        if (!setBlockAsBank(block, id, size, price)) {
            player.sendMessage("Fail, please target a valid chest.");
            return;
        }

        player.sendMessage("Done, the chest is now a bank: " + id + ", size " + size + ", price " + price);
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        String bankId = getBankId(block);
        if (bankId == null) {
            purchaseConfirmations.remove(event.getPlayer().getUniqueId());
            return;
        }

        int bankSize = getBankSize(block);
        int bankPrice = getBankPrice(block);

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (playerHasChest(player, bankId)) {
            openChest(player, block, bankId, bankSize);
        } else if (purchaseConfirmations.getOrDefault(player.getUniqueId(), "").equals(bankId)) {
            purchaseConfirmations.remove(player.getUniqueId());

            Economy.ResultType result = plugin.getEconomy().removeMoney(player.getUniqueId(), bankPrice);

            if (result == Economy.ResultType.SUCCESS) {
                String successMessage = plugin.getMessage("item-bank.messages.purchase-success")
                        .replace("{PRICE}", plugin.getEconomy().format(bankPrice));
                player.sendMessage(TextComponent.fromLegacyText(successMessage));
                purchaseChest(player, bankId);
                openChest(player, block, bankId, bankSize);
            } else if (result == Economy.ResultType.NOT_ENOUGH_MONEY) {
                String notEnoughMoneyMessage = plugin.getMessage("item-bank.messages.purchase-not-enough");
                player.sendMessage(TextComponent.fromLegacyText(notEnoughMoneyMessage));
            } else {
                String errorMessage = plugin.getMessage("item-bank.messages.purchase-error");
                player.sendMessage(TextComponent.fromLegacyText(errorMessage));
            }
        } else {
            String purchaseMessage = plugin.getMessage("item-bank.messages.must-purchase")
                    .replace("{PRICE}", plugin.getEconomy().format(bankPrice));
            player.sendMessage(TextComponent.fromLegacyText(purchaseMessage));

            long currentMoney = plugin.getEconomy().getMoney(player.getUniqueId());

            if (currentMoney >= bankPrice) {
                purchaseConfirmations.put(player.getUniqueId(), bankId);
                String confirmMessage = plugin.getMessage("item-bank.messages.purchase-confirm");
                player.sendMessage(TextComponent.fromLegacyText(confirmMessage));
            } else {
                String notEnoughMoneyMessage = plugin.getMessage("item-bank.messages.purchase-not-enough");
                player.sendMessage(TextComponent.fromLegacyText(notEnoughMoneyMessage));
            }
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player)event.getPlayer();
        OpenedBank openedBank = openBanks.get(player.getUniqueId());
        if (openedBank != null && openedBank.inventory == event.getInventory()) {
            closeChest(player, openedBank);
        }
    }

    private void openChest(Player player, Block block, String bankId, int bankSize) {
        if (openBanks.containsKey(player.getUniqueId())) {
            plugin.getLogger().warning(player.getName() + " tried to open two banks at once");
            return;
        }

        String inventoryTitle = plugin.getMessage("item-bank.title")
                .replace("{ID}", bankId);

        Inventory bankInventory = Bukkit.createInventory(player, bankSize, inventoryTitle);
        bankInventory.setContents(playerBanks.get(player.getUniqueId()).get(bankId));
        openBanks.put(player.getUniqueId(), new OpenedBank(bankId, bankInventory, block.getLocation()));

        player.openInventory(bankInventory);
        player.playSound(block.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1.0f);
    }

    private void closeChest(Player player, OpenedBank bank) {
        playerBanks.get(player.getUniqueId()).put(bank.bankId, bank.inventory.getContents());
        player.playSound(bank.location, Sound.BLOCK_ENDER_CHEST_CLOSE, 0.5f, 1.0f);

        openBanks.remove(player.getUniqueId());
        saveBankToDatabase(player.getUniqueId(), bank.bankId);
    }

    private void purchaseChest(Player player, String bankId) {
        plugin.getLogger().info(player.getName() + " purchased bank " + bankId);

        if (!playerBanks.containsKey(player.getUniqueId())) {
            playerBanks.put(player.getUniqueId(), new HashMap<>());
        }
        if (!playerBanks.get(player.getUniqueId()).containsKey(bankId)) {
            playerBanks.get(player.getUniqueId()).put(bankId, new ItemStack[] {});
        }
    }

    private boolean playerHasChest(Player player, String bankId) {
        if (!playerBanks.containsKey(player.getUniqueId())) {
            return false;
        }
        return playerBanks.get(player.getUniqueId()).containsKey(bankId);
    }

    private boolean isBlockValidChest(Block block) {
        if (block == null) {
            return false;
        }
        if (block.getType() != Material.CHEST) {
            return false;
        }
        return block.getState() instanceof Chest;
    }

    private String getBankId(Block block) {
        if (!isBlockValidChest(block)) {
            return null;
        }
        Chest chest = (Chest)block.getState();
        return chest.getPersistentDataContainer().get(bankIdKey, PersistentDataType.STRING);
    }

    private int getBankSize(Block block) {
        Chest chest = (Chest)block.getState();
        Integer size = chest.getPersistentDataContainer().get(bankSizeKey, PersistentDataType.INTEGER);
        return size != null ? size : 0;
    }

    private int getBankPrice(Block block) {
        Chest chest = (Chest)block.getState();
        Integer price = chest.getPersistentDataContainer().get(bankPriceKey, PersistentDataType.INTEGER);
        return price != null ? price : 0;
    }

    private boolean setBlockAsBank(Block block, String id, int size, int price) {
        if (!isBlockValidChest(block)) {
            return false;
        }
        Chest chest = (Chest)block.getState();
        chest.getPersistentDataContainer().set(bankIdKey, PersistentDataType.STRING, id);
        chest.getPersistentDataContainer().set(bankSizeKey, PersistentDataType.INTEGER, size);
        chest.getPersistentDataContainer().set(bankPriceKey, PersistentDataType.INTEGER, price);
        chest.update();
        return true;
    }

    private void loadAllBanksFromDatabase() {
        try (PreparedStatement ps = plugin.getDatabase().prepareStatement(
                "SELECT player_id, bank_id, bank_content FROM banks"
        )) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString(1));
                    String bankId = rs.getString(2);
                    byte[] bankContent = rs.getBytes(3);

                    if (!playerBanks.containsKey(playerId)) {
                        playerBanks.put(playerId, new HashMap<>());
                    }

                    try {
                        ItemStack[] stacks = ItemStackSerializer.deserializeStacks(bankContent);
                        playerBanks.get(playerId).put(bankId, stacks);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Could not deserialize bank (" + playerId + ", " + bankId + ")");
                        e.printStackTrace();
                    }
                }
            }
            plugin.getLogger().info("[Bank] Loaded " + playerBanks.size() + " players");
        } catch (SQLException e) {
            plugin.getLogger().warning("[Bank] Could not load banks!");
            e.printStackTrace();
        }
    }

    private void saveBankToDatabase(UUID playerId, String bankId) {
        ItemStack[] stacks = playerBanks.get(playerId).get(bankId);
        byte[] bankContent;

        try {
            bankContent = ItemStackSerializer.serializeStacks(stacks);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not serialize bank (" + playerId + ", " + bankId + ")");
            e.printStackTrace();
            return;
        }

        try (PreparedStatement ps = plugin.getDatabase().prepareStatement("INSERT INTO banks" +
                " (player_id, bank_id, bank_content)" +
                " VALUES (?, ?, ?)" +
                " ON DUPLICATE KEY UPDATE bank_content = ?"
        )) {
            ps.setString(1, playerId.toString());
            ps.setString(2, bankId);
            ps.setBytes(3, bankContent);
            ps.setBytes(4, bankContent);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save bank to database (" + playerId + ", " + bankId + ")");
            e.printStackTrace();
        }
    }
}
