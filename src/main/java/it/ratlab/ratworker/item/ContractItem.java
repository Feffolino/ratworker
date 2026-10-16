package it.ratlab.ratworker.item;

import it.ratlab.ratworker.RatWorker;
import it.ratlab.ratworker.config.Preset.NameMode;
import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.config.Settings;
import it.ratlab.ratworker.task.TaskRules;
import it.ratlab.ratworker.world.WorkerSpawner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Used on a block, recruits a worker standing on that spot. Optional NBT for scripted contracts:
 * {WorkerName:"Nick", Skin:"ratworker:textures/entity/worker/nick.png", Slim:false}.
 */
public class ContractItem extends Item {
    public static final TagKey<Block> RECRUIT_SPOTS = TagKey.create(Registries.BLOCK, RatWorker.id("recruit_spots"));

    public ContractItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(ctx.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        ItemStack stack = ctx.getItemInHand();

        if (!ServerConfig.CONTRACT_ENABLED.get() || !Settings.enabled()) return deny(player, "ratworker.reason.contract_disabled");
        BlockPos clicked = ctx.getClickedPos();
        if (ServerConfig.CONTRACT_REQUIRES_SPOT.get() && !level.getBlockState(clicked).is(RECRUIT_SPOTS)) {
            return deny(player, "ratworker.reason.not_a_spot");
        }
        if (Settings.requireStageToRecruit() && !TaskRules.isOpBypass(player) && !TaskRules.hasStage(player, ServerConfig.RECRUIT_STAGE.get())) {
            return deny(player, "ratworker.reason.cannot_recruit");
        }

        CompoundTag tag = stack.getTag();
        String fixedName = tag != null && tag.contains("WorkerName") ? tag.getString("WorkerName") : null;
        String chosen = stack.hasCustomHoverName() ? stack.getHoverName().getString() : null;
        NameMode mode = ServerConfig.NAME_MODE.get();
        String name = switch (mode) {
            case POOL -> fixedName;
            case CUSTOM -> fixedName != null ? fixedName : chosen;
            case PLAYER_CHOSEN -> chosen != null ? chosen : fixedName;
        };
        if (mode == NameMode.CUSTOM && name == null) return deny(player, "ratworker.reason.needs_name");

        ResourceLocation skin = tag != null && tag.contains("Skin") ? ResourceLocation.tryParse(tag.getString("Skin")) : null;
        Boolean slim = tag != null && tag.contains("Slim") ? tag.getBoolean("Slim") : null;

        BlockPos spot = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(ctx.getClickedFace());
        WorkerSpawner.Result r = WorkerSpawner.spawn((ServerLevel) level, Vec3.atBottomCenterOf(spot), player.getUUID(),
                player.getGameProfile().getName(), name, skin, slim, false);
        if (!r.ok()) {
            player.displayClientMessage(r.reason(), true);
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable("ratworker.msg.recruited", r.worker().getName()), true);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult deny(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable(key), true);
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("WorkerName")) {
            lines.add(Component.translatable("item.ratworker.contract.for", tag.getString("WorkerName")).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("item.ratworker.contract.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
