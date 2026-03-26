package ru.spdrug.drug;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Описание одного зелья для препарата (положительное или отрицательное).
 */
public record PotionEffectSpec(PotionEffectType type, int amplifier, int durationTicks) {

    public void apply(Player player) {
        player.addPotionEffect(
                new PotionEffect(type, durationTicks, amplifier, false, true, true));
    }
}
