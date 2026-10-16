package it.ratlab.ratworker.menu;

import it.ratlab.ratworker.entity.WorkerEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The worker's armor and hand slots seen as a 6-slot container (head, chest, legs, feet, main hand, off hand). */
public class EquipmentContainer implements Container {
    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private final WorkerEntity worker;

    public EquipmentContainer(WorkerEntity worker) {
        this.worker = worker;
    }

    @Override
    public int getContainerSize() {
        return SLOTS.length;
    }

    @Override
    public boolean isEmpty() {
        for (EquipmentSlot s : SLOTS) if (!worker.getItemBySlot(s).isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int i) {
        return worker.getItemBySlot(SLOTS[i]);
    }

    @Override
    public ItemStack removeItem(int i, int count) {
        ItemStack s = getItem(i);
        if (s.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = s.split(count);
        worker.setItemSlot(SLOTS[i], s.isEmpty() ? ItemStack.EMPTY : s);
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        ItemStack s = getItem(i);
        worker.setItemSlot(SLOTS[i], ItemStack.EMPTY);
        return s;
    }

    @Override
    public void setItem(int i, ItemStack stack) {
        worker.setItemSlot(SLOTS[i], stack);
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) {
        return worker.isAlive() && player.distanceToSqr(worker) < 64;
    }

    @Override
    public void clearContent() {
        for (EquipmentSlot s : SLOTS) worker.setItemSlot(s, ItemStack.EMPTY);
    }
}
