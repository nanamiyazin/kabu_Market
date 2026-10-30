package dev.kabumarket;

import dev.kabumarket.market.Market;
import dev.kabumarket.market.MarketClock;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** /kabu で開くカブ市場の画面 */
public final class KabuMenu implements InventoryHolder {

    private static final int SIZE = 27;

    private final KabuPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    public KabuMenu(KabuPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, SIZE, Msg.mm("<dark_green>カブ市場"));
        render();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(inventory);
    }

    /** クリックされたスロットの処理。処理後に表示を更新する */
    public void click(int slot) {
        Consumer<Player> action = actions.get(slot);
        if (action == null) return;
        action.accept(player);
        render();
    }

    public void render() {
        inventory.clear();
        actions.clear();

        KabuService svc = plugin.service();
        MarketClock.Moment m = svc.now();
        Market market = plugin.market();
        int bundle = plugin.bundleSize();
        double balance = svc.balance(player);
        int fresh = plugin.items().countFresh(player.getInventory(), m.weekId());

        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, filler);

        // 情報（中央上）
        inventory.setItem(4, infoIcon(m, market, fresh));

        // ===== 左: 買う / 右: 売る を同じ画面に並べる =====
        boolean canBuy = m.isSundayMorning();
        boolean canSell = !m.isSunday();

        // 見出し
        inventory.setItem(2, icon(canBuy ? Material.LIME_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE,
                (canBuy ? "<green>" : "<gray>") + "◆ 買う（ウリ）",
                List.of("<gray>日曜日の午前だけ買えるよ",
                        "<gray>今週の販売価格: <white>" + plugin.priceText(market.priceAt(0)),
                        canBuy ? "<green>● 営業中" : "<red>● 営業時間外")));
        inventory.setItem(6, icon(canSell ? Material.YELLOW_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE,
                (canSell ? "<yellow>" : "<gray>") + "◆ 売る（買取）",
                List.of("<gray>月曜〜土曜に売れるよ",
                        canSell ? "<gray>今の買取価格: <white>" + plugin.priceText(market.priceAt(m.slot()))
                                : "<gray>買取価格: <white>月曜の朝に発表",
                        canSell ? "<green>● 営業中" : "<red>● 営業時間外")));

        // 買うボタン（10, 11, 12）
        double buyUnit = plugin.unitPrice(market.priceAt(0));
        String buyClosed = "<red>今は買えないよ（日曜午前のみ）";
        tradeButton(10, canBuy, Material.BEETROOT, "<green>" + bundle + "個 買う",
                "<gray>" + plugin.money(buyUnit * bundle), buyClosed, p -> svc.buy(p, bundle));
        tradeButton(11, canBuy, Material.BEETROOT, "<green>" + bundle * 10 + "個 買う",
                "<gray>" + plugin.money(buyUnit * bundle * 10), buyClosed, p -> svc.buy(p, bundle * 10));
        tradeButton(12, canBuy, Material.HAY_BLOCK, "<gold>買えるだけ買う",
                "<gray>所持金と持ち物の空きの分だけ", buyClosed, p -> svc.buy(p, -1));

        // 売るボタン（14, 15, 16）
        double sellUnit = canSell ? plugin.unitPrice(market.priceAt(m.slot())) : 0;
        String sellClosed = "<red>今は売れないよ（月〜土のみ）";
        tradeButton(14, canSell, Material.GOLD_NUGGET, "<yellow>" + bundle + "個 売る",
                "<gray>+" + plugin.money(sellUnit * Math.min(bundle, fresh)), sellClosed, p -> svc.sell(p, bundle));
        tradeButton(15, canSell, Material.GOLD_INGOT, "<yellow>64個 売る",
                "<gray>+" + plugin.money(sellUnit * Math.min(64, fresh)), sellClosed, p -> svc.sell(p, 64));
        tradeButton(16, canSell, Material.GOLD_BLOCK, "<gold>全部売る",
                "<gray>" + Msg.num(fresh) + "個 → +" + plugin.money(sellUnit * fresh), sellClosed, p -> svc.sell(p, -1));

        // 真ん中の仕切り
        inventory.setItem(13, icon(Material.WHITE_STAINED_GLASS_PANE, " ", List.of()));

        // 所持金
        inventory.setItem(22, icon(Material.SUNFLOWER, "<gold>所持金: " + plugin.money(balance),
                List.of("<gray>サーバーの経済プラグインのお金")));
    }

    private ItemStack infoIcon(MarketClock.Moment m, Market market, int fresh) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>今: <white>" + MarketClock.slotName(m.slot()));
        if (m.isSundayMorning()) {
            lore.add("<gray>ウリの販売価格: <yellow>" + plugin.priceText(market.priceAt(0)));
        } else if (!m.isSunday()) {
            lore.add("<gray>今の買取価格: <yellow>" + plugin.priceText(market.priceAt(m.slot())));
            lore.add("<gray>今週の購入価格: <white>" + plugin.priceText(market.priceAt(0)));
        }
        lore.add("<gray>売れるカブ: <white>" + Msg.num(fresh) + "個");
        lore.add("");
        lore.add("<dark_aqua>今週のカブ価");
        lore.addAll(plugin.weekChart(m, false));
        return icon(Material.CLOCK, "<yellow>カブ価情報", lore);
    }

    /** 売買ボタン。営業時間外は灰色で表示（押すと理由をチャットで案内） */
    private void tradeButton(int slot, boolean open, Material mat, String name, String detail,
                             String closedText, Consumer<Player> action) {
        if (open) {
            button(slot, mat, name, List.of(detail, "", "<white>クリックで実行"), action);
        } else {
            button(slot, Material.GRAY_DYE, stripTags(name), List.of(closedText), action);
        }
    }

    private static String stripTags(String mini) {
        return "<gray>" + mini.replaceAll("<[^>]+>", "");
    }

    private void button(int slot, Material mat, String name, List<String> lore, Consumer<Player> action) {
        inventory.setItem(slot, icon(mat, name, lore));
        actions.put(slot, action);
    }

    private static ItemStack icon(Material mat, String name, List<String> lore) {
        ItemStack s = new ItemStack(mat);
        s.editMeta(meta -> {
            meta.displayName(Msg.item(name));
            List<Component> l = new ArrayList<>();
            for (String line : lore) l.add(Msg.item(line));
            meta.lore(l);
        });
        return s;
    }
}
