package dev.kabumarket.market;

public enum PricePattern {
    FLUCTUATING(0, "波型"),
    LARGE_SPIKE(1, "跳ね大型"),
    DECREASING(2, "減少型"),
    SMALL_SPIKE(3, "跳ね小型");

    private final int id;
    private final String displayName;

    PricePattern(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static PricePattern byId(int id) {
        for (PricePattern p : values()) {
            if (p.id == id) return p;
        }
        return FLUCTUATING;
    }

    public static PricePattern byName(String name) {
        if (name == null) return null;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
