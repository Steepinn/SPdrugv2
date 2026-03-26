package ru.spdrug.drug;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Реестр культур для фермы. Добавление новой культуры — {@link #register(CropDefinition)}.
 */
public final class CropRegistry {

    private static final List<CropDefinition> CROPS = new ArrayList<>();

    private CropRegistry() {}

    public static void register(CropDefinition c) {
        CROPS.add(c);
    }

    public static Optional<CropDefinition> findById(String id) {
        for (CropDefinition c : CROPS) {
            if (c.getId().equalsIgnoreCase(id)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public static List<CropDefinition> all() {
        return List.copyOf(CROPS);
    }

    public static void registerDefaults() {
        CROPS.clear();
        register(new CropDefinition("nightshade", "Паслён", Material.WHEAT_SEEDS, 400));
        register(new CropDefinition("bitter", "Горькая трава", Material.BEETROOT_SEEDS, 500));
    }
}
