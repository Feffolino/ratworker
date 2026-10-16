package it.ratlab.ratworker.compat;

import dev.latvian.mods.kubejs.stages.Stages;
import net.minecraft.server.level.ServerPlayer;

/** Loaded only when KubeJS is present. */
final class KubeJSStages {
    private KubeJSStages() {}

    static boolean has(ServerPlayer player, String stage) {
        return Stages.get(player).has(stage);
    }
}
