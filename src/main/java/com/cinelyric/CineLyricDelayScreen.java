/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Second-level screen for the experimental "delay" option: keep the subtitle hidden for a
 * while after a cinematic starts, then show it. This page has its own replay button so the
 * delay can be checked without starting a cinematic.
 */
public class CineLyricDelayScreen extends CineLyricScreen {

    private static final int PANEL_WIDTH = 236;
    private static final int PANEL_TOP = 4;
    private static final int PANEL_BOTTOM = 144;

    private final CineLyricConfigScreen parent;

    public CineLyricDelayScreen(CineLyricConfigScreen parent) {
        super(Component.literal("CineLyric 延迟显示"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        suppressBlur();
        CineLyricOverlay.beginPreview();
        int left = 12;
        int width = PANEL_WIDTH - 24;

        addRenderableWidget(new DelaySlider(left, 34, width));

        addRenderableWidget(Button.builder(Component.literal("预览延迟"), button -> {
            CineLyricOverlay.restartPreview();
        }).bounds(left, 60, width, 20).build());

        addRenderableWidget(Button.builder(Component.literal("关闭延迟"), button -> {
            CineLyricConfig.delayMillis = 0;
            rebuildWidgets();
        }).bounds(left, 86, width, 20).build());

        addRenderableWidget(Button.builder(Component.literal("返回"), button -> onClose())
                .bounds(left, 112, width, 20).build());
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
        graphics.drawCenteredString(font, "CineLyric 延迟显示（实验性）", PANEL_WIDTH / 2, 14, 0xFFFFFF);

        int x = 12;
        int y = PANEL_BOTTOM + 6;
        graphics.drawString(font, "进镜头后先不显示字幕，过了设定时间才出现", x, y, 0xE8E8E8, true);
        graphics.drawString(font, "点「预览延迟」可以在这里看效果，不用真开镜头", x, y + 11, 0xB8B8B8, true);
        if (CineLyricConfig.delayMillis <= 0) {
            graphics.drawString(font, "当前：关闭（字幕立即出现）", x, y + 22, 0xFF6060, false);
        } else if (!CineLyricOverlay.delayElapsed()) {
            graphics.drawString(font, "延迟中…", x, y + 22, 0xFFD060, false);
        }
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

    /** 0 = off, otherwise 0.5 s .. 60 s in half-second steps. */
    private static final class DelaySlider extends AbstractSliderButton {

        DelaySlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(), toValue(CineLyricConfig.delayMillis));
            updateMessage();
        }

        private static double toValue(int millis) {
            if (millis <= 0) {
                return 0.0;
            }
            return Math.max(0.01, Math.min(1.0, (millis - 500) / 59500.0));
        }

        private static int toMillis(double value) {
            if (value < 0.01) {
                return 0;
            }
            int millis = 500 + (int) Math.round(value * 59500.0 / 500.0) * 500;
            return Math.max(500, Math.min(60000, millis));
        }

        @Override
        protected void updateMessage() {
            int millis = toMillis(value);
            setMessage(Component.literal(millis <= 0
                    ? "延迟：关闭"
                    : String.format("延迟：%.1f 秒", millis / 1000.0F)));
        }

        @Override
        protected void applyValue() {
            CineLyricConfig.delayMillis = toMillis(value);
            CineLyricOverlay.restartPreview();
            updateMessage();
        }
    }
}
