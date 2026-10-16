package it.ratlab.ratworker.menu;

import com.mojang.datafixers.util.Pair;
import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.registry.ModMenus;
import it.ratlab.ratworker.task.WorkerAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Worker inventory: 6 equipment slots, 27 storage slots, player inventory.
 * Slot positions match WorkerInventoryScreen.
 */
public class WorkerInventoryMenu extends AbstractContainerMenu {
    public static final int EQUIP_Y = 20, NEEDS_Y = 40, STORE_Y = 72, PLAYER_Y = 142, HOTBAR_Y = 200;
    private static final int EQUIP = 6, STORE = 27;

    @Nullable private final WorkerEntity worker;
    private final ContainerData data;

    public WorkerInventoryMenu(int id, Inventory playerInv, WorkerEntity worker) {
        this(id, playerInv, worker, worker.inventory(), new EquipmentContainer(worker), new NeedsData(worker));
    }

    public static WorkerInventoryMenu fromNetwork(int id, Inventory playerInv, FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        // client side: data slots must be writable, slot contents arrive through menu sync
        if (playerInv.player.level().getEntity(entityId) instanceof WorkerEntity w)
            return new WorkerInventoryMenu(id, playerInv, w, w.inventory(), new EquipmentContainer(w), new SimpleContainerData(5));
        return new WorkerInventoryMenu(id, playerInv, null, new SimpleContainer(STORE), new SimpleContainer(EQUIP), new SimpleContainerData(5));
    }

    private WorkerInventoryMenu(int id, Inventory playerInv, @Nullable WorkerEntity worker, Container store, Container equip, ContainerData data) {
        super(ModMenus.WORKER_INVENTORY.get(), id);
        this.worker = worker;
        this.data = data;

        for (int i = 0; i < EQUIP; i++) {
            EquipmentSlot type = EquipmentContainer.SLOTS[i];
            addSlot(new EquipSlot(equip, i, 8 + i * 18, EQUIP_Y, type, worker));
        }
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(store, c + r * 9, 8 + c * 18, STORE_Y + r * 18));
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, PLAYER_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInv, c, 8 + c * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    @Nullable
    public WorkerEntity worker() {
        return worker;
    }

    public float hunger() {
        return data.get(0) / 10f;
    }

    public float thirst() {
        return data.get(1) / 10f;
    }

    public float health() {
        return data.get(3) / 10f;
    }

    public float maxHealth() {
        return Math.max(1, data.get(4)) / 10f;
    }

    public int flags() {
        return data.get(2);
    }

    @Override
    public boolean stillValid(Player player) {
        return worker != null && worker.isAlive() && player.distanceToSqr(worker) < 64 && WorkerAccess.canCommand(player, worker);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int workerEnd = EQUIP + STORE;
        if (index < workerEnd) {
            if (!moveItemStackTo(stack, workerEnd, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, EQUIP, workerEnd, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    private static class EquipSlot extends Slot {
        private static final ResourceLocation[] ICONS = {
                InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
                InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS, null, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD};
        private final EquipmentSlot type;
        @Nullable private final WorkerEntity worker;

        EquipSlot(Container c, int i, int x, int y, EquipmentSlot type, @Nullable WorkerEntity worker) {
            super(c, i, x, y);
            this.type = type;
            this.worker = worker;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (type.getType() != EquipmentSlot.Type.ARMOR) return true;
            return worker != null && stack.canEquip(type, worker);
        }

        @Override
        public int getMaxStackSize() {
            return type.getType() == EquipmentSlot.Type.ARMOR ? 1 : super.getMaxStackSize();
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            ResourceLocation icon = ICONS[getContainerSlot()];
            return icon == null ? null : Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
        }
    }

    /** Server side: exposes needs (x10) and flags to the open screen. */
    private record NeedsData(WorkerEntity w) implements ContainerData {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> Math.round(w.needs().hunger() * 10);
                case 1 -> Math.round(w.needs().thirst() * 10);
                case 3 -> Math.round(w.getHealth() * 10);
                case 4 -> Math.round(w.getMaxHealth() * 10);
                default -> w.syncedFlags();
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 5;
        }
    }
}
