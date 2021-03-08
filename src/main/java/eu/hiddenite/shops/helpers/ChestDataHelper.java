package eu.hiddenite.shops.helpers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.persistence.PersistentDataType;

public class ChestDataHelper {
    public static boolean setBlockChestType(Block block, NamespacedKey chestType) {
        if (!isBlockValidChest(block)) {
            return false;
        }
        Chest chest = (Chest)block.getState();
        chest.getPersistentDataContainer().set(chestType, PersistentDataType.BYTE, (byte)1);
        chest.update();
        return true;
    }

    public static boolean isBlockChestType(Block block, NamespacedKey chestType) {
        if (!isBlockValidChest(block)) {
            return false;
        }
        Chest chest = (Chest)block.getState();
        Byte result = chest.getPersistentDataContainer().get(chestType, PersistentDataType.BYTE);
        return result != null && result == 1;
    }

    public static boolean isBlockValidChest(Block block) {
        if (block == null) {
            return false;
        }
        if (block.getType() != Material.CHEST) {
            return false;
        }
        return block.getState() instanceof Chest;
    }
}
