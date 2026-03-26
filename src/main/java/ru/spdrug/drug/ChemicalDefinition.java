package ru.spdrug.drug;

import org.bukkit.Material;

import java.util.List;

/** Игровой хим-компонент для поэтапного лабораторного синтеза. */
public final class ChemicalDefinition {
    private final String id;
    private final String symbol;
    private final String displayName;
    private final Material itemMaterial;
    private final List<String> recipeKeys;

    public ChemicalDefinition(String id, String symbol, String displayName, Material itemMaterial, List<String> recipeKeys) {
        this.id = id;
        this.symbol = symbol;
        this.displayName = displayName;
        this.itemMaterial = itemMaterial;
        this.recipeKeys = List.copyOf(recipeKeys);
    }

    public String getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getItemMaterial() {
        return itemMaterial;
    }

    public List<String> getRecipeKeys() {
        return recipeKeys;
    }
}
