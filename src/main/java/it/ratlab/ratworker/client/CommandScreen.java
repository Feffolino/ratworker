package it.ratlab.ratworker.client;

import it.ratlab.ratworker.network.RWNetwork;
import it.ratlab.ratworker.network.WorkerScreenData;
import it.ratlab.ratworker.network.WorkerScreenData.TaskEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Compact command screen: unlocked tasks on the left, status and task detail on the right.
 * All content comes from the server (WorkerScreenData); buttons only send intents.
 */
public class CommandScreen extends Screen {
    private static final int W = 300, H = 184, HEADER = 16, ROW = 18, LIST_W = 124;

    private WorkerScreenData data;
    private String selected;
    private int scroll;
    private int left, top;
    private FlatButton start, stop, supply;
    private boolean skillsView;
    private int refreshTimer;

    public CommandScreen(WorkerScreenData data) {
        super(data.name());
        this.data = data;
        this.selected = !data.currentTask().isEmpty() ? data.currentTask() : data.tasks().isEmpty() ? "" : data.tasks().get(0).id();
    }

    public int entityId() {
        return data.entityId();
    }

    public void update(WorkerScreenData d) {
        data = d;
        if (d.tasks().stream().noneMatch(t -> t.id().equals(selected))) selected = d.tasks().isEmpty() ? "" : d.tasks().get(0).id();
        if (skillsView) rebuildWidgets(); // + buttons depend on XP and levels
        else updateButtons();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int bx = left + LIST_W + 14, by = top + H - 22, bw = 50;
        start = addRenderableWidget(new FlatButton(bx, by, bw, 16, Component.translatable("ratworker.ui.start"), () -> send(RWNetwork.Action.START, selected)));
        stop = addRenderableWidget(new FlatButton(bx + bw + 3, by, bw, 16, Component.translatable("ratworker.ui.stop"), () -> send(RWNetwork.Action.STOP, "")));
        supply = addRenderableWidget(new FlatButton(bx + 2 * (bw + 3), by, bw, 16, supplyLabel(), () -> {
            if (data.hasSupply()) {
                send(RWNetwork.Action.CLEAR_SUPPLY, "");
            } else {
                send(RWNetwork.Action.LINK_SUPPLY, "");
                onClose(); // next right click on a container links it
            }
        }));
        addRenderableWidget(new FlatButton(left + W - 58, top + 2, 52, 12,
                Component.translatable(skillsView ? "ratworker.ui.status_tab" : "ratworker.ui.skills_tab"), () -> {
                    skillsView = !skillsView;
                    rebuildWidgets();
                }));
        if (skillsView) {
            int x = left + LIST_W + 14, w = W - LIST_W - 20;
            for (NodeRow row : nodeRows()) {
                WorkerScreenData.NodeEntry n = row.node;
                FlatButton plus = addRenderableWidget(new FlatButton(x + w - 12, row.y - 1, 12, 10, Component.literal("+"),
                        () -> send(RWNetwork.Action.UPGRADE, n.id())));
                plus.active = n.level() < n.max() && data.xp() >= n.cost();
            }
        }
        updateButtons();
    }

    private record NodeRow(WorkerScreenData.NodeEntry node, int y) {}

    /** Worker-wide branches, then the selected task's branches (positions shared by render and buttons). */
    private List<NodeRow> nodeRows() {
        List<NodeRow> rows = new java.util.ArrayList<>();
        int y = top + HEADER + 18;
        for (WorkerScreenData.NodeEntry n : data.nodes()) {
            if (!n.task().isEmpty()) continue;
            rows.add(new NodeRow(n, y));
            y += 12;
        }
        y += 18; // task title line
        for (WorkerScreenData.NodeEntry n : data.nodes()) {
            if (!n.task().equals(selected)) continue;
            rows.add(new NodeRow(n, y));
            y += 12;
        }
        return rows;
    }

    private void updateButtons() {
        if (start == null) return;
        TaskEntry sel = selectedEntry();
        start.active = sel != null && sel.available() && !sel.id().equals(data.currentTask());
        stop.active = !data.currentTask().isEmpty();
        supply.setMessage(supplyLabel());
    }

    private Component supplyLabel() {
        return Component.translatable(data.hasSupply() ? "ratworker.ui.clear_supply" : "ratworker.ui.link_supply");
    }

    private TaskEntry selectedEntry() {
        for (TaskEntry t : data.tasks()) if (t.id().equals(selected)) return t;
        return null;
    }

    private void send(RWNetwork.Action action, String task) {
        RWNetwork.CHANNEL.sendToServer(new RWNetwork.ActionPacket(data.entityId(), action, task));
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.level == null) return;
        var e = minecraft.level.getEntity(data.entityId());
        if (e == null || !e.isAlive() || minecraft.player == null || minecraft.player.distanceToSqr(e) > 16 * 16) {
            onClose();
            return;
        }
        if (++refreshTimer >= 20) {
            refreshTimer = 0;
            send(RWNetwork.Action.REFRESH, "");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int visibleRows() {
        return (H - HEADER - 22) / ROW;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int lx = left + 6, ly = top + HEADER + 14;
        if (mx >= lx && mx < lx + LIST_W && my >= ly) {
            int row = (int) ((my - ly) / ROW) + scroll;
            if (row >= 0 && row < data.tasks().size() && row - scroll < visibleRows()) {
                selected = data.tasks().get(row).id();
                if (skillsView) rebuildWidgets();
                else updateButtons();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, data.tasks().size() - visibleRows()));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.fill(left, top, left + W, top + H, UiColors.BG);
        g.renderOutline(left, top, W, H, UiColors.LINE);
        g.fill(left + 1, top + 1, left + W - 1, top + HEADER, UiColors.HEADER);
        g.drawString(font, data.name(), left + 6, top + 4, UiColors.TEXT_BRIGHT, false);
        g.drawString(font, Component.translatable("ratworker.ui.xp", data.xp()), left + 10 + font.width(data.name()), top + 4, UiColors.accent(), false);
        Component owner = Component.translatable("ratworker.ui.owner", data.owner());
        g.drawString(font, owner, left + W - 64 - font.width(owner), top + 4, UiColors.TEXT_DIM, false);

        renderTaskList(g, mx, my);
        g.fill(left + LIST_W + 8, top + HEADER + 4, left + LIST_W + 9, top + H - 6, UiColors.LINE);
        if (skillsView) renderSkills(g);
        else renderStatus(g);
        super.render(g, mx, my, partial);
    }

    private void renderTaskList(GuiGraphics g, int mx, int my) {
        int x = left + 6, y = top + HEADER + 4;
        g.drawString(font, Component.translatable("ratworker.ui.tasks"), x, y, UiColors.TEXT_DIM, false);
        y += 10;
        List<TaskEntry> tasks = data.tasks();
        if (tasks.isEmpty()) {
            g.drawString(font, Component.translatable("ratworker.ui.no_tasks"), x, y + 4, UiColors.TEXT_DIM, false);
            return;
        }
        int rows = visibleRows();
        for (int i = scroll; i < Math.min(tasks.size(), scroll + rows); i++) {
            TaskEntry t = tasks.get(i);
            int ry = y + (i - scroll) * ROW;
            boolean hover = mx >= x && mx < x + LIST_W && my >= ry && my < ry + ROW;
            if (t.id().equals(selected)) g.fill(x, ry, x + LIST_W, ry + ROW, UiColors.ROW_SELECTED);
            else if (hover) g.fill(x, ry, x + LIST_W, ry + ROW, UiColors.ROW_HOVER);
            if (t.id().equals(data.currentTask())) g.fill(x, ry, x + 2, ry + ROW, UiColors.accent());
            g.renderItem(t.icon(), x + 4, ry + 1);
            int color = t.available() || t.id().equals(data.currentTask()) ? UiColors.TEXT : UiColors.TEXT_DIM;
            g.drawString(font, font.plainSubstrByWidth(t.title().getString(), LIST_W - 28), x + 24, ry + 5, color, false);
        }
    }

    private void renderSkills(GuiGraphics g) {
        int x = left + LIST_W + 14, y = top + HEADER + 4, w = W - LIST_W - 20;
        g.drawString(font, Component.translatable("ratworker.ui.worker_branches"), x, y, UiColors.TEXT_DIM, false);
        List<NodeRow> rows = nodeRows();
        boolean taskHeader = false;
        for (NodeRow row : rows) {
            WorkerScreenData.NodeEntry n = row.node();
            if (!n.task().isEmpty() && !taskHeader) {
                taskHeader = true;
                g.fill(x, row.y() - 16, x + w, row.y() - 15, UiColors.LINE);
                TaskEntry sel = selectedEntry();
                if (sel != null) g.drawString(font, sel.title(), x, row.y() - 11, UiColors.TEXT_BRIGHT, false);
            }
            g.drawString(font, Component.translatable("ratworker.branch." + n.branch()), x, row.y(), UiColors.TEXT, false);
            int px = x + 54;
            for (int i = 0; i < n.max(); i++) {
                g.fill(px + i * 7, row.y() + 1, px + i * 7 + 5, row.y() + 6, i < n.level() ? UiColors.accent() : UiColors.LINE);
            }
            String cost = n.level() >= n.max() ? Component.translatable("ratworker.ui.max").getString() : n.cost() + " XP";
            int color = n.level() >= n.max() || data.xp() >= n.cost() ? UiColors.TEXT_DIM : UiColors.warn();
            g.drawString(font, cost, x + w - 16 - font.width(cost), row.y(), color, false);
        }
        if (!taskHeader) {
            TaskEntry sel = selectedEntry();
            if (sel != null) g.drawString(font, Component.translatable("ratworker.ui.no_branches"), x, top + HEADER + 18 + 2 * 12 + 4, UiColors.TEXT_DIM, false);
        }
    }

    private void renderStatus(GuiGraphics g) {
        int x = left + LIST_W + 14, y = top + HEADER + 4, w = W - LIST_W - 20;

        g.drawString(font, Component.translatable("ratworker.ui.health"), x, y, UiColors.TEXT_DIM, false);
        NeedIcons.drawHealth(g, x + 40, y - 1, data.health(), data.maxHealth());
        y += 12;
        if (data.hungerOn()) {
            icons(g, x, y, "ratworker.ui.food", NeedIcons.Kind.FOOD, data.hunger());
            y += 12;
        }
        if (data.thirstOn()) {
            icons(g, x, y, "ratworker.ui.water", NeedIcons.Kind.WATER, data.thirst());
            y += 12;
        }
        y += 2;
        y = field(g, x, y, w, "ratworker.ui.current", data.currentTitle(), UiColors.TEXT_BRIGHT);
        if (!data.status().getString().isEmpty()) y = field(g, x, y, w, "ratworker.ui.status", data.status(), UiColors.TEXT);
        if (!data.lastStop().getString().isEmpty() && data.currentTask().isEmpty()) y = field(g, x, y, w, "ratworker.ui.last_stop", data.lastStop(), UiColors.warn());
        y = field(g, x, y, w, "ratworker.ui.supply", data.supply(), UiColors.TEXT);

        TaskEntry sel = selectedEntry();
        if (sel == null) return;
        y += 4;
        g.fill(x, y, x + w, y + 1, UiColors.LINE);
        y += 4;
        g.drawString(font, sel.title(), x, y, UiColors.TEXT_BRIGHT, false);
        y += 10;
        int maxY = top + H - 26;
        for (FormattedCharSequence line : font.split(sel.description(), w)) {
            if (y > maxY - 9) break;
            g.drawString(font, line, x, y, UiColors.TEXT_DIM, false);
            y += 9;
        }
        if (!sel.available() && !sel.reason().getString().isEmpty()) {
            for (FormattedCharSequence line : font.split(sel.reason(), w)) {
                if (y > maxY) break;
                g.drawString(font, line, x, y + 1, UiColors.warn(), false);
                y += 9;
            }
        }
    }

    private void icons(GuiGraphics g, int x, int y, String key, NeedIcons.Kind kind, float value) {
        g.drawString(font, Component.translatable(key), x, y, UiColors.TEXT_DIM, false);
        NeedIcons.draw(g, kind, x + 40, y - 1, value);
    }

    private int field(GuiGraphics g, int x, int y, int w, String key, Component value, int color) {
        Component label = Component.translatable(key);
        g.drawString(font, label, x, y, UiColors.TEXT_DIM, false);
        int vx = x + 40;
        g.drawString(font, font.substrByWidth(value, w - 40).getString(), vx, y, color, false);
        return y + 10;
    }
}
