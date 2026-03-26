package ru.spdrug.drug;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Описание «наркотика»: порядок реагентов в лаборатории и набор эффектов при приёме.
 * Новые виды добавляются через реестр ({@link DrugRecipeRegistry#register(DrugDefinition)}).
 */
public final class DrugDefinition {

    private final String id;
    private final String displayName;
    /** Точная последовательность ингредиентов (как в Breaking Bad — порядок важен). */
    private final List<Material> recipeSequence;
    private final List<PotionEffectSpec> positive;
    private final List<PotionEffectSpec> negative;
    /** Шанс (0..1) дополнительного «плохого» эпизода при приёме (урон, вспышка). */
    private final double badTripChance;
    /**
     * Если true — первый слот реагентов должен быть «сушёным сбором» с меткой плагина (после сушки на
     * компостере), а не обычный незерский нарост.
     */
    private final boolean firstSlotMustBeDriedBatch;
    /** Предмет готового препарата; null — брать из config item-materials.finished-drug */
    private final Material itemMaterial;
    /** Расширенный рецепт лаборатории: MAT:REDSTONE / CHEM:h. */
    private final List<String> recipeKeys;
    /** Если задано — 1-й dried_batch должен быть именно этого типа (например cocaine). */
    private final String requiredDriedType;

    public DrugDefinition(
            String id,
            String displayName,
            List<Material> recipeSequence,
            List<PotionEffectSpec> positive,
            List<PotionEffectSpec> negative,
            double badTripChance) {
        this(id, displayName, recipeSequence, positive, negative, badTripChance, false, null);
    }

    public DrugDefinition(
            String id,
            String displayName,
            List<Material> recipeSequence,
            List<PotionEffectSpec> positive,
            List<PotionEffectSpec> negative,
            double badTripChance,
            boolean firstSlotMustBeDriedBatch) {
        this(id, displayName, recipeSequence, positive, negative, badTripChance, firstSlotMustBeDriedBatch, null);
    }

    public DrugDefinition(
            String id,
            String displayName,
            List<Material> recipeSequence,
            List<PotionEffectSpec> positive,
            List<PotionEffectSpec> negative,
            double badTripChance,
            boolean firstSlotMustBeDriedBatch,
            Material itemMaterial) {
        this(id, displayName, recipeSequence, positive, negative, badTripChance, firstSlotMustBeDriedBatch, itemMaterial, null, null);
    }

    public DrugDefinition(
            String id,
            String displayName,
            List<Material> recipeSequence,
            List<PotionEffectSpec> positive,
            List<PotionEffectSpec> negative,
            double badTripChance,
            boolean firstSlotMustBeDriedBatch,
            Material itemMaterial,
            List<String> recipeKeys) {
        this(id, displayName, recipeSequence, positive, negative, badTripChance, firstSlotMustBeDriedBatch, itemMaterial, recipeKeys, null);
    }

    public DrugDefinition(
            String id,
            String displayName,
            List<Material> recipeSequence,
            List<PotionEffectSpec> positive,
            List<PotionEffectSpec> negative,
            double badTripChance,
            boolean firstSlotMustBeDriedBatch,
            Material itemMaterial,
            List<String> recipeKeys,
            String requiredDriedType) {
        this.id = id;
        this.displayName = displayName;
        this.recipeSequence = List.copyOf(recipeSequence);
        this.positive = List.copyOf(positive);
        this.negative = List.copyOf(negative);
        this.badTripChance = badTripChance;
        this.firstSlotMustBeDriedBatch = firstSlotMustBeDriedBatch;
        this.itemMaterial = itemMaterial;
        this.requiredDriedType =
                requiredDriedType == null || requiredDriedType.isBlank()
                        ? null
                        : requiredDriedType.trim().toLowerCase(Locale.ROOT);
        if (recipeKeys == null || recipeKeys.isEmpty()) {
            List<String> fallback = new ArrayList<>();
            for (Material m : recipeSequence) {
                fallback.add("MAT:" + m.name());
            }
            this.recipeKeys = List.copyOf(fallback);
        } else {
            List<String> normalized = new ArrayList<>();
            for (String k : recipeKeys) {
                if (k == null || k.isBlank()) {
                    continue;
                }
                String v = k.trim();
                if (v.toUpperCase(Locale.ROOT).startsWith("CHEM:")) {
                    normalized.add("CHEM:" + v.substring(5).trim().toLowerCase(Locale.ROOT));
                } else if (v.toUpperCase(Locale.ROOT).startsWith("MAT:")) {
                    normalized.add("MAT:" + v.substring(4).trim().toUpperCase(Locale.ROOT));
                } else {
                    normalized.add("MAT:" + v.toUpperCase(Locale.ROOT));
                }
            }
            this.recipeKeys = List.copyOf(normalized);
        }
    }

    /** Может быть null — тогда используется глобальный материал из конфига. */
    public Material getItemMaterial() {
        return itemMaterial;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<Material> getRecipeSequence() {
        return recipeSequence;
    }

    public List<PotionEffectSpec> getPositive() {
        return positive;
    }

    public List<PotionEffectSpec> getNegative() {
        return negative;
    }

    public double getBadTripChance() {
        return badTripChance;
    }

    public boolean isFirstSlotMustBeDriedBatch() {
        return firstSlotMustBeDriedBatch;
    }

    public List<String> getRecipeKeys() {
        return recipeKeys;
    }

    public String getRequiredDriedType() {
        return requiredDriedType;
    }
}
