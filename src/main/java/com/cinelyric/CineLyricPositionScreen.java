/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Second-level screen: adaptive/manual placement, the bar side, and fine offsets. */
public class CineLyricPositionScreen extends CineLyricScreen {

    private static final int PANEL_WIDTH = 236;
    private static final int PANEL_TOP = 4;
    private static final int PANEL_BOTTOM = 168;

    private final CineLyricConfigScreen parent;
    private VerticalSlider verticalSlider;
    private VerticalSlider horizontalSlider;

    public CineLyricPositionScreen(CineLyricConfigScreen parent) {
        super(Component.literal("CineLyric 位置调整"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        suppressBlur();
        CineLyricOverlay.beginPreview();
        int left = 12;
        int width = PANEL_WIDTH - 24;

        addRenderableWidget(Button.builder(modeLabel(), button -> {
            CineLyricConfig.autoLayout = !CineLyricConfig.autoLayout;
            button.setMessage(modeLabel());
            verticalSlider.active = !CineLyricConfig.autoLayout && CineLyricConfig.showTranslation;
            horizontalSlider.active = !CineLyricConfig.autoLayout;
        }).bounds(left, 32, width, 20).build());

        verticalSlider = new VerticalSlider(left, 58, width, "垂直位置", CineLyricConfig.yPercent, true);
        verticalSlider.active = !CineLyricConfig.autoLayout;
        addRenderableWidget(verticalSlider);

        horizontalSlider = new VerticalSlider(left, 84, width, "水平偏移", CineLyricConfig.xPercent, false);
        horizontalSlider.active = !CineLyricConfig.autoLayout;
        addRenderableWidget(horizontalSlider);

        addRenderableWidget(Button.builder(sideLabel(), button -> {
            CineLyricConfig.barSide = CineLyricOverlay.isTopBar() ? "bottom" : "top";
            button.setMessage(sideLabel());
            Diag.note("[ui] bar side -> " + CineLyricConfig.barSide);
        }).bounds(left, 112, width, 20).build());

        addRenderableWidget(Button.builder(Component.literal("返回"), button -> onClose())
                .bounds(left, 140, (width - 8) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("复位"), button -> {
            CineLyricConfig.yPercent = 4.0;
            CineLyricConfig.xPercent = 0.0;
            CineLyricConfig.autoLayout = true;
            rebuildWidgets();
        }).bounds(left + (width - 8) / 2 + 8, 140, (width - 8) / 2, 20).build());
    }

    private static Component modeLabel() {
        return Component.literal("位置模式："
                + (CineLyricConfig.autoLayout ? "自适应（自动居中）" : "手动"));
    }

    private static Component sideLabel() {
        return Component.literal("字幕黑边：" + (CineLyricOverlay.isTopBar() ? "上边" : "下边")
                + "");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        beginFrame();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        paintCinematicBars(graphics);
        CineLyricOverlay.drawLines(graphics, CineLyricOverlay.SAMPLE_MAIN,
                CineLyricOverlay.SAMPLE_TRANSLATION, CineLyricOverlay.previewAlpha());

        graphics.fill(4, PANEL_TOP, PANEL_WIDTH, PANEL_BOTTOM, 0xD0000000);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "CineLyric 位置调整", PANEL_WIDTH / 2, 14, 0xFFFFFF);

        // Hints sit just left of the crosshair: the subtitle can move anywhere, so the
        // corners are the one place a hint can end up covering it.
        String line = "字幕只能落在 " + Math.round(CineLyricConfig.BAR_RATIO * 100) + "% 高的黑边内";
        String line2 = CineLyricConfig.autoLayout
                ? "自适应模式：整块自动居中，字号变大也不会出界"
                : "手动模式：滑块调整，超出黑边会被自动夹回";
        int right = width / 2 - 12;
        graphics.drawString(font, line, right - font.width(line), height / 2 - 22, 0xC0C0C0, false);
        graphics.drawString(font, line2, right - font.width(line2), height / 2 - 10, 0x909090, false);
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

    /** One slider class for both axes; the value range depends on the axis. */
    private final class VerticalSlider extends CineLyricConfigScreen.DoubleSlider {
        private final boolean vertical;

        VerticalSlider(int x, int y, int width, String label, double initial, boolean vertical) {
            super(x, y, width, label,
                    vertical ? 0.0 : -50.0,
                    vertical ? CineLyricConfig.BAR_RATIO * 100.0 : 50.0,
                    Math.max(vertical ? 0.0 : -50.0,
                            Math.min(initial, vertical ? CineLyricConfig.BAR_RATIO * 100.0 : 50.0)),
                    value -> {
                        if (vertical) {
                            CineLyricConfig.yPercent = value;
                        } else {
                            CineLyricConfig.xPercent = value;
                        }
                    },
                    "%.1f%%");
            this.vertical = vertical;
            updateMessage();
        }

        @Override
        protected String detail() {
            // NOTE: "height"/"width" would resolve to the widget's own fields (20 / slider width),
            // so measure against the screen explicitly.
            int screenHeight = CineLyricPositionScreen.this.height;
            int screenWidth = CineLyricPositionScreen.this.width;
            int pixels = (int) Math.round((vertical ? screenHeight : screenWidth) * Math.abs(current()) / 100.0);
            if (vertical) {
                return "（距" + (CineLyricOverlay.isTopBar() ? "顶边 " : "底边 ") + pixels + " px）";
            }
            return "（" + (current() >= 0 ? "右移 " : "左移 ") + pixels + " px）";
        }
    }
}
