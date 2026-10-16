package it.ratlab.ratworker.client;

import it.ratlab.ratworker.config.ClientConfig;

/** The whole palette in one place: cold, low-saturation, lab terminal. ARGB. */
public final class UiColors {
    public static final int BG = 0xF00C1013;
    public static final int PANEL = 0xFF12181C;
    public static final int LINE = 0xFF2A333A;
    public static final int HEADER = 0xFF161E23;
    public static final int TEXT = 0xFFB8C2C8;
    public static final int TEXT_DIM = 0xFF6E7A82;
    public static final int TEXT_BRIGHT = 0xFFE4EAEE;
    public static final int ROW_HOVER = 0xFF1A2329;
    public static final int ROW_SELECTED = 0xFF1F2E35;
    public static final int SLOT = 0xFF0A0D10;
    public static final int BAR_BG = 0xFF0A0D10;
    public static final int HEALTH = 0xFF8FA3AD;

    private static final int ACCENT_DEFAULT = 0xFF5FA8A8;
    private static final int WARN_DEFAULT = 0xFFC99A3E;
    private static final int DANGER_DEFAULT = 0xFFB4483F;

    private UiColors() {}

    public static int accent() {
        return parse(ClientConfig.ACCENT.get(), ACCENT_DEFAULT);
    }

    public static int warn() {
        return parse(ClientConfig.WARN.get(), WARN_DEFAULT);
    }

    public static int danger() {
        return parse(ClientConfig.DANGER.get(), DANGER_DEFAULT);
    }

    /** Bar color for a 0..20 need: accent, warn when low, danger at zero. */
    public static int need(float value, int low) {
        if (value <= 0) return danger();
        return value < low ? warn() : accent();
    }

    private static int parse(String hex, int fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        try {
            return 0xFF000000 | Integer.parseInt(hex.replace("#", "").trim(), 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
