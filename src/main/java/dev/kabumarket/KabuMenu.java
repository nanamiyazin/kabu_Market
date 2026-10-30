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
        long balance = plugin.wallets().get(player.getUniqueId());
        int fresh = plugin.items().countFresh(player.getInventory(), m.weekId());

        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, filler);

        // 情報（中央上）
        inventory.setItem(4, infoIcon(m, market, fresh));

        // 売買ボタン
        if (m.isSundayMorning()) {
            int price = market.priceAt(0);
            button(11, Material.BEETROOT, "<green>カブを " + bundle + "個 買う",
                    List.of("<gray>" + Msg.bells((long) price * bundle)), p -> svc.buy(p, bundle));
            button(13, Material.BEETROOT, "<green>カブを " + bundle * 10 + "個 買う",
                    List.of("<gray>" + Msg.bells((long) price * bundle * 10)), p -> svc.buy(p, bundle * 10));
            button(15, Material.HAY_BLOCK, "<gold>買えるだけ買う",
                    List.of("<gray>所持ベルと持ち物の空きの分だけ"), p -> svc.buy(p, -1));
        } else if (!m.isSunday()) {
            int price = market.priceAt(m.slot());
            button(11, Material.GOLD_NUGGET, "<yellow>カブを " + bundle + "個 売る",
                    List.of("<gray>+" + Msg.bells((long) price * Math.min(bundle, fresh))), p -> svc.sell(p, bundle));
            button(13, Material.GOLD_INGOT, "<yellow>カブを 64個 売る",
                    List.of("<gray>+" + Msg.bells((long) price * Math.min(64, fresh))), p -> svc.sell(p, 64));
            button(15, Material.GOLD_BLOCK, "<gold>全部売る",
                    List.of("<gray>" + Msg.num(fresh) + "個 → +" + Msg.bells((long) price * fresh)),
                    p -> svc.sell(p, -1));
        } else {
            ItemStack closed = icon(Material.BARRIER, "<red>営業時間外",
                    List.of("<gray>日曜午後はお休み。", "<gray>月曜の朝から買取が始まるよ。"));
            inventory.setItem(13, closed);
        }

        // 財布と両替
        int bpe = plugin.bellsPerEmerald();
        button(18, Material.EMERALD, "<green>エメラルドを1個 入金",
                List.of("<gray>+" + Msg.bells(bpe)), p -> plugin.service().deposit(p, 1));
        button(19, Material.EMERALD_BLOCK, "<green>エメラルドを全部 入金",
                List.of("<gray>1個 = " + Msg.bells(bpe)), p -> plugin.service().deposit(p, -1));
        inventory.setItem(22, icon(Material.SUNFLOWER, "<gold>財布: " + Msg.bells(balance),
                List.of("<gray>エメラルドと両替できる")));
        button(25, Material.EMERALD, "<aqua>1個 引き出す",
                List.of("<gray>-" + Msg.bells(bpe)), p -> plugin.service().withdraw(p, 1));
        button(26, Material.EMERALD, "<aqua>10個 引き出す",
                List.of("<gray>-" + Msg.bells(bpe * 10L)), p -> plugin.service().withdraw(p, 10));
    }

    private ItemStack infoIcon(MarketClock.Moment m, Market market, int fresh) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>今: <white>" + MarketClock.slotName(m.slot()));
        if (m.isSundayMorning()) {
            lore.add("<gray>ウリの販売価格: <yellow>" + market.priceAt(0) + "ベル");
        } else if (!m.isSunday()) {
            lore.add("<gray>今の買取価格: <yellow>" + market.priceAt(m.slot()) + "ベル");
            lore.add("<gray>今週の購入価格: <white>" + market.priceAt(0) + "ベル");
        }
        lore.add("<gray>売れるカブ: <white>" + Msg.num(fresh) + "個");
        lore.add("");
        lore.add("<dark_aqua>今週のカブ価");
        lore.addAll(KabuPlugin.weekChart(market, m, false));
        return icon(Material.CLOCK, "<yellow>カブ価情報", lore);
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
