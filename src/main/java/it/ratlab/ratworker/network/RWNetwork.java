package it.ratlab.ratworker.network;

import it.ratlab.ratworker.RatWorker;
import it.ratlab.ratworker.api.TaskCheck;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.api.WorkerTaskRegistry;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.supply.SupplyLinks;
import it.ratlab.ratworker.task.TaskRules;
import it.ratlab.ratworker.task.WorkerAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/** Versioned channel. Clients only send intents; the server validates everything. */
public final class RWNetwork {
    public static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            RatWorker.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private RWNetwork() {}

    public static void register() {
        int i = 0;
        CHANNEL.registerMessage(i++, ScreenPacket.class, ScreenPacket::write, ScreenPacket::read, ScreenPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(i++, ActionPacket.class, ActionPacket::write, ActionPacket::read, ActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void openCommandScreen(ServerPlayer player, WorkerScreenData data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ScreenPacket(data, true));
    }

    /** S2C: open (or refresh) the command screen. */
    public record ScreenPacket(WorkerScreenData data, boolean open) {
        void write(FriendlyByteBuf buf) {
            buf.writeBoolean(open);
            data.write(buf);
        }

        static ScreenPacket read(FriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            return new ScreenPacket(WorkerScreenData.read(buf), open);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> it.ratlab.ratworker.client.ClientPacketHandler.screen(data, open)));
            ctx.get().setPacketHandled(true);
        }
    }

    public enum Action { REFRESH, START, STOP, LINK_SUPPLY, CLEAR_SUPPLY, UPGRADE }

    /** C2S: a player asks the server to do something with a worker. */
    public record ActionPacket(int entityId, Action action, String taskId) {
        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeEnum(action);
            buf.writeUtf(taskId, 256);
        }

        static ActionPacket read(FriendlyByteBuf buf) {
            return new ActionPacket(buf.readVarInt(), buf.readEnum(Action.class), buf.readUtf(256));
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null) return;
                if (!(player.level().getEntity(entityId) instanceof WorkerEntity w) || !w.isAlive()) return;
                if (player.distanceToSqr(w) > 16 * 16 || !WorkerAccess.canCommand(player, w)) return;
                switch (action) {
                    case START -> {
                        ResourceLocation id = ResourceLocation.tryParse(taskId);
                        WorkerTask task = id == null ? null : WorkerTaskRegistry.INSTANCE.get(id);
                        if (task == null || !TaskRules.isUnlocked(player, w, task)) return;
                        TaskCheck c = w.startTask(task, player);
                        if (!c.ok()) player.displayClientMessage(c.reason(), true);
                    }
                    case STOP -> w.runner().stop(Component.translatable("ratworker.reason.stopped_by", player.getName()));
                    case LINK_SUPPLY -> {
                        SupplyLinks.begin(player, w);
                        return; // the client closed the screen so the player can click a container
                    }
                    case CLEAR_SUPPLY -> w.setSupply(null);
                    case UPGRADE -> {
                        // taskId carries the node id; only nodes of known tasks (or worker-wide ones) can be bought
                        String node = taskId;
                        int hash = node.lastIndexOf('#');
                        if (hash > 0) {
                            ResourceLocation tid = ResourceLocation.tryParse(node.substring(0, hash));
                            WorkerTask t = tid == null ? null : WorkerTaskRegistry.INSTANCE.get(tid);
                            if (t == null || !TaskRules.isUnlocked(player, w, t)) return;
                        }
                        String fail = w.skills().upgrade(w, node);
                        if (fail != null) player.displayClientMessage(Component.translatable(fail), true);
                    }
                    case REFRESH -> {}
                }
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ScreenPacket(WorkerScreenData.build(w, player), false));
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
