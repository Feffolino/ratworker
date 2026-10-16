package it.ratlab.ratworker.client;

import it.ratlab.ratworker.network.WorkerScreenData;
import net.minecraft.client.Minecraft;

/** Client-only packet targets. Referenced from packets only inside DistExecutor lambdas. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void screen(WorkerScreenData data, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CommandScreen cs && cs.entityId() == data.entityId()) {
            cs.update(data);
        } else if (open) {
            mc.setScreen(new CommandScreen(data));
        }
    }
}
