package it.ratlab.ratworker.client;

import com.mojang.blaze3d.vertex.PoseStack;
import it.ratlab.ratworker.config.ClientConfig;
import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.entity.WorkerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Player-shaped worker with a per-entity skin and wide/slim arms. */
public class WorkerRenderer extends HumanoidMobRenderer<WorkerEntity, PlayerModel<WorkerEntity>> {
    private final PlayerModel<WorkerEntity> wide;
    private final PlayerModel<WorkerEntity> slim;

    public WorkerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        wide = model;
        slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(WorkerEntity e) {
        return e.skin();
    }

    @Override
    public void render(WorkerEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        model = e.slim() ? slim : wide;
        super.render(e, yaw, partial, pose, buffers, light);
        renderNeedsStrip(e, pose, buffers, light);
    }

    /** Classic food/water icon rows above the name, only when a need is low. */
    private void renderNeedsStrip(WorkerEntity e, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!ClientConfig.OVERHEAD_WARNING.get()) return;
        double max = ClientConfig.OVERHEAD_DISTANCE.get();
        if (entityRenderDispatcher.distanceToSqr(e) > max * max) return;
        int low = ServerConfig.isLoaded() ? ServerConfig.LOW_THRESHOLD.get() : 6; // synced from the server
        int flags = e.syncedFlags();
        float hunger = e.syncedHunger(), thirst = e.syncedThirst();
        boolean h = (flags & WorkerEntity.FLAG_HUNGER) != 0 && hunger < low;
        boolean t = (flags & WorkerEntity.FLAG_THIRST) != 0 && thirst < low;
        if (!h && !t) return;

        pose.pushPose();
        pose.translate(0, e.getNameTagOffsetY() + (h && t ? 0.5f : 0.35f), 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.scale(-0.0125f, -0.0125f, 0.0125f);
        Matrix4f m = pose.last().pose();
        float row = 0;
        if (h) {
            NeedIcons.drawInWorld(m, buffers, NeedIcons.Kind.FOOD, row, hunger, light);
            row += NeedIcons.ICON + 2;
        }
        if (t) NeedIcons.drawInWorld(m, buffers, NeedIcons.Kind.WATER, row, thirst, light);
        pose.popPose();
    }
}
