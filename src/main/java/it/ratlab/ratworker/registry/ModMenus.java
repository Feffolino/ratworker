package it.ratlab.ratworker.registry;

import it.ratlab.ratworker.RatWorker;
import it.ratlab.ratworker.menu.WorkerInventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, RatWorker.MOD_ID);

    public static final RegistryObject<MenuType<WorkerInventoryMenu>> WORKER_INVENTORY =
            MENUS.register("worker_inventory", () -> IForgeMenuType.create(WorkerInventoryMenu::fromNetwork));

    private ModMenus() {}
}
