package dev.kabumarket.market;

import java.util.Arrays;
import java.util.Random;

/**
 * あつまれどうぶつの森のカブ価アルゴリズムを再現した価格生成器。
 *
 * 価格配列は14要素:
 *   [0],[1] = 日曜 午前/午後（ウリの販売価格 = 基準価格）
 *   [2]..[13] = 月曜午前 〜 土曜午後 の買取価格
 *
 * パターン:
 *   0 = 波型, 1 = 跳ね大型(3〜6倍), 2 = 減少型, 3 = 跳ね小型(1.4〜2倍)
 */
public final class PriceGenerator {

    public static final int SLOTS = 14;

    /** 前週パターン → 今週パターン の遷移確率（%） [prev][next] */
    private static final int[][] TRANSITION = {
            {20, 30, 15, 35}, // 前週: 波型
            {50,  5, 20, 25}, // 前週: 跳ね大型
            {25, 45,  5, 25}, // 前週: 減少型
            {45, 25, 15, 15}, // 前週: 跳ね小型
    };

    private final Random random;

    public PriceGenerator(Random random) {
        this.random = random;
    }

    public PriceGenerator() {
        this(new Random());
    }

    public record Week(PricePattern pattern, int basePrice, int[] prices) {
        @Override
        public String toString() {
            return pattern + " base=" + basePrice + " " + Arrays.toString(prices);
        }
    }

    /** 前週パターンから今週のパターンを抽選する。prev が null なら初週扱い（跳ね小型）。 */
    public PricePattern nextPattern(PricePattern prev) {
        if (prev == null) {
            return PricePattern.SMALL_SPIKE;
        }
        int roll = random.nextInt(100);
        int[] row = TRANSITION[prev.id()];
        int acc = 0;
        for (int i = 0; i < row.length; i++) {
            acc += row[i];
            if (roll < acc) {
                return PricePattern.byId(i);
            }
        }
        return PricePattern.FLUCTUATING;
    }

    public Week generate(PricePattern prev) {
        PricePattern pattern = nextPattern(prev);
        int base = randInt(90, 110);
        return new Week(pattern, base, generate(pattern, base));
    }

    public int[] generate(PricePattern pattern, int base) {
        int[] p = new int[SLOTS];
        p[0] = base;
        p[1] = base;
        switch (pattern) {
            case FLUCTUATING -> fluctuating(p, base);
            case LARGE_SPIKE -> largeSpike(p, base);
            case DECREASING -> decreasing(p, base);
            case SMALL_SPIKE -> smallSpike(p, base);
        }
        return p;
    }

    // ---------------- 各パターン ----------------

    private void fluctuating(int[] p, int base) {
        int decPhaseLen1 = random.nextBoolean() ? 3 : 2;
        int decPhaseLen2 = 5 - decPhaseLen1;
        int hiPhaseLen1 = randInt(0, 6);
        int hiPhaseLen2and3 = 7 - hiPhaseLen1;
        int hiPhaseLen3 = randInt(0, hiPhaseLen2and3 - 1);

        int work = 2;
        for (int i = 0; i < hiPhaseLen1; i++) {
            p[work++] = intCeil(randFloat(0.9, 1.4) * base);
        }
        double rate = randFloat(0.8, 0.6);
        for (int i = 0; i < decPhaseLen1; i++) {
            p[work++] = intCeil(rate * base);
            rate -= 0.04;
            rate -= randFloat(0, 0.06);
        }
        for (int i = 0; i < hiPhaseLen2and3 - hiPhaseLen3; i++) {
            p[work++] = intCeil(randFloat(0.9, 1.4) * base);
        }
        rate = randFloat(0.8, 0.6);
        for (int i = 0; i < decPhaseLen2; i++) {
            p[work++] = intCeil(rate * base);
            rate -= 0.04;
            rate -= randFloat(0, 0.06);
        }
        for (int i = 0; i < hiPhaseLen3; i++) {
            p[work++] = intCeil(randFloat(0.9, 1.4) * base);
        }
    }

    private void largeSpike(int[] p, int base) {
        int peakStart = randInt(3, 9);
        double rate = randFloat(0.9, 0.85);
        int work = 2;
        for (; work < peakStart; work++) {
            p[work] = intCeil(rate * base);
            rate -= 0.03;
            rate -= randFloat(0, 0.02);
        }
        p[work++] = intCeil(randFloat(0.9, 1.4) * base);
        p[work++] = intCeil(randFloat(1.4, 2.0) * base);
        p[work++] = intCeil(randFloat(2.0, 6.0) * base);
        p[work++] = intCeil(randFloat(1.4, 2.0) * base);
        p[work++] = intCeil(randFloat(0.9, 1.4) * base);
        for (; work < SLOTS; work++) {
            p[work] = intCeil(randFloat(0.4, 0.9) * base);
        }
    }

    private void decreasing(int[] p, int base) {
        double rate = 0.9;
        rate -= randFloat(0, 0.05);
        for (int work = 2; work < SLOTS; work++) {
            p[work] = intCeil(rate * base);
            rate -= 0.03;
            rate -= randFloat(0, 0.02);
        }
    }

    private void smallSpike(int[] p, int base) {
        int peakStart = randInt(2, 9);
        double rate = randFloat(0.9, 0.4);
        int work = 2;
        for (; work < peakStart; work++) {
            p[work] = intCeil(rate * base);
            rate -= 0.03;
            rate -= randFloat(0, 0.02);
        }
        p[work++] = intCeil(randFloat(0.9, 1.4) * base);
        p[work++] = intCeil(randFloat(0.9, 1.4) * base);
        rate = randFloat(1.4, 2.0);
        p[work++] = intCeil(randFloat(1.4, rate) * base) - 1;
        p[work++] = intCeil(rate * base);
        p[work++] = intCeil(randFloat(1.4, rate) * base) - 1;
        if (work < SLOTS) {
            rate = randFloat(0.9, 0.4);
            for (; work < SLOTS; work++) {
                p[work] = intCeil(rate * base);
                rate -= 0.03;
                rate -= randFloat(0, 0.02);
            }
        }
    }

    // ---------------- 乱数ユーティリティ ----------------

    private int randInt(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    /** a と b の間の実数（a > b でも可） */
    private double randFloat(double a, double b) {
        return a + (b - a) * random.nextDouble();
    }

    /** ゲーム内と同じ切り上げ処理 */
    private static int intCeil(double v) {
        return (int) (v + 0.99999);
    }
}
