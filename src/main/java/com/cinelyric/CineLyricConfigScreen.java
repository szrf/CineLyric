/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import java.awt.Desktop;
import java.nio.file.Files;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Standalone settings screen with a live cinematic preview. */
public class CineLyricConfigScreen extends CineLyricScreen {

    static final int PANEL_WIDTH = 236;
    private static final int ROW = 22;

    private final Screen parent;
    private final Component heading = Component.literal("CineLyric 设置");

    private boolean capturing;
    private int panelHeight;

    public CineLyricConfigScreen(Screen parent) {
        super(Component.literal("CineLyric 设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        suppressBlur();
        CineLyricOverlay.beginPreview();
        LyricSources.dumpBackends();
        FontLibrary.reload();
        CineLyricConfig.ensureFontSelection();
        
        int left = 8;
        int top = 32;
        int width = PANEL_WIDTH - 16;
        int row = 0;

        addRenderableWidget(new IntSlider(left, top + ROW * row++, width, "字号",
                CineLyricConfig.FONT_MIN, CineLyricConfig.FONT_MAX, CineLyricConfig.fontSize, value -> {
                    CineLyricConfig.fontSize = value;
                    TextTexture.clear();
                }));

        addRenderableWidget(new IntSlider(left, top + ROW * row++, width, "字间距",
                0, 6, CineLyricConfig.letterSpacing, value -> {
                    CineLyricConfig.letterSpacing = value;
                    TextTexture.clear();
                }));

        addRenderableWidget(Button.builder(engineLabel(), button -> {
            cycleEngine();
            rebuildWidgets();
        }).bounds(left, top + ROW * row++, width, 20).build());

        if (FontLibrary.CUSTOM.equals(CineLyricConfig.fontSource)) {
            addRenderableWidget(Button.builder(fontPackLabel(), button -> openFonts())
                    .bounds(left, top + ROW * row++, width, 20).build());
        }

        addRenderableWidget(Button.builder(musicLabel(), button -> {
            cycleMusic();
            rebuildWidgets();
        }).bounds(left, top + ROW * row++, width, 20).build());

        addRenderableWidget(Button.builder(Component.literal("显示译文…"), button -> openTranslation())
                .bounds(left, top + ROW * row++, width, 20).build());

        addRenderableWidget(Button.builder(delayLabel(), button -> openDelay())
                .bounds(left, top + ROW * row++, width, 20).build());

        addRenderableWidget(Button.builder(shadowLabel(), button -> {
            CineLyricConfig.shadow = !CineLyricConfig.shadow;
            TextTexture.clear();
            button.setMessage(shadowLabel());
        }).bounds(left, top + ROW * row++, width, 20).build());

        addRenderableWidget(Button.builder(Component.literal("位置调整…"), button -> openPosition())
                .bounds(left, top + ROW * row++, width, 20).build());

        addRenderableWidget(Button.builder(keyLabel(), button -> {
            capturing = !capturing;
            rebuildWidgets();
        }).bounds(left, top + ROW * row++, width, 20).build());

        addRenderableWidget(Button.builder(Component.literal("完成"), button -> onClose())
                .bounds(left, top + ROW * row++, width, 20).build());

        panelHeight = top + ROW * row + 8;
    }

    boolean isCapturingKey() {
        return capturing;
    }

    private void openPosition() {
        if (minecraft == null) {
            return;
        }
        CineLyricConfig.save();
        minecraft.setScreen(new CineLyricPositionScreen(this));
    }

    private void openFonts() {
        if (minecraft == null) {
            return;
        }
        CineLyricConfig.save();
        minecraft.setScreen(new CineLyricFontScreen(this));
    }

    private void openTranslation() {
        if (minecraft == null) {
            return;
        }
        CineLyricConfig.save();
        minecraft.setScreen(new CineLyricTranslationScreen(this));
    }

    private void openDelay() {
        if (minecraft == null) {
            return;
        }
        CineLyricConfig.save();
        minecraft.setScreen(new CineLyricDelayScreen(this));
    }

    private static Component delayLabel() {
        int millis = CineLyricConfig.delayMillis;
        String value = millis <= 0 ? "关闭" : String.format("%.1f 秒", millis / 1000.0F);
        return Component.literal("延迟显示（实验性）：" + value);
    }

    private static Component musicLabel() {
        String mode = CineLyricConfig.normalizeSource(CineLyricConfig.musicSource);
        String name = "muonium".equals(mode) ? "MuoniumPlayer"
                : "dango".equals(mode) ? "DangoMusic" : "自动检测";
        return Component.literal("音乐来源：" + name + "");
    }

    /** auto -> muonium -> dango -> auto; switching re-detects the lyric backend. */
    private static void cycleMusic() {
        String mode = CineLyricConfig.normalizeSource(CineLyricConfig.musicSource);
        if ("auto".equals(mode)) {
            CineLyricConfig.musicSource = "muonium";
        } else if ("muonium".equals(mode)) {
            CineLyricConfig.musicSource = "dango";
        } else {
            CineLyricConfig.musicSource = "auto";
        }
        LyricSources.reset();
    }

    private Component keyLabel() {
        if (capturing) {
            return Component.literal("快捷键：请按下新按键…（Esc 取消）");
        }
        return Component.literal("快捷键：" + CineLyricClient.keyName() + "（点击修改）");
    }

    private Component engineLabel() {
        return Component.literal("字体引擎：" + FontLibrary.describe(CineLyricConfig.fontSource));
    }

    private Component fontPackLabel() {
        if (FontLibrary.count() == 0) {
            return Component.literal("字体设置：还没有字体文件（点击添加）");
        }
        return Component.literal("字体设置：主 " + FontLibrary.label(CineLyricConfig.fontPrimary)
                + " / 副 " + FontLibrary.label(CineLyricConfig.fontSecondary));
    }

    private Component shadowLabel() {
        return Component.literal("文字阴影：" + (CineLyricConfig.shadow ? "开" : "关"));
    }

    private void cycleEngine() {
        String next;
        if (FontLibrary.AUTO.equals(CineLyricConfig.fontSource)) {
            next = FontLibrary.CUSTOM;
        } else if (FontLibrary.CUSTOM.equals(CineLyricConfig.fontSource)) {
            next = FontLibrary.PLAYER;
        } else {
            next = FontLibrary.AUTO;
        }
        CineLyricConfig.fontSource = next;
        CineLyricConfig.ensureFontSelection();
        TextTexture.clear();
    }

    static void openFontsFolder() {
        try {
            Files.createDirectories(FontLibrary.root());
            String path = FontLibrary.root().toAbsolutePath().toString();
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(FontLibrary.root().toFile());
                    return;
                }
            } catch (Throwable ignored) {
                // fall through to explorer
            }
            new ProcessBuilder("explorer.exe", path).start();
        } catch (Throwable failure) {
            System.out.println("[CineLyric] could not open fonts folder: " + failure);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capturing) {
            if (keyCode == 256) {
                capturing = false;
                rebuildWidgets();
                return true;
            }
            CineLyricClient.bind(keyCode);
            capturing = false;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        beginFrame();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        drawPreview(graphics);
        graphics.fill(4, 4, PANEL_WIDTH, panelHeight, 0xD0000000);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, heading, PANEL_WIDTH / 2, 14, 0xFFFFFF);

        if (!modsPresent()) {
            String missing = "需要 Ji AFK Cinematic + 音乐播放器（Muonium / Dango）";
            graphics.drawString(font, missing, width - font.width(missing) - 6, 6, 0xFF8080, false);
        }
    }

    private static boolean modsPresent() {
        return classPresent("com.ji.afkcinematic.variants.v1_21_1.render.LetterboxRenderer")
                && (classPresent("com.muoniumplayer.core.ncm.music.CloudMusic")
                || classPresent("me.sorihiko.dango.music.MusicPlayer"));
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name, false, CineLyricConfigScreen.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Simulates Ji's cine frame: two 16% bars and the subtitle inside the selected one. */
    private void drawPreview(GuiGraphics graphics) {
        paintCinematicBars(graphics);
        CineLyricOverlay.drawLines(graphics, CineLyricOverlay.SAMPLE_MAIN,
                CineLyricOverlay.SAMPLE_TRANSLATION, CineLyricOverlay.previewAlpha());
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

    static final class IntSlider extends AbstractSliderButton {
        private final String label;
        private final int min;
        private final int max;
        private final IntConsumer consumer;

        IntSlider(int x, int y, int width, String label, int min, int max, int initial, IntConsumer consumer) {
            super(x, y, width, 20, Component.empty(), (initial - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.consumer = consumer;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (label == null) {
                return;
            }
            setMessage(Component.literal(label + "：" + current()));
        }

        @Override
        protected void applyValue() {
            consumer.accept(current());
        }

        private int current() {
            return (int) Math.round(min + value * (max - min));
        }
    }

    static class DoubleSlider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final String format;
        private final DoubleConsumer consumer;

        DoubleSlider(int x, int y, int width, String label, double min, double max, double initial,
                     DoubleConsumer consumer, String format) {
            super(x, y, width, 20, Component.empty(), (initial - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.format = format;
            this.consumer = consumer;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (label == null) {
                return;
            }
            setMessage(Component.literal(label + "：" + String.format(java.util.Locale.ROOT, format, current())
                    + detail()));
        }

        protected String detail() {
            return "";
        }

        @Override
        protected void applyValue() {
            consumer.accept(current());
            updateMessage();
        }

        protected double current() {
            return min + value * (max - min);
        }
    }

    interface IntConsumer {
        void accept(int value);
    }

    interface DoubleConsumer {
        void accept(double value);
    }
}
