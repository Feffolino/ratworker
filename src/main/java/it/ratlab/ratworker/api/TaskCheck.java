package it.ratlab.ratworker.api;

import net.minecraft.network.chat.Component;

/** Result of an availability check. {@code reason} is shown in the command screen. */
public record TaskCheck(boolean ok, Component reason) {
    public static final TaskCheck OK = new TaskCheck(true, Component.empty());

    public static TaskCheck fail(Component reason) {
        return new TaskCheck(false, reason);
    }

    public static TaskCheck fail(String langKey, Object... args) {
        return new TaskCheck(false, Component.translatable(langKey, args));
    }
}
