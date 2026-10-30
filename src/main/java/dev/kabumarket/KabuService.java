package dev.kabumarket;

import dev.kabumarket.market.Market;
import dev.kabumarket.market.MarketClock;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

/** 売買・両替の実処理。コマンドと GUI の両方から呼ばれる */
public final class KabuService {

    private final KabuPlugin plugin;

    public KabuService(KabuPlugin plugin) {
        this.plugin = plugin;
    }

    private Market market() { return plugin.market(); }
    private Wallets wallets() { return plugin.wallets(); }
    private TurnipItems items() { return plugin.items(); }

    public MarketClock.Moment now() {
        MarketClock.Moment m = plugin.clock().now();
        plugin.ensureWeek(m);
        return m;
    }

    // ---------------- 買う（日曜午前のみ） ----------------

    /** @param amount 買いたい個数。-1 なら買えるだけ */
    public void buy(Player p, int amount) {
        MarketClock.Moment m = now();
        if (!m.isSundayMorning()) {
            p.sendMessage(Msg.chat("<red>カブ売りのウリは <yellow>日曜日の午前</yellow> にしか来ないよ。"));
            return;
        }
        int bundle = plugin.bundleSize();
        int price = market().priceAt(0);
        ItemStack sample = items().turnip(1, m.weekId(), price);
        int space = TurnipItems.freeSpaceFor(p.getInventory(), sample);
        long money = wallets().get(p.getUniqueId());

        int maxAffordable = (int) Math.min(Integer.MAX_VALUE, money / price);
        int max = Math.min(space, maxAffordable);
        max -= max % bundle;

        if (amount < 0) {
            amount = max;
            if (amount <= 0) {
                if (maxAffordable < bundle) {
                    p.sendMessage(Msg.chat("<red>ベルが足りないよ。（" + bundle + "個で "
                            + Msg.bells((long) price * bundle) + "）"));
                } else {
                    p.sendMessage(Msg.chat("<red>持ち物がいっぱいで買えないよ。"));
                }
                return;
            }
        }
        if (amount <= 0 || amount % bundle != 0) {
            p.sendMessage(Msg.chat("<red>カブは " + bundle + " 個単位で買ってね。"));
            return;
        }
        long cost = (long) price * amount;
        if (amount > space) {
            p.sendMessage(Msg.chat("<red>持ち物に入りきらないよ。（あと " + space + " 個まで）"));
            return;
        }
        if (!wallets().take(p.getUniqueId(), cost)) {
            p.sendMessage(Msg.chat("<red>ベルが足りないよ。必要: " + Msg.bells(cost)
                    + " / 所持: " + Msg.bells(money)));
            return;
        }
        giveStacks(p, items().turnip(1, m.weekId(), price), amount);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1.2f);
        p.sendMessage(Msg.chat("カブを <yellow>" + Msg.num(amount) + "個</yellow> 買ったよ！ (-"
                + Msg.bells(cost) + ")  <gray>今週の土曜日までに売ってね。"));
    }

    // ---------------- 売る（月〜土） ----------------

    /** @param amount 売りたい個数。-1 なら全部 */
    public void sell(Player p, int amount) {
        MarketClock.Moment m = now();
        if (m.isSunday()) {
            p.sendMessage(Msg.chat("<red>日曜日はカブを買い取っていないよ。月曜〜土曜に売ってね。"));
            return;
        }
        PlayerInventory inv = p.getInventory();
        items().rotInventory(inv, m.weekId());
        int have = items().countFresh(inv, m.weekId());
        if (have <= 0) {
            p.sendMessage(Msg.chat("<red>売れるカブを持っていないよ。"));
            return;
        }
        if (amount < 0 || amount > have) amount = have;
        int removed = items().removeFresh(inv, m.weekId(), amount);
        int price = market().priceAt(m.slot());
        long gain = (long) price * removed;
        wallets().add(p.getUniqueId(), gain);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        p.sendMessage(Msg.chat("カブを <yellow>" + Msg.num(removed) + "個</yellow> 売って <gold>"
                + Msg.bells(gain) + "</gold> 手に入れた！ <gray>(1個 " + price + "ベル)"));
    }

    // ---------------- 両替 ----------------

    /** @param amount エメラルドの個数。-1 なら全部 */
    public void deposit(Player p, int amount) {
        PlayerInventory inv = p.getInventory();
        int have = 0;
        for (ItemStack it : inv.getStorageContents()) {
            if (isPlainEmerald(it)) have += it.getAmount();
        }
        if (have == 0) {
            p.sendMessage(Msg.chat("<red>エメラルドを持っていないよ。"));
            return;
        }
        if (amount < 0 || amount > have) amount = have;
        int left = amount;
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (!isPlainEmerald(it)) continue;
            int take = Math.min(left, it.getAmount());
            left -= take;
            it.setAmount(it.getAmount() - take);
            inv.setItem(i, it.getAmount() <= 0 ? null : it);
        }
        long bells = (long) amount * plugin.bellsPerEmerald();
        wallets().add(p.getUniqueId(), bells);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
        p.sendMessage(Msg.chat("エメラルド " + amount + "個 を <gold>" + Msg.bells(bells)
                + "</gold> に両替したよ。残高: " + Msg.bells(wallets().get(p.getUniqueId()))));
    }

    public void withdraw(Player p, int amount) {
        if (amount <= 0) {
            p.sendMessage(Msg.chat("<red>1個以上を指定してね。"));
            return;
        }
        long cost = (long) amount * plugin.bellsPerEmerald();
        if (!wallets().take(p.getUniqueId(), cost)) {
            p.sendMessage(Msg.chat("<red>ベルが足りないよ。エメラルド1個 = "
                    + Msg.bells(plugin.bellsPerEmerald())));
            return;
        }
        giveStacks(p, new ItemStack(Material.EMERALD), amount);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
        p.sendMessage(Msg.chat(Msg.bells(cost) + " を エメラルド " + amount + "個 に両替したよ。残高: "
                + Msg.bells(wallets().get(p.getUniqueId()))));
    }

    private static boolean isPlainEmerald(ItemStack it) {
        return it != null && it.getType() == Material.EMERALD && !it.hasItemMeta();
    }

    /** 複数スタックに分けて渡す。入りきらない分は足元に落とす */
    private static void giveStacks(Player p, ItemStack sample, int amount) {
        int max = sample.getMaxStackSize();
        while (amount > 0) {
            int n = Math.min(max, amount);
            amount -= n;
            ItemStack s = sample.clone();
            s.setAmount(n);
            Map<Integer, ItemStack> leftover = p.getInventory().addItem(s);
            leftover.values().forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
        }
    }
}
