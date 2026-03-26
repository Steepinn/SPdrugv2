package ru.spdrug.config;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.spdrug.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Чтение config.yml: крафты, радиус фермы, тексты, тайминги.
 * Тексты: {@code lang/&lt;general.language&gt;.yml} поверх config (см. {@link LocaleStrings}).
 */
public final class SPdrugConfig {

    private final JavaPlugin plugin;
    private final LocaleStrings locale;

    public SPdrugConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.locale = new LocaleStrings(plugin);
    }

    public void reload() {
        plugin.reloadConfig();
    }

    /** Вызывать после {@code reloadConfig()} в плагине. */
    public void reloadLocales() {
        locale.reload();
    }

    private FileConfiguration c() {
        return plugin.getConfig();
    }

    /** {@code en} или {@code ru} из {@code general.language}. */
    public String uiLanguage() {
        return LocaleStrings.normalizeLanguage(c().getString("general.language", "en"));
    }

    public boolean uiEnglish() {
        return "en".equals(uiLanguage());
    }

    /**
     * Строка из {@code messages.*} без Component; плейсхолдеры {@code {name}} и т.д.
     */
    public String messageLine(String path, String fallback, String... placeholders) {
        String s = locale.line("messages." + path, fallback);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String val = placeholders[i + 1] == null ? "" : placeholders[i + 1];
            s = s.replace("{" + placeholders[i] + "}", val);
        }
        return s;
    }

    /** Горизонтальный радиус от компостера (2 = клетки до 2 блоков по X/Z, как 5×5). */
    public int farmHorizontalRadius() {
        return Math.max(1, Math.min(16, c().getInt("farm.horizontal-radius", 2)));
    }

    /** Допустимое отклонение по Y от уровня компостера. */
    public int farmVerticalRange() {
        return Math.max(0, Math.min(8, c().getInt("farm.vertical-range", 1)));
    }

    public int dryingDurationTicks() {
        int t = c().getInt("farm.drying-duration-ticks", 200);
        return Math.max(20, Math.min(20 * 600, t));
    }

    /** Базовая длительность созревания полевых культур (секунды), до множителя скорости. */
    public int fieldHerbGrowDurationSeconds() {
        int v = c().getInt("farm.field-herb-grow-duration-seconds", 60);
        return Math.max(5, Math.min(7200, v));
    }

    /**
     * Множитель скорости роста полевых культур: чем больше, тем быстрее (время ÷ множитель).
     */
    public double fieldHerbGrowSpeedMultiplier() {
        double m = c().getDouble("farm.field-herb-grow-speed-multiplier", 1.0);
        return Math.max(0.05, Math.min(20.0, m));
    }

    /** Эффективное время созревания полевых культур без буста светокамня (мс). */
    public long fieldHerbBaseGrowMillis() {
        long ms = (long) fieldHerbGrowDurationSeconds() * 1000L;
        double mult = fieldHerbGrowSpeedMultiplier();
        return Math.max(1000L, (long) (ms / mult));
    }

    /** Шанс 0..1 особого семена при ломании tall/short grass. */
    public double grassBreakSpecialSeedChanceRatio() {
        double p = c().getDouble("farm.grass-break-special-seed-chance-percent", 10.0);
        p = Math.max(0.0, Math.min(100.0, p));
        return p / 100.0;
    }

    /**
     * cannabis / cocaine / tobacco — пропорционально {@code farm.grass-break-special-seed-weights.*}.
     */
    public String pickGrassSpecialSeedType() {
        double wc = Math.max(0.0, c().getDouble("farm.grass-break-special-seed-weights.cannabis", 1.0));
        double wco = Math.max(0.0, c().getDouble("farm.grass-break-special-seed-weights.cocaine", 1.0));
        double wt = Math.max(0.0, c().getDouble("farm.grass-break-special-seed-weights.tobacco", 1.0));
        double sum = wc + wco + wt;
        if (sum <= 0.0) {
            return "cannabis";
        }
        double r = ThreadLocalRandom.current().nextDouble() * sum;
        if (r < wc) {
            return "cannabis";
        }
        r -= wc;
        if (r < wco) {
            return "cocaine";
        }
        return "tobacco";
    }

    /** Множитель роста пшеницы в зоне теплицы: 1.0 = ванилла, больше — быстрее (см. BlockGrowEvent). */
    public double greenhouseWheatGrowSpeedMultiplier() {
        double m = c().getDouble("farm.greenhouse-wheat-grow-speed-multiplier", 1.0);
        return Math.max(1.0, Math.min(10.0, m));
    }

    public int synthesisDurationTicks() {
        int t = c().getInt("laboratory.synthesis-duration-ticks", 100);
        return Math.max(20, Math.min(20 * 300, t));
    }

    public double bossBarShowRadiusBlocks() {
        double r = c().getDouble("general.bossbar-show-radius-blocks", 2.5);
        return Math.max(0.5, Math.min(32.0, r));
    }

    public float labExplosionPower() {
        float p = (float) c().getDouble("laboratory.explosion-power", 3.5);
        return Math.max(0f, Math.min(20f, p));
    }

    public double labExplosionDamageRadius() {
        double r = c().getDouble("laboratory.explosion-damage-radius", 6.0);
        return Math.max(1.0, Math.min(32.0, r));
    }

    public double labExplosionDamageToSource() {
        return Math.max(0, c().getDouble("laboratory.explosion-damage-to-source", 6.0));
    }

    public double labExplosionDamageToOthers() {
        return Math.max(0, c().getDouble("laboratory.explosion-damage-to-others", 10.0));
    }

    public List<Material> laboratoryCraftIngredients() {
        return parseMaterials("crafting.laboratory.ingredients");
    }

    public List<Material> farmCraftIngredients() {
        return parseMaterials("crafting.farm.ingredients");
    }

    public List<Material> growthWatchCraftIngredients() {
        return parseMaterials("crafting.growth-watch.ingredients");
    }

    public List<Material> cigaretteCraftIngredients() {
        return parseMaterials("crafting.cigarette.ingredients");
    }

    private List<Material> parseMaterials(String path) {
        List<String> raw = c().getStringList(path);
        List<Material> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            Material m = Material.matchMaterial(s.trim().toUpperCase(Locale.ROOT));
            if (m != null && !m.isAir()) {
                out.add(m);
            } else {
                plugin.getLogger().warning("[config] Неизвестный материал в " + path + ": " + s);
            }
        }
        return out;
    }

    /** Блок после установки лаборатории (дымилка по умолчанию). */
    public Material materialLaboratoryBlock() {
        return parseBlockMaterial("blocks.laboratory-placed", Material.SMOKER);
    }

    /** Блок после установки теплицы (компостер по умолчанию). */
    public Material materialFarmBlock() {
        return parseBlockMaterial("blocks.farm-placed", Material.COMPOSTER);
    }

    public Material materialLabPlacer() {
        return parseItemMaterial("item-materials.lab-placer", Material.BLAZE_ROD);
    }

    public Material materialFarmPlacer() {
        return parseItemMaterial("item-materials.farm-placer", Material.GOLDEN_HOE);
    }

    public Material materialGrowthWatch() {
        return parseItemMaterial("item-materials.growth-watch", Material.CLOCK);
    }

    public Material materialCigarette() {
        return parseItemMaterial("item-materials.cigarette", Material.PAPER);
    }

    public Material materialHerbRaw() {
        return parseItemMaterial("item-materials.herb-raw", Material.DRIED_KELP);
    }

    public Material materialDriedBatch() {
        return parseItemMaterial("item-materials.dried-batch", Material.NETHER_WART);
    }

    /** Готовый препарат, если у drug не задан свой item-material. */
    public Material materialFinishedDrug() {
        return parseItemMaterial("item-materials.finished-drug", Material.SUGAR);
    }

    public Material guiSynthesisButtonMaterial() {
        return parseItemMaterial("gui.laboratory.synthesis-button.material", Material.LIME_STAINED_GLASS_PANE);
    }

    public Material guiHintMaterial() {
        return parseItemMaterial("gui.laboratory.hint.material", Material.PAPER);
    }

    public Material guiBorderMaterial() {
        return parseItemMaterial("gui.laboratory.border.material", Material.GRAY_STAINED_GLASS_PANE);
    }

    private Material parseItemMaterial(String path, Material fallback) {
        String raw = c().getString(path);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material m = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (m == null || m.isAir()) {
            plugin.getLogger().warning("[config] Неверный материал (предмет) " + path + ": " + raw);
            return fallback;
        }
        if (!m.isItem()) {
            plugin.getLogger().warning("[config] Не ItemStack-материал " + path + ": " + raw);
            return fallback;
        }
        return m;
    }

    private Material parseBlockMaterial(String path, Material fallback) {
        String raw = c().getString(path);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material m = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (m == null || m.isAir()) {
            plugin.getLogger().warning("[config] Неверный материал (блок) " + path + ": " + raw);
            return fallback;
        }
        if (!m.isBlock()) {
            plugin.getLogger().warning("[config] Не блок " + path + ": " + raw);
            return fallback;
        }
        return m;
    }

    public Component message(String path, String... placeholders) {
        String s = locale.line("messages." + path, "");
        if (s.isEmpty()) {
            return Component.text(path);
        }
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            s = s.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return TextUtil.amp(s);
    }

    public Component guiLaboratoryTitle() {
        return TextUtil.amp(locale.line("gui.laboratory.title", "&bLaboratory — chemistry"));
    }

    public Component guiSynthesisButtonName() {
        return TextUtil.amp(locale.line("gui.laboratory.synthesis-button.name", "&aStart synthesis"));
    }

    public List<Component> guiSynthesisButtonLore() {
        return TextUtil.ampLines(locale.lines("gui.laboratory.synthesis-button.lore"));
    }

    public Component guiHintPaperName() {
        return TextUtil.amp(locale.line("gui.laboratory.hint.name", "&6Hint"));
    }

    public List<Component> guiHintPaperLore() {
        return TextUtil.ampLines(locale.lines("gui.laboratory.hint.lore"));
    }

    public Component guiBorderEmptyName() {
        return TextUtil.amp(locale.line("gui.laboratory.border-empty-name", " "));
    }

    public Component bossBarDryingTitle() {
        return TextUtil.amp(locale.line("bossbar.drying.title", "&6Drying…"));
    }

    public BossBar.Color bossBarDryingColor() {
        return parseBossColor(c().getString("bossbar.drying.color", "YELLOW"));
    }

    public Component bossBarSynthesisTitle() {
        return TextUtil.amp(locale.line("bossbar.synthesis.title", "&bSynthesis…"));
    }

    public BossBar.Color bossBarSynthesisColor() {
        return parseBossColor(c().getString("bossbar.synthesis.color", "BLUE"));
    }

    private static BossBar.Color parseBossColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return BossBar.Color.WHITE;
        }
        try {
            return BossBar.Color.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Color.WHITE;
        }
    }

    public Component itemLabPlacerName() {
        return TextUtil.amp(locale.line("items.lab-placer.name", "&bLaboratory (placer)"));
    }

    public List<Component> itemLabPlacerLore() {
        return TextUtil.ampLines(locale.lines("items.lab-placer.lore"));
    }

    public Component itemFarmPlacerName() {
        return TextUtil.amp(locale.line("items.farm-placer.name", "&aGreenhouse (composter)"));
    }

    public List<Component> itemFarmPlacerLore() {
        return TextUtil.ampLines(locale.lines("items.farm-placer.lore"));
    }

    public Component itemGrowthWatchName() {
        return TextUtil.amp(locale.line("items.growth-watch.name", "&eGrowth watch"));
    }

    public List<Component> itemGrowthWatchLore() {
        return TextUtil.ampLines(locale.lines("items.growth-watch.lore"));
    }

    public Component itemCigaretteEmptyName() {
        return TextUtil.amp(locale.line("items.cigarette-empty.name", "&fEmpty cigarette"));
    }

    public List<Component> itemCigaretteEmptyLore() {
        return TextUtil.ampLines(locale.lines("items.cigarette-empty.lore"));
    }

    public Component itemCigaretteLoadedName() {
        return TextUtil.amp(locale.line("items.cigarette-loaded.name", "&fCigarette"));
    }

    public List<Component> itemCigaretteLoadedLore(int uses) {
        List<String> lines = locale.lines("items.cigarette-loaded.lore");
        List<Component> out = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            out.add(TextUtil.amp(line.replace("{uses}", String.valueOf(uses))));
        }
        return out;
    }

    public int cigaretteMaxUses() {
        int v = c().getInt("cigarette.max-uses", 5);
        return Math.max(1, Math.min(20, v));
    }

    public int cigaretteEffectTicks() {
        int v = c().getInt("cigarette.effect-ticks", 120);
        return Math.max(20, Math.min(20 * 120, v));
    }

    public Component itemHerbRawName() {
        return TextUtil.amp(locale.line("items.herb-raw.name", "&2Plant matter"));
    }

    public Component itemHerbRawName(String herbType) {
        String key = "items.herb-raw-types." + herbType + ".name";
        String raw = locale.line(key, "");
        if (raw.isBlank()) {
            return itemHerbRawName();
        }
        return TextUtil.amp(raw);
    }

    public List<Component> itemHerbRawLore() {
        return TextUtil.ampLines(locale.lines("items.herb-raw.lore"));
    }

    public List<Component> itemHerbRawLore(String herbType) {
        String key = "items.herb-raw-types." + herbType + ".lore";
        List<String> lines = locale.lines(key);
        if (lines.isEmpty()) {
            return itemHerbRawLore();
        }
        return TextUtil.ampLines(lines);
    }

    public Component itemDriedBatchName() {
        return TextUtil.amp(locale.line("items.dried-batch.name", "&6Dried batch"));
    }

    public Component itemDriedBatchName(String herbType) {
        String key = "items.dried-batch-types." + herbType + ".name";
        String raw = locale.line(key, "");
        if (raw.isBlank()) {
            return itemDriedBatchName();
        }
        return TextUtil.amp(raw);
    }

    public List<Component> itemDriedBatchLore() {
        return TextUtil.ampLines(locale.lines("items.dried-batch.lore"));
    }

    public List<Component> itemDriedBatchLore(String herbType) {
        String key = "items.dried-batch-types." + herbType + ".lore";
        List<String> lines = locale.lines(key);
        if (lines.isEmpty()) {
            return itemDriedBatchLore();
        }
        return TextUtil.ampLines(lines);
    }

    public Component itemDrugName(String drugDisplayRaw) {
        return TextUtil.amp(drugDisplayRaw);
    }

    public List<Component> itemDrugLore(String drugId, String drugDisplayRaw) {
        List<String> lines = locale.lines("items.drug.lore");
        List<Component> out = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            out.add(
                    TextUtil.amp(
                            line.replace("{id}", drugId).replace("{name}", stripColorCodes(drugDisplayRaw))));
        }
        return out;
    }

    private static String stripColorCodes(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("(?i)&[0-9a-fk-orx]", "");
    }

    public Component drugUseTitleMain(String drugDisplayRaw) {
        String pat = locale.line("drug-use.title.main", "&dYou took {name}");
        return TextUtil.amp(pat.replace("{name}", stripColorCodes(drugDisplayRaw)));
    }

    public Component drugUseTitleSubtitle() {
        return TextUtil.amp(locale.line("drug-use.title.subtitle", "&8Hang on…"));
    }

    public int drugUseTitleFadeInMs() {
        return c().getInt("drug-use.title.fade-in-ms", 300);
    }

    public int drugUseTitleStayMs() {
        return c().getInt("drug-use.title.stay-ms", 2500);
    }

    public int drugUseTitleFadeOutMs() {
        return c().getInt("drug-use.title.fade-out-ms", 400);
    }

    public Component drugUseBadTripMessage() {
        return TextUtil.amp(locale.line("drug-use.bad-trip-message", "&7Bad reaction…"));
    }

    public double drugUseBadTripDamageMin() {
        double v = c().getDouble("drug-use.bad-trip-damage-min", 0.25);
        return Math.max(0.0, Math.min(20.0, v));
    }

    public double drugUseBadTripDamageMax() {
        double lo = drugUseBadTripDamageMin();
        double v = c().getDouble("drug-use.bad-trip-damage-max", 1.25);
        v = Math.max(0.0, Math.min(20.0, v));
        return Math.max(lo, v);
    }

    public int drugUseScreenFlashCount() {
        int v = c().getInt("drug-use.screen-flash-count", 2);
        return Math.max(0, Math.min(24, v));
    }

    public long drugUseScreenFlashIntervalTicks() {
        long v = c().getLong("drug-use.screen-flash-interval-ticks", 40L);
        return Math.max(5L, Math.min(200L, v));
    }

    public Component drugUseBossBarTitle(String drugDisplayRaw) {
        return TextUtil.amp(
                locale.line("drug-use.bossbar-title", "&d{name}").replace("{name}", stripColorCodes(drugDisplayRaw)));
    }

    public BossBar.Color drugUseBossBarColor() {
        return parseBossColor(c().getString("drug-use.bossbar-color", "PURPLE"));
    }

    public boolean overdoseEnabled() {
        return c().getBoolean("drug-use.overdose.enabled", true);
    }

    public int overdoseWindowSeconds() {
        int v = c().getInt("drug-use.overdose.window-seconds", 120);
        return Math.max(5, Math.min(3600, v));
    }

    public int overdoseThreshold() {
        int v = c().getInt("drug-use.overdose.threshold", 3);
        return Math.max(2, Math.min(12, v));
    }

    public double overdoseDamage() {
        double v = c().getDouble("drug-use.overdose.damage", 6.0);
        return Math.max(0.0, Math.min(40.0, v));
    }

    public int overdoseNauseaSeconds() {
        int v = c().getInt("drug-use.overdose.nausea-seconds", 12);
        return Math.max(1, Math.min(300, v));
    }

    public int overdoseWeaknessSeconds() {
        int v = c().getInt("drug-use.overdose.weakness-seconds", 18);
        return Math.max(1, Math.min(300, v));
    }

    public Component overdoseMessage() {
        return TextUtil.amp(
                locale.line("drug-use.overdose.message", "&4Overdose! Your body cannot take it."));
    }

    public String commandNoPermission() {
        return locale.line("commands.no-permission", "&cNo permission (spdrug.admin).");
    }

    public String commandNoBookPermission() {
        return locale.line("commands.no-book-permission", "&cNo permission for book (spdrug.book).");
    }

    public String commandUsage() {
        return locale.line("commands.usage", "&e/spdrug book &7| &e/spdrug give <...> &7| &e/spdrug reload");
    }

    public String commandGiveLab() {
        return locale.line("commands.give-lab", "&aLaboratory placer given.");
    }

    public String commandGiveFarm() {
        return locale.line("commands.give-farm", "&aFarm placer given.");
    }

    public String commandGiveBook() {
        return locale.line("commands.give-book", "&aRecipe book given.");
    }

    public String commandGiveDrug(String displayName) {
        String s = locale.line("commands.give-drug", "&aDrug given: {name}");
        return s.replace("{name}", displayName);
    }

    public String commandGiveChemical(String displayName) {
        String s = locale.line("commands.give-chemical", "&aChemical given: {name}");
        return s.replace("{name}", stripColorCodes(displayName));
    }

    public String commandUnknownDrug() {
        return locale.line("commands.unknown-drug", "&cUnknown drug id.");
    }

    public String commandUnknownChemical() {
        return locale.line("commands.unknown-chemical", "&cUnknown chemical id.");
    }

    public String commandDrugIdsHint() {
        return locale.line("commands.drug-ids-hint", "&eIds are keys under drugs in config.yml");
    }

    public String commandChemicalIdsHint() {
        return locale.line("commands.chemical-ids-hint", "&eIds are keys under chemicals in config.yml");
    }

    public String commandReloadDone() {
        return locale.line("commands.reload-done", "&aConfig and recipes reloaded.");
    }

    public String commandGiveSeedsDone() {
        return locale.line("commands.give-seeds-done", "&aAll special seeds given.");
    }

    public String commandGiveRawsDone() {
        return locale.line("commands.give-raws-done", "&aAll plant matter types given.");
    }

    public String commandGiveDriedDone() {
        return locale.line("commands.give-dried-done", "&aAll dried batch types given.");
    }

    public String commandGiveChemicalsDone() {
        return locale.line("commands.give-chemicals-done", "&aAll chemicals given.");
    }

    public String commandGiveDrugsDone() {
        return locale.line("commands.give-drugs-done", "&aAll drugs given.");
    }

    public String commandGiveAllDone() {
        return locale.line("commands.give-all-done", "&aFull SPdrug kit given.");
    }

    public String commandBookPlayerOnly() {
        return locale.line("commands.book-player-only", "&cOnly players can get the book.");
    }

    public String commandGivePlayerOnly() {
        return locale.line("commands.give-player-only", "&cgive is for players only.");
    }

    public String commandPlayerHelpTip() {
        return locale.line("commands.player-help-tip", "&7- &e/spdrug book &7— recipe book");
    }

    public List<String> adminHelpLines() {
        return locale.lines("messages.admin-help");
    }

    public Component specialSeedDisplayName(String seedType) {
        String fallback =
                switch (seedType) {
                    case "cannabis" -> "&aCannabis seeds";
                    case "cocaine" -> "&fCoca seeds";
                    case "tobacco" -> "&6Tobacco seeds";
                    default -> "&fSeeds";
                };
        return TextUtil.amp(locale.line("items.special-seed." + seedType + ".name", fallback));
    }

    public List<Component> specialSeedLoreComponents(String seedType) {
        List<String> lines = locale.lines("items.special-seed." + seedType + ".lore");
        if (lines.isEmpty()) {
            lines = List.of("&7Farmland only; glowstone +2 blocks above for boost");
        }
        return TextUtil.ampLines(lines);
    }

    /** Секция drugs для парсинга (может быть null). */
    public ConfigurationSection drugsSection() {
        return c().getConfigurationSection("drugs");
    }

    public ConfigurationSection chemicalsSection() {
        return c().getConfigurationSection("chemicals");
    }
}
