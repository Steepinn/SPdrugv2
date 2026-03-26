package ru.spdrug.drug;

import org.bukkit.Material;

/**
 * Тип растения на ферме: семя, базовое время роста (тики), материал урожая.
 */
public final class CropDefinition {

    private final String id;
    private final String displayName;
    private final Material seedMaterial;
    /** Базовое число тиков до созревания без удобрений. */
    private final int baseGrowTicks;

    public CropDefinition(String id, String displayName, Material seedMaterial, int baseGrowTicks) {
        this.id = id;
        this.displayName = displayName;
        this.seedMaterial = seedMaterial;
        this.baseGrowTicks = baseGrowTicks;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getSeedMaterial() {
        return seedMaterial;
    }

    public int getBaseGrowTicks() {
        return baseGrowTicks;
    }
}
