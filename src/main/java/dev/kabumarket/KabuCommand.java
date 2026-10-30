package dev.kabumarket;

import dev.kabumarket.market.Market;
import dev.kabumarket.market.MarketClock;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KabuCommand implements TabExecutor {

    private final KabuPlugin plugin;

    public KabuCommand(KabuPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("admin")) {
            admin(sender, args);
            return true;
        }
        if (sub.equals("price") || sub.equals("p")) {
            showPrice(sender);
            return true;
        }
        if (sub.equals("help")) {
            help(sender);
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(Msg.chat("<red>プレイヤーのみ使えます。（/kabu price, /kabu admin は可）"));
            return true;
        }
        KabuService svc = plugin.service();
        switch (sub) {
            case "" -> new KabuMenu(plugin, p).open();
            case "buy" -> {
                Integer n = args.length >= 2 ? parseAmount(args[1]) : Integer.valueOf(plugin.bundleSize());
                if (n == null) { usage(p, "/kabu buy <個数|max>"); return true; }
                svc.buy(p, n);
            }
            case "sell" -> {
                Integer n = args.length >= 2 ? parseAmount(args[1]) : Integer.valueOf(-1);
                if (n == null) { usage(p, "/kabu sell [個数|all]"); return true; }
                svc.sell(p, n);
            }
            case "balance", "bal", "money" -> p.sendMessage(Msg.chat("所持金: <gold>"
                    + plugin.money(svc.balance(p))));
            default -> help(p);
        }
        return true;
    }

    /** 数値 / all / max を解釈。all・max は -1 */
    private static Integer parseAmount(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        if (l.equals("all") || l.equals("max")) return -1;
        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void usage(CommandSender s, String u) {
        s.sendMessage(Msg.chat("<red>使い方: " + u));
    }

    private void showPrice(CommandSender sender) {
        MarketClock.Moment m = plugin.service().now();
        Market market = plugin.market();
        sender.sendMessage(Msg.chat("<yellow>今週のカブ価</yellow> <gray>(今: " + MarketClock.slotName(m.slot()) + ")"));
        // 未来の価格は隠す（管理者は /kabu admin info で全部見られる）
        for (String line : plugin.weekChart(m, false)) {
            sender.sendMessage(Msg.mm("  " + line));
        }
    }

    private void help(CommandSender s) {
        s.sendMessage(Msg.chat("<yellow>カブ市場 コマンド一覧"));
        s.sendMessage(Msg.mm("<gold>/kabu</gold> <gray>- 市場の画面を開く"));
        s.sendMessage(Msg.mm("<gold>/kabu price</gold> <gray>- 今週のカブ価を見る"));
        s.sendMessage(Msg.mm("<gold>/kabu buy <個数|max></gold> <gray>- 日曜午前にカブを買う"));
        s.sendMessage(Msg.mm("<gold>/kabu sell [個数|all]</gold> <gray>- 月〜土にカブを売る"));
        s.sendMessage(Msg.mm("<gold>/kabu balance</gold> <gray>- 所持金を見る"));
        if (s.hasPermission("kabu.admin")) {
            s.sendMessage(Msg.mm("<red>/kabu admin info|reroll"));
        }
    }

    // ---------------- 管理者 ----------------

    private void admin(CommandSender s, String[] args) {
        if (!s.hasPermission("kabu.admin")) {
            s.sendMessage(Msg.chat("<red>権限がありません。"));
            return;
        }
        String op = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "info";
        Market market = plugin.market();
        switch (op) {
            case "info" -> {
                MarketClock.Moment m = plugin.service().now();
                s.sendMessage(Msg.chat("<red>[管理]</red> 週ID: " + m.weekId() + " / パターン: <yellow>"
                        + market.pattern().displayName() + "</yellow> / 基準価格: " + plugin.priceText(market.basePrice())
                        + " / モード: " + plugin.clock().mode()
                        + " / 経済: " + (plugin.economy() == null ? "<red>未接続</red>" : plugin.economy().getName())));
                for (String line : plugin.weekChart(m, true)) {
                    s.sendMessage(Msg.mm("  " + line));
                }
            }
            case "reroll" -> {
                market.roll(market.weekId());
                s.sendMessage(Msg.chat("<red>[管理]</red> 今週の相場を作り直しました: <yellow>"
                        + market.pattern().displayName()));
            }
            default -> usage(s, "/kabu admin info|reroll");
        }
    }

    // ---------------- 補完 ----------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("price", "buy", "sell", "balance", "help"));
            if (sender.hasPermission("kabu.admin")) subs.add("admin");
            filter(out, subs, args[0]);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "buy" -> filter(out, List.of("10", "100", "max"), args[1]);
                case "sell" -> filter(out, List.of("all", "10", "64"), args[1]);
                case "admin" -> {
                    if (sender.hasPermission("kabu.admin"))
                        filter(out, List.of("info", "reroll"), args[1]);
                }
                default -> { }
            }
        }
        return out;
    }

    private static void filter(List<String> out, List<String> options, String prefix) {
        String l = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(l)) out.add(o);
        }
    }
}
