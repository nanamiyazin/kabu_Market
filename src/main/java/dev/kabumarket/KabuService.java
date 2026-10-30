package dev.kabumarket;

import dev.kabumarket.market.Market;
import dev.kabumarket.market.MarketClock;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

/** 売買の実処理。コマンドと GUI の両方から呼ばれる。お金は Vault の経済プラグインを使う */
public final class KabuService {

    private final KabuPlugin plugin;

    public KabuService(KabuPlugin plugin) {
        this.plugin = plugin;
    }

    private Market market() { return plugin.market(); }
    private TurnipItems items() { return plugin.items(); }

    public MarketClock.Moment now() {
        MarketClock.Moment m = plugin.clock().now();
        plugin.ensureWeek(m);
        return m;
    }

    /** 経済プラグインを取得。無ければプレイヤーに知らせて null */
    private Economy economy(Player p) {
        Economy eco = plugin.economy();
        if (eco == null) {
            p.sendMessage(Msg.chat("<red>経済プラグインが見つからないため、今は売買できません。管理者に連絡してね。"));
        }
        return eco;
    }

    public double balance(Player p) {
        Economy eco = plugin.economy();
        return eco == null ? 0 : eco.getBalance(p);
    }

    // ---------------- 買う（日曜午前のみ） ----------------

    /** @param amount 買いたい個数。-1 なら買えるだけ */
    public void buy(Player p, int amount) {
        buy(p, amount, true);
    }

    /**
     * @param amount        買いたい個数。-1 なら買えるだけ
     * @param enforceBundle false なら bundle-size 単位でなくても買える（GUI の64個ボタン用）
     */
    public void buy(Player p, int amount, boolean enforceBundle) {
        MarketClock.Moment m = now();
        if (!m.isSundayMorning()) {
            p.sendMessage(Msg.chat("<red>カブ売りのウリは <yellow>日曜日の午前</yellow> にしか来ないよ。"));
            return;
        }
        Economy eco = economy(p);
        if (eco == null) return;

        int bundle = plugin.bundleSize();
        int raw = market().priceAt(0);
        double unit = plugin.unitPrice(raw);
        ItemStack sample = items().turnip(1, m.weekId(), raw);
        int space = TurnipItems.freeSpaceFor(p.getInventory(), sample);
        double money = eco.getBalance(p);

        int maxAffordable = unit <= 0 ? Integer.MAX_VALUE
                : (int) Math.min(Integer.MAX_VALUE, Math.floor(money / unit));
        int max = Math.min(space, maxAffordable);
        max -= max % bundle;

        if (amount < 0) {
            amount = max;
            if (amount <= 0) {
                if (maxAffordable < bundle) {
                    p.sendMessage(Msg.chat("<red>お金が足りないよ。（" + bundle + "個で "
                            + plugin.money(unit * bundle) + "）"));
                } else {
                    p.sendMessage(Msg.chat("<red>持ち物がいっぱいで買えないよ。"));
                }
                return;
            }
        }
        if (amount <= 0 || (enforceBundle && amount % bundle != 0)) {
            p.sendMessage(Msg.chat("<red>カブは " + bundle + " 個単位で買ってね。"));
            return;
        }
        if (amount > space) {
            p.sendMessage(Msg.chat("<red>持ち物に入りきらないよ。（あと " + space + " 個まで）"));
            return;
        }
        double cost = unit * amount;
        if (!eco.has(p, cost)) {
            p.sendMessage(Msg.chat("<red>お金が足りないよ。必要: " + plugin.money(cost)
                    + " / 所持: " + plugin.money(money)));
            return;
        }
        EconomyResponse res = eco.withdrawPlayer(p, cost);
        if (!res.transactionSuccess()) {
            p.sendMessage(Msg.chat("<red>支払いに失敗しました: " + res.errorMessage));
            return;
        }
        giveStacks(p, items().turnip(1, m.weekId(), raw), amount);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1.2f);
        p.sendMessage(Msg.chat("カブを <yellow>" + Msg.num(amount) + "個</yellow> 買ったよ！ (-"
                + plugin.money(cost) + ")  <gray>今週の土曜日までに売ってね。"));
    }

    // ---------------- 売る（月〜土） ----------------

    /** @param amount 売りたい個数。-1 なら全部 */
    public void sell(Player p, int amount) {
        MarketClock.Moment m = now();
        if (m.isSunday()) {
            p.sendMessage(Msg.chat("<red>日曜日はカブを買い取っていないよ。月曜〜土曜に売ってね。"));
            return;
        }
        Economy eco = economy(p);
        if (eco == null) return;

        PlayerInventory inv = p.getInventory();
        items().rotInventory(inv, m.weekId());
        int have = items().countFresh(inv, m.weekId());
        if (have <= 0) {
            p.sendMessage(Msg.chat("<red>売れるカブを持っていないよ。"));
            return;
        }
        if (amount < 0 || amount > have) amount = have;
        int raw = market().priceAt(m.slot());
        double gain = plugin.unitPrice(raw) * amount;

        // 先に入金を試し、成功してからカブを取り除く
        EconomyResponse res = eco.depositPlayer(p, gain);
        if (!res.transactionSuccess()) {
            p.sendMessage(Msg.chat("<red>入金に失敗しました: " + res.errorMessage));
            return;
        }
        int removed = items().removeFresh(inv, m.weekId(), amount);
        if (removed < amount) {
            // 念のため：取り除けなかった分を返金
            eco.withdrawPlayer(p, plugin.unitPrice(raw) * (amount - removed));
            gain = plugin.unitPrice(raw) * removed;
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        p.sendMessage(Msg.chat("カブを <yellow>" + Msg.num(removed) + "個</yellow> 売って <gold>"
                + plugin.money(gain) + "</gold> 手に入れた！ <gray>(1個 " + plugin.priceText(raw) + ")"));
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
