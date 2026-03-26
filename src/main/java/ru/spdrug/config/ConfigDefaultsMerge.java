package ru.spdrug.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Подмешивает в существующий config.yml недостающие ключи из jar (например item-material у drugs).
 */
public final class ConfigDefaultsMerge {

    private ConfigDefaultsMerge() {
    }

    public static void mergeFromJarResource(JavaPlugin plugin, String resourceName) {
        try (InputStream in = plugin.getResource(resourceName)) {
            if (in == null) {
                plugin.getLogger().warning("Ресурс " + resourceName + " не найден в jar — пропуск слияния дефолтов.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            FileConfiguration cfg = plugin.getConfig();
            cfg.setDefaults(defaults);
            cfg.options().copyDefaults(true);
            plugin.saveConfig();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось подмешать дефолты из " + resourceName, e);
        }
    }
}
