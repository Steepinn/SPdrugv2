package ru.spdrug.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Строки из {@code lang/en.yml} или {@code lang/ru.yml}; затем {@code config.yml}. По умолчанию язык {@code en}.
 */
public final class LocaleStrings {

    private final JavaPlugin plugin;
    private FileConfiguration lang;

    public LocaleStrings(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String code = normalizeLanguage(plugin.getConfig().getString("general.language", "en"));
        lang = loadLang(code);
    }

    /** {@code ru} — русский; любое другое значение (в т.ч. пустое) — английский. */
    public static String normalizeLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "en";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("ru")) {
            return "ru";
        }
        return "en";
    }

    private FileConfiguration loadLang(String code) {
        String path = "lang/" + code + ".yml";
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                plugin.getLogger().warning("[SPdrug] Missing resource " + path + " — using config / fallbacks");
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[SPdrug] Не удалось загрузить " + path, e);
            return new YamlConfiguration();
        }
    }

    /** Одна строка: сначала lang, затем config, затем fallback. */
    public String line(String dottedPath, String fallback) {
        String s = lang.getString(dottedPath);
        if (s != null && !s.isBlank()) {
            return s;
        }
        s = plugin.getConfig().getString(dottedPath);
        if (s != null && !s.isBlank()) {
            return s;
        }
        return fallback == null ? "" : fallback;
    }

    /** Список строк: сначала lang, затем config. */
    public List<String> lines(String dottedPath) {
        List<String> a = lang.getStringList(dottedPath);
        if (a != null && !a.isEmpty()) {
            return new ArrayList<>(a);
        }
        List<String> b = plugin.getConfig().getStringList(dottedPath);
        return b != null ? new ArrayList<>(b) : new ArrayList<>();
    }
}
