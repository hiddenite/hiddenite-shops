package eu.hiddenite.shops.casino;

import eu.hiddenite.shops.Economy;
import eu.hiddenite.shops.ShopsPlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.UUID;

public class CasinoManager implements Listener {
    private final ShopsPlugin plugin;
    private final HashMap<UUID, Long> moneyButtonDelay = new HashMap<>();

    public CasinoManager(ShopsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.ACACIA_BUTTON) {
            return;
        }

        if (clickedBlock.getX() == 11 && clickedBlock.getY() == 68 && clickedBlock.getZ() == -12) {
            Player player = event.getPlayer();
            UUID playerID = player.getUniqueId();

            long currentTimeStamp = System.currentTimeMillis();
            if (moneyButtonDelay.containsKey(playerID))
            {
                long timePassed = currentTimeStamp - moneyButtonDelay.get(playerID);
                if (timePassed < 1000)
                {
                    return;
                }
            };

            Economy.ResultType result = plugin.getEconomy().addMoney(player.getUniqueId(), 1);
            if (result == Economy.ResultType.SUCCESS) {
                long money = plugin.getEconomy().getMoney(player.getUniqueId());
                player.sendMessage("+1! You now have " + plugin.getEconomy().format(money) + " C$");
                moneyButtonDelay.put(player.getUniqueId(), currentTimeStamp);
            } else {
                player.sendMessage("An error occurred :(");
            }
        }
    }
}
