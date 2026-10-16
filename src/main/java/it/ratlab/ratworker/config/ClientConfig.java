package it.ratlab.ratworker.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;

/** Visual-only settings. Never holds gameplay values. */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final BooleanValue JADE;
    public static final ConfigValue<String> ACCENT;
    public static final ConfigValue<String> WARN;
    public static final ConfigValue<String> DANGER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("hud");
        JADE = b.comment("Add worker lines to Jade tooltips (if Jade is installed).").define("jadeTooltip", true);
        b.pop();
        b.comment("Colors as RRGGBB hex. Empty = built-in palette.").push("colors");
        ACCENT = b.define("accent", "");
        WARN = b.define("warn", "");
        DANGER = b.define("danger", "");
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {}
}
