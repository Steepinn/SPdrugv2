package ru.spdrug.drug;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Реестр игровых хим-компонентов и их формул для лаборатории. */
public final class ChemicalRegistry {
    private static final List<ChemicalDefinition> CHEMICALS = new ArrayList<>();

    private ChemicalRegistry() {
    }

    public static void clear() {
        CHEMICALS.clear();
    }

    public static List<ChemicalDefinition> all() {
        return List.copyOf(CHEMICALS);
    }

    public static Optional<ChemicalDefinition> findById(String id) {
        for (ChemicalDefinition c : CHEMICALS) {
            if (c.getId().equalsIgnoreCase(id)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public static Optional<ChemicalDefinition> findByRecipeKeys(List<String> keys) {
        for (ChemicalDefinition c : CHEMICALS) {
            if (c.getRecipeKeys().equals(keys)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public static void reloadFromConfig(ConfigurationSection root, JavaPlugin plugin) {
        clear();
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            String symbol = s.getString("symbol", id.toUpperCase(Locale.ROOT));
            String name = s.getString("display-name", "&f" + symbol);
            Material mat = parseItemMaterial(s.getString("item-material", "SNOWBALL"), plugin, id);
            List<String> keys = normalizeRecipeKeys(s.getStringList("recipe"), plugin, id);
            if (!keys.isEmpty()) {
                CHEMICALS.add(new ChemicalDefinition(id, symbol, name, mat, keys));
            }
        }
    }

    private static Material parseItemMaterial(String raw, JavaPlugin plugin, String id) {
        Material m = Material.matchMaterial(raw == null ? "SNOWBALL" : raw.trim().toUpperCase(Locale.ROOT));
        if (m == null || m.isAir() || !m.isItem()) {
            plugin.getLogger().warning("Химкомпонент " + id + ": неверный item-material, использую SNOWBALL");
            return Material.SNOWBALL;
        }
        return m;
    }

    private static List<String> normalizeRecipeKeys(List<String> input, JavaPlugin plugin, String id) {
        List<String> out = new ArrayList<>();
        for (String raw : input) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String v = raw.trim();
            if (v.toUpperCase(Locale.ROOT).startsWith("CHEM:")) {
                out.add("CHEM:" + v.substring(5).trim().toLowerCase(Locale.ROOT));
                continue;
            }
            Material m = Material.matchMaterial(v.toUpperCase(Locale.ROOT));
            if (m == null || m.isAir()) {
                plugin.getLogger().warning("Химкомпонент " + id + ": неизвестный ингредиент " + raw);
                continue;
            }
            out.add("MAT:" + m.name());
        }
        return out;
    }
}
