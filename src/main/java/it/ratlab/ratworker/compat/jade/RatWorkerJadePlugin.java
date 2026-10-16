package it.ratlab.ratworker.compat.jade;

import it.ratlab.ratworker.RatWorker;
import it.ratlab.ratworker.client.NeedIcons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;
import it.ratlab.ratworker.config.ClientConfig;
import it.ratlab.ratworker.config.Settings;
import it.ratlab.ratworker.entity.WorkerEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Jade discovers this through the annotation; the class is never loaded without Jade. */
@WailaPlugin
public class RatWorkerJadePlugin implements IWailaPlugin {
    private static final ResourceLocation UID = RatWorker.id("worker");

    @Override
    public void register(IWailaCommonRegistration reg) {
        reg.registerEntityDataProvider(Server.INSTANCE, WorkerEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration reg) {
        reg.registerEntityComponent(Client.INSTANCE, WorkerEntity.class);
    }

    enum Server implements IServerDataProvider<EntityAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag tag, EntityAccessor accessor) {
            if (!(accessor.getEntity() instanceof WorkerEntity w)) return;
            if (Settings.hungerEnabled()) tag.putFloat("Hunger", w.needs().hunger());
            if (Settings.thirstEnabled()) tag.putFloat("Thirst", w.needs().thirst());
            var task = w.runner().task();
            tag.putString("Task", Component.Serializer.toJson(task == null ? Component.translatable("ratworker.ui.idle") : task.title()));
            tag.putString("Owner", w.ownerName());
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    enum Client implements IEntityComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!ClientConfig.JADE.get()) return;
            CompoundTag d = accessor.getServerData();
            if (d.contains("Hunger")) tooltip.add(new IconRow(NeedIcons.Kind.FOOD, d.getFloat("Hunger")));
            if (d.contains("Thirst")) tooltip.add(new IconRow(NeedIcons.Kind.WATER, d.getFloat("Thirst")));
            if (d.contains("Task")) {
                Component task = Component.Serializer.fromJson(d.getString("Task"));
                if (task != null) tooltip.add(Component.translatable("ratworker.ui.current").append(" ").append(task));
            }
            if (d.contains("Owner") && !d.getString("Owner").isEmpty()) {
                tooltip.add(Component.translatable("ratworker.ui.owner", d.getString("Owner")));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    /** Classic food/water icon row as a Jade element. */
    static class IconRow extends Element {
        private final NeedIcons.Kind kind;
        private final float value;

        IconRow(NeedIcons.Kind kind, float value) {
            this.kind = kind;
            this.value = value;
        }

        @Override
        public Vec2 getSize() {
            return new Vec2(NeedIcons.ROW_WIDTH, NeedIcons.ICON + 1);
        }

        @Override
        public void render(GuiGraphics g, float x, float y, float maxX, float maxY) {
            NeedIcons.draw(g, kind, Math.round(x), Math.round(y), value);
        }
    }
}
