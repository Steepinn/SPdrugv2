package ru.spdrug.item;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.spdrug.SPdrugKeys;
import ru.spdrug.config.SPdrugConfig;
import ru.spdrug.drug.ChemicalRegistry;
import ru.spdrug.drug.DrugDefinition;
import ru.spdrug.drug.DrugRecipeRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Предметы установки, растительное сырьё, сушёный сбор, готовые препараты.
 */
public final class ItemFactory {

    public static final String PLACER_LAB = "lab_placer";
    public static final String PLACER_FARM = "farm_placer";
    public static final String KIND_HERB_RAW = "herb_raw";
    public static final String KIND_DRIED_BATCH = "dried_batch";
    public static final String KIND_SEED = "seed";
    public static final String KIND_GROWTH_WATCH = "growth_watch";
    public static final String KIND_CIGARETTE_EMPTY = "cigarette_empty";
    public static final String KIND_CIGARETTE_LOADED = "cigarette_loaded";

    private ItemFactory() {}

    /** Пустой / wild → cannabis, чтобы не создавать отдельные «Конопля» / «Сушёный сбор» без ветки. */
    private static String normalizeHerbTypeKey(String herbType) {
        if (herbType == null || herbType.isBlank() || "wild".equalsIgnoreCase(herbType.trim())) {
            return "cannabis";
        }
        return herbType.trim();
    }

    public static ItemStack createLabPlacer(SPdrugKeys keys, SPdrugConfig cfg) {
        ItemStack s = new ItemStack(cfg.materialLabPlacer());
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(cfg.itemLabPlacerName());
            m.lore(cfg.itemLabPlacerLore());
            m.getPersistentDataContainer()
                    .set(keys.blockType, PersistentDataType.STRING, PLACER_LAB);
            s.setItemMeta(m);
        }
        return s;
    }

    public static ItemStack createFarmPlacer(SPdrugKeys keys, SPdrugConfig cfg) {
        ItemStack s = new ItemStack(cfg.materialFarmPlacer());
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(cfg.itemFarmPlacerName());
            m.lore(cfg.itemFarmPlacerLore());
            m.getPersistentDataContainer()
                    .set(keys.blockType, PersistentDataType.STRING, PLACER_FARM);
            s.setItemMeta(m);
        }
        return s;
    }

    /** Сырьё по умолчанию — каннабис (без отдельного «дикого» типа в игре). */
    public static ItemStack createHerbRaw(SPdrugKeys keys, SPdrugConfig cfg) {
        return createHerbRaw(keys, cfg, "cannabis");
    }

    public static ItemStack createHerbRaw(SPdrugKeys keys, SPdrugConfig cfg, String herbType) {
        ItemStack s = new ItemStack(cfg.materialHerbRaw());
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            String type = normalizeHerbTypeKey(herbType);
            m.displayName(cfg.itemHerbRawName(type));
            m.lore(cfg.itemHerbRawLore(type));
            m.getPersistentDataContainer().set(keys.itemKind, PersistentDataType.STRING, KIND_HERB_RAW + ":" + type);
            s.setItemMeta(m);
        }
        return s;
    }

    public static ItemStack createDriedBatch(SPdrugKeys keys, SPdrugConfig cfg) {
        return createDriedBatch(keys, cfg, "cannabis");
    }

    public static ItemStack createDriedBatch(SPdrugKeys keys, SPdrugConfig cfg, String herbType) {
        ItemStack s = new ItemStack(cfg.materialDriedBatch());
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            String type = normalizeHerbTypeKey(herbType);
            m.displayName(cfg.itemDriedBatchName(type));
            m.lore(cfg.itemDriedBatchLore(type));
            m.getPersistentDataContainer().set(keys.itemKind, PersistentDataType.STRING, KIND_DRIED_BATCH + ":" + type);
            s.setItemMeta(m);
        }
        return s;
    }

    public static ItemStack createSpecialSeed(SPdrugKeys keys, String seedType, Component name, List<Component> lore) {
        ItemStack s = new ItemStack(Material.WHEAT_SEEDS);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                m.lore(lore);
            }
            m.getPersistentDataContainer().set(keys.itemKind, PersistentDataType.STRING, KIND_SEED + ":" + seedType);
            s.setItemMeta(m);
        }
        return s;
    }

    public static ItemStack createGrowthWatch(SPdrugKeys keys, SPdrugConfig cfg) {
        ItemStack s = new ItemStack(cfg.materialGrowthWatch());
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(cfg.itemGrowthWatchName());
            m.lore(cfg.itemGrowthWatchLore());
            m.getPersistentDataContainer().set(keys.itemKind, PersistentDataType.STRING, KIND_GROWTH_WATCH);
            s.setItemMeta(m);
        }
        return s;
    }

    public static ItemStack createEmptyCigarette(SPdrugKeys keys, SPdrugConfig cfg) {
        ItemStack s = new ItemStack(cfg.materialCigarette());
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(cfg.itemCigaretteEmptyName());
            m.lore(cfg.itemCigaretteEmptyLore());
            m.getPersistentDataContainer().set(keys.itemKind, PersistentDataType.STRING, KIND_CIGARETTE_EMPTY);
            s.setItemMeta(m);
        }
        return s;
    }

    public static ItemStack createLoadedCigarette(SPdrugKeys keys, SPdrugConfig cfg, int uses) {
        ItemStack s = new ItemStack(cfg.materialCigarette());
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            int safeUses = Math.max(1, uses);
            m.displayName(cfg.itemCigaretteLoadedName());
            m.lore(cfg.itemCigaretteLoadedLore(safeUses));
            m.getPersistentDataContainer().set(keys.itemKind, PersistentDataType.STRING, KIND_CIGARETTE_LOADED);
            m.getPersistentDataContainer().set(keys.cigaretteUses, PersistentDataType.INTEGER, safeUses);
            s.setItemMeta(m);
        }
        return s;
    }

    public static ItemStack createRecipeBook(SPdrugConfig cfg) {
        boolean en = cfg.uiEnglish();
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (!(meta instanceof BookMeta bm)) {
            return book;
        }
        bm.setTitle(en ? "SPdrug Recipes" : "SPdrug Рецепты");
        bm.setAuthor("SteepStudio · Steepin");
        List<String> pages = new ArrayList<>();
        Map<String, String> chemLegend = new LinkedHashMap<>();
        Map<String, String> matLegend = new LinkedHashMap<>();

        pages.add(
                en
                        ? "SPdrug: handbook\n\n"
                                + "Short notation:\n"
                                + "[code] + [code] + ...\n\n"
                                + "After recipes see\n"
                                + "the code legend."
                        : "SPdrug: справочник\n\n"
                                + "Короткие формулы:\n"
                                + "[код] + [код] + ...\n\n"
                                + "После рецептов смотри\n"
                                + "легенду кодов.");

        pages.add(
                (en ? "Setup\n\nLaboratory:\n" : "Подготовка\n\nЛаборатория:\n")
                        + join(cfg.laboratoryCraftIngredients(), en)
                        + (en ? "\n\nGreenhouse:\n" : "\n\nФерма:\n")
                        + join(cfg.farmCraftIngredients(), en));
        pages.add(
                (en ? "Growth watch\n\nShapeless craft\n(any order):\n\n" : "Агрочасы\n\nБесформенный крафт\n(любой порядок):\n\n")
                        + join(cfg.growthWatchCraftIngredients(), en));

        String componentWord = en ? "Component" : "Компонент";
        String formulaWord = en ? "Formula:" : "Формула:";
        String finalWord = en ? "Final:" : "Финал:";
        String riskWord = en ? "Risk:" : "Риск:";

        for (var c : ChemicalRegistry.all()) {
            pages.add(
                    componentWord
                            + "\n\n"
                            + plain(c.getDisplayName())
                            + " ["
                            + c.getSymbol()
                            + "]\n\n"
                            + formulaWord
                            + "\n"
                            + formatRecipeKeys(c.getRecipeKeys(), null, chemLegend, matLegend, en));
        }

        for (DrugDefinition d : DrugRecipeRegistry.all()) {
            pages.add(
                    finalWord + " " + d.getId() + "\n\n"
                            + plain(d.getDisplayName())
                            + "\n\n"
                            + formulaWord
                            + "\n"
                            + formatRecipeKeys(d.getRecipeKeys(), d, chemLegend, matLegend, en)
                            + "\n\n"
                            + riskWord
                            + " "
                            + Math.round(d.getBadTripChance() * 100.0)
                            + "%");
        }

        pages.add(buildChemLegendPage(chemLegend, en));
        pages.add(buildMatLegendPage(matLegend, en));

        pages.add(
                en
                        ? "Plants & drying\n\n"
                                + "Seeds: from grass.\n"
                                + "Plant on farmland.\n"
                                + "Boost: glowstone\n"
                                + "+2 blocks above.\n\n"
                                + "Raw matter first,\n"
                                + "then dried batch."
                        : "Растения и сушка\n\n"
                                + "Семена: трава.\n"
                                + "Посадка: грядка\n"
                                + "(вспаханная земля).\n"
                                + "Ускорение: светокамень\n"
                                + "над растением (+2y).\n\n"
                                + "Сначала сырьё,\n"
                                + "потом сушёный сбор.");

        pages.add(
                en
                        ? "Laboratory\n\n"
                                + "Place reagents\n"
                                + "exactly in order.\n\n"
                                + "If recipe shows [NW],\n"
                                + "use plugin dried batch,\n"
                                + "not plain nether wart."
                        : "Лаборатория\n\n"
                                + "Клади реагенты\n"
                                + "строго по формуле.\n"
                                + "Порядок обязателен.\n\n"
                                + "Если в рецепте [NW],\n"
                                + "используй сушёный сбор,\n"
                                + "а не обычный нарост.");

        bm.setPages(pages);
        book.setItemMeta(bm);
        return book;
    }

    private static String join(List<Material> mats, boolean en) {
        if (mats == null || mats.isEmpty()) {
            return en ? "(none)" : "(нет)";
        }
        StringJoiner j = new StringJoiner(", ");
        for (Material m : mats) {
            j.add(materialDisplayName(m, en));
        }
        return j.toString();
    }

    private static String plain(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("(?i)&[0-9a-fk-orx]", "");
    }

    private static String formatRecipeKeys(
            List<String> keys,
            DrugDefinition drug,
            Map<String, String> chemLegend,
            Map<String, String> matLegend,
            boolean en) {
        StringJoiner j = new StringJoiner("  +  ");
        int idx = 0;
        for (String k : keys) {
            if (k == null || k.isBlank()) {
                continue;
            }
            idx++;
            String v = k.trim();
            if (v.toUpperCase(Locale.ROOT).startsWith("CHEM:")) {
                String id = v.substring(5).trim().toLowerCase(Locale.ROOT);
                var chem = ChemicalRegistry.findById(id);
                if (chem.isPresent()) {
                    String token = "[" + chem.get().getSymbol() + "]";
                    chemLegend.put(token, plain(chem.get().getDisplayName()));
                    j.add(token);
                } else {
                    String token = "[CHEM:" + id.toUpperCase(Locale.ROOT) + "]";
                    chemLegend.putIfAbsent(
                            token,
                            (en ? "Component " : "Компонент ") + id.toUpperCase(Locale.ROOT));
                    j.add(token);
                }
                continue;
            }
            String matName = v.toUpperCase(Locale.ROOT).startsWith("MAT:")
                    ? v.substring(4).trim().toUpperCase(Locale.ROOT)
                    : v.toUpperCase(Locale.ROOT);
            Material m = Material.matchMaterial(matName);
            if (m != null) {
                if (drug != null
                        && idx == 1
                        && drug.isFirstSlotMustBeDriedBatch()
                        && m == Material.NETHER_WART) {
                    String type = drug.getRequiredDriedType();
                    String display =
                            type == null || type.isBlank()
                                    ? (en ? "Dried batch" : "Сушёный сбор")
                                    : (en ? "Dried batch (" + type + ")" : "Сушёный сбор (" + type + ")");
                    matLegend.put("[NW]", display);
                    j.add("[NW]");
                } else {
                    String token = materialToken(m);
                    matLegend.putIfAbsent(token, materialDisplayName(m, en));
                    j.add(token);
                }
            } else {
                j.add(v);
            }
        }
        return j.toString();
    }

    private static String buildChemLegendPage(Map<String, String> chemLegend, boolean en) {
        StringBuilder sb = new StringBuilder(en ? "Chemistry legend\n\n" : "Легенда химии\n\n");
        if (chemLegend.isEmpty()) {
            return sb.append(en ? "(empty)" : "(пусто)").toString();
        }
        for (var e : chemLegend.entrySet()) {
            if (sb.length() > 190) {
                break;
            }
            sb.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
        }
        return sb.toString().trim();
    }

    private static String buildMatLegendPage(Map<String, String> matLegend, boolean en) {
        StringBuilder sb = new StringBuilder(en ? "Materials legend\n\n" : "Легенда материалов\n\n");
        if (matLegend.isEmpty()) {
            return sb.append(en ? "(empty)" : "(пусто)").toString();
        }
        for (var e : matLegend.entrySet()) {
            if (sb.length() > 190) {
                break;
            }
            sb.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
        }
        return sb.toString().trim();
    }

    private static String materialToken(Material m) {
        return switch (m) {
            case NETHER_WART -> "[NW]";
            case GLASS_BOTTLE -> "[GB]";
            case WATER_BUCKET -> "[WB]";
            case TORCH -> "[TR]";
            case DANDELION -> "[DN]";
            case SUGAR_CANE -> "[SC]";
            case KELP -> "[KP]";
            case SUGAR -> "[SG]";
            case BONE_MEAL -> "[BM]";
            case REDSTONE -> "[RS]";
            case HONEY_BOTTLE -> "[HB]";
            case FERMENTED_SPIDER_EYE -> "[FSE]";
            default -> "[" + m.name() + "]";
        };
    }

    private static String materialDisplayName(Material m, boolean en) {
        if (en) {
            return switch (m) {
                case IRON_INGOT -> "Iron ingot";
                case IRON_BLOCK -> "Iron block";
                case GLASS -> "Glass";
                case BLAZE_POWDER -> "Blaze powder";
                case CAULDRON -> "Cauldron";
                case COMPOSTER -> "Composter";
                case BONE_MEAL -> "Bone meal";
                case IRON_HOE -> "Iron hoe";
                case CLOCK -> "Clock";
                case REDSTONE -> "Redstone";
                case GLASS_BOTTLE -> "Glass bottle";
                case WATER_BUCKET -> "Water bucket";
                case TORCH -> "Torch";
                case DANDELION -> "Dandelion";
                case SUGAR_CANE -> "Sugar cane";
                case KELP -> "Kelp";
                case SUGAR -> "Sugar";
                case HONEY_BOTTLE -> "Honey bottle";
                case FERMENTED_SPIDER_EYE -> "Fermented spider eye";
                case WHEAT -> "Wheat";
                case GLOWSTONE_DUST -> "Glowstone dust";
                case SPIDER_EYE -> "Spider eye";
                case ENDER_PEARL -> "Ender pearl";
                case BLAZE_ROD -> "Blaze rod";
                case NETHER_WART -> "Nether wart";
                case DRIED_KELP -> "Dried kelp";
                default -> {
                    String raw = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
                    yield Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
                }
            };
        }
        return switch (m) {
            case IRON_INGOT -> "Железный слиток";
            case IRON_BLOCK -> "Железный блок";
            case GLASS -> "Стекло";
            case BLAZE_POWDER -> "Огненный порошок";
            case CAULDRON -> "Котёл";
            case COMPOSTER -> "Компостер";
            case BONE_MEAL -> "Костная мука";
            case IRON_HOE -> "Железная мотыга";
            case CLOCK -> "Часы";
            case REDSTONE -> "Редстоун";
            case GLASS_BOTTLE -> "Колба";
            case WATER_BUCKET -> "Ведро воды";
            case TORCH -> "Факел";
            case DANDELION -> "Одуванчик";
            case SUGAR_CANE -> "Тростник";
            case KELP -> "Ламинария";
            case SUGAR -> "Сахар";
            case HONEY_BOTTLE -> "Бутылочка мёда";
            case FERMENTED_SPIDER_EYE -> "Маринованный паучий глаз";
            case WHEAT -> "Пшеница";
            case GLOWSTONE_DUST -> "Светопыль";
            case SPIDER_EYE -> "Паучий глаз";
            case ENDER_PEARL -> "Эндер-жемчуг";
            case BLAZE_ROD -> "Огненный стержень";
            case NETHER_WART -> "Нарост Нижнего мира";
            case DRIED_KELP -> "Сушёная ламинария";
            default -> {
                String raw = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
                yield Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
            }
        };
    }

    public static ItemStack createDrugItem(SPdrugKeys keys, SPdrugConfig cfg, DrugDefinition drug) {
        Material mat = drug.getItemMaterial();
        if (mat == null || !mat.isItem()) {
            mat = cfg.materialFinishedDrug();
        }
        ItemStack st = new ItemStack(mat);
        ItemMeta m = st.getItemMeta();
        if (m != null) {
            m.displayName(cfg.itemDrugName(drug.getDisplayName()));
            m.lore(cfg.itemDrugLore(drug.getId(), drug.getDisplayName()));
            m.getPersistentDataContainer().set(keys.drugId, PersistentDataType.STRING, drug.getId());
            st.setItemMeta(m);
        }
        return st;
    }

    public static String getPlacerType(ItemStack stack, SPdrugKeys keys) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(keys.blockType, PersistentDataType.STRING);
    }

    public static String getDrugId(ItemStack stack, SPdrugKeys keys) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(keys.drugId, PersistentDataType.STRING);
    }

    public static ItemStack createChemicalItem(
            SPdrugKeys keys,
            SPdrugConfig cfg,
            String chemId,
            String symbol,
            Component displayName,
            Material material) {
        ItemStack st = new ItemStack(material);
        ItemMeta m = st.getItemMeta();
        if (m != null) {
            m.displayName(displayName);
            String el = cfg.uiEnglish() ? "Element: " : "Элемент: ";
            m.lore(List.of(Component.text(el + symbol), Component.text("id: " + chemId)));
            m.getPersistentDataContainer().set(keys.chemicalId, PersistentDataType.STRING, chemId);
            st.setItemMeta(m);
        }
        return st;
    }

    public static String getChemicalId(ItemStack stack, SPdrugKeys keys) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(keys.chemicalId, PersistentDataType.STRING);
    }

    public static String getItemKind(ItemStack stack, SPdrugKeys keys) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(keys.itemKind, PersistentDataType.STRING);
    }

    public static boolean isHerbRaw(ItemStack stack, SPdrugKeys keys) {
        String k = getItemKind(stack, keys);
        return k != null && k.startsWith(KIND_HERB_RAW);
    }

    public static boolean isDriedBatch(ItemStack stack, SPdrugKeys keys) {
        String k = getItemKind(stack, keys);
        return k != null && k.startsWith(KIND_DRIED_BATCH);
    }

    public static String getHerbType(ItemStack stack, SPdrugKeys keys) {
        String k = getItemKind(stack, keys);
        if (k == null || !k.startsWith(KIND_HERB_RAW)) {
            return null;
        }
        int idx = k.indexOf(':');
        return idx < 0 ? "wild" : k.substring(idx + 1);
    }

    public static String getSeedType(ItemStack stack, SPdrugKeys keys) {
        String k = getItemKind(stack, keys);
        if (k == null || !k.startsWith(KIND_SEED + ":")) {
            return null;
        }
        return k.substring((KIND_SEED + ":").length());
    }

    public static String getDriedBatchType(ItemStack stack, SPdrugKeys keys) {
        String k = getItemKind(stack, keys);
        if (k == null || !k.startsWith(KIND_DRIED_BATCH)) {
            return null;
        }
        int idx = k.indexOf(':');
        return idx < 0 ? "wild" : k.substring(idx + 1);
    }

    public static boolean isGrowthWatch(ItemStack stack, SPdrugKeys keys) {
        return KIND_GROWTH_WATCH.equals(getItemKind(stack, keys));
    }

    public static boolean isEmptyCigarette(ItemStack stack, SPdrugKeys keys) {
        return KIND_CIGARETTE_EMPTY.equals(getItemKind(stack, keys));
    }

    public static boolean isLoadedCigarette(ItemStack stack, SPdrugKeys keys) {
        return KIND_CIGARETTE_LOADED.equals(getItemKind(stack, keys));
    }

    public static int getCigaretteUses(ItemStack stack, SPdrugKeys keys) {
        if (stack == null || !stack.hasItemMeta()) {
            return 0;
        }
        Integer v = stack.getItemMeta().getPersistentDataContainer().get(keys.cigaretteUses, PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(0, v);
    }
}
