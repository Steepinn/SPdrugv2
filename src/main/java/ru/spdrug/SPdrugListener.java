package ru.spdrug;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ru.spdrug.drug.DrugDefinition;
import ru.spdrug.drug.DrugRecipeRegistry;
import ru.spdrug.gui.LaboratoryGui;
import ru.spdrug.item.ItemFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ПКМ по блокам, GUI лаборатории, компостер-ферма (сушка), ломание, употребление препарата.
 */
public final class SPdrugListener implements Listener {

    private final SPdrugManager manager;

    public SPdrugListener(SPdrugManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK && clicked != null) {
            String kind = manager.getBlockKind(clicked);
            if (SPdrugManager.TYPE_LAB.equals(kind) && manager.isSynthesizing(player)) {
                if (manager.handleSynthesisTimingClick(player, clicked.getLocation())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && clicked != null) {
            if (ItemFactory.isGrowthWatch(player.getInventory().getItemInMainHand(), manager.getKeys())
                    && (clicked.getType() == Material.FERN || clicked.getType() == Material.LARGE_FERN)) {
                SPdrugManager.FieldHerbState st = manager.getFieldHerb(clicked);
                if (st == null) {
                    player.sendActionBar(manager.cfg().message("field-herb-normal-fern"));
                } else {
                    boolean boosted = st.boostedNow(
                            clicked.getWorld(), clicked.getX(), clicked.getY(), clicked.getZ());
                    int pct = (int) Math.round(st.progress(System.currentTimeMillis(), boosted) * 100.0);
                    String boost = boosted ? "&ax2" : "&7x1";
                    player.sendActionBar(manager.cfg().message(
                            "field-herb-watch-status",
                            "pct",
                            String.valueOf(Math.min(100, pct)),
                            "boost",
                            boost,
                            "type",
                            st.herbType()));
                }
                event.setCancelled(true);
                return;
            }
            String seedType = ItemFactory.getSeedType(player.getInventory().getItemInMainHand(), manager.getKeys());
            if (seedType != null) {
                if (clicked.getType() != Material.FARMLAND) {
                    player.sendActionBar(manager.cfg().message("field-herb-seed-need-farmland"));
                    event.setCancelled(true);
                    return;
                }
                Block up = clicked.getRelative(BlockFace.UP);
                Block glow = clicked.getRelative(BlockFace.UP, 2);
                if (up.getType().isAir()) {
                    boolean boosted = glow.getType() == Material.GLOWSTONE;
                    up.setType(Material.FERN);
                    manager.registerFieldHerb(up, seedType, boosted);
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    hand.setAmount(hand.getAmount() - 1);
                    if (boosted) {
                        player.sendActionBar(manager.cfg().message("field-herb-seed-boosted"));
                    } else {
                        player.sendActionBar(manager.cfg().message("field-herb-seed-normal"));
                    }
                    event.setCancelled(true);
                    return;
                }
            }
            String kind = manager.getBlockKind(clicked);
            if (SPdrugManager.TYPE_LAB.equals(kind)) {
                event.setCancelled(true);
                if (event.getHand() == EquipmentSlot.HAND) {
                    if (manager.isSynthesizing(player)) {
                        manager.handleSynthesisTimingClick(player, clicked.getLocation());
                        return;
                    }
                    manager.openLab(player, clicked);
                }
                return;
            }
            if (SPdrugManager.TYPE_FARM.equals(kind)) {
                event.setCancelled(true);
                if (event.getHand() == EquipmentSlot.HAND) {
                    manager.tryStartDrying(player, clicked);
                }
                return;
            }
            if (event.getBlockFace() == BlockFace.UP && clicked.getType().isSolid()) {
                if (ItemFactory.PLACER_LAB.equals(
                        ItemFactory.getPlacerType(player.getInventory().getItemInMainHand(), manager.getKeys()))) {
                    if (manager.tryPlaceLab(player, clicked, BlockFace.UP)) {
                        event.setCancelled(true);
                    }
                    return;
                }
                if (ItemFactory.PLACER_FARM.equals(
                        ItemFactory.getPlacerType(player.getInventory().getItemInMainHand(), manager.getKeys()))) {
                    if (manager.tryPlaceFarm(player, clicked, BlockFace.UP)) {
                        event.setCancelled(true);
                    }
                }
            }
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            Location at = clicked != null ? clicked.getLocation() : player.getLocation();
            if (manager.handleQuickTimeAction(player, SPdrugManager.QuickTimeAction.RIGHT_CLICK, at)) {
                event.setCancelled(true);
                return;
            }
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (ItemFactory.isLoadedCigarette(item, manager.getKeys())) {
            if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                    || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                manager.trySmokeCigarette(player, item);
                return;
            }
        }
        String drugId = ItemFactory.getDrugId(item, manager.getKeys());
        if (drugId == null) {
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        Optional<DrugDefinition> def = DrugRecipeRegistry.findById(drugId);
        if (def.isEmpty()) {
            return;
        }
        item.setAmount(item.getAmount() - 1);
        manager.tryUseDrug(player, def.get());
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof LaboratoryGui labGui) {
            int slot = event.getRawSlot();
            if (slot >= 27) {
                return;
            }
            boolean seqSlot = false;
            for (int s : LaboratoryGui.SEQUENCE_SLOTS) {
                if (s == slot) {
                    seqSlot = true;
                    break;
                }
            }
            if (slot == LaboratoryGui.BUTTON_SYNTH) {
                event.setCancelled(true);
                Location labLoc = manager.getOpenLab(player);
                if (labLoc == null) {
                    return;
                }
                List<Material> seq = labGui.readSequence();
                ItemStack[] slotClones = labGui.cloneFiveSequenceSlots();
                labGui.clearSequence();
                player.closeInventory();
                manager.clearOpenLab(player);
                manager.startSynthesis(player, labLoc, seq, slotClones);
                return;
            }
            if (!seqSlot && slot != 4) {
                event.setCancelled(true);
            }
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder h = event.getInventory().getHolder();
        if (h instanceof LaboratoryGui) {
            for (int slot : event.getRawSlots()) {
                if (slot < 27) {
                    boolean ok = false;
                    for (int s : LaboratoryGui.SEQUENCE_SLOTS) {
                        if (s == slot) {
                            ok = true;
                            break;
                        }
                    }
                    if (!ok && slot != 4) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof LaboratoryGui lab) {
            if (manager.isSynthesizing(player)) {
                return;
            }
            lab.returnSequenceTo(player);
            manager.clearOpenLab(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWheatGrow(BlockGrowEvent event) {
        Block b = event.getBlock();
        if (b.getType() != Material.WHEAT) {
            return;
        }
        if (!manager.isWheatInAnyFarmZone(b)) {
            return;
        }
        double mult = manager.cfg().greenhouseWheatGrowSpeedMultiplier();
        if (mult <= 1.0001) {
            return;
        }
        var loc = b.getLocation().clone();
        manager.getPlugin()
                .getServer()
                .getScheduler()
                .runTask(manager.getPlugin(), () -> applyGreenhouseWheatGrowBonus(loc, mult));
    }

    private static void applyGreenhouseWheatGrowBonus(Location loc, double mult) {
        Block bb = loc.getBlock();
        if (bb.getType() != Material.WHEAT) {
            return;
        }
        if (!(bb.getBlockData() instanceof Ageable ag)) {
            return;
        }
        int max = ag.getMaximumAge();
        int age = ag.getAge();
        if (age >= max) {
            return;
        }
        double whole = Math.floor(mult);
        int extraFromWhole = (int) whole - 1;
        if (extraFromWhole < 0) {
            extraFromWhole = 0;
        }
        double frac = mult - whole;
        int toAdd = extraFromWhole;
        if (frac > 1e-9 && ThreadLocalRandom.current().nextDouble() < frac) {
            toAdd++;
        }
        int newAge = Math.min(max, age + toAdd);
        if (newAge <= age) {
            return;
        }
        ag.setAge(newAge);
        bb.setBlockData(ag);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWheatHarvest(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (b.getType() == Material.SHORT_GRASS || b.getType() == Material.TALL_GRASS) {
            Player p = event.getPlayer();
            var cfg = manager.cfg();
            if (ThreadLocalRandom.current().nextDouble() < cfg.grassBreakSpecialSeedChanceRatio()) {
                String type = cfg.pickGrassSpecialSeedType();
                ItemStack seed =
                        ItemFactory.createSpecialSeed(
                                manager.getKeys(),
                                type,
                                cfg.specialSeedDisplayName(type),
                                cfg.specialSeedLoreComponents(type));
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), seed);
                p.sendActionBar(cfg.message("field-herb-seeds-found"));
            } else {
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.WHEAT_SEEDS, 1));
            }
            return;
        }
        if (b.getType() == Material.FERN || b.getType() == Material.LARGE_FERN) {
            Block base = b;
            SPdrugManager.FieldHerbState herb = manager.getFieldHerb(b);
            if (herb == null && b.getType() == Material.LARGE_FERN) {
                var bd = b.getBlockData();
                if (bd instanceof Bisected bis && bis.getHalf() == Bisected.Half.TOP) {
                    Block below = b.getRelative(BlockFace.DOWN);
                    herb = manager.getFieldHerb(below);
                    if (herb != null) {
                        base = below;
                    }
                }
            }
            if (herb != null) {
                long now = System.currentTimeMillis();
                boolean boostedNow =
                        herb.boostedNow(base.getWorld(), base.getX(), base.getY(), base.getZ());
                boolean mature = herb.isMature(now, boostedNow);
                manager.removeFieldHerb(base);
                event.setDropItems(false);
                var cfg = manager.cfg();
                String herbType = herb.herbType();
                if (!mature) {
                    ItemStack seedsBack =
                            ItemFactory.createSpecialSeed(
                                    manager.getKeys(),
                                    herbType,
                                    cfg.specialSeedDisplayName(herbType),
                                    cfg.specialSeedLoreComponents(herbType));
                    b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), seedsBack);
                } else {
                    int amount = boostedNow ? 2 : 1;
                    ItemStack out = ItemFactory.createHerbRaw(manager.getKeys(), cfg, herbType);
                    out.setAmount(amount);
                    b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), out);
                }
                boolean breakingTopLarge =
                        b.getType() == Material.LARGE_FERN
                                && b.getBlockData() instanceof Bisected bis
                                && bis.getHalf() == Bisected.Half.TOP
                                && base.getY() < b.getY();
                if (breakingTopLarge) {
                    final Block bottomToClear = base;
                    Bukkit.getScheduler()
                            .runTask(
                                    manager.getPlugin(),
                                    () -> {
                                        if (bottomToClear.getType() == Material.LARGE_FERN) {
                                            bottomToClear.setType(Material.AIR);
                                        }
                                    });
                }
                return;
            }
        }
        if (b.getType() != Material.WHEAT) {
            return;
        }
        if (!(b.getBlockData() instanceof Ageable age)) {
            return;
        }
        if (age.getAge() < age.getMaximumAge()) {
            return;
        }
        if (!manager.isWheatInAnyFarmZone(b)) {
            return;
        }
        event.setDropItems(false);
        Location drop = b.getLocation().clone().add(0.5, 0.5, 0.5);
        b.getWorld().dropItemNaturally(drop, ItemFactory.createHerbRaw(manager.getKeys(), manager.cfg()));
        int seeds = ThreadLocalRandom.current().nextInt(4);
        if (seeds > 0) {
            b.getWorld().dropItemNaturally(drop, new ItemStack(Material.WHEAT_SEEDS, seeds));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.discoverCraftRecipesFor(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.cancelDryingForPlayer(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        manager.handleQuickTimeAction(event.getPlayer(), SPdrugManager.QuickTimeAction.SNEAK, event.getPlayer().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (manager.handleQuickTimeAction(
                event.getPlayer(), SPdrugManager.QuickTimeAction.SWAP, event.getPlayer().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (!manager.isOurBlock(b)) {
            return;
        }
        String kind = manager.getBlockKind(b);
        if (SPdrugManager.TYPE_LAB.equals(kind)) {
            manager.onLabBroken(b);
            event.setDropItems(false);
            b.getWorld()
                    .dropItemNaturally(
                            b.getLocation().add(0.5, 0.3, 0.5),
                            ItemFactory.createLabPlacer(manager.getKeys(), manager.cfg()));
        } else if (SPdrugManager.TYPE_FARM.equals(kind)) {
            manager.onFarmBroken(b);
            event.setDropItems(false);
            b.getWorld()
                    .dropItemNaturally(
                            b.getLocation().add(0.5, 0.3, 0.5),
                            ItemFactory.createFarmPlacer(manager.getKeys(), manager.cfg()));
        }
    }
}
