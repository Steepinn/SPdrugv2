package ru.spdrug.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import ru.spdrug.config.SPdrugConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI лаборатории: слоты 10–14 — реагенты по порядку; 26 — запуск синтеза.
 */
public final class LaboratoryGui implements InventoryHolder {

    public static final int[] SEQUENCE_SLOTS = {10, 11, 12, 13, 14};
    public static final int BUTTON_SYNTH = 26;

    private final Inventory inventory;

    public LaboratoryGui(SPdrugConfig config) {
        this.inventory = Bukkit.createInventory(this, 27, config.guiLaboratoryTitle());
        fillBorder(config);
        ItemStack go = new ItemStack(config.guiSynthesisButtonMaterial());
        ItemMeta gm = go.getItemMeta();
        if (gm != null) {
            gm.displayName(config.guiSynthesisButtonName());
            List<Component> lore = config.guiSynthesisButtonLore();
            if (!lore.isEmpty()) {
                gm.lore(lore);
            }
            go.setItemMeta(gm);
        }
        inventory.setItem(BUTTON_SYNTH, go);

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.displayName(config.guiHintPaperName());
            List<Component> hl = config.guiHintPaperLore();
            if (!hl.isEmpty()) {
                im.lore(hl);
            }
            info.setItemMeta(im);
        }
        inventory.setItem(4, info);
    }

    private void fillBorder(SPdrugConfig config) {
        ItemStack g = new ItemStack(config.guiBorderMaterial());
        ItemMeta m = g.getItemMeta();
        if (m != null) {
            m.displayName(config.guiBorderEmptyName());
            g.setItemMeta(m);
        }
        for (int i = 0; i < 27; i++) {
            if (i == 4 || i == BUTTON_SYNTH) {
                continue;
            }
            boolean seq = false;
            for (int s : SEQUENCE_SLOTS) {
                if (s == i) {
                    seq = true;
                    break;
                }
            }
            if (!seq) {
                inventory.setItem(i, g.clone());
            }
        }
    }

    public List<Material> readSequence() {
        List<Material> list = new ArrayList<>();
        for (int slot : SEQUENCE_SLOTS) {
            ItemStack st = inventory.getItem(slot);
            if (st == null || st.getType().isAir()) {
                break;
            }
            list.add(st.getType());
        }
        return list;
    }

    public List<ItemStack> readSequenceStacks() {
        List<ItemStack> list = new ArrayList<>();
        for (int slot : SEQUENCE_SLOTS) {
            ItemStack st = inventory.getItem(slot);
            if (st == null || st.getType().isAir()) {
                break;
            }
            list.add(st.clone());
        }
        return list;
    }

    /**
     * Клоны всех пяти слотов реагентов по порядку (null если пусто) — для пакетного синтеза и возврата остатков.
     */
    public ItemStack[] cloneFiveSequenceSlots() {
        ItemStack[] arr = new ItemStack[SEQUENCE_SLOTS.length];
        for (int i = 0; i < SEQUENCE_SLOTS.length; i++) {
            ItemStack st = inventory.getItem(SEQUENCE_SLOTS[i]);
            arr[i] = (st == null || st.getType().isAir()) ? null : st.clone();
        }
        return arr;
    }

    public void clearSequence() {
        for (int slot : SEQUENCE_SLOTS) {
            inventory.setItem(slot, null);
        }
    }

    public void returnSequenceTo(org.bukkit.entity.Player player) {
        for (int slot : SEQUENCE_SLOTS) {
            ItemStack st = inventory.getItem(slot);
            if (st != null && !st.getType().isAir()) {
                player.getInventory().addItem(st.clone());
                inventory.setItem(slot, null);
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
