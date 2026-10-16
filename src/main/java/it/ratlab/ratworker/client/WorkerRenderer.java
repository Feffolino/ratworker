package it.ratlab.ratworker.client;

import com.mojang.blaze3d.vertex.PoseStack;
import it.ratlab.ratworker.entity.WorkerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

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
    }
}
