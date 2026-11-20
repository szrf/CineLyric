/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Second-level screen: pick which file in {@code config/cinelyric/fonts/} is the primary
 * font and which one is the fallback (secondary) font. Every file stays reachable from
 * both rows: picking one side never hides the other side's choice from the cycle.
 */
public class CineLyricFontScreen extends CineLyricScreen {

    private static final int PANEL_WIDTH = 236;
    private static final int PANEL_TOP = 4;
    private static final int PANEL_BOTTOM = 168;

    private final CineLyricConfigScreen parent;

    public CineLyricFontScreen(CineLyricConfigScreen parent) {
        super(Component.literal("CineLyric 字体包"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        suppressBlur();
        CineLyricOverlay.beginPreview();
        // Restart the preview fade on every (re)build, so picking a font shows it fading in.
        FontLibrary.reload();
        CineLyricConfig.ensureFontSelection();

        int left = 12;
        int width = PANEL_WIDTH - 24;
        addRenderableWidget(Button.builder(Component.literal("重新扫描字体文件夹"), button -> {
            FontLibrary.reload();
            CineLyricConfig.ensureFontSelection();
            TextTexture.clear();
            rebuildWidgets();
        }).bounds(left, 28, width, 20).build());

        addCycleRow(left, 56, width, true);
        addCycleRow(left, 82, width, false);

        addRenderableWidget(Button.builder(Component.literal("打开字体文件夹"), button ->
                CineLyricConfigScreen.openFontsFolder()).bounds(left, 110, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), button -> onClose())
                .bounds(left, 136, width, 20).build());
    }

    /** One row: "◀ [label] ▶"; the middle button also steps forward. */
    private void addCycleRow(int x, int y, int width, boolean primary) {
        addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
            shift(primary, -1);
            rebuildWidgets();
        }).bounds(x, y, 20, 20).build());

        String text = primary
                ? "主字体：" + FontLibrary.label(CineLyricConfig.fontPrimary)
                : "副字体：" + FontLibrary.label(CineLyricConfig.fontSecondary);
        addRenderableWidget(Button.builder(Component.literal(text), button -> {
            shift(primary, 1);
            rebuildWidgets();
        }).bounds(x + 22, y, width - 44, 20).build());

        addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
            shift(primary, 1);
            rebuildWidgets();
        }).bounds(x + width - 20, y, 20, 20).build());
    }

    /**
     * Steps one row through <em>every</em> font in the folder plus a trailing "无" slot.
     * Both rows may be "无"; the only thing skipped is picking the same real file twice.
     */
    private static void shift(boolean primary, int step) {
        List<String> names = FontLibrary.names();
        if (names.isEmpty()) {
            return;
        }
        java.util.List<String> slots = new java.util.ArrayList<>(names);
        slots.add(FontLibrary.NONE);
        String other = primary ? CineLyricConfig.fontSecondary : CineLyricConfig.fontPrimary;
        String current = primary ? CineLyricConfig.fontPrimary : CineLyricConfig.fontSecondary;
        int index = slots.indexOf(current == null ? "" : current);
        if (index < 0) {
            index = 0;
        }
        for (int attempt = 1; attempt <= slots.size(); attempt++) {
            String value = slots.get(Math.floorMod(index + step * attempt, slots.size()));
            if (value.equals(other) && !FontLibrary.NONE.equals(value)) {
                continue;
            }
            if (primary) {
                CineLyricConfig.fontPrimary = value;
            } else {
                CineLyricConfig.fontSecondary = value;
            }
            TextTexture.clear();
            return;
        }
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
        graphics.drawCenteredString(font, "CineLyric 字体包", PANEL_WIDTH / 2, 12, 0xFFFFFF);

        // Hints sit left of the screen centre: the subtitle moves, so corners can be masked.
        String line1 = "已读到 " + FontLibrary.count() + " 个字体，两行都能选「无」";
        String line2 = "缺字顺序：主字体 → 副字体 → 现代化UI → 原版";
        int right = width / 2 - 12;
        graphics.drawString(font, line1, right - font.width(line1), height / 2 - 22, 0xF0F0F0, true);
        graphics.drawString(font, line2, right - font.width(line2), height / 2 - 10, 0xC8C8C8, true);
        if (FontLibrary.count() == 0) {
            String none = "fonts 文件夹里还没有字体，点上面的「打开字体文件夹」";
            graphics.drawString(font, none, right - font.width(none), height / 2 + 2, 0xFF8080, false);
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
}
