package dev.kabumarket.market;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * サーバー全体（＝島）で共通のカブ相場。週が変わると新しい相場を生成する。
 */
public final class Market {

    private final File file;
    private final Logger logger;
    private final PriceGenerator generator = new PriceGenerator();

    private long weekId = Long.MIN_VALUE;
    private PricePattern pattern;
    private PricePattern previousPattern;
    private int basePrice;
    private int[] prices = new int[PriceGenerator.SLOTS];

    public Market(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /** 週が変わっていたら相場を作り直す。作り直したら true */
    public boolean ensureWeek(long currentWeek) {
        if (currentWeek == weekId && pattern != null) {
            return false;
        }
        roll(currentWeek);
        return true;
    }

    /** 新しい週の相場を生成（管理者の再抽選にも使う） */
    public void roll(long newWeek) {
        if (newWeek != weekId) {
            previousPattern = pattern;
        }
        PriceGenerator.Week w = generator.generate(previousPattern);
        weekId = newWeek;
        pattern = w.pattern();
        basePrice = w.basePrice();
        prices = w.prices();
        save();
    }

    public long weekId() { return weekId; }
    public PricePattern pattern() { return pattern; }
    public int basePrice() { return basePrice; }

    /** 指定スロットの価格。日曜は販売価格、月〜土は買取価格 */
    public int priceAt(int slot) {
        return prices[slot];
    }

    // ---------------- 保存 ----------------

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (!y.contains("week")) return;
        weekId = y.getLong("week");
        pattern = PricePattern.byName(y.getString("pattern"));
        previousPattern = PricePattern.byName(y.getString("previous-pattern"));
        basePrice = y.getInt("base-price");
        List<Integer> list = y.getIntegerList("prices");
        if (list.size() == PriceGenerator.SLOTS) {
            for (int i = 0; i < list.size(); i++) prices[i] = list.get(i);
        } else {
            pattern = null; // 壊れていたら作り直す
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("week", weekId);
        y.set("pattern", pattern == null ? null : pattern.name());
        y.set("previous-pattern", previousPattern == null ? null : previousPattern.name());
        y.set("base-price", basePrice);
        List<Integer> list = new ArrayList<>();
        for (int p : prices) list.add(p);
        y.set("prices", list);
        try {
            y.save(file);
        } catch (IOException e) {
            logger.warning("market.yml を保存できませんでした: " + e.getMessage());
        }
    }
}
