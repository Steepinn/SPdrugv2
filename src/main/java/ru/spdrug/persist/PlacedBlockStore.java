package ru.spdrug.persist;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Резерв к PDC на блоке: тип (лаборатория/теплица) и UUID, если ядро не пишет данные в компостер/дымилку.
 */
public final class PlacedBlockStore {

    public record StoredBlock(String type, UUID uuid) {}

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, StoredBlock> entries = new ConcurrentHashMap<>();

    public PlacedBlockStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "placed-blocks.yml");
    }

    public void register(String locKey, String type, UUID uuid) {
        entries.put(locKey, new StoredBlock(type, uuid));
        save();
    }

    public void remove(String locKey) {
        if (entries.remove(locKey) != null) {
            save();
        }
    }

    public StoredBlock get(String locKey) {
        return entries.get(locKey);
    }

    public Map<String, StoredBlock> copyEntries() {
        return Map.copyOf(entries);
    }

    public void load() {
        entries.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> list = cfg.getMapList("blocks");
        if (list == null) {
            return;
        }
        for (Map<?, ?> raw : list) {
            Object loc = raw.get("location");
            Object type = raw.get("type");
            Object uuid = raw.get("uuid");
            if (loc == null || type == null || uuid == null) {
                continue;
            }
            try {
                entries.put(
                        String.valueOf(loc),
                        new StoredBlock(String.valueOf(type), UUID.fromString(String.valueOf(uuid))));
            } catch (IllegalArgumentException ignored) {
                // skip bad row
            }
        }
    }

    public void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку плагина.");
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, StoredBlock> e : entries.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("location", e.getKey());
            row.put("type", e.getValue().type());
            row.put("uuid", e.getValue().uuid().toString());
            list.add(row);
        }
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("blocks", list);
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить placed-blocks.yml: " + ex.getMessage());
        }
    }
}
