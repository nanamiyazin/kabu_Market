package dev.kabumarket;

import dev.kabumarket.market.Market;
import dev.kabumarket.market.MarketClock;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class KabuPlugin extends JavaPlugin {

    private MarketClock clock;
    private Market market;
    private Economy economy;
    private TurnipItems items;
    private KabuService service;

    private int bundleSize;
    private double priceMultiplier;
    private boolean announce;

    /** 最後にお知らせした「週ID*14+スロット」 */
    private long lastAnnounced = Long.MIN_VALUE;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        items = new TurnipItems(this);
        market = new Market(new File(getDataFolder(), "market.yml"), getLogger());
        market.load();
        service = new KabuService(this);

        MarketClock.Moment now = clock.now();
        market.ensureWeek(now.weekId());
        lastAnnounced = key(now); // 起動直後はお知らせしない

        PluginCommand cmd = getCommand("kabu");
        if (cmd != null) {
            KabuCommand executor = new KabuCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
        getServer().getPluginManager().registerEvents(new KabuListener(this), this);

        // 5秒ごとに時間の進みをチェック
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 100L, 100L);
        // 経済プラグインは自分より後に有効化されることがあるので、全プラグインの起動後に確認する
        Bukkit.getScheduler().runTask(this, () -> {
            if (economy() == null) {
                getLogger().severe("Vault 対応の経済プラグインが見つかりません。"
                        + "EssentialsX などの経済プラグインを入れてください。カブの売買はできません。");
            } else {
                getLogger().info("経済プラグインと連携しました: " + economy.getName());
            }
        });

        getLogger().info("KabuMarket 有効化 (モード: " + clock.mode() + ", 今週: "
                + market.pattern().displayName() + ")");
    }

    @Override
    public void onDisable() {
        if (market != null) market.save();
    }

    private void reloadSettings() {
        reloadConfig();
        clock = new MarketClock(getConfig());
        bundleSize = Math.max(1, getConfig().getInt("bundle-size", 10));
        priceMultiplier = Math.max(0.0, getConfig().getDouble("price-multiplier", 1.0));
        announce = getConfig().getBoolean("announce-price-change", true);
    }

    // ---------------- 定期処理 ----------------

    private void tick() {
        MarketClock.Moment m = clock.now();
        ensureWeek(m);
        long k = key(m);
        if (k == lastAnnounced) return;
        lastAnnounced = k;
        if (!announce) return;

        if (m.isSundayMorning()) {
            broadcast("<light_purple>カブ売りのウリが来たよ！</light_purple> 今週のカブは 1個 <yellow>"
                    + priceText(market.priceAt(0)) + "</yellow>。 <gray>/kabu で買えるよ");
        } else if (m.slot() == 1) {
            broadcast("<gray>ウリは帰っていった… 月曜日から買取が始まるよ。");
        } else {
            int price = market.priceAt(m.slot());
            int buy = market.priceAt(0);
            String color = price > buy ? "<green>" : "<red>";
            broadcast("<white>" + MarketClock.slotName(m.slot()) + "のカブ買取価格: " + color
                    + priceText(price) + "</white> <gray>(購入価格 " + priceText(buy) + ")");
            if (price >= buy * 3) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                }
            }
        }
    }

    private static long key(MarketClock.Moment m) {
        return m.weekId() * 14 + m.slot();
    }

    /** 週が変わっていたら新しい相場を作り、オンラインのプレイヤーのカブを腐らせる */
    void ensureWeek(MarketClock.Moment m) {
        if (market.ensureWeek(m.weekId())) {
            getLogger().info("新しい週になりました: " + market.pattern().displayName()
                    + " / 基準価格 " + market.basePrice());
            for (Player p : Bukkit.getOnlinePlayers()) {
                rotPlayer(p, m.weekId(), true);
            }
        }
    }

    void rotPlayer(Player p, boolean notify) {
        MarketClock.Moment m = clock.now();
        ensureWeek(m);
        rotPlayer(p, m.weekId(), notify);
    }

    private void rotPlayer(Player p, long week, boolean notify) {
        int rotted = items.rotInventory(p.getInventory(), week);
        rotted += items.rotInventory(p.getEnderChest(), week);
        if (rotted > 0 && notify) {
            p.sendMessage(Msg.chat("<dark_green>持っていたカブ " + rotted + "個 が腐ってしまった…"));
        }
    }

    private void broadcast(String mini) {
        Bukkit.broadcast(Msg.chat(mini));
    }

    // ---------------- 表示用 ----------------

    /**
     * 今週のカブ価の一覧（MiniMessage 文字列）。
     * @param reveal true なら未来の価格も表示（管理者用）
     */
    List<String> weekChart(MarketClock.Moment now, boolean reveal) {
        List<String> lines = new ArrayList<>();
        int buy = market.priceAt(0);
        lines.add("<gray>日 購入: <white>" + priceText(buy));
        for (int day = 1; day <= 6; day++) {
            StringBuilder sb = new StringBuilder("<gray>").append(MarketClock.dayName(day)).append(" ");
            for (int half = 0; half < 2; half++) {
                int slot = day * 2 + half;
                sb.append(half == 0 ? "午前 " : " / 午後 ");
                sb.append(cell(market.priceAt(slot), buy, slot, now.slot(), reveal, priceMultiplier));
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    private static String cell(int price, int buy, int slot, int current, boolean reveal, double mult) {
        String v = Msg.num(price * mult);
        if (slot > current) {
            // 未来: 通常は隠す。管理者表示ではカッコ付きで灰色
            return reveal ? "<dark_gray>(" + v + ")</dark_gray>" : "<dark_gray>???</dark_gray>";
        }
        String color = price > buy ? "green" : "red";
        String text = v;
        if (slot == current) {
            text = "<bold>▶" + v + "</bold>";
        }
        return "<" + color + ">" + text + "</" + color + ">";
    }

    // ---------------- アクセサ ----------------

    public MarketClock clock() { return clock; }
    public Market market() { return market; }
    public TurnipItems items() { return items; }
    public KabuService service() { return service; }
    public int bundleSize() { return bundleSize; }

    // ---------------- お金（Vault） ----------------

    /** Vault の経済プラグイン。見つからなければ null */
    public Economy economy() {
        if (economy == null && getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        return economy;
    }

    /** カブ価（相場の数値）1個あたりの実際の金額 */
    public double unitPrice(int rawPrice) {
        return rawPrice * priceMultiplier;
    }

    /** 金額を経済プラグインの書式で表示 */
    public String money(double amount) {
        Economy eco = economy();
        return eco != null ? eco.format(amount) : Msg.num(amount);
    }

    /** カブ価1個分を経済プラグインの書式で表示 */
    public String priceText(int rawPrice) {
        return money(unitPrice(rawPrice));
    }
}
