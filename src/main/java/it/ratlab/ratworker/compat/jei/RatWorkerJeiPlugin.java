package it.ratlab.ratworker.compat.jei;

import it.ratlab.ratworker.RatWorker;
import it.ratlab.ratworker.item.TomeItem;
import it.ratlab.ratworker.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** JEI finds this through the annotation; the class is never loaded without JEI. */
@JeiPlugin
public class RatWorkerJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return RatWorker.id("jei");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration reg) {
        // one JEI entry per taught task
        reg.registerSubtypeInterpreter(ModItems.TOME.get(), (IIngredientSubtypeInterpreter<ItemStack>) (stack, ctx) -> {
            ResourceLocation task = TomeItem.taskId(stack);
            return task == null ? IIngredientSubtypeInterpreter.NONE : task.toString();
        });
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        reg.addIngredientInfo(ModItems.CONTRACT.get(), Component.translatable("jei.ratworker.contract"));
        reg.addIngredientInfo(ModItems.TOME.get(), Component.translatable("jei.ratworker.tome"));
    }
}
