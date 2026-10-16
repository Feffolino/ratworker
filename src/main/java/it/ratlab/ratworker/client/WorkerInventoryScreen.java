package it.ratlab.ratworker.client;

import it.ratlab.ratworker.entity.WorkerEntity;
import it.ratlab.ratworker.menu.WorkerInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Flat, texture-free inventory screen matching the command screen palette. */
public class WorkerInventoryScreen extends AbstractContainerScreen<WorkerInventoryMenu> {
    public WorkerInventoryScreen(WorkerInventoryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 224;
        titleLabelY = 6;
        inventoryLabelY = WorkerInventoryMenu.PLAYER_Y - 10;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, UiColors.BG);
        g.renderOutline(x, y, imageWidth, imageHeight, UiColors.LINE);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 16, UiColors.HEADER);
        for (Slot s : menu.slots) {
            g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, UiColors.LINE);
            g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, UiColors.SLOT);
        }
        // classic HUD rows under the equipment: hearts and food, water below food
        int flags = menu.flags();
        int ny = y + WorkerInventoryMenu.NEEDS_Y, right = x + imageWidth - 7 - NeedIcons.ROW_WIDTH;
        NeedIcons.drawHealth(g, x + 7, ny, menu.health(), menu.maxHealth());
        if ((flags & WorkerEntity.FLAG_HUNGER) != 0) NeedIcons.draw(g, NeedIcons.Kind.FOOD, right, ny, menu.hunger());
        if ((flags & WorkerEntity.FLAG_THIRST) != 0) NeedIcons.draw(g, NeedIcons.Kind.WATER, right, ny + 11, menu.thirst());
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, titleLabelX, titleLabelY, UiColors.TEXT_BRIGHT, false);
        g.drawString(font, Component.translatable("ratworker.ui.storage"), 8, WorkerInventoryMenu.STORE_Y - 10, UiColors.TEXT_DIM, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiColors.TEXT_DIM, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }
}
