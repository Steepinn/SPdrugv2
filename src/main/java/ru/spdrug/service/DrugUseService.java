package ru.spdrug.service;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.spdrug.config.SPdrugConfig;
import ru.spdrug.drug.DrugDefinition;
import ru.spdrug.drug.PotionEffectSpec;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Употребление препарата: title, BossBar, эффекты, шанс плохой реакции.
 */
public final class DrugUseService {

    private static final Map<UUID, Deque<Long>> USE_TIMES = new ConcurrentHashMap<>();

    private DrugUseService() {}

    public static void apply(Player player, DrugDefinition drug, JavaPlugin plugin, SPdrugConfig cfg) {
        int maxTicks = 20 * 30;
        for (PotionEffectSpec s : drug.getPositive()) {
            maxTicks = Math.max(maxTicks, s.durationTicks());
        }
        for (PotionEffectSpec s : drug.getNegative()) {
            maxTicks = Math.max(maxTicks, s.durationTicks());
        }

        for (PotionEffectSpec s : drug.getPositive()) {
            s.apply(player);
        }
        for (PotionEffectSpec s : drug.getNegative()) {
            s.apply(player);
        }

        if (ThreadLocalRandom.current().nextDouble() < drug.getBadTripChance()) {
            double lo = cfg.drugUseBadTripDamageMin();
            double hi = cfg.drugUseBadTripDamageMax();
            double dmg = lo >= hi ? lo : ThreadLocalRandom.current().nextDouble(lo, hi);
            player.damage(dmg);
            player.sendMessage(cfg.drugUseBadTripMessage());
        }
        applyOverdoseIfNeeded(player, cfg);

        player.showTitle(
                Title.title(
                        cfg.drugUseTitleMain(drug.getDisplayName()),
                        cfg.drugUseTitleSubtitle(),
                        Title.Times.times(
                                Duration.ofMillis(cfg.drugUseTitleFadeInMs()),
                                Duration.ofMillis(cfg.drugUseTitleStayMs()),
                                Duration.ofMillis(cfg.drugUseTitleFadeOutMs()))));

        BossBar bar =
                BossBar.bossBar(
                        cfg.drugUseBossBarTitle(drug.getDisplayName()),
                        1.0f,
                        cfg.drugUseBossBarColor(),
                        BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);

        int ticksTotal = maxTicks;
        new BukkitRunnable() {
            int left = ticksTotal;

            @Override
            public void run() {
                left--;
                bar.progress(Math.max(0f, left / (float) ticksTotal));
                if (left <= 0) {
                    player.hideBossBar(bar);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        int flashCount = cfg.drugUseScreenFlashCount();
        if (flashCount > 0) {
            long interval = cfg.drugUseScreenFlashIntervalTicks();
            new BukkitRunnable() {
                int flashes = flashCount;

                @Override
                public void run() {
                    if (flashes-- <= 0 || !player.isOnline()) {
                        cancel();
                        return;
                    }
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 6, 0, false, false, false));
                }
            }.runTaskTimer(plugin, 15L, interval);
        }
    }

    private static void applyOverdoseIfNeeded(Player player, SPdrugConfig cfg) {
        if (!cfg.overdoseEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long windowMs = cfg.overdoseWindowSeconds() * 1000L;
        Deque<Long> q = USE_TIMES.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
            q.pollFirst();
        }
        q.addLast(now);
        if (q.size() < cfg.overdoseThreshold()) {
            return;
        }
        player.damage(cfg.overdoseDamage());
        player.addPotionEffect(
                new PotionEffect(PotionEffectType.NAUSEA, cfg.overdoseNauseaSeconds() * 20, 0, false, true, true));
        player.addPotionEffect(
                new PotionEffect(PotionEffectType.WEAKNESS, cfg.overdoseWeaknessSeconds() * 20, 1, false, true, true));
        player.sendMessage(cfg.overdoseMessage());
        q.clear();
    }
}
