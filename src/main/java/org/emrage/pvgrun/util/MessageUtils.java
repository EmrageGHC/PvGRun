package org.emrage.pvgrun.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jspecify.annotations.NonNull;

public final class MessageUtils {

    private static MiniMessage mm;

    public static void init() { mm = MiniMessage.miniMessage(); }

    public static @NonNull Component c(String in) {
        // Remove or convert legacy formatting codes (§ codes) before processing with MiniMessage
        // Option 1: Strip all legacy codes
        String cleaned = in.replaceAll("§[0-9a-fk-or]", "");
        return mm.deserialize(cleaned);
    }

    /**
     * Parse text that may contain legacy formatting codes
     * Converts legacy codes to Adventure components
     */
    public static @NonNull Component legacy(String in) {
        return LegacyComponentSerializer.legacySection().deserialize(in);
    }
}