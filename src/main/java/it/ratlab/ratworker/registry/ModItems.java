package it.ratlab.ratworker.registry;

import it.ratlab.ratworker.RatWorker;
import it.ratlab.ratworker.item.ContractItem;
import it.ratlab.ratworker.item.TomeItem;
import it.ratlab.ratworker.skill.WorkerSkills;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RatWorker.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RatWorker.MOD_ID);

    public static final RegistryObject<Item> CONTRACT = ITEMS.register("contract", () -> new ContractItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> TOME = ITEMS.register("tome", () -> new TomeItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ratworker"))
            .icon(() -> new ItemStack(CONTRACT.get()))
            .displayItems((params, out) -> {
                out.accept(CONTRACT.get());
                for (var t : it.ratlab.ratworker.api.WorkerTaskRegistry.INSTANCE.all()) {
                    if (!WorkerSkills.isInnateDefault(t)) out.accept(TomeItem.of(TOME.get(), t.id()));
                }
            })
            .build());

    private ModItems() {}
}
