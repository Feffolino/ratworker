package it.ratlab.ratworker.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Flat terminal-style button: thin outline, no texture. */
public class FlatButton extends AbstractButton {
    private final Runnable action;

    public FlatButton(int x, int y, int w, int h, Component label, Runnable action) {
        super(x, y, w, h, label);
        this.action = action;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int line = !active ? UiColors.LINE : isHoveredOrFocused() ? UiColors.accent() : UiColors.TEXT_DIM;
        g.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() && active ? UiColors.ROW_HOVER : UiColors.PANEL);
        g.renderOutline(getX(), getY(), width, height, line);
        int color = active ? UiColors.TEXT_BRIGHT : UiColors.TEXT_DIM;
        var font = Minecraft.getInstance().font;
        g.drawString(font, getMessage(), getX() + (width - font.width(getMessage())) / 2, getY() + (height - 8) / 2, color, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
