package it.ratlab.ratworker.registry;

import it.ratlab.ratworker.RatWorker;
import it.ratlab.ratworker.entity.WorkerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RatWorker.MOD_ID);

    // MISC: never counted against mob caps, never naturally spawned
    public static final RegistryObject<EntityType<WorkerEntity>> WORKER = ENTITIES.register("worker",
            () -> EntityType.Builder.<WorkerEntity>of(WorkerEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .build(RatWorker.MOD_ID + ":worker"));

    private ModEntities() {}
}
