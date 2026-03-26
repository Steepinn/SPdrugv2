package ru.spdrug.util;

import org.bukkit.Location;

/**
 * Грядка вокруг компостера: центр занят блоком фермы, культуры в кольце по горизонтали.
 */
public final class FarmZoneUtil {

    private FarmZoneUtil() {}

    /**
     * @param horizontalRadius макс. |dx| и |dz| от компостера (2 → 5×5 с центром)
     * @param verticalRange макс. |y − cy|
     */
    public static boolean isCropInFarmZone(
            Location cropBlock, Location composterBlock, int horizontalRadius, int verticalRange) {
        if (!cropBlock.getWorld().equals(composterBlock.getWorld())) {
            return false;
        }
        int cx = composterBlock.getBlockX();
        int cy = composterBlock.getBlockY();
        int cz = composterBlock.getBlockZ();
        int x = cropBlock.getBlockX();
        int y = cropBlock.getBlockY();
        int z = cropBlock.getBlockZ();
        if (Math.abs(y - cy) > verticalRange) {
            return false;
        }
        int dx = x - cx;
        int dz = z - cz;
        if (Math.abs(dx) > horizontalRadius || Math.abs(dz) > horizontalRadius) {
            return false;
        }
        return dx != 0 || dz != 0;
    }
}
