package ru.spdrug;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Ключи PersistentDataContainer: тип блока, id препарата, тип растения.
 */
public final class SPdrugKeys {

    public final NamespacedKey blockType;
    public final NamespacedKey blockUuid;
    public final NamespacedKey drugId;
    public final NamespacedKey chemicalId;
    public final NamespacedKey cropId;
    /** Тип предмета: herb_raw, dried_batch (отличие от ванили). */
    public final NamespacedKey itemKind;
    public final NamespacedKey cigaretteUses;

    public SPdrugKeys(JavaPlugin plugin) {
        this.blockType = new NamespacedKey(plugin, "spdrug_block");
        this.blockUuid = new NamespacedKey(plugin, "spdrug_uuid");
        this.drugId = new NamespacedKey(plugin, "drug_id");
        this.chemicalId = new NamespacedKey(plugin, "chemical_id");
        this.cropId = new NamespacedKey(plugin, "crop_id");
        this.itemKind = new NamespacedKey(plugin, "item_kind");
        this.cigaretteUses = new NamespacedKey(plugin, "cigarette_uses");
    }
}
