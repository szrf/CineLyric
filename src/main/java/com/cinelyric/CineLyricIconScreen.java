/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 三级页面：音符图标的开关、颜色、跳动以及尺寸/间距/垂直微调。 */
public class CineLyricIconScreen extends CineLyricScreen {

    private static final int PANEL_WIDTH = 236;
    private static final int ROW = 22;

    private final Screen parent;
    private int panelHeight;

    public CineLyricIconScreen(Screen parent) {
        super(Component.literal("CineLyric 音符图标详细设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        suppressBlur();
        CineLyricOverlay.beginPreview();
        int left = 8;
        int top = 32;
        int width = PANEL_WIDTH - 16;
        int row = 0;

        Button enabled = Button.builder(noteLabel(), button -> {
            CineLyricConfig.songTitleIcon = "note".equals(
                    CineLyricConfig.normalizeIcon(CineLyricConfig.songTitleIcon)) ? "none" : "note";
            button.setMessage(noteLabel());
        }).bounds(left, top + ROW * row++, width, 20).build();
        enabled.setTooltip(Tooltip.create(Component.literal("关闭后歌名前面不显示任何图标")));
        addRenderableWidget(enabled);

        Button color = Button.builder(colorLabel(), button -> {
            CineLyricConfig.songTitleNoteColor = !CineLyricConfig.songTitleNoteColor;
            button.setMessage(colorLabel());
        }).bounds(left, top + ROW * row++, width, 20).build();
        color.setTooltip(Tooltip.create(Component.literal(
                "开启：音符色相随时间流动；关闭：固定白色单色")));
        addRenderableWidget(color);

        Button bounce = Button.builder(bounceLabel(), button -> {
            CineLyricConfig.songTitleNoteBounce = !CineLyricConfig.songTitleNoteBounce;
            button.setMessage(bounceLabel());
            rebuildWidgets();
        }).bounds(left, top + ROW * row++, width, 20).build();
        bounce.setTooltip(Tooltip.create(Component.literal("控制音符是否随节奏上下跳动")));
        addRenderableWidget(bounce);

        CineLyricConfigScreen.IntSlider amplitude = new CineLyricConfigScreen.IntSlider(left,
                top + ROW * row++, width, "跳动幅度", 0, 8,
                CineLyricConfig.songTitleNoteBounceAmount,
                value -> CineLyricConfig.songTitleNoteBounceAmount = value, "静止");
        amplitude.active = CineLyricConfig.songTitleNoteBounce;
        amplitude.setTooltip(Tooltip.create(Component.literal(
                "上下跳动的最大像素；0 表示不跳动")));
        addRenderableWidget(amplitude);

        CineLyricConfigScreen.IntSlider widthSlider = new CineLyricConfigScreen.IntSlider(left,
                top + ROW * row++, width, "图标宽度", 0, 128,
                CineLyricConfig.songTitleIconWidth,
                value -> CineLyricConfig.songTitleIconWidth = value, "自动");
        widthSlider.setTooltip(Tooltip.create(Component.literal(
                "像素宽度；0 = 与歌名当前行高一致")));
        addRenderableWidget(widthSlider);

        CineLyricConfigScreen.IntSlider heightSlider = new CineLyricConfigScreen.IntSlider(left,
                top + ROW * row++, width, "图标高度", 0, 128,
                CineLyricConfig.songTitleIconHeight,
                value -> CineLyricConfig.songTitleIconHeight = value, "自动");
        heightSlider.setTooltip(Tooltip.create(Component.literal(
                "像素高度；0 = 与歌名当前行高一致")));
        addRenderableWidget(heightSlider);

        CineLyricConfigScreen.IntSlider gap = new CineLyricConfigScreen.IntSlider(left,
                top + ROW * row++, width, "图标间距", 0, 64,
                CineLyricConfig.songTitleIconGap,
                value -> CineLyricConfig.songTitleIconGap = value);
        gap.setTooltip(Tooltip.create(Component.literal("音符与歌名之间的水平像素间距")));
        addRenderableWidget(gap);

        CineLyricConfigScreen.IntSlider offset = new CineLyricConfigScreen.IntSlider(left,
                top + ROW * row++, width, "垂直微调", -64, 64,
                CineLyricConfig.songTitleIconOffsetY,
                value -> CineLyricConfig.songTitleIconOffsetY = value);
        offset.setTooltip(Tooltip.create(Component.literal("音符相对歌名垂直中心的像素偏移")));
        addRenderableWidget(offset);

        addRenderableWidget(Button.builder(Component.literal("返回"), button -> onClose())
                .bounds(left, top + ROW * row++, width, 20).build());

        panelHeight = top + ROW * row + 8;
    }

    private static Component noteLabel() {
        return Component.literal("显示音符图标："
                + ("note".equals(CineLyricConfig.normalizeIcon(CineLyricConfig.songTitleIcon))
                        ? "开" : "关"));
    }

    private static Component colorLabel() {
        return Component.literal("音符彩色："
                + (CineLyricConfig.songTitleNoteColor ? "开（动态色相）" : "关（白色）"));
    }

    private static Component bounceLabel() {
        return Component.literal("音符跳动："
                + (CineLyricConfig.songTitleNoteBounce ? "开" : "关（静止）"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        beginFrame();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        paintCinematicBars(graphics);
        CineLyricOverlay.drawLines(graphics, CineLyricOverlay.SAMPLE_MAIN,
                CineLyricOverlay.SAMPLE_TRANSLATION, 1.0F);
        SongTitleRenderer.drawPreview(graphics);
        graphics.fill(4, 4, PANEL_WIDTH, panelHeight, 0xD0000000);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, PANEL_WIDTH / 2, 14, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        CineLyricConfig.save();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
