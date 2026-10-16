package it.ratlab.ratworker.item;

import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.api.WorkerTaskRegistry;
import it.ratlab.ratworker.compat.Compat;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.skill.WorkerSkills;
import it.ratlab.ratworker.task.TaskRules;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Training manual: ratworker:tome with NBT {Task:"ratworker:crank"}. Used on a worker, it teaches that task
 * if the worker has the XP (tasks.&lt;id&gt;.learnXp). The pack decides which era can craft which tome.
 */
public class TomeItem extends Item {
    public static final String TAG = "Task";

    public TomeItem(Properties props) {
        super(props);
    }

    public static ItemStack of(Item tome, ResourceLocation task) {
        ItemStack s = new ItemStack(tome);
        s.getOrCreateTag().putString(TAG, task.toString());
        return s;
    }

    @Nullable
    public static ResourceLocation taskId(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t == null || !t.contains(TAG) ? null : ResourceLocation.tryParse(t.getString(TAG));
    }

    /** Server side, from WorkerEntity.mobInteract. */
    public static void teach(ServerPlayer player, WorkerEntity w, ItemStack stack) {
        ResourceLocation id = taskId(stack);
        WorkerTask task = id == null ? null : WorkerTaskRegistry.INSTANCE.get(id);
        if (task == null) {
            player.displayClientMessage(Component.translatable("ratworker.reason.unknown_task", String.valueOf(id)), true);
            return;
        }
        if (!TaskRules.enabled(task)) {
            player.displayClientMessage(Component.translatable("ratworker.reason.task_disabled"), true);
            return;
        }
        if (w.skills().knows(task)) {
            player.displayClientMessage(Component.translatable("ratworker.msg.already_knows", w.getName(), task.title()), true);
            return;
        }
        int cost = WorkerSkills.learnCost(task);
        if (w.skills().xp() < cost) {
            player.displayClientMessage(Component.translatable("ratworker.reason.learn_xp", cost, (int) w.skills().xp()), true);
            return;
        }
        if (Compat.kubejs() && !Compat.kubeLearn(w, task, player)) return; // a script said no
        w.skills().addXp(-cost);
        w.skills().learn(task.id());
        if (!player.getAbilities().instabuild) stack.shrink(1);
        w.playSound(SoundEvents.BOOK_PAGE_TURN, 1f, 1f);
        player.displayClientMessage(Component.translatable("ratworker.msg.learned", w.getName(), task.title()), true);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = taskId(stack);
        if (id == null) return super.getName(stack);
        WorkerTask task = WorkerTaskRegistry.INSTANCE.get(id);
        Component title = task != null ? task.title() : Component.translatable("task." + id.getNamespace() + "." + id.getPath());
        return Component.translatable("item.ratworker.tome.named", title);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.ratworker.tome.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
