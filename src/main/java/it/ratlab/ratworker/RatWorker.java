package it.ratlab.ratworker;

import com.mojang.logging.LogUtils;
import it.ratlab.ratworker.api.RegisterWorkerTasksEvent;
import it.ratlab.ratworker.api.WorkerTaskRegistry;
import it.ratlab.ratworker.command.RatWorkerCommand;
import it.ratlab.ratworker.config.ClientConfig;
import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.data.WorkerDataLoader;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.network.RWNetwork;
import it.ratlab.ratworker.registry.ModEntities;
import it.ratlab.ratworker.registry.ModItems;
import it.ratlab.ratworker.registry.ModMenus;
import it.ratlab.ratworker.supply.SupplyLinks;
import it.ratlab.ratworker.task.builtin.BuiltinTasks;
import it.ratlab.ratworker.world.WorkerTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RatWorker.MOD_ID)
public class RatWorker {
    public static final String MOD_ID = "ratworker";
    public static final Logger LOG = LogUtils.getLogger();

    public RatWorker(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);

        context.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, MOD_ID + "-server.toml");
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, MOD_ID + "-client.toml");

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::attributes);
        modBus.addListener(this::configLoading);
        modBus.addListener(this::configReloading);
        modBus.addListener(BuiltinTasks::register);

        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.addListener(this::commands);
        forgeBus.addListener(this::reloadListeners);
        forgeBus.addListener(this::serverTick);
        forgeBus.addListener(SupplyLinks::onRightClickBlock);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        RWNetwork.register();
        // Tasks are collected once. Addons listen to RegisterWorkerTasksEvent on their own mod bus.
        event.enqueueWork(() -> {
            ModLoader.get().postEvent(new RegisterWorkerTasksEvent(WorkerTaskRegistry.INSTANCE));
            LOG.info("Rat Worker: {} built-in/addon tasks registered", WorkerTaskRegistry.INSTANCE.all().size());
        });
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.WORKER.get(), WorkerEntity.createAttributes().build());
    }

    private void configLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ServerConfig.SPEC) ServerConfig.bind(event.getConfig());
    }

    private void configReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ServerConfig.SPEC) ServerConfig.bind(event.getConfig());
    }

    private void commands(RegisterCommandsEvent event) {
        RatWorkerCommand.register(event.getDispatcher());
    }

    private void reloadListeners(AddReloadListenerEvent event) {
        event.addListener(WorkerDataLoader.INSTANCE);
    }

    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) WorkerTracker.serverTick(event.getServer());
    }
}
