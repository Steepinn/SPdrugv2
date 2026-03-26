package ru.spdrug.state;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.UUID;

/**
 * Состояние грядки у блока-фермы: культура, прогресс роста, удобрение.
 */
public final class FarmPlotState {

    private final UUID id;
    private final Location blockLocation;
    private String cropId;
    /** Текущий прогресс в тиках. */
    private int growthTicks;
    /** Порог созревания (уменьшается при удобрении). */
    private int targetGrowTicks;
    private boolean fertilized;

    public FarmPlotState(UUID id, Location blockLocation) {
        this.id = id;
        this.blockLocation = blockLocation.clone();
        this.cropId = null;
        this.growthTicks = 0;
        this.targetGrowTicks = 400;
        this.fertilized = false;
    }

    public UUID getId() {
        return id;
    }

    public Location getBlockLocation() {
        return blockLocation.clone();
    }

    public String getCropId() {
        return cropId;
    }

    public void plant(String cropId, int baseTicks) {
        this.cropId = cropId;
        this.growthTicks = 0;
        this.targetGrowTicks = baseTicks;
        this.fertilized = false;
    }

    public boolean hasCrop() {
        return cropId != null;
    }

    public void addGrowth(int ticks) {
        growthTicks += ticks;
    }

    /** Удобрение: один раз уменьшает нужный порог на 25%. */
    public boolean applyFertilizerBonus() {
        if (fertilized || !hasCrop()) {
            return false;
        }
        fertilized = true;
        targetGrowTicks = (int) (targetGrowTicks * 0.75);
        return true;
    }

    public boolean isReady() {
        return hasCrop() && growthTicks >= targetGrowTicks;
    }

    public double getProgress() {
        if (!hasCrop() || targetGrowTicks <= 0) {
            return 0;
        }
        return Math.min(1.0, growthTicks / (double) targetGrowTicks);
    }

    public void clearCrop() {
        cropId = null;
        growthTicks = 0;
        targetGrowTicks = 400;
        fertilized = false;
    }

    public int getGrowthTicks() {
        return growthTicks;
    }

    public int getTargetGrowTicks() {
        return targetGrowTicks;
    }

    public boolean isFertilized() {
        return fertilized;
    }

    /** Тика роста с грядки (вызывается планировщиком). */
    public void tickGrowth() {
        if (hasCrop() && !isReady()) {
            growthTicks += 10;
        }
    }
}
