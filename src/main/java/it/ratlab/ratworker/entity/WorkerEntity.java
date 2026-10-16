package it.ratlab.ratworker.entity;

import it.ratlab.ratworker.RatWorker;
import it.ratlab.ratworker.api.TaskCheck;
import it.ratlab.ratworker.compat.Compat;
import it.ratlab.ratworker.api.WorkerInput;
import it.ratlab.ratworker.api.WorkerTask;
import it.ratlab.ratworker.config.ServerConfig;
import it.ratlab.ratworker.config.Settings;
import it.ratlab.ratworker.item.TomeItem;
import it.ratlab.ratworker.menu.WorkerInventoryMenu;
import it.ratlab.ratworker.network.RWNetwork;
import it.ratlab.ratworker.network.WorkerScreenData;
import it.ratlab.ratworker.skill.WorkerSkills;
import it.ratlab.ratworker.supply.SupplyGoal;
import it.ratlab.ratworker.task.TaskGoal;
import it.ratlab.ratworker.task.TaskRunner;
import it.ratlab.ratworker.task.WorkerAccess;
import it.ratlab.ratworker.world.WorkerSavedData;
import it.ratlab.ratworker.world.WorkerTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** The worker NPC: owner-bound, inventory, hunger/thirst, one task at a time. */
public class WorkerEntity extends PathfinderMob {
    public static final ResourceLocation DEFAULT_SKIN = RatWorker.id("textures/entity/worker/worker_1.png");
    public static final int FLAG_HUNGER = 1, FLAG_THIRST = 2, FLAG_PAUSED = 4;

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(WorkerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_SKIN = SynchedEntityData.defineId(WorkerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_SLIM = SynchedEntityData.defineId(WorkerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_HUNGER = SynchedEntityData.defineId(WorkerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_THIRST = SynchedEntityData.defineId(WorkerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_TASK = SynchedEntityData.defineId(WorkerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(WorkerEntity.class, EntityDataSerializers.BYTE);

    private final SimpleContainer inventory = new SimpleContainer(27);
    private final WorkerNeeds needs = new WorkerNeeds();
    private final TaskRunner runner = new TaskRunner(this);
    private final WorkerInput input = new WorkerInput(this);
    private String ownerName = "";
    private final WorkerSkills skills = new WorkerSkills();
    @Nullable private BlockPos home;
    @Nullable private BlockPos supply;

    public WorkerEntity(EntityType<? extends WorkerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0f); // drops handled in dropCustomDeathLoot
        if (getNavigation() instanceof GroundPathNavigation nav) nav.setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.ATTACK_DAMAGE, 1);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_OWNER, Optional.empty());
        entityData.define(DATA_SKIN, DEFAULT_SKIN.toString());
        entityData.define(DATA_SLIM, false);
        entityData.define(DATA_HUNGER, WorkerNeeds.MAX);
        entityData.define(DATA_THIRST, WorkerNeeds.MAX);
        entityData.define(DATA_TASK, "");
        entityData.define(DATA_FLAGS, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(1, new SupplyGoal(this));
        goalSelector.addGoal(2, new TaskGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    // ---- accessors ----

    public SimpleContainer inventory() {
        return inventory;
    }

    public WorkerNeeds needs() {
        return needs;
    }

    public TaskRunner runner() {
        return runner;
    }

    public WorkerInput input() {
        return input;
    }

    @Nullable
    public UUID getOwnerId() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwner(@Nullable UUID id, String name) {
        entityData.set(DATA_OWNER, Optional.ofNullable(id));
        ownerName = name == null ? "" : name;
    }

    public WorkerSkills skills() {
        return skills;
    }

    public String ownerName() {
        return ownerName;
    }

    public ResourceLocation skin() {
        ResourceLocation rl = ResourceLocation.tryParse(entityData.get(DATA_SKIN));
        return rl == null ? DEFAULT_SKIN : rl;
    }

    public boolean slim() {
        return entityData.get(DATA_SLIM);
    }

    public void setSkin(ResourceLocation skin, boolean slim) {
        entityData.set(DATA_SKIN, skin.toString());
        entityData.set(DATA_SLIM, slim);
    }

    /** Client-visible needs (updated once per second). */
    public float syncedHunger() {
        return entityData.get(DATA_HUNGER);
    }

    public float syncedThirst() {
        return entityData.get(DATA_THIRST);
    }

    public String syncedTaskId() {
        return entityData.get(DATA_TASK);
    }

    public int syncedFlags() {
        return entityData.get(DATA_FLAGS);
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    public void setHome(@Nullable BlockPos home) {
        this.home = home;
    }

    @Nullable
    public BlockPos supply() {
        return supply;
    }

    public void setSupply(@Nullable BlockPos supply) {
        this.supply = supply == null ? null : supply.immutable();
    }

    public void syncTask() {
        entityData.set(DATA_TASK, runner.syncedId());
    }

    private void syncNeeds() {
        entityData.set(DATA_HUNGER, needs.hunger());
        entityData.set(DATA_THIRST, needs.thirst());
        int flags = (Settings.hungerEnabled() ? FLAG_HUNGER : 0) | (Settings.thirstEnabled() ? FLAG_THIRST : 0) | (runner.isPaused() ? FLAG_PAUSED : 0);
        entityData.set(DATA_FLAGS, (byte) flags);
    }

    // ---- tasks ----

    public TaskCheck startTask(WorkerTask task, @Nullable ServerPlayer by) {
        return runner.start(task, by);
    }

    // ---- ticking ----

    @Override
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
        if (!level().isClientSide) {
            input.tick();
            if (tickCount % 20 == 0) {
                needs.tickSecond(this);
                syncNeeds();
            }
            if (tickCount % 100 == 0) updateRegistry();
        }
    }

    private void updateRegistry() {
        UUID owner = getOwnerId();
        if (owner == null || level().getServer() == null) return;
        WorkerSavedData.get(level().getServer()).put(getUUID(),
                new WorkerSavedData.Entry(owner, getName().getString(), level().dimension().location().toString(), blockPosition()));
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide) {
            WorkerTracker.onLoad(this);
            updateRegistry();
            syncNeeds();
            syncTask();
        }
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!level().isClientSide) {
            input.releaseAll();
            WorkerTracker.onUnload(this);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!level().isClientSide && reason == RemovalReason.DISCARDED && level().getServer() != null) {
            WorkerSavedData.get(level().getServer()).remove(getUUID());
        }
    }

    // ---- interaction ----

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        if (!WorkerAccess.canCommand(sp, this)) {
            sp.displayClientMessage(Component.translatable("ratworker.msg.not_yours"), true);
            return InteractionResult.CONSUME;
        }
        if (sp.getUUID().equals(getOwnerId())) ownerName = sp.getGameProfile().getName();
        ItemStack held = sp.getItemInHand(hand);
        if (held.getItem() instanceof TomeItem) {
            TomeItem.teach(sp, this, held);
            return InteractionResult.CONSUME;
        }
        if (sp.isShiftKeyDown()) {
            NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new WorkerInventoryMenu(id, inv, this), getName()),
                    buf -> buf.writeVarInt(getId()));
        } else {
            RWNetwork.openCommandScreen(sp, WorkerScreenData.build(this, sp));
        }
        return InteractionResult.CONSUME;
    }

    /** Grants XP (work, scripts). Passes through WorkerEvents.gainXp when KubeJS is present. */
    public void gainXp(double amount, @Nullable WorkerTask task) {
        double a = Compat.kubejs() ? Compat.kubeGainXp(this, task, amount) : amount;
        if (a > 0) skills.addXp(a);
    }

    /** Picks up item entities within radius into the inventory. Returns items picked. */
    public int pickupItems(double radius) {
        int n = 0;
        for (net.minecraft.world.entity.item.ItemEntity ie : level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                getBoundingBox().inflate(radius), e -> e.isAlive() && !e.hasPickUpDelay())) {
            ItemStack s = ie.getItem();
            int before = s.getCount();
            ItemStack rest = inventory.addItem(s.copy());
            n += before - rest.getCount();
            if (rest.isEmpty()) ie.discard();
            else ie.setItem(rest);
        }
        return n;
    }

    /** Puts a stack into the inventory or drops what does not fit. */
    public void giveOrDrop(ItemStack stack) {
        ItemStack rest = inventory.addItem(stack);
        if (!rest.isEmpty()) spawnAtLocation(rest);
    }

    // ---- damage and death ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.getEntity() instanceof Player p && !p.isCreative()) {
            boolean owner = p.getUUID().equals(getOwnerId());
            if (owner && !ServerConfig.OWNER_FRIENDLY_FIRE.get()) return false;
            if (!owner && WorkerAccess.isFriend(p, this) && !ServerConfig.TEAM_FRIENDLY_FIRE.get()) return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) needs.addExhaustion(source.getFoodExhaustion());
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && level().getServer() != null && !isRemoved() && !dead) {
            runner.stop(null);
            input.releaseAll();
            WorkerSavedData data = WorkerSavedData.get(level().getServer());
            data.remove(getUUID());
            UUID owner = getOwnerId();
            if (!Settings.permaDeath() && owner != null) {
                CompoundTag snapshot = saveWithoutId(new CompoundTag());
                if (ServerConfig.DROP_INVENTORY.get()) {
                    snapshot.remove("Inventory");
                    snapshot.remove("ArmorItems");
                    snapshot.remove("HandItems");
                }
                long ready = level().getServer().overworld().getGameTime() + ServerConfig.RESPAWN_COOLDOWN.get() * 20L;
                data.addRespawn(new WorkerSavedData.Respawn(owner, ready, snapshot));
            }
            ServerPlayer op = owner == null ? null : level().getServer().getPlayerList().getPlayer(owner);
            if (op != null) op.sendSystemMessage(getCombatTracker().getDeathMessage());
        }
        super.die(source);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        if (!ServerConfig.DROP_INVENTORY.get()) return;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.removeItemNoUpdate(i);
            if (!s.isEmpty()) spawnAtLocation(s);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack s = getItemBySlot(slot);
            if (!s.isEmpty()) {
                spawnAtLocation(s);
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    /** Called on a reloaded snapshot before it re-enters the world. */
    public void prepareRespawn() {
        dead = false;
        deathTime = 0;
        setHealth(getMaxHealth());
        clearFire();
        removeAllEffects();
        needs.set(10, 10);
        runner.stop(null);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // ---- persistence ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = getOwnerId();
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.put("Skills", skills.save());
        tag.putString("Skin", entityData.get(DATA_SKIN));
        tag.putBoolean("Slim", slim());
        tag.put("Needs", needs.save());
        tag.put("Runner", runner.save());
        tag.put("Inventory", inventory.createTag());
        if (home != null) tag.putLong("Home", home.asLong());
        if (supply != null) tag.putLong("Supply", supply.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_OWNER, tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        ownerName = tag.getString("OwnerName");
        skills.load(tag.getCompound("Skills"));
        skills.applyAttributes(this);
        if (tag.contains("Skin")) entityData.set(DATA_SKIN, tag.getString("Skin"));
        entityData.set(DATA_SLIM, tag.getBoolean("Slim"));
        needs.load(tag.getCompound("Needs"));
        runner.load(tag.getCompound("Runner"));
        inventory.fromTag(tag.getList("Inventory", 10));
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
        supply = tag.contains("Supply") ? BlockPos.of(tag.getLong("Supply")) : null;
        syncTask();
    }
}
