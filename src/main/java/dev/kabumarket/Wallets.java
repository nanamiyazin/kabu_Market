package dev.kabumarket;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/** プレイヤーごとのベル残高 */
public final class Wallets {

    private final File file;
    private final Logger logger;
    private final Map<UUID, Long> balances = new HashMap<>();
    private boolean dirty;

    public Wallets(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public long get(UUID id) {
        return balances.getOrDefault(id, 0L);
    }

    public void set(UUID id, long amount) {
        balances.put(id, Math.max(0, amount));
        dirty = true;
    }

    public void add(UUID id, long amount) {
        set(id, get(id) + amount);
    }

    /** 残高が足りれば引いて true */
    public boolean take(UUID id, long amount) {
        long cur = get(id);
        if (cur < amount) return false;
        set(id, cur - amount);
        return true;
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), y.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        balances.forEach((id, v) -> y.set(id.toString(), v));
        try {
            y.save(file);
            dirty = false;
        } catch (IOException e) {
            logger.warning("wallets.yml を保存できませんでした: " + e.getMessage());
        }
    }
}
