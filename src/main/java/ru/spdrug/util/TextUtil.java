package ru.spdrug.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/** Цвета в конфиге: символ & (ampersand). */
public final class TextUtil {

    private static final LegacyComponentSerializer AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();

    private TextUtil() {}

    public static Component amp(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return AMPERSAND.deserialize(raw);
    }

    public static List<Component> ampLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<Component> out = new ArrayList<>(lines.size());
        for (String s : lines) {
            out.add(amp(s));
        }
        return out;
    }
}
