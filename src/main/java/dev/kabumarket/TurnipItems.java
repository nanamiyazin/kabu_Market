package dev.kabumarket;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * カブ・腐ったカブのアイテム。
 * カブには「買った週」を記録し、週が変わると腐ったカブになる。
 */
public final class TurnipItems {

    private static final String TYPE_TURNIP = "turnip";
    private static final String TYPE_ROTTEN = "rotten";

    private final NamespacedKey typeKey;
    private final NamespacedKey weekKey;

    public TurnipItems(Plugin plugin) {
        this.typeKey = new NamespacedKey(plugin, "type");
        this.weekKey = new NamespacedKey(plugin, "week");
    }

    public ItemStack turnip(int amount, long weekId, int boughtPrice) {
        ItemStack stack = new ItemStack(Material.BEETROOT, amount);
        stack.editMeta(meta -> {
            meta.displayName(Msg.item("<white><bold>カブ</bold></white>"));
            meta.lore(List.of(
                    Msg.item("<gray>ウリから買ったカブ。"),
                    Msg.item("<gray>月〜土に <yellow>/kabu</yellow> で売れる。"),
                    Msg.item("<red>次の日曜日になると腐ってしまう！"),
                    Msg.item("<dark_gray>購入時のカブ価: " + boughtPrice)
            ));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(typeKey, PersistentDataType.STRING, TYPE_TURNIP);
            pdc.set(weekKey, PersistentDataType.LONG, weekId);
        });
        return stack;
    }

    public ItemStack rotten(int amount) {
        ItemStack stack = new ItemStack(Material.POISONOUS_POTATO, amount);
        stack.editMeta(meta -> {
            meta.displayName(Msg.item("<dark_green>腐ったカブ"));
            meta.lore(List.of(
                    Msg.item("<gray>売りそびれて腐ってしまったカブ。"),
                    Msg.item("<gray>もう誰も買い取ってくれない…")
            ));
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, TYPE_ROTTEN);
        });
        return stack;
    }

    private String typeOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        return meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    public boolean isTurnip(ItemStack stack) {
        return TYPE_TURNIP.equals(typeOf(stack));
    }

    public boolean isRotten(ItemStack stack) {
        return TYPE_ROTTEN.equals(typeOf(stack));
    }

    public long weekOf(ItemStack stack) {
        Long w = stack.getItemMeta().getPersistentDataContainer().get(weekKey, PersistentDataType.LONG);
        return w == null ? Long.MIN_VALUE : w;
    }

    /** 今週のカブ（＝売れるカブ）か */
    public boolean isFresh(ItemStack stack, long currentWeek) {
        return isTurnip(stack) && weekOf(stack) == currentWeek;
    }

    /** インベントリ内の古いカブを腐らせる。腐った個数を返す */
    public int rotInventory(Inventory inv, long currentWeek) {
        int rotted = 0;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (isTurnip(it) && weekOf(it) != currentWeek) {
                rotted += it.getAmount();
                inv.setItem(i, rotten(it.getAmount()));
            }
        }
        return rotted;
    }

    /** 売れるカブの所持数 */
    public int countFresh(PlayerInventory inv, long currentWeek) {
        int n = 0;
        for (ItemStack it : inv.getContents()) {
            if (isFresh(it, currentWeek)) n += it.getAmount();
        }
        return n;
    }

    /** 売れるカブを最大 amount 個取り除き、実際に取り除いた数を返す */
    public int removeFresh(PlayerInventory inv, long currentWeek, int amount) {
        int left = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (!isFresh(it, currentWeek)) continue;
            int take = Math.min(left, it.getAmount());
            left -= take;
            if (take == it.getAmount()) {
                inv.setItem(i, null);
            } else {
                it.setAmount(it.getAmount() - take);
                inv.setItem(i, it);
            }
        }
        return amount - left;
    }

    /** プレイヤーのメインインベントリ(36枠)に sample と同じアイテムがあと何個入るか */
    public static int freeSpaceFor(PlayerInventory inv, ItemStack sample) {
        int max = sample.getMaxStackSize();
        int space = 0;
        for (ItemStack it : inv.getStorageContents()) {
            if (it == null || it.isEmpty()) {
                space += max;
            } else if (it.isSimilar(sample)) {
                space += Math.max(0, max - it.getAmount());
            }
        }
        return space;
    }
}
