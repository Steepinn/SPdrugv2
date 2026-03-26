package ru.spdrug;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import ru.spdrug.config.ConfigDefaultsMerge;
import ru.spdrug.config.SPdrugConfig;
import ru.spdrug.drug.ChemicalRegistry;
import ru.spdrug.drug.DrugRecipeRegistry;
import ru.spdrug.item.ItemFactory;
import ru.spdrug.util.TextUtil;

/**
 * SPdrug: лаборатория, теплица (компостер), препараты. Настройки — config.yml.
 *
 * <p>Автор: Steepin · SteepStudio. Лицензия: MIT ({@code LICENSE} в корне репозитория).
 */
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SPdrugPlugin extends JavaPlugin implements TabCompleter {

    private SPdrugKeys keys;
    private SPdrugConfig spdrugConfig;
    private SPdrugManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        ConfigDefaultsMerge.mergeFromJarResource(this, "config.yml");
        reloadConfig();
        spdrugConfig = new SPdrugConfig(this);
        ChemicalRegistry.reloadFromConfig(spdrugConfig.chemicalsSection(), this);
        DrugRecipeRegistry.reloadFromConfig(spdrugConfig.drugsSection(), this);

        keys = new SPdrugKeys(this);
        manager = new SPdrugManager(this, keys, spdrugConfig);
        manager.registerCrafts();
        manager.registerGlobalTicks();

        getServer().getPluginManager().registerEvents(new SPdrugListener(manager), this);
        if (getCommand("spdrug") != null) {
            getCommand("spdrug").setExecutor(this);
            getCommand("spdrug").setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.savePlacedBlocks();
        }
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("spdrug")) {
            return false;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("spdrug.admin")) {
                sender.sendMessage(TextUtil.amp(spdrugConfig.commandNoPermission()));
                return true;
            }
            reloadConfig();
            ConfigDefaultsMerge.mergeFromJarResource(this, "config.yml");
            reloadConfig();
            spdrugConfig.reloadLocales();
            ChemicalRegistry.reloadFromConfig(spdrugConfig.chemicalsSection(), this);
            DrugRecipeRegistry.reloadFromConfig(spdrugConfig.drugsSection(), this);
            manager.registerCrafts();
            sender.sendMessage(TextUtil.amp(spdrugConfig.commandReloadDone()));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("book")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(TextUtil.amp(spdrugConfig.commandBookPlayerOnly()));
                return true;
            }
            if (!player.hasPermission("spdrug.book") && !player.hasPermission("spdrug.admin")) {
                player.sendMessage(TextUtil.amp(spdrugConfig.commandNoBookPermission()));
                return true;
            }
            giveBook(player);
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player p && (p.hasPermission("spdrug.book") || p.hasPermission("spdrug.admin"))) {
                sender.sendMessage(TextUtil.amp(spdrugConfig.commandPlayerHelpTip()));
            }
            if (sender.hasPermission("spdrug.admin")) {
                for (String line : spdrugConfig.adminHelpLines()) {
                    sender.sendMessage(TextUtil.amp(line));
                }
            } else if (!(sender instanceof Player) || !sender.hasPermission("spdrug.book")) {
                sender.sendMessage(TextUtil.amp(spdrugConfig.commandNoBookPermission()));
            }
            return true;
        }
        if (!sender.hasPermission("spdrug.admin")) {
            sender.sendMessage(TextUtil.amp(spdrugConfig.commandNoPermission()));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.amp(spdrugConfig.commandGivePlayerOnly()));
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(TextUtil.amp(spdrugConfig.commandUsage()));
            return true;
        }
        if (args[1].equalsIgnoreCase("lab")) {
            player.getInventory().addItem(ItemFactory.createLabPlacer(keys, spdrugConfig));
            player.sendMessage(TextUtil.amp(spdrugConfig.commandGiveLab()));
            return true;
        }
        if (args[1].equalsIgnoreCase("farm")) {
            player.getInventory().addItem(ItemFactory.createFarmPlacer(keys, spdrugConfig));
            player.sendMessage(TextUtil.amp(spdrugConfig.commandGiveFarm()));
            return true;
        }
        if (args[1].equalsIgnoreCase("book")) {
            if (!player.hasPermission("spdrug.book") && !player.hasPermission("spdrug.admin")) {
                player.sendMessage(TextUtil.amp(spdrugConfig.commandNoBookPermission()));
                return true;
            }
            giveBook(player);
            return true;
        }
        if (args[1].equalsIgnoreCase("seeds")) {
            giveItems(player, createAllSpecialSeeds());
            player.sendMessage(TextUtil.amp(spdrugConfig.commandGiveSeedsDone()));
            return true;
        }
        if (args[1].equalsIgnoreCase("raws")) {
            List<ItemStack> items = new ArrayList<>();
            for (String herbType : herbTypesFromConfig("items.herb-raw-types")) {
                items.add(ItemFactory.createHerbRaw(keys, spdrugConfig, herbType));
            }
            giveItems(player, items);
            player.sendMessage(TextUtil.amp(spdrugConfig.commandGiveRawsDone()));
            return true;
        }
        if (args[1].equalsIgnoreCase("dried")) {
            List<ItemStack> items = new ArrayList<>();
            for (String herbType : herbTypesFromConfig("items.dried-batch-types")) {
                items.add(ItemFactory.createDriedBatch(keys, spdrugConfig, herbType));
            }
            giveItems(player, items);
            player.sendMessage(TextUtil.amp(spdrugConfig.commandGiveDriedDone()));
            return true;
        }
        if (args[1].equalsIgnoreCase("chemicals")) {
            List<ItemStack> items = new ArrayList<>();
            for (var c : ChemicalRegistry.all()) {
                items.add(ItemFactory.createChemicalItem(
                        keys,
                        spdrugConfig,
                        c.getId(),
                        c.getSymbol(),
                        TextUtil.amp(c.getDisplayName()),
                        c.getItemMaterial()));
            }
            giveItems(player, items);
            player.sendMessage(TextUtil.amp(spdrugConfig.commandGiveChemicalsDone()));
            return true;
        }
        if (args[1].equalsIgnoreCase("drugs")) {
            List<ItemStack> items = new ArrayList<>();
            for (var d : DrugRecipeRegistry.all()) {
                items.add(ItemFactory.createDrugItem(keys, spdrugConfig, d));
            }
            giveItems(player, items);
            player.sendMessage(TextUtil.amp(spdrugConfig.commandGiveDrugsDone()));
            return true;
        }
        if (args[1].equalsIgnoreCase("chemical") && args.length >= 3) {
            ChemicalRegistry.findById(args[2])
                    .ifPresentOrElse(
                            c -> {
                                giveItems(
                                        player,
                                        List.of(ItemFactory.createChemicalItem(
                                                keys,
                                                spdrugConfig,
                                                c.getId(),
                                                c.getSymbol(),
                                                TextUtil.amp(c.getDisplayName()),
                                                c.getItemMaterial())));
                                player.sendMessage(TextUtil.amp(spdrugConfig.commandGiveChemical(c.getDisplayName())));
                            },
                            () -> {
                                player.sendMessage(TextUtil.amp(spdrugConfig.commandUnknownChemical()));
                                player.sendMessage(TextUtil.amp(spdrugConfig.commandChemicalIdsHint()));
                            });
            return true;
        }
        if (args[1].equalsIgnoreCase("all")) {
            List<ItemStack> all = new ArrayList<>();
            all.add(ItemFactory.createLabPlacer(keys, spdrugConfig));
            all.add(ItemFactory.createFarmPlacer(keys, spdrugConfig));
            all.add(ItemFactory.createGrowthWatch(keys, spdrugConfig));
            all.add(ItemFactory.createRecipeBook(spdrugConfig));
            all.add(ItemFactory.createEmptyCigarette(keys, spdrugConfig));
            all.addAll(createAllSpecialSeeds());
            for (String herbType : herbTypesFromConfig("items.herb-raw-types")) {
                all.add(ItemFactory.createHerbRaw(keys, spdrugConfig, herbType));
            }
            for (String herbType : herbTypesFromConfig("items.dried-batch-types")) {
                all.add(ItemFactory.createDriedBatch(keys, spdrugConfig, herbType));
            }
            for (var c : ChemicalRegistry.all()) {
                all.add(ItemFactory.createChemicalItem(
                        keys,
                        spdrugConfig,
                        c.getId(),
                        c.getSymbol(),
                        TextUtil.amp(c.getDisplayName()),
                        c.getItemMaterial()));
            }
            for (var d : DrugRecipeRegistry.all()) {
                all.add(ItemFactory.createDrugItem(keys, spdrugConfig, d));
            }
            giveItems(player, all);
            player.sendMessage(TextUtil.amp(spdrugConfig.commandGiveAllDone()));
            return true;
        }
        if (args[1].equalsIgnoreCase("drug") && args.length >= 3) {
            DrugRecipeRegistry.findById(args[2])
                    .ifPresentOrElse(
                            d -> {
                                player.getInventory().addItem(ItemFactory.createDrugItem(keys, spdrugConfig, d));
                                player.sendMessage(TextUtil.amp(spdrugConfig.commandGiveDrug(d.getDisplayName())));
                            },
                            () -> {
                                player.sendMessage(TextUtil.amp(spdrugConfig.commandUnknownDrug()));
                                player.sendMessage(TextUtil.amp(spdrugConfig.commandDrugIdsHint()));
                            });
            return true;
        }
        sender.sendMessage(TextUtil.amp(spdrugConfig.commandUsage()));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("spdrug")) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            List<String> opts = new ArrayList<>();
            if (sender.hasPermission("spdrug.book") || sender.hasPermission("spdrug.admin")) {
                opts.add("book");
            }
            if (sender.hasPermission("spdrug.admin")) {
                opts.add("give");
                opts.add("reload");
            }
            for (String s : opts) {
                if (s.startsWith(pref)) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (String s : List.of(
                    "lab",
                    "farm",
                    "book",
                    "seeds",
                    "raws",
                    "dried",
                    "chemicals",
                    "chemical",
                    "drugs",
                    "all",
                    "drug")) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("drug")) {
            for (var d : DrugRecipeRegistry.all()) {
                if (d.getId().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(d.getId());
                }
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("chemical")) {
            for (var c : ChemicalRegistry.all()) {
                if (c.getId().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(c.getId());
                }
            }
            return out;
        }
        return List.of();
    }

    private void giveBook(Player player) {
        giveItems(player, List.of(ItemFactory.createRecipeBook(spdrugConfig)));
        player.sendMessage(TextUtil.amp(spdrugConfig.commandGiveBook()));
    }

    private void giveItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            var excess = player.getInventory().addItem(item);
            for (ItemStack e : excess.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), e);
            }
        }
    }

    private List<ItemStack> createAllSpecialSeeds() {
        return List.of(
                ItemFactory.createSpecialSeed(
                        keys,
                        "cannabis",
                        spdrugConfig.specialSeedDisplayName("cannabis"),
                        spdrugConfig.specialSeedLoreComponents("cannabis")),
                ItemFactory.createSpecialSeed(
                        keys,
                        "cocaine",
                        spdrugConfig.specialSeedDisplayName("cocaine"),
                        spdrugConfig.specialSeedLoreComponents("cocaine")),
                ItemFactory.createSpecialSeed(
                        keys,
                        "tobacco",
                        spdrugConfig.specialSeedDisplayName("tobacco"),
                        spdrugConfig.specialSeedLoreComponents("tobacco")));
    }

    private List<String> herbTypesFromConfig(String path) {
        Set<String> out = new LinkedHashSet<>();
        var sec = getConfig().getConfigurationSection(path);
        if (sec != null) {
            out.addAll(sec.getKeys(false));
        }
        return List.copyOf(out);
    }
}
