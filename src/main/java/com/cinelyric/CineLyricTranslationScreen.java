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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 译文设置：显示开关、独立字号、中文自动跳过、上下位置和像素微调。
 */
public class CineLyricTranslationScreen extends CineLyricScreen {

    private static final int PANEL_WIDTH = 236;
    private static final int PANEL_TOP = 4;
    private static final int PANEL_BOTTOM = 196;

    private final CineLyricConfigScreen parent;

    public CineLyricTranslationScreen(CineLyricConfigScreen parent) {
        super(Component.literal("CineLyric 译文设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        suppressBlur();
        CineLyricOverlay.beginPreview();
        int left = 12;
        int width = PANEL_WIDTH - 24;

        Button show = Button.builder(showLabel(), button -> {
            CineLyricConfig.showTranslation = !CineLyricConfig.showTranslation;
            button.setMessage(showLabel());
        }).bounds(left, 34, width, 20).build();
        show.setTooltip(Tooltip.create(Component.literal("关闭后不渲染译文行")));
        addRenderableWidget(show);

        CineLyricConfigScreen.IntSlider size = new CineLyricConfigScreen.IntSlider(left, 60,
                width, "译文字号", 0, CineLyricConfig.TITLE_FONT_MAX,
                CineLyricConfig.translationFontSize,
                value -> CineLyricConfig.translationFontSize = value,
                "跟随主句", value -> value == 0 ? 0 : Math.max(8, value));
        size.setTooltip(Tooltip.create(Component.literal(
                "译文独立字号 8–32；0 = 完全跟随主句字号")));
        addRenderableWidget(size);

        Button skip = Button.builder(skipLabel(), button -> {
            CineLyricConfig.skipChineseTranslation = !CineLyricConfig.skipChineseTranslation;
            button.setMessage(skipLabel());
        }).bounds(left, 86, width, 20).build();
        skip.setTooltip(Tooltip.create(Component.literal(
                "原文包含中文时跳过译文，只显示主句，避免中文串到译文处")));
        addRenderableWidget(skip);

        Button sideButton = Button.builder(sideLabel(), button -> {
            cycleSide();
            button.setMessage(sideLabel());
        }).bounds(left, 112, width, 20).build();
        sideButton.setTooltip(Tooltip.create(Component.literal("译文放在原文上方或下方")));
        addRenderableWidget(sideButton);

        OffsetSlider slider = new OffsetSlider(left, 138, width);
        slider.active = CineLyricConfig.showTranslation;
        slider.setTooltip(Tooltip.create(Component.literal("负数往上，正数往下")));
        addRenderableWidget(slider);

        addRenderableWidget(Button.builder(Component.literal("返回"), button -> onClose())
                .bounds(left, 164, width, 20).build());
    }

    private static Component showLabel() {
        return Component.literal("显示译文：" + (CineLyricConfig.showTranslation ? "开" : "关"));
    }

    private static Component skipLabel() {
        return Component.literal("自动跳过中文翻译："
                + (CineLyricConfig.skipChineseTranslation ? "开" : "关"));
    }

    private static Component sideLabel() {
        String side = CineLyricConfig.translationSide;
        String text = "below".equals(side) ? "原文在上（译文在下）" : "译文在上（原文在下）";
        return Component.literal("译文位置：" + text);
    }

    /** below (default) -> above -> below */
    private static void cycleSide() {
        CineLyricConfig.translationSide = "below".equals(CineLyricConfig.translationSide) ? "above" : "below";
        Diag.note("[ui] translation side -> " + CineLyricConfig.translationSide);
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
        graphics.drawCenteredString(font, "CineLyric 译文设置", PANEL_WIDTH / 2, 14, 0xFFFFFF);

        int x = 12;
        int y = PANEL_BOTTOM + 6;
        graphics.drawString(font, "原文已是中文时只显示主句，不显示译文", x, y, 0xE8E8E8, true);
        graphics.drawString(font, "译文字号 0 = 跟随主句；8–32 = 独立字号", x, y + 11, 0xB8B8B8, true);
        if (!CineLyricConfig.showTranslation) {
            graphics.drawString(font, "译文已关闭，位置和微调不生效", x, y + 22, 0xFF6060, false);
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

    private static final class OffsetSlider extends AbstractSliderButton {
        OffsetSlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(),
                    (CineLyricConfig.translationOffset + 12) / 36.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int offset = current();
            setMessage(Component.literal("译文微调：" + (offset > 0 ? "+" : "") + offset + " px"));
        }

        @Override
        protected void applyValue() {
            CineLyricConfig.translationOffset = current();
            updateMessage();
        }

        private int current() {
            return (int) Math.round(-12 + value * 36);
        }
    }
}
