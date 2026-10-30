package dev.kabumarket;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;

public final class KabuListener implements Listener {

    private final KabuPlugin plugin;

    public KabuListener(KabuPlugin plugin) {
        this.plugin = plugin;
    }

    private TurnipItems items() {
        return plugin.items();
    }

    private boolean isKabuItem(ItemStack it) {
        return items().isTurnip(it) || items().isRotten(it);
    }

    // ---------------- メニュー操作 ----------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof KabuMenu menu)) {
            onMerchantClick(e);
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() == top && e.getWhoClicked() instanceof Player) {
            menu.click(e.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof KabuMenu) {
            e.setCancelled(true);
        }
    }

    // ---------------- 腐敗チェック ----------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.rotPlayer(e.getPlayer(), true);
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (e.getInventory().getHolder(false) instanceof KabuMenu) return;
        long week = plugin.service().now().weekId();
        items().rotInventory(e.getInventory(), week);
    }

    // ---------------- カブを別用途に使えないようにする ----------------

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (isKabuItem(e.getItem())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Msg.chat("<red>このカブは食べられないよ！"));
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (isKabuItem(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent e) {
        ItemStack hand = e.getPlayer().getInventory().getItem(e.getHand());
        if (isKabuItem(hand)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onCompost(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b != null && b.getType() == Material.COMPOSTER && isKabuItem(e.getItem())) {
            e.setCancelled(true);
        }
    }

    /** 村人にカブを売らせない */
    private void onMerchantClick(InventoryClickEvent e) {
        if (!(e.getInventory() instanceof MerchantInventory mi)) return;
        if (e.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (isKabuItem(mi.getItem(0)) || isKabuItem(mi.getItem(1))) {
            e.setCancelled(true);
            e.getWhoClicked().sendMessage(Msg.chat("<red>カブは村人には売れないよ。/kabu で売ってね。"));
        }
    }
}
