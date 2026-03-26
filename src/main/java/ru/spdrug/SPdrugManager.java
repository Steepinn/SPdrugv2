package ru.spdrug;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Bisected;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.spdrug.config.SPdrugConfig;
import ru.spdrug.drug.DrugDefinition;
import ru.spdrug.drug.DrugRecipeRegistry;
import ru.spdrug.drug.ChemicalDefinition;
import ru.spdrug.drug.ChemicalRegistry;
import ru.spdrug.item.ItemFactory;
import ru.spdrug.persist.PlacedBlockStore;
import ru.spdrug.service.DrugUseService;
import ru.spdrug.state.ComposterFarmState;
import ru.spdrug.util.FarmZoneUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Лаборатория (дымилка), ферма (компостер), синтез, сушка, BossBar только вблизи блока.
 */
public final class SPdrugManager {

    public static final String TYPE_LAB = "LAB";
    public static final String TYPE_FARM = "FARM";

    private final JavaPlugin plugin;
    private final SPdrugKeys keys;
    private final SPdrugConfig cfg;

    private final Map<String, UUID> labs = new ConcurrentHashMap<>();
    private final Map<String, ComposterFarmState> farms = new ConcurrentHashMap<>();
    private final Map<String, FieldHerbState> fieldHerbs = new ConcurrentHashMap<>();
    private final Set<UUID> synthesizingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, QuickTimeSession> quickTimeByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, SynthesisStirSession> synthesisStirByPlayer = new ConcurrentHashMap<>();

    private final Map<UUID, Location> openLabForPlayer = new ConcurrentHashMap<>();

    private final PlacedBlockStore placedBlocks;

    public static final class FieldHerbState {
        private final String herbType;
        private final long plantedAtMs;
        private final long baseGrowDurationMs;

        public FieldHerbState(String herbType, long plantedAtMs, long baseGrowDurationMs) {
            this.herbType = herbType;
            this.plantedAtMs = plantedAtMs;
            this.baseGrowDurationMs = baseGrowDurationMs;
        }

        public String herbType() {
            return herbType;
        }

        public boolean boostedNow(World world, int x, int y, int z) {
            if (world == null) {
                return false;
            }
            return world.getBlockAt(x, y + 2, z).getType() == Material.GLOWSTONE;
        }

        public boolean isMature(long nowMs, boolean boostedNow) {
            return nowMs - plantedAtMs >= effectiveDurationMs(boostedNow);
        }

        public double progress(long nowMs, boolean boostedNow) {
            long d = effectiveDurationMs(boostedNow);
            if (d <= 0) {
                return 1.0;
            }
            return Math.max(0.0, Math.min(1.0, (nowMs - plantedAtMs) / (double) d));
        }

        private long effectiveDurationMs(boolean boostedNow) {
            return boostedNow ? Math.max(5000L, baseGrowDurationMs / 2) : baseGrowDurationMs;
        }
    }

    public enum QuickTimeAction {
        RIGHT_CLICK,
        SNEAK,
        SWAP
    }

    private static final class QuickTimeSession {
        private final String mode;
        private final Location center;
        private final int requiredHits;
        private final int maxDistanceSquared;
        private int hits;
        private long expiresAtMs;
        private QuickTimeAction requiredAction;

        private QuickTimeSession(String mode, Location center, int requiredHits, int maxDistanceSquared) {
            this.mode = mode;
            this.center = center;
            this.requiredHits = requiredHits;
            this.maxDistanceSquared = maxDistanceSquared;
        }
    }

    private static final class SynthesisStirSession {
        private final Location center;
        private final double targetStability;
        private double stability;

        private SynthesisStirSession(Location center, double targetStability) {
            this.center = center;
            this.targetStability = targetStability;
            this.stability = 40.0;
        }

        private void decay() {
            stability = Math.max(0.0, stability - 0.35);
        }
    }

    public SPdrugManager(JavaPlugin plugin, SPdrugKeys keys, SPdrugConfig cfg) {
        this.plugin = plugin;
        this.keys = keys;
        this.cfg = cfg;
        this.placedBlocks = new PlacedBlockStore(plugin);
        this.placedBlocks.load();
        rehydrateLabsAndFarmsFromStore();
    }

    public SPdrugConfig cfg() {
        return cfg;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    /** Сохранить координаты блоков (вызывать при onDisable). */
    public void savePlacedBlocks() {
        placedBlocks.save();
    }

    private void rehydrateLabsAndFarmsFromStore() {
        for (Map.Entry<String, PlacedBlockStore.StoredBlock> e : placedBlocks.copyEntries().entrySet()) {
            String key = e.getKey();
            PlacedBlockStore.StoredBlock sb = e.getValue();
            Location loc = parseLocationKey(key);
            if (loc == null) {
                continue;
            }
            Block b = loc.getBlock();
            if (TYPE_FARM.equals(sb.type()) && b.getType() == cfg.materialFarmBlock()) {
                farms.put(key, new ComposterFarmState(sb.uuid(), loc));
            } else if (TYPE_LAB.equals(sb.type()) && b.getType() == cfg.materialLaboratoryBlock()) {
                labs.put(key, sb.uuid());
            }
        }
    }

    private static Location parseLocationKey(String key) {
        int i1 = key.indexOf(':');
        int i2 = key.indexOf(':', i1 + 1);
        int i3 = key.indexOf(':', i2 + 1);
        if (i1 < 0 || i2 < 0 || i3 < 0) {
            return null;
        }
        String worldName = key.substring(0, i1);
        try {
            int x = Integer.parseInt(key.substring(i1 + 1, i2));
            int y = Integer.parseInt(key.substring(i2 + 1, i3));
            int z = Integer.parseInt(key.substring(i3 + 1));
            World w = Bukkit.getWorld(worldName);
            if (w == null) {
                return null;
            }
            return new Location(w, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public SPdrugKeys getKeys() {
        return keys;
    }

    public static String locKey(Location loc) {
        return loc.getWorld().getName()
                + ":"
                + loc.getBlockX()
                + ":"
                + loc.getBlockY()
                + ":"
                + loc.getBlockZ();
    }

    public void registerCrafts() {
        List<Material> labMats = cfg.laboratoryCraftIngredients();
        List<Material> farmMats = cfg.farmCraftIngredients();
        List<Material> watchMats = cfg.growthWatchCraftIngredients();
        if (labMats.isEmpty()) {
            plugin.getLogger().warning("[config] crafting.laboratory.ingredients пуст — крафт лаборатории отключён.");
        } else {
            NamespacedKey labRecipe = new NamespacedKey(plugin, "lab_placer");
            if (Bukkit.getRecipe(labRecipe) != null) {
                Bukkit.removeRecipe(labRecipe);
            }
            ShapelessRecipe lr = new ShapelessRecipe(labRecipe, ItemFactory.createLabPlacer(keys, cfg));
            for (Material m : labMats) {
                lr.addIngredient(m);
            }
            Bukkit.addRecipe(lr);
        }
        if (farmMats.isEmpty()) {
            plugin.getLogger().warning("[config] crafting.farm.ingredients пуст — крафт теплицы отключён.");
        } else {
            NamespacedKey farmRecipe = new NamespacedKey(plugin, "farm_placer");
            if (Bukkit.getRecipe(farmRecipe) != null) {
                Bukkit.removeRecipe(farmRecipe);
            }
            ShapelessRecipe fr = new ShapelessRecipe(farmRecipe, ItemFactory.createFarmPlacer(keys, cfg));
            for (Material m : farmMats) {
                fr.addIngredient(m);
            }
            Bukkit.addRecipe(fr);
        }
        if (!watchMats.isEmpty()) {
            NamespacedKey watchRecipe = new NamespacedKey(plugin, "growth_watch");
            if (Bukkit.getRecipe(watchRecipe) != null) {
                Bukkit.removeRecipe(watchRecipe);
            }
            ShapelessRecipe wr = new ShapelessRecipe(watchRecipe, ItemFactory.createGrowthWatch(keys, cfg));
            for (Material m : watchMats) {
                wr.addIngredient(m);
            }
            Bukkit.addRecipe(wr);
        }
        NamespacedKey emptyCigRecipe = new NamespacedKey(plugin, "cigarette_empty");
        if (Bukkit.getRecipe(emptyCigRecipe) != null) {
            Bukkit.removeRecipe(emptyCigRecipe);
        }
        ShapedRecipe er = new ShapedRecipe(emptyCigRecipe, ItemFactory.createEmptyCigarette(keys, cfg));
        er.shape("PPP");
        er.setIngredient('P', Material.PAPER);
        Bukkit.addRecipe(er);
        DrugRecipeRegistry.findById("nicotine").ifPresent(nic -> {
            NamespacedKey loadedCigRecipe = new NamespacedKey(plugin, "cigarette_loaded");
            if (Bukkit.getRecipe(loadedCigRecipe) != null) {
                Bukkit.removeRecipe(loadedCigRecipe);
            }
            ShapelessRecipe lr = new ShapelessRecipe(
                    loadedCigRecipe, ItemFactory.createLoadedCigarette(keys, cfg, cfg.cigaretteMaxUses()));
            lr.addIngredient(new RecipeChoice.ExactChoice(ItemFactory.createEmptyCigarette(keys, cfg)));
            lr.addIngredient(new RecipeChoice.ExactChoice(ItemFactory.createDrugItem(keys, cfg, nic)));
            Bukkit.addRecipe(lr);
        });
        discoverCraftRecipesForAllOnline();
    }

    /** Показать ванильную «книгу рецептов» крафты плагина (в т.ч. агрочасы). */
    public void discoverCraftRecipesFor(Player player) {
        if (player == null) {
            return;
        }
        discoverIfPresent(player, "lab_placer");
        discoverIfPresent(player, "farm_placer");
        discoverIfPresent(player, "growth_watch");
        discoverIfPresent(player, "cigarette_empty");
        discoverIfPresent(player, "cigarette_loaded");
    }

    public void discoverCraftRecipesForAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            discoverCraftRecipesFor(p);
        }
    }

    private void discoverIfPresent(Player player, String recipeKey) {
        NamespacedKey nk = new NamespacedKey(plugin, recipeKey);
        if (Bukkit.getRecipe(nk) != null) {
            player.discoverRecipe(nk);
        }
    }

    /** Все тики: сушка на компостере + BossBar только вблизи блока. */
    public void registerGlobalTicks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateFieldHerbsVisuals();
                double r = cfg.bossBarShowRadiusBlocks();
                double rsq = r * r;
                for (ComposterFarmState st : farms.values()) {
                    if (!st.isDrying()) {
                        continue;
                    }
                    Player p = Bukkit.getPlayer(st.getDryingStarter());
                    BossBar bar = st.getDryingBar();
                    Location center = st.getBlockLocation().clone().add(0.5, 0.5, 0.5);
                    if (p == null || !p.isOnline() || bar == null) {
                        endDryingAbort(st);
                        continue;
                    }
                    boolean near = p.getLocation().distanceSquared(center) <= rsq;
                    float prog = st.getDryingTicksLeft() / (float) st.getDryingTotal();
                    bar.progress(Math.max(0f, Math.min(1f, prog)));
                    if (near) {
                        p.showBossBar(bar);
                    } else {
                        p.hideBossBar(bar);
                    }
                    if (!tickQuickTime(p, center)) {
                        Objects.requireNonNull(st.getBlockLocation().getWorld())
                                .dropItemNaturally(
                                        st.getBlockLocation().clone().add(0.5, 0.8, 0.5),
                                        ItemFactory.createHerbRaw(keys, cfg, st.getDryingHerbType()));
                        p.sendMessage(cfg.message("drying-skill-fail"));
                        p.hideBossBar(bar);
                        quickTimeByPlayer.remove(p.getUniqueId());
                        st.endDrying();
                        continue;
                    }

                    if (st.tickDrying()) {
                        QuickTimeSession q = quickTimeByPlayer.get(p.getUniqueId());
                        if (q == null || !"drying".equals(q.mode) || q.hits < q.requiredHits) {
                            Objects.requireNonNull(st.getBlockLocation().getWorld())
                                    .dropItemNaturally(
                                            st.getBlockLocation().clone().add(0.5, 0.8, 0.5),
                                            ItemFactory.createHerbRaw(keys, cfg, st.getDryingHerbType()));
                            p.sendMessage(cfg.message("drying-skill-fail"));
                            p.hideBossBar(bar);
                            quickTimeByPlayer.remove(p.getUniqueId());
                            st.endDrying();
                            continue;
                        }
                        quickTimeByPlayer.remove(p.getUniqueId());
                        p.getInventory().addItem(ItemFactory.createDriedBatch(keys, cfg, st.getDryingHerbType()));
                        p.sendMessage(cfg.message("drying-done"));
                        p.hideBossBar(bar);
                        st.endDrying();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void endDryingAbort(ComposterFarmState st) {
        BossBar bar = st.getDryingBar();
        if (bar != null && st.getDryingStarter() != null) {
            Player op = Bukkit.getPlayer(st.getDryingStarter());
            if (op != null && op.isOnline()) {
                op.hideBossBar(bar);
            }
        }
        Location drop = st.getBlockLocation().clone().add(0.5, 0.8, 0.5);
        String returnType = st.getDryingHerbType();
        Objects.requireNonNull(st.getBlockLocation().getWorld())
                .dropItemNaturally(drop, ItemFactory.createHerbRaw(keys, cfg, returnType));
        st.endDrying();
    }

    public void cancelDryingForPlayer(Player player) {
        synthesisStirByPlayer.remove(player.getUniqueId());
        for (ComposterFarmState st : farms.values()) {
            if (st.isDrying() && player.getUniqueId().equals(st.getDryingStarter())) {
                quickTimeByPlayer.remove(player.getUniqueId());
                BossBar b = st.getDryingBar();
                if (b != null) {
                    player.hideBossBar(b);
                }
                st.getBlockLocation()
                        .getWorld()
                        .dropItemNaturally(
                                st.getBlockLocation().clone().add(0.5, 0.8, 0.5),
                                ItemFactory.createHerbRaw(keys, cfg, st.getDryingHerbType()));
                st.endDrying();
                player.sendMessage(cfg.message("drying-cancelled"));
            }
        }
    }

    public void tryStartDrying(Player player, Block composterBlock) {
        ComposterFarmState st = getOrCreateFarmState(composterBlock);
        if (st == null) {
            player.sendMessage(cfg.message("drying-busy-or-no-farm"));
            return;
        }
        if (st.isDrying()) {
            if (player.getUniqueId().equals(st.getDryingStarter())) {
                handleQuickTimeAction(player, QuickTimeAction.RIGHT_CLICK, composterBlock.getLocation());
            } else {
                player.sendMessage(cfg.message("drying-busy-or-no-farm"));
            }
            return;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        boolean mainHerb = ItemFactory.isHerbRaw(main, keys);
        boolean offHerb = ItemFactory.isHerbRaw(off, keys);
        if (!mainHerb && !offHerb) {
            player.sendMessage(cfg.message("drying-need-herb"));
            return;
        }
        String herbType = mainHerb ? ItemFactory.getHerbType(main, keys) : ItemFactory.getHerbType(off, keys);
        if (mainHerb) {
            main.setAmount(main.getAmount() - 1);
        } else {
            off.setAmount(off.getAmount() - 1);
        }
        BossBar bar =
                BossBar.bossBar(
                        cfg.bossBarDryingTitle(),
                        1f,
                        cfg.bossBarDryingColor(),
                        BossBar.Overlay.PROGRESS);
        st.setDryingBar(bar);
        st.beginDrying(player.getUniqueId(), cfg.dryingDurationTicks(), herbType);
        startQuickTime(player, "drying", composterBlock.getLocation(), 4, 16);
        player.sendMessage(cfg.message("drying-minigame-start"));
    }

    public void registerFieldHerb(Block block, String herbType, boolean boosted) {
        long baseMs = cfg.fieldHerbBaseGrowMillis();
        fieldHerbs.put(
                locKey(block.getLocation()),
                new FieldHerbState(
                        herbType == null || herbType.isBlank() ? "cannabis" : herbType,
                        System.currentTimeMillis(),
                        baseMs));
    }

    public FieldHerbState getFieldHerb(Block block) {
        return fieldHerbs.get(locKey(block.getLocation()));
    }

    public FieldHerbState removeFieldHerb(Block block) {
        return fieldHerbs.remove(locKey(block.getLocation()));
    }

    private void updateFieldHerbsVisuals() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, FieldHerbState> e : fieldHerbs.entrySet()) {
            Location loc = parseLocationKey(e.getKey());
            if (loc == null || loc.getWorld() == null) {
                continue;
            }
            Block lower = loc.getBlock();
            World w = loc.getWorld();
            boolean boosted = e.getValue().boostedNow(w, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            if (!e.getValue().isMature(now, boosted)) {
                continue;
            }
            Block upper = w.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
            if (!upper.getType().isAir() && upper.getType() != Material.LARGE_FERN) {
                continue;
            }
            lower.setType(Material.LARGE_FERN, false);
            upper.setType(Material.LARGE_FERN, false);
            if (lower.getBlockData() instanceof Bisected b1) {
                b1.setHalf(Bisected.Half.BOTTOM);
                lower.setBlockData(b1, false);
            }
            if (upper.getBlockData() instanceof Bisected b2) {
                b2.setHalf(Bisected.Half.TOP);
                upper.setBlockData(b2, false);
            }
        }
    }

    private static TileState tileState(Block block) {
        BlockState state = block.getState(true);
        return state instanceof TileState ts ? ts : null;
    }

    public boolean isOurBlock(Block block) {
        String k = getBlockKind(block);
        return TYPE_LAB.equals(k) || TYPE_FARM.equals(k);
    }

    public String getBlockKind(Block block) {
        String key = locKey(block.getLocation());
        PlacedBlockStore.StoredBlock stored = placedBlocks.get(key);
        if (stored != null) {
            if (TYPE_FARM.equals(stored.type()) && block.getType() == cfg.materialFarmBlock()) {
                return TYPE_FARM;
            }
            if (TYPE_LAB.equals(stored.type()) && block.getType() == cfg.materialLaboratoryBlock()) {
                return TYPE_LAB;
            }
            return null;
        }
        TileState ts = tileState(block);
        if (ts == null) {
            return null;
        }
        return ts.getPersistentDataContainer().get(keys.blockType, PersistentDataType.STRING);
    }

    private void mark(Block block, String type, UUID id) {
        TileState ts = tileState(block);
        if (ts == null) {
            return;
        }
        ts.getPersistentDataContainer().set(keys.blockType, PersistentDataType.STRING, type);
        ts.getPersistentDataContainer().set(keys.blockUuid, PersistentDataType.STRING, id.toString());
        ts.update();
    }

    private UUID readBlockUuid(Block block) {
        String key = locKey(block.getLocation());
        PlacedBlockStore.StoredBlock stored = placedBlocks.get(key);
        if (stored != null) {
            return stored.uuid();
        }
        TileState ts = tileState(block);
        if (ts == null) {
            return null;
        }
        String s = ts.getPersistentDataContainer().get(keys.blockUuid, PersistentDataType.STRING);
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public ComposterFarmState getOrCreateFarmState(Block composterBlock) {
        if (composterBlock.getType() != cfg.materialFarmBlock() || !TYPE_FARM.equals(getBlockKind(composterBlock))) {
            return null;
        }
        String key = locKey(composterBlock.getLocation());
        ComposterFarmState st = farms.get(key);
        if (st != null) {
            return st;
        }
        UUID id = readBlockUuid(composterBlock);
        if (id == null) {
            return null;
        }
        st = new ComposterFarmState(id, composterBlock.getLocation());
        farms.put(key, st);
        return st;
    }

    private ComposterFarmState findFarmCoveringCrop(Block cropBlock) {
        Location loc = cropBlock.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        int hr = cfg.farmHorizontalRadius();
        int vr = cfg.farmVerticalRange();
        int wx = loc.getBlockX();
        int wy = loc.getBlockY();
        int wz = loc.getBlockZ();
        for (int x = wx - hr; x <= wx + hr; x++) {
            for (int z = wz - hr; z <= wz + hr; z++) {
                for (int y = wy - vr; y <= wy + vr; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() != cfg.materialFarmBlock()) {
                        continue;
                    }
                    if (!TYPE_FARM.equals(getBlockKind(b))) {
                        continue;
                    }
                    if (!FarmZoneUtil.isCropInFarmZone(loc, b.getLocation(), hr, vr)) {
                        continue;
                    }
                    ComposterFarmState st = getOrCreateFarmState(b);
                    if (st != null) {
                        return st;
                    }
                }
            }
        }
        return null;
    }

    public boolean tryPlaceLab(Player player, Block clicked, BlockFace face) {
        if (face != BlockFace.UP || !clicked.getType().isSolid()) {
            return false;
        }
        Block up = clicked.getRelative(BlockFace.UP);
        if (up.getType() != Material.AIR) {
            return false;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ItemFactory.PLACER_LAB.equals(ItemFactory.getPlacerType(hand, keys))) {
            return false;
        }
        up.setType(cfg.materialLaboratoryBlock());
        UUID id = UUID.randomUUID();
        String labKey = locKey(up.getLocation());
        mark(up, TYPE_LAB, id);
        placedBlocks.register(labKey, TYPE_LAB, id);
        if (!TYPE_LAB.equals(getBlockKind(up))) {
            placedBlocks.remove(labKey);
            up.setType(Material.AIR);
            player.sendMessage(cfg.message("lab-register-fail"));
            return false;
        }
        labs.put(labKey, id);
        hand.setAmount(hand.getAmount() - 1);
        player.sendMessage(cfg.message("lab-placed"));
        return true;
    }

    public boolean tryPlaceFarm(Player player, Block clicked, BlockFace face) {
        if (face != BlockFace.UP || !clicked.getType().isSolid()) {
            return false;
        }
        Block up = clicked.getRelative(BlockFace.UP);
        if (up.getType() != Material.AIR) {
            return false;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ItemFactory.PLACER_FARM.equals(ItemFactory.getPlacerType(hand, keys))) {
            return false;
        }
        up.setType(cfg.materialFarmBlock());
        UUID id = UUID.randomUUID();
        String farmKey = locKey(up.getLocation());
        mark(up, TYPE_FARM, id);
        placedBlocks.register(farmKey, TYPE_FARM, id);
        if (!TYPE_FARM.equals(getBlockKind(up))) {
            placedBlocks.remove(farmKey);
            up.setType(Material.AIR);
            player.sendMessage(cfg.message("farm-register-fail"));
            return false;
        }
        farms.put(farmKey, new ComposterFarmState(id, up.getLocation()));
        hand.setAmount(hand.getAmount() - 1);
        int size = cfg.farmHorizontalRadius() * 2 + 1;
        player.sendMessage(cfg.message("farm-placed", "size", String.valueOf(size)));
        return true;
    }

    public void openLab(Player player, Block block) {
        if (!TYPE_LAB.equals(getBlockKind(block))) {
            return;
        }
        ru.spdrug.gui.LaboratoryGui gui = new ru.spdrug.gui.LaboratoryGui(cfg);
        player.openInventory(gui.getInventory());
        setOpenLab(player, block.getLocation());
    }

    public void setOpenLab(Player player, Location labBlock) {
        openLabForPlayer.put(player.getUniqueId(), labBlock.clone());
    }

    public void clearOpenLab(Player player) {
        openLabForPlayer.remove(player.getUniqueId());
    }

    public Location getOpenLab(Player player) {
        return openLabForPlayer.get(player.getUniqueId());
    }

    public ComposterFarmState getFarmState(Location loc) {
        return farms.get(locKey(loc));
    }

    public boolean handleSynthesisTimingClick(Player player, Location where) {
        SynthesisStirSession s = synthesisStirByPlayer.get(player.getUniqueId());
        if (s == null) {
            return false;
        }
        if (where == null || where.getWorld() == null || s.center.getWorld() == null) {
            return false;
        }
        if (!where.getWorld().equals(s.center.getWorld()) || where.distanceSquared(s.center) > 16) {
            return false;
        }
        s.stability = Math.min(100.0, s.stability + 12.0);
        player.sendActionBar(cfg.message("synthesis-stir-hit", "value", String.valueOf((int) Math.round(s.stability))));
        return true;
    }

    public boolean handleQuickTimeAction(Player player, QuickTimeAction action, Location where) {
        QuickTimeSession q = quickTimeByPlayer.get(player.getUniqueId());
        if (q == null) {
            return false;
        }
        if (where != null) {
            if (where.getWorld() == null || q.center.getWorld() == null) {
                return false;
            }
            if (!where.getWorld().equals(q.center.getWorld())) {
                return false;
            }
            if (where.distanceSquared(q.center) > q.maxDistanceSquared) {
                return false;
            }
        }
        if (System.currentTimeMillis() > q.expiresAtMs) {
            return false;
        }
        if (q.requiredAction != action) {
            return false;
        }
        q.hits++;
        if (q.hits >= q.requiredHits) {
            player.sendActionBar(cfg.message("minigame-step-done"));
            return true;
        }
        nextQuickTimePrompt(player, q);
        return true;
    }

    private void startQuickTime(Player player, String mode, Location center, int requiredHits, int maxDistanceSquared) {
        QuickTimeSession q = new QuickTimeSession(mode, center.clone(), requiredHits, maxDistanceSquared);
        quickTimeByPlayer.put(player.getUniqueId(), q);
        nextQuickTimePrompt(player, q);
    }

    private boolean tickQuickTime(Player player, Location centerCheck) {
        QuickTimeSession q = quickTimeByPlayer.get(player.getUniqueId());
        if (q == null) {
            return false;
        }
        if (q.hits >= q.requiredHits) {
            return true;
        }
        if (!player.getWorld().equals(centerCheck.getWorld())) {
            return false;
        }
        if (player.getLocation().distanceSquared(centerCheck) > q.maxDistanceSquared) {
            return false;
        }
        return System.currentTimeMillis() <= q.expiresAtMs;
    }

    private void nextQuickTimePrompt(Player player, QuickTimeSession q) {
        q.requiredAction = randomQuickTimeAction();
        q.expiresAtMs = System.currentTimeMillis() + 2600L;
        String actionText =
                switch (q.requiredAction) {
                    case RIGHT_CLICK -> cfg.messageLine("minigame-action-rmb", "RMB");
                    case SNEAK -> cfg.messageLine("minigame-action-sneak", "Shift");
                    case SWAP -> cfg.messageLine("minigame-action-swap", "F");
                };
        String modeLabel =
                "drying".equals(q.mode)
                        ? cfg.messageLine("minigame-mode-drying", "Drying")
                        : cfg.messageLine("minigame-mode-synthesis", "Synthesis");
        player.showTitle(
                Title.title(
                        cfg.message("minigame-title-main", "key", actionText),
                        cfg.message(
                                "minigame-title-sub",
                                "mode",
                                modeLabel,
                                "step",
                                String.valueOf(q.hits + 1),
                                "total",
                                String.valueOf(q.requiredHits)),
                        Title.Times.times(Duration.ofMillis(120), Duration.ofMillis(900), Duration.ofMillis(120))));
    }

    private static QuickTimeAction randomQuickTimeAction() {
        int r = ThreadLocalRandom.current().nextInt(3);
        if (r == 0) {
            return QuickTimeAction.RIGHT_CLICK;
        }
        if (r == 1) {
            return QuickTimeAction.SNEAK;
        }
        return QuickTimeAction.SWAP;
    }

    public void startSynthesis(Player player, Location labBlock, List<Material> sequence, ItemStack[] slotClones) {
        if (sequence.isEmpty()) {
            player.sendMessage(cfg.message("synthesis-need-reagents"));
            return;
        }
        player.sendMessage(cfg.message("synthesis-stir-start"));
        synthesizingPlayers.add(player.getUniqueId());
        BossBar bar =
                BossBar.bossBar(
                        cfg.bossBarSynthesisTitle(),
                        0f,
                        cfg.bossBarSynthesisColor(),
                        BossBar.Overlay.PROGRESS);
        Location center = labBlock.clone().add(0.5, 0.5, 0.5);
        int synthTicks = cfg.synthesisDurationTicks();
        final ItemStack[] slotsSnap = slotClones == null ? new ItemStack[5] : slotClones.clone();
        SynthesisStirSession stir = new SynthesisStirSession(center, 65.0);
        synthesisStirByPlayer.put(player.getUniqueId(), stir);
        player.showTitle(
                Title.title(
                        cfg.message("synthesis-title-main"),
                        cfg.message("synthesis-title-sub"),
                        Title.Times.times(
                                Duration.ofMillis(120),
                                Duration.ofMillis(Math.max(500, (long) synthTicks * 50L)),
                                Duration.ofMillis(200))));

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    player.hideBossBar(bar);
                    synthesizingPlayers.remove(player.getUniqueId());
                    synthesisStirByPlayer.remove(player.getUniqueId());
                    return;
                }
                double r = cfg.bossBarShowRadiusBlocks();
                boolean near = player.getLocation().distanceSquared(center) <= r * r;
                t++;
                bar.progress(Math.min(1f, t / (float) synthTicks));
                if (near) {
                    player.showBossBar(bar);
                } else {
                    player.hideBossBar(bar);
                }
                SynthesisStirSession s = synthesisStirByPlayer.get(player.getUniqueId());
                if (s == null || !player.getWorld().equals(s.center.getWorld()) || player.getLocation().distanceSquared(s.center) > 16) {
                    cancel();
                    player.hideBossBar(bar);
                    synthesizingPlayers.remove(player.getUniqueId());
                    synthesisStirByPlayer.remove(player.getUniqueId());
                    player.sendMessage(cfg.message("synthesis-focus-fail"));
                    explodeLaboratory(labBlock, player);
                    return;
                }
                s.decay();
                if (s.stability <= 0.0) {
                    cancel();
                    player.hideBossBar(bar);
                    synthesizingPlayers.remove(player.getUniqueId());
                    synthesisStirByPlayer.remove(player.getUniqueId());
                    player.sendMessage(cfg.message("synthesis-focus-fail"));
                    explodeLaboratory(labBlock, player);
                    return;
                }
                player.sendActionBar(cfg.message(
                        "synthesis-stir-status",
                        "value", String.valueOf((int) Math.round(s.stability)),
                        "target", String.valueOf((int) Math.round(s.targetStability))));
                if (t >= synthTicks) {
                    cancel();
                    player.hideBossBar(bar);
                    synthesizingPlayers.remove(player.getUniqueId());
                    SynthesisStirSession done = synthesisStirByPlayer.remove(player.getUniqueId());
                    if (done == null || done.stability < done.targetStability) {
                        player.sendMessage(cfg.message("synthesis-stir-incomplete"));
                        explodeLaboratory(labBlock, player);
                        return;
                    }
                    finishSynthesis(player, labBlock, sequence, slotsSnap);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void finishSynthesis(Player player, Location labBlock, List<Material> sequence, ItemStack[] slotClones) {
        int recipeLen = sequence.size();
        if (recipeLen <= 0 || recipeLen > 5) {
            player.sendMessage(cfg.message("synthesis-fail"));
            explodeLaboratory(labBlock, player);
            return;
        }
        ItemStack[] slots = slotClones != null ? slotClones : new ItemStack[5];
        List<String> slotKeys = buildSlotKeys(slots, recipeLen);
        Optional<DrugDefinition> match = findDrugByKeys(slotKeys);
        if (match.isPresent()) {
            DrugDefinition def = match.get();
            if (def.isFirstSlotMustBeDriedBatch()) {
                ItemStack s0 = recipeLen > 0 && slots.length > 0 ? slots[0] : null;
                if (s0 == null || s0.getType().isAir() || !ItemFactory.isDriedBatch(s0, keys)) {
                    match = Optional.empty();
                } else {
                    String required = def.getRequiredDriedType();
                    if (required != null) {
                        String got = ItemFactory.getDriedBatchType(s0, keys);
                        if (got == null || !required.equalsIgnoreCase(got)) {
                            match = Optional.empty();
                        }
                    }
                }
            }
        }
        if (match.isEmpty()) {
            Optional<ChemicalDefinition> chem = ChemicalRegistry.findByRecipeKeys(slotKeys);
            if (chem.isPresent()) {
                int batch = Integer.MAX_VALUE;
                for (int i = 0; i < recipeLen; i++) {
                    batch = Math.min(batch, slots[i].getAmount());
                }
                if (batch <= 0 || batch == Integer.MAX_VALUE) {
                    player.sendMessage(cfg.message("synthesis-fail"));
                    explodeLaboratory(labBlock, player);
                    return;
                }
                ChemicalDefinition cdef = chem.get();
                ItemStack template = ItemFactory.createChemicalItem(
                        keys,
                        cfg,
                        cdef.getId(),
                        cdef.getSymbol(),
                        ru.spdrug.util.TextUtil.amp(cdef.getDisplayName()),
                        cdef.getItemMaterial());
                int left = batch;
                while (left > 0) {
                    int n = Math.min(left, template.getMaxStackSize());
                    ItemStack piece = template.clone();
                    piece.setAmount(n);
                    giveOrDrop(player, piece);
                    left -= n;
                }
                for (int i = 0; i < 5; i++) {
                    ItemStack st = i < slots.length ? slots[i] : null;
                    if (st == null || st.getType().isAir()) {
                        continue;
                    }
                    int returnAmt = i < recipeLen ? st.getAmount() - batch : st.getAmount();
                    if (returnAmt > 0) {
                        ItemStack out = st.clone();
                        out.setAmount(returnAmt);
                        giveOrDrop(player, out);
                    }
                }
                player.sendMessage(cfg.message("synthesis-component-success", "name", cdef.getDisplayName()));
                return;
            }
        }
        for (int i = 0; match.isPresent() && i < recipeLen; i++) {
            ItemStack st = i < slots.length ? slots[i] : null;
            Material want = sequence.get(i);
            if (st == null || st.getType().isAir() || st.getType() != want) {
                match = Optional.empty();
                break;
            }
        }
        if (match.isEmpty()) {
            player.sendMessage(cfg.message("synthesis-fail"));
            explodeLaboratory(labBlock, player);
            return;
        }
        DrugDefinition def = match.get();
        int batch = Integer.MAX_VALUE;
        for (int i = 0; i < recipeLen; i++) {
            batch = Math.min(batch, slots[i].getAmount());
        }
        if (batch == Integer.MAX_VALUE || batch <= 0) {
            player.sendMessage(cfg.message("synthesis-fail"));
            explodeLaboratory(labBlock, player);
            return;
        }
        ItemStack template = ItemFactory.createDrugItem(keys, cfg, def);
        int leftDrug = batch;
        while (leftDrug > 0) {
            int n = Math.min(leftDrug, template.getMaxStackSize());
            ItemStack piece = template.clone();
            piece.setAmount(n);
            giveOrDrop(player, piece);
            leftDrug -= n;
        }
        for (int i = 0; i < 5; i++) {
            ItemStack st = i < slots.length ? slots[i] : null;
            if (st == null || st.getType().isAir()) {
                continue;
            }
            int returnAmt;
            if (i < recipeLen) {
                returnAmt = st.getAmount() - batch;
            } else {
                returnAmt = st.getAmount();
            }
            if (returnAmt > 0) {
                ItemStack out = st.clone();
                out.setAmount(returnAmt);
                giveOrDrop(player, out);
            }
        }
        player.sendMessage(
                cfg.message(
                        "synthesis-success",
                        "count",
                        String.valueOf(batch),
                        "name",
                        def.getDisplayName()));
        Location p = labBlock.clone().add(0.5, 1.0, 0.5);
        Objects.requireNonNull(labBlock.getWorld()).spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, p, 20, 0.4, 0.3, 0.4);
    }

    private List<String> buildSlotKeys(ItemStack[] slots, int recipeLen) {
        List<String> keysOut = new ArrayList<>();
        for (int i = 0; i < recipeLen; i++) {
            ItemStack st = i < slots.length ? slots[i] : null;
            if (st == null || st.getType().isAir()) {
                break;
            }
            String chemId = ItemFactory.getChemicalId(st, keys);
            if (chemId != null && !chemId.isBlank()) {
                keysOut.add("CHEM:" + chemId.toLowerCase(java.util.Locale.ROOT));
            } else {
                keysOut.add("MAT:" + st.getType().name());
            }
        }
        return keysOut;
    }

    private Optional<DrugDefinition> findDrugByKeys(List<String> keysIn) {
        for (DrugDefinition d : DrugRecipeRegistry.all()) {
            if (d.getRecipeKeys().equals(keysIn)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        Map<Integer, ItemStack> excess = player.getInventory().addItem(stack);
        for (ItemStack e : excess.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), e);
        }
    }

    public void explodeLaboratory(Location labBlock, Player source) {
        String key = locKey(labBlock);
        labs.remove(key);
        placedBlocks.remove(key);
        Block b = labBlock.getBlock();
        Location center = labBlock.clone().add(0.5, 0.5, 0.5);
        var world = Objects.requireNonNull(labBlock.getWorld());
        b.setType(Material.AIR);
        world.createExplosion(center, cfg.labExplosionPower(), false, false);
        double dmgR = cfg.labExplosionDamageRadius();
        for (LivingEntity e : world.getNearbyLivingEntities(center, dmgR)) {
            if (e.equals(source)) {
                e.damage(cfg.labExplosionDamageToSource());
            } else {
                e.damage(cfg.labExplosionDamageToOthers());
            }
        }
        source.sendMessage(cfg.message("lab-exploded"));
    }

    public void onLabBroken(Block block) {
        String key = locKey(block.getLocation());
        labs.remove(key);
        placedBlocks.remove(key);
    }

    public void onFarmBroken(Block block) {
        String key = locKey(block.getLocation());
        placedBlocks.remove(key);
        ComposterFarmState st = farms.remove(key);
        if (st != null && st.isDrying() && st.getDryingBar() != null && st.getDryingStarter() != null) {
            Player p = Bukkit.getPlayer(st.getDryingStarter());
            if (p != null && p.isOnline()) {
                p.hideBossBar(st.getDryingBar());
            }
        }
    }

    public boolean isSynthesizing(Player player) {
        return synthesizingPlayers.contains(player.getUniqueId());
    }

    public void tryUseDrug(Player player, DrugDefinition drug) {
        DrugUseService.apply(player, drug, plugin, cfg);
    }

    public void trySmokeCigarette(Player player, ItemStack inHand) {
        if (!ItemFactory.isLoadedCigarette(inHand, keys)) {
            return;
        }
        int uses = ItemFactory.getCigaretteUses(inHand, keys);
        if (uses <= 0) {
            inHand.setAmount(inHand.getAmount() - 1);
            return;
        }
        player.getWorld().spawnParticle(
                Particle.SMOKE,
                player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.4)),
                12,
                0.15,
                0.1,
                0.15,
                0.004);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, cfg.cigaretteEffectTicks(), 0, false, true, true));
        int left = uses - 1;
        if (left <= 0) {
            inHand.setAmount(inHand.getAmount() - 1);
            return;
        }
        ItemStack updated = ItemFactory.createLoadedCigarette(keys, cfg, left);
        updated.setAmount(inHand.getAmount());
        player.getInventory().setItemInMainHand(updated);
    }

    public boolean isWheatInAnyFarmZone(Block wheatBlock) {
        return findFarmCoveringCrop(wheatBlock) != null;
    }

    public ComposterFarmState findFarmForCrop(Block wheatBlock) {
        return findFarmCoveringCrop(wheatBlock);
    }
}
