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

/**
 * 歌名显示：开关、字号、以屏幕中心为原点的对齐/自定义位置、彩虹色和动态音符图标。
 *
 * <p>对齐方式为“自定义位置”时才展开 X/Y 两个滑块；图标尺寸等细节收进独立的三级页面，
 * 避免主页面被低频选项塞满。
 */
public class CineLyricTitleScreen extends CineLyricScreen {

    private static final int PANEL_WIDTH = CineLyricConfigScreen.PANEL_WIDTH;
    private static final int ROW = 22;

    private final Screen parent;
    private int panelHeight;

    public CineLyricTitleScreen(Screen parent) {
        super(Component.literal("CineLyric 歌名显示"));
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

        Button enabled = Button.builder(enabledLabel(), button -> {
            CineLyricConfig.showSongTitle = !CineLyricConfig.showSongTitle;
            button.setMessage(enabledLabel());
            rebuildWidgets();
        }).bounds(left, top + ROW * row++, width, 20).build();
        enabled.setTooltip(Tooltip.create(Component.literal("关闭后整条歌名都不渲染")));
        addRenderableWidget(enabled);

        CineLyricConfigScreen.IntSlider size = new CineLyricConfigScreen.IntSlider(left,
                top + ROW * row++, width, "歌名字号", CineLyricConfig.TITLE_FONT_MIN,
                CineLyricConfig.TITLE_FONT_MAX, CineLyricConfig.songTitleSize,
                value -> CineLyricConfig.songTitleSize = value);
        size.setTooltip(Tooltip.create(Component.literal("歌名独立字号，8–32")));
        addRenderableWidget(size);

        CineLyricConfigScreen.IntSlider spacing = new CineLyricConfigScreen.IntSlider(left,
                top + ROW * row++, width, "歌名字间距", 0, 10,
                CineLyricConfig.songTitleLetterSpacing,
                value -> CineLyricConfig.songTitleLetterSpacing = value);
        spacing.setTooltip(Tooltip.create(Component.literal(
                "仅作用于歌名字符之间，0–10 像素；不影响前缀音符")));
        addRenderableWidget(spacing);

        Button align = Button.builder(alignLabel(), button -> {
            cycleAlign();
            rebuildWidgets();
        }).bounds(left, top + ROW * row++, width, 20).build();
        align.setTooltip(Tooltip.create(Component.literal(
                "左/中/右对齐控制歌名排版；自定义位置额外展开 X/Y 偏移")));
        addRenderableWidget(align);

        boolean custom = "custom".equals(CineLyricConfig.normalizeAlign(CineLyricConfig.songTitleAlign));
        if (custom) {
            CineLyricConfigScreen.IntSlider x = new CineLyricConfigScreen.IntSlider(left,
                    top + ROW * row++, width, "水平位置", -400, 400, CineLyricConfig.songTitleX,
                    value -> CineLyricConfig.songTitleX = value);
            x.setTooltip(Tooltip.create(Component.literal(
                    "相对屏幕中心的水平像素偏移，正数向右")));
            addRenderableWidget(x);

            CineLyricConfigScreen.IntSlider y = new CineLyricConfigScreen.IntSlider(left,
                    top + ROW * row++, width, "垂直位置", -200, 200, CineLyricConfig.songTitleY,
                    value -> CineLyricConfig.songTitleY = value);
            y.setTooltip(Tooltip.create(Component.literal(
                    "相对屏幕中心的垂直像素偏移，正数向下")));
            addRenderableWidget(y);
        }

        Button colorful = Button.builder(colorLabel(), button -> {
            CineLyricConfig.songTitleColorful = !CineLyricConfig.songTitleColorful;
            button.setMessage(colorLabel());
        }).bounds(left, top + ROW * row++, width, 20).build();
        colorful.setTooltip(Tooltip.create(Component.literal("开启后为彩虹流光；关闭为纯白 #FFFFFF")));
        addRenderableWidget(colorful);

        Button iconDetails = Button.builder(Component.literal("音符图标详细设置…"), button -> {
            if (minecraft != null) {
                CineLyricConfig.save();
                minecraft.setScreen(new CineLyricIconScreen(this));
            }
        }).bounds(left, top + ROW * row++, width, 20).build();
        iconDetails.setTooltip(Tooltip.create(Component.literal(
                "音符开关、颜色、跳动幅度、大小、间距和垂直微调")));
        addRenderableWidget(iconDetails);

        addRenderableWidget(Button.builder(Component.literal("返回"), button -> onClose())
                .bounds(left, top + ROW * row++, width, 20).build());

        panelHeight = top + ROW * row + 8;
    }

    private static void cycleAlign() {
        CineLyricConfig.songTitleAlign = switch (CineLyricConfig.normalizeAlign(
                CineLyricConfig.songTitleAlign)) {
            case "left" -> "center";
            case "center" -> "right";
            case "right" -> "custom";
            default -> "left";
        };
    }

    private static Component enabledLabel() {
        return Component.literal("启用歌名显示：" + (CineLyricConfig.showSongTitle ? "开" : "关"));
    }

    private static Component alignLabel() {
        String value = switch (CineLyricConfig.normalizeAlign(CineLyricConfig.songTitleAlign)) {
            case "left" -> "左对齐";
            case "right" -> "右对齐";
            case "custom" -> "自定义位置";
            default -> "居中对齐";
        };
        return Component.literal("对齐方式：" + value);
    }

    private static Component colorLabel() {
        return Component.literal("彩色字体：" + (CineLyricConfig.songTitleColorful ? "开（彩虹流光）" : "关（白色）"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        beginFrame();
        // In custom-position mode the dark screen backdrop would hide the very title the user is
        // placing. Remove the dim layer while that mode is active; leaving custom restores it.
        if ("custom".equals(CineLyricConfig.normalizeAlign(CineLyricConfig.songTitleAlign))) {
            suppressBackdrop();
        } else {
            renderBackground(graphics, mouseX, mouseY, partialTick);
        }
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
