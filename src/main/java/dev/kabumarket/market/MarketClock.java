package dev.kabumarket.market;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 「今が第何週の何曜日の午前/午後か」を返す時計。
 * slot: 0=日AM 1=日PM 2=月AM ... 13=土PM
 */
public final class MarketClock {

    public enum Mode { REAL, GAME }

    public record Moment(long weekId, int slot) {
        public int dayOfWeek() { return slot / 2; }          // 0=日曜
        public boolean pm() { return slot % 2 == 1; }
        public boolean isSundayMorning() { return slot == 0; }
        public boolean isSunday() { return slot <= 1; }
    }

    private static final String[] DAY_NAMES = {"日", "月", "火", "水", "木", "金", "土"};

    private final Mode mode;
    private final ZoneId zone;
    private final int realPmHour;
    private final String gameWorld;
    private final int gamePmTick;

    public MarketClock(ConfigurationSection cfg) {
        Mode m;
        try {
            m = Mode.valueOf(cfg.getString("time-mode", "REAL").toUpperCase());
        } catch (IllegalArgumentException e) {
            m = Mode.REAL;
        }
        this.mode = m;
        ZoneId z;
        try {
            z = ZoneId.of(cfg.getString("timezone", "Asia/Tokyo"));
        } catch (Exception e) {
            z = ZoneId.systemDefault();
        }
        this.zone = z;
        this.realPmHour = cfg.getInt("real-pm-hour", 12);
        this.gameWorld = cfg.getString("game-world", "world");
        this.gamePmTick = cfg.getInt("game-pm-tick", 6000);
    }

    public Mode mode() {
        return mode;
    }

    public Moment now() {
        return mode == Mode.GAME ? gameNow() : realNow();
    }

    private Moment realNow() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        int dow = now.getDayOfWeek().getValue() % 7; // 月=1..日=7 → 日=0
        long epochDay = now.toLocalDate().toEpochDay();
        // 1970-01-01 は木曜。+4 して7で割ると日曜始まりの週番号になる
        long weekId = Math.floorDiv(epochDay + 4, 7);
        boolean pm = now.getHour() >= realPmHour;
        return new Moment(weekId, dow * 2 + (pm ? 1 : 0));
    }

    private Moment gameNow() {
        World world = Bukkit.getWorld(gameWorld);
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
        }
        long full = world.getFullTime();
        long day = Math.floorDiv(full, 24000L);
        long tick = Math.floorMod(full, 24000L);
        long weekId = Math.floorDiv(day, 7L);
        int dow = (int) Math.floorMod(day, 7L);
        boolean pm = tick >= gamePmTick;
        return new Moment(weekId, dow * 2 + (pm ? 1 : 0));
    }

    public static String slotName(int slot) {
        return DAY_NAMES[slot / 2] + "曜" + (slot % 2 == 0 ? "午前" : "午後");
    }

    public static String dayName(int dayOfWeek) {
        return DAY_NAMES[dayOfWeek];
    }
}
