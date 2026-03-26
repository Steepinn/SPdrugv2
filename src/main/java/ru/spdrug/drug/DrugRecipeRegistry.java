package ru.spdrug.drug;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Реестр рецептов лаборатории. Загрузка из config.yml (секция drugs) или встроенные значения.
 */
public final class DrugRecipeRegistry {

    private static final List<DrugDefinition> DRUGS = new ArrayList<>();

    private DrugRecipeRegistry() {}

    public static void clear() {
        DRUGS.clear();
    }

    public static void register(DrugDefinition def) {
        DRUGS.add(def);
    }

    public static Optional<DrugDefinition> findBySequence(List<Material> sequence) {
        if (sequence == null || sequence.isEmpty()) {
            return Optional.empty();
        }
        for (DrugDefinition d : DRUGS) {
            if (d.getRecipeSequence().equals(sequence)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    public static Optional<DrugDefinition> findById(String id) {
        for (DrugDefinition d : DRUGS) {
            if (d.getId().equalsIgnoreCase(id)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    public static List<DrugDefinition> all() {
        return List.copyOf(DRUGS);
    }

    public static void reloadFromConfig(ConfigurationSection drugsRoot, JavaPlugin plugin) {
        clear();
        if (drugsRoot == null || drugsRoot.getKeys(false).isEmpty()) {
            plugin.getLogger().info("Секция drugs пуста — загружены встроенные препараты.");
            registerDefaults();
            return;
        }
        for (String id : drugsRoot.getKeys(false)) {
            ConfigurationSection s = drugsRoot.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            try {
                DrugDefinition def = parseDrug(id, s, plugin);
                if (def != null) {
                    register(def);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Препарат \"" + id + "\" пропущен: " + ex.getMessage());
            }
        }
        if (DRUGS.isEmpty()) {
            plugin.getLogger().warning("Ни один препарат не загрузился — подставляю встроенный набор.");
            registerDefaults();
        }
    }

    private static DrugDefinition parseDrug(String id, ConfigurationSection s, JavaPlugin plugin) {
        String displayName = s.getString("display-name", id);
        List<Material> recipe = parseRecipeMaterials(s.getStringList("recipe"), plugin, id);
        if (recipe.isEmpty()) {
            plugin.getLogger().warning("Препарат " + id + ": пустой recipe.");
            return null;
        }
        List<String> recipeKeys = parseRecipeKeys(s.getStringList("recipe-keys"), plugin, id);
        boolean dried = s.getBoolean("first-slot-must-be-dried-batch", false);
        String requiredDriedType = s.getString("required-dried-type");
        double badTrip = Math.max(0, Math.min(1, s.getDouble("bad-trip-chance", 0.2)));
        List<PotionEffectSpec> pos = parseEffectsFlexible(s, "positive-effects", plugin, id + ".positive");
        List<PotionEffectSpec> neg = parseEffectsFlexible(s, "negative-effects", plugin, id + ".negative");
        Material itemMat = parseItemMaterial(firstDrugMaterialString(s), plugin, id);
        if (dried) {
            return new DrugDefinition(
                    id, displayName, recipe, pos, neg, badTrip, true, itemMat, recipeKeys, requiredDriedType);
        }
        return new DrugDefinition(
                id, displayName, recipe, pos, neg, badTrip, false, itemMat, recipeKeys, requiredDriedType);
    }

    /** Первое непустое: item-material, material, drug-material. */
    private static String firstDrugMaterialString(ConfigurationSection s) {
        for (String key : List.of("item-material", "material", "drug-material")) {
            String v = s.getString(key);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static Material parseItemMaterial(String raw, JavaPlugin plugin, String drugId) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Material m = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (m == null || m.isAir()) {
            plugin.getLogger().warning("Препарат " + drugId + ": неверный item-material: " + raw);
            return null;
        }
        if (!m.isItem()) {
            plugin.getLogger().warning("Препарат " + drugId + ": item-material не предмет: " + raw);
            return null;
        }
        return m;
    }

    private static List<Material> parseRecipeMaterials(List<String> list, JavaPlugin plugin, String drugId) {
        List<Material> out = new ArrayList<>();
        for (String line : list) {
            if (line == null || line.isBlank()) {
                continue;
            }
            Material m = Material.matchMaterial(line.trim().toUpperCase(Locale.ROOT));
            if (m != null && !m.isAir()) {
                out.add(m);
            } else {
                plugin.getLogger().warning("Препарат " + drugId + ": неизвестный материал в recipe: " + line);
            }
        }
        return out;
    }

    private static List<String> parseRecipeKeys(List<String> list, JavaPlugin plugin, String drugId) {
        List<String> out = new ArrayList<>();
        for (String raw : list) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String v = raw.trim();
            if (v.toUpperCase(Locale.ROOT).startsWith("CHEM:")) {
                out.add("CHEM:" + v.substring(5).trim().toLowerCase(Locale.ROOT));
                continue;
            }
            if (v.toUpperCase(Locale.ROOT).startsWith("MAT:")) {
                String matName = v.substring(4).trim().toUpperCase(Locale.ROOT);
                Material m = Material.matchMaterial(matName);
                if (m == null || m.isAir()) {
                    plugin.getLogger().warning("Препарат " + drugId + ": неизвестный MAT в recipe-keys: " + raw);
                    continue;
                }
                out.add("MAT:" + m.name());
                continue;
            }
            Material m = Material.matchMaterial(v.toUpperCase(Locale.ROOT));
            if (m == null || m.isAir()) {
                plugin.getLogger().warning("Препарат " + drugId + ": неизвестный ключ в recipe-keys: " + raw);
                continue;
            }
            out.add("MAT:" + m.name());
        }
        return out;
    }

    private static List<PotionEffectSpec> parseEffectsFlexible(
            ConfigurationSection drug, String path, JavaPlugin plugin, String label) {
        Object raw = drug.get(path);
        if (raw instanceof List<?> list) {
            List<PotionEffectSpec> out = new ArrayList<>();
            int i = 0;
            for (Object o : list) {
                if (o instanceof java.util.Map<?, ?> map) {
                    PotionEffectSpec spec = mapToEffect(map, plugin, label + "[" + i + "]");
                    if (spec != null) {
                        out.add(spec);
                    }
                }
                i++;
            }
            return out;
        }
        return parseEffectsNested(drug.getConfigurationSection(path), plugin, label);
    }

    private static List<PotionEffectSpec> parseEffectsNested(ConfigurationSection sec, JavaPlugin plugin, String label) {
        List<PotionEffectSpec> out = new ArrayList<>();
        if (sec == null) {
            return out;
        }
        for (String k : sec.getKeys(false)) {
            ConfigurationSection one = sec.getConfigurationSection(k);
            if (one == null) {
                continue;
            }
            PotionEffectSpec spec = sectionToEffect(one, plugin, label + "." + k);
            if (spec != null) {
                out.add(spec);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static PotionEffectSpec mapToEffect(java.util.Map<?, ?> map, JavaPlugin plugin, String label) {
        Object typeObj = map.get("type");
        if (typeObj == null) {
            typeObj = map.get("effect");
        }
        String typeStr = typeObj != null ? String.valueOf(typeObj) : null;
        PotionEffectType type = resolveEffectType(typeStr);
        if (type == null) {
            plugin.getLogger().warning("[" + label + "] неизвестный эффект: " + typeStr);
            return null;
        }
        int amp = toInt(map.get("amplifier"), 0);
        int ticks = durationTicksFromMap(map);
        return new PotionEffectSpec(type, amp, ticks);
    }

    private static PotionEffectSpec sectionToEffect(ConfigurationSection one, JavaPlugin plugin, String label) {
        String typeStr = one.getString("type", one.getString("effect"));
        PotionEffectType type = resolveEffectType(typeStr);
        if (type == null) {
            plugin.getLogger().warning("[" + label + "] неизвестный эффект: " + typeStr);
            return null;
        }
        int amp = one.getInt("amplifier", 0);
        int ticks = one.getInt("duration-ticks", -1);
        if (ticks < 0) {
            double sec = one.getDouble("duration-seconds", 0);
            ticks = (int) Math.round(sec * 20.0);
        }
        if (ticks <= 0) {
            ticks = 20;
        }
        return new PotionEffectSpec(type, amp, ticks);
    }

    private static int durationTicksFromMap(java.util.Map<?, ?> map) {
        Object t = map.get("duration-ticks");
        if (t != null) {
            int v = toInt(t, -1);
            if (v > 0) {
                return v;
            }
        }
        Object s = map.get("duration-seconds");
        if (s != null) {
            return (int) Math.round(toDouble(s, 1) * 20.0);
        }
        return 20;
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double toDouble(Object o, double def) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static PotionEffectType resolveEffectType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String n = raw.trim();
        NamespacedKey key;
        if (n.contains(":")) {
            key = NamespacedKey.fromString(n.toLowerCase(Locale.ROOT));
        } else {
            key = NamespacedKey.minecraft(n.toLowerCase(Locale.ROOT).replace(' ', '_'));
        }
        if (key == null) {
            return null;
        }
        PotionEffectType t = PotionEffectType.getByKey(key);
        if (t != null) {
            return t;
        }
        NamespacedKey alt = NamespacedKey.minecraft(n.toLowerCase(Locale.ROOT).replace(' ', '_'));
        return PotionEffectType.getByKey(alt);
    }

    /** Встроенный набор, если в конфиге нет drugs. */
    public static void registerDefaults() {
        clear();

        register(
                new DrugDefinition(
                        "meth",
                        "&dMephedrone",
                        List.of(Material.REDSTONE, Material.SUGAR, Material.GLOWSTONE_DUST),
                        List.of(
                                new PotionEffectSpec(PotionEffectType.SPEED, 1, 20 * 45),
                                new PotionEffectSpec(PotionEffectType.JUMP_BOOST, 0, 20 * 30)),
                        List.of(
                                new PotionEffectSpec(PotionEffectType.WEAKNESS, 0, 20 * 20),
                                new PotionEffectSpec(PotionEffectType.SLOWNESS, 0, 20 * 15)),
                        0.18,
                        false,
                        Material.GLOWSTONE_DUST));

        register(
                new DrugDefinition(
                        "lsd",
                        "&eLSD",
                        List.of(Material.SUGAR, Material.WHEAT, Material.REDSTONE),
                        List.of(
                                new PotionEffectSpec(PotionEffectType.REGENERATION, 0, 20 * 25),
                                new PotionEffectSpec(PotionEffectType.NIGHT_VISION, 0, 20 * 40)),
                        List.of(
                                new PotionEffectSpec(PotionEffectType.NAUSEA, 0, 20 * 15),
                                new PotionEffectSpec(PotionEffectType.BLINDNESS, 0, 20 * 8)),
                        0.22,
                        false,
                        Material.SUGAR));

        register(
                new DrugDefinition(
                        "cocaine",
                        "&5Cocaine",
                        List.of(Material.SPIDER_EYE, Material.GLASS_BOTTLE, Material.REDSTONE),
                        List.of(
                                new PotionEffectSpec(PotionEffectType.INVISIBILITY, 0, 20 * 20),
                                new PotionEffectSpec(PotionEffectType.SPEED, 0, 20 * 25)),
                        List.of(
                                new PotionEffectSpec(PotionEffectType.WEAKNESS, 1, 20 * 25),
                                new PotionEffectSpec(PotionEffectType.DARKNESS, 0, 20 * 12)),
                        0.25,
                        false,
                        Material.FERMENTED_SPIDER_EYE));

        register(
                new DrugDefinition(
                        "cannabis",
                        "&aCannabis",
                        List.of(Material.NETHER_WART, Material.SUGAR, Material.GLASS_BOTTLE),
                        List.of(
                                new PotionEffectSpec(PotionEffectType.REGENERATION, 0, 20 * 18),
                                new PotionEffectSpec(PotionEffectType.SLOWNESS, 0, 20 * 35)),
                        List.of(
                                new PotionEffectSpec(PotionEffectType.NAUSEA, 0, 20 * 12),
                                new PotionEffectSpec(PotionEffectType.BLINDNESS, 0, 20 * 5)),
                        0.2,
                        true,
                        Material.BLAZE_POWDER));
    }
}
