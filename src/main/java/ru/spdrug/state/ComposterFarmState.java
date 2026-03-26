package ru.spdrug.state;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Состояние блока-фермы (компостер): опциональный процесс сушки растительного сырья.
 */
public final class ComposterFarmState {

    private final UUID id;
    private final Location blockLocation;

    private boolean drying;
    private int dryingTicksLeft;
    private int dryingTotal;
    private UUID dryingStarter;
    private BossBar dryingBar;
    private int dryingSkillHits;
    private String dryingHerbType;

    public static final int DEFAULT_DRY_TICKS = 200; // ~10 с
    public static final int REQUIRED_DRYING_SKILL_HITS = 3;

    public ComposterFarmState(UUID id, Location blockLocation) {
        this.id = id;
        this.blockLocation = blockLocation.clone();
    }

    public UUID getId() {
        return id;
    }

    public Location getBlockLocation() {
        return blockLocation.clone();
    }

    public boolean isDrying() {
        return drying;
    }

    public int getDryingTicksLeft() {
        return dryingTicksLeft;
    }

    public int getDryingTotal() {
        return dryingTotal;
    }

    public UUID getDryingStarter() {
        return dryingStarter;
    }

    public BossBar getDryingBar() {
        return dryingBar;
    }

    public void setDryingBar(BossBar dryingBar) {
        this.dryingBar = dryingBar;
    }

    /** Начать сушку; вызывается после создания BossBar снаружи. */
    public void beginDrying(UUID starterPlayer, int totalTicks) {
        this.drying = true;
        this.dryingStarter = starterPlayer;
        this.dryingTotal = totalTicks;
        this.dryingTicksLeft = totalTicks;
        this.dryingSkillHits = 0;
        this.dryingHerbType = "cannabis";
    }

    public void beginDrying(UUID starterPlayer, int totalTicks, String herbType) {
        beginDrying(starterPlayer, totalTicks);
        this.dryingHerbType = herbType == null || herbType.isBlank() ? "cannabis" : herbType;
    }

    /** Один тик сушки; возвращает true, если процесс завершён. */
    public boolean tickDrying() {
        if (!drying) {
            return false;
        }
        dryingTicksLeft--;
        return dryingTicksLeft <= 0;
    }

    public void endDrying() {
        drying = false;
        dryingTicksLeft = 0;
        dryingStarter = null;
        dryingBar = null;
        dryingSkillHits = 0;
        dryingHerbType = null;
    }

    public int getDryingSkillHits() {
        return dryingSkillHits;
    }

    public int getRequiredDryingSkillHits() {
        return REQUIRED_DRYING_SKILL_HITS;
    }

    public boolean isDryingSkillPassed() {
        return dryingSkillHits >= REQUIRED_DRYING_SKILL_HITS;
    }

    /** Возвращает true, если нажатие засчитано. */
    public boolean registerDryingSkillHit() {
        if (!drying || dryingSkillHits >= REQUIRED_DRYING_SKILL_HITS) {
            return false;
        }
        dryingSkillHits++;
        return true;
    }

    public String getDryingHerbType() {
        return dryingHerbType == null ? "cannabis" : dryingHerbType;
    }
}
