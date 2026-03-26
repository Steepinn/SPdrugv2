package ru.spdrug.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import ru.spdrug.state.FarmPlotState;

import java.util.List;

/**
 * GUI фермы: информация о грядке, посадка, полив, удобрение, сбор.
 */
public final class FarmGui implements InventoryHolder {

    public static final int SLOT_PLANT = 11;
    public static final int SLOT_WATER = 13;
    public static final int SLOT_FERT = 15;
    public static final int SLOT_HARVEST = 22;

    private final Inventory inventory;

    public FarmGui(FarmPlotState state) {
        this.inventory =
                Bukkit.createInventory(this, 27, Component.text("Ферма — грядка", NamedTextColor.GREEN));
        fillBorder();
        refresh(state);
    }

    private void fillBorder() {
        ItemStack g = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta m = g.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(" "));
            g.setItemMeta(m);
        }
        for (int i = 0; i < 27; i++) {
            if (i != SLOT_PLANT && i != SLOT_WATER && i != SLOT_FERT && i != SLOT_HARVEST && i != 4) {
                inventory.setItem(i, g.clone());
            }
        }
    }

    public void refresh(FarmPlotState state) {
        ItemStack plant = new ItemStack(Material.WHEAT_SEEDS);
        ItemMeta pm = plant.getItemMeta();
        if (pm != null) {
            pm.displayName(Component.text("Посадить паслён", NamedTextColor.YELLOW));
            pm.lore(
                    List.of(
                            Component.text("Нужны пшеничные семена в инвентаре", NamedTextColor.GRAY),
                            Component.text(
                                    state.hasCrop()
                                            ? "Уже растёт: " + state.getCropId()
                                            : "Грядка пуста",
                                    NamedTextColor.DARK_AQUA)));
            plant.setItemMeta(pm);
        }
        inventory.setItem(SLOT_PLANT, plant);

        ItemStack water = new ItemStack(Material.WATER_BUCKET);
        ItemMeta wm = water.getItemMeta();
        if (wm != null) {
            wm.displayName(Component.text("Полить (+рост)", NamedTextColor.AQUA));
            wm.lore(List.of(Component.text("+30 к прогрессу роста", NamedTextColor.GRAY)));
            water.setItemMeta(wm);
        }
        inventory.setItem(SLOT_WATER, water);

        ItemStack bone = new ItemStack(Material.BONE_MEAL);
        ItemMeta bm = bone.getItemMeta();
        if (bm != null) {
            bm.displayName(Component.text("Удобрение", NamedTextColor.GOLD));
            bm.lore(
                    List.of(
                            Component.text("Съесть 1 костную муку из инвентаря", NamedTextColor.GRAY),
                            Component.text(
                                    state.isFertilized() ? "Уже внесено" : "−25% ко времени созревания",
                                    NamedTextColor.DARK_PURPLE)));
            bone.setItemMeta(bm);
        }
        inventory.setItem(SLOT_FERT, bone);

        ItemStack har = new ItemStack(Material.HOPPER);
        ItemMeta hm = har.getItemMeta();
        if (hm != null) {
            hm.displayName(Component.text("Собрать урожай", NamedTextColor.GREEN));
            hm.lore(
                    List.of(
                            Component.text(
                                    "Готово: " + (int) (state.getProgress() * 100) + "%",
                                    NamedTextColor.GRAY)));
            har.setItemMeta(hm);
        }
        inventory.setItem(SLOT_HARVEST, har);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.displayName(Component.text("Состояние", NamedTextColor.WHITE));
            im.lore(
                    List.of(
                            Component.text(
                                    "Рост: %d / %d тиков"
                                            .formatted(state.getGrowthTicks(), state.getTargetGrowTicks()),
                                    NamedTextColor.GRAY)));
            info.setItemMeta(im);
        }
        inventory.setItem(4, info);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
