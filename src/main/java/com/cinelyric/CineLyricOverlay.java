/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the current Muonium lyric line (original + translation) inside Ji's letterbox
 * bar. Used from the Ji HUD path, and reused by the settings preview.
 */
public final class CineLyricOverlay {

    /** Sample lines mirror a real English song with a Chinese translation: original on top. */
    public static final String SAMPLE_MAIN = "How many people loved your youthful face";
    public static final String SAMPLE_TRANSLATION = "多少人曾爱慕你年轻时的容颜";

    private static final float TRANSLATION_RATIO = 0.78F;
    private static boolean loggedActive;
    private static boolean loggedError;

    /** When the current subtitle run started; used by the experimental delay feature. */
    private static long sessionStart;
    private static long lastSeen;
    private static long lastNullNote;
    private static boolean dumpedOnce;
    private static String lastGeometry;
    private static boolean loggedCinematicDraw;

    private CineLyricOverlay() {
    }

    public static void render(GuiGraphics graphics) {
        try {
            drawCurrentLyric(graphics);
        } catch (Throwable failure) {
            if (!loggedError) {
                loggedError = true;
                System.out.println("[CineLyric] overlay failed: " + failure);
            }
        }
    }

    private static void drawCurrentLyric(GuiGraphics graphics) {
        LyricSources.Line line = LyricSources.current();
        if (line == null) {
            long now = System.nanoTime();
            if (now - lastNullNote > 5_000_000_000L) {
                lastNullNote = now;
                Diag.note("[overlay] no lyric line (mode="
                        + CineLyricConfig.musicSource + ", backends=" + LyricSources.chainNames() + ")");
                if (!dumpedOnce) {
                    dumpedOnce = true;
                    LyricSources.dumpBackends();
                }
            }
            // silence for a while means a new session: let the delay run again next time
            if (sessionStart != 0L && System.nanoTime() - lastSeen > 1_500_000_000L) {
                sessionStart = 0L;
            }
            return;
        }
        lastSeen = System.nanoTime();
        String main = clean(line.main);
        String translation = clean(line.translation);
        if (main.isEmpty() && translation.isEmpty()) {
            return;
        }
        if (!loggedActive) {
            loggedActive = true;
            System.out.println("[CineLyric] overlay drawing lyrics inside the cinematic letterbox ("
                    + LyricSources.activeName() + ")");
        }
        if (!delayElapsed()) {
            return;
        }
        if (!loggedCinematicDraw && JiCinematic.cinematic()) {
            loggedCinematicDraw = true;
            Diag.note("[overlay] drawing inside the cinematic bars (source="
                    + LyricSources.activeName() + ")");
        }
        drawLines(graphics, main, translation, line.alpha);
    }

    /** True once the experimental start delay (if any) has passed. */
    public static boolean delayElapsed() {
        int delay = CineLyricConfig.delayMillis;
        if (delay <= 0) {
            return true;
        }
        long now = System.nanoTime();
        if (sessionStart == 0L) {
            sessionStart = now;
        }
        return (now - sessionStart) / 1_000_000L >= delay;
    }

    /** Starts the delay countdown when a settings page opens. */
    public static void beginPreview() {
        sessionStart = System.nanoTime();
    }

    /** "Preview" button: replay the delay from the start. */
    public static void restartPreview() {
        sessionStart = System.nanoTime();
    }

    /** Sample subtitle opacity for the settings preview (0 while the delay runs). */
    public static float previewAlpha() {
        return delayElapsed() ? 1.0F : 0.0F;
    }

    /** Shared by the overlay and the settings preview. */
    public static void drawLines(GuiGraphics graphics, String main, String translation, float alpha) {
        if (alpha <= 0.02F) {
            return;
        }
        boolean showTranslation = CineLyricConfig.showTranslation;
        String sub = showTranslation ? translation : "";
        // Only draw a real second line: empty translations, and translations that merely
        // repeat the lyric (some sources echo the Chinese line), would otherwise push a
        // single-language song into the "translation" slot under the original.
        if (!sub.isEmpty() && (main.isEmpty() || sameText(main, sub))) {
            sub = "";
        }
        if (main.isEmpty() && sub.isEmpty()) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        double maxWidth = width * CineLyricConfig.MAX_WIDTH_RATIO;

        int mainSize = CineLyricConfig.fontSize;
        int subSize = Math.max(4, Math.round(mainSize * TRANSLATION_RATIO));

        TextTexture.Entry mainTexture = main.isEmpty() ? null
                : prepare(main, mainSize, CineLyricConfig.color, alpha);
        TextTexture.Entry subTexture = sub.isEmpty() ? null
                : prepare(sub, subSize, CineLyricConfig.translationColor, alpha);

        int mainHeight = main.isEmpty() ? 0 : textHeight(mainTexture, mainSize);
        int subHeight = sub.isEmpty() ? 0 : textHeight(subTexture, subSize);
        int gap = main.isEmpty() || sub.isEmpty() ? 0
                : Math.max(0, mainSize / 6 + CineLyricConfig.translationOffset);
        int blockHeight = mainHeight + gap + subHeight;

        int barHeight = Math.round(height * CineLyricConfig.BAR_RATIO);
        int barTop = isTopBar() ? 0 : height - barHeight;
        boolean above = translationAbove();

        int mainTop;
        int subTop;
        if (CineLyricConfig.autoLayout) {
            // Keep the MAIN line centred in the bar so switching the translation on never
            // shifts the lyric up or down; the translation grows on the chosen side instead.
            if (main.isEmpty()) {
                mainTop = barTop + Math.max(0, (barHeight - subHeight) / 2);
                subTop = mainTop;
            } else {
                mainTop = barTop + Math.max(0, (barHeight - mainHeight) / 2);
                int wanted = above ? mainTop - gap - subHeight : mainTop + mainHeight + gap;
                subTop = Math.max(barTop, Math.min(wanted, barTop + barHeight - subHeight));
            }
        } else {
            int blockTop = blockTop(height, barTop, barHeight, blockHeight);
            mainTop = above ? blockTop + subHeight + gap : blockTop;
            subTop = above ? blockTop : blockTop + mainHeight + gap;
        }

        noteGeometry(height, barHeight, blockHeight, mainTop, subTop);

        int centerX = CineLyricConfig.autoLayout
                ? width / 2
                : width / 2 + (int) Math.round(width * (CineLyricConfig.xPercent / 100.0));

        if (!main.isEmpty()) {
            draw(graphics, main, mainTexture, mainSize, centerX, mainTop, maxWidth, width,
                    CineLyricConfig.color, alpha);
        }
        if (!sub.isEmpty()) {
            draw(graphics, sub, subTexture, subSize, centerX, subTop, maxWidth, width,
                    CineLyricConfig.translationColor, alpha);
        }
    }

    /**
     * Vertical placement of the whole two-line block. Auto mode simply centres it inside the
     * active bar (and nails it there even if the font gets bigger); manual mode honours
     * {@link CineLyricConfig#yPercent}, clamped so the block can never leave the bar.
     */
    private static int blockTop(int screenHeight, int barTop, int barHeight, int blockHeight) {
        if (CineLyricConfig.autoLayout) {
            return barTop + Math.max(0, (barHeight - blockHeight) / 2);
        }
        int margin = Math.max(2, (int) Math.round(screenHeight * (CineLyricConfig.yPercent / 100.0)));
        int top = isTopBar() ? margin : screenHeight - margin - blockHeight;
        top = Math.max(barTop, Math.min(top, barTop + barHeight - blockHeight));
        return Math.max(barTop, top);
    }

    /** Translation above the lyric line is the default; below is the other option. */
    static boolean translationAbove() {
        return !"below".equalsIgnoreCase(CineLyricConfig.translationSide);
    }

    /**
     * Logs where the block was placed whenever that changes, so a "subtitle never appears"
     * report can be told apart from "it was placed outside the bar".
     */
    private static void noteGeometry(int height, int barHeight, int blockHeight,
                                     int mainTop, int subTop) {
        String key = (isTopBar() ? "top" : "bottom") + "/" + height + "/" + barHeight
                + "/" + blockHeight + "/" + mainTop + "/" + subTop;
        if (key.equals(lastGeometry)) {
            return;
        }
        lastGeometry = key;
        Diag.note("[overlay] geo side=" + (isTopBar() ? "top" : "bottom")
                + " h=" + height + " bar=" + barHeight + " block=" + blockHeight
                + " mainTop=" + mainTop + " subTop=" + subTop
                + " auto=" + CineLyricConfig.autoLayout
                + " y=" + Math.round(CineLyricConfig.yPercent * 100.0) / 100.0);
    }

    /** The subtitle follows the bar the user selected. */
    public static boolean isTopBar() {
        return "top".equalsIgnoreCase(CineLyricConfig.barSide);
    }

    private static TextTexture.Entry prepare(String text, int size, int rgb, float alpha) {
        return TextTexture.get(text, CineLyricConfig.fontSource, CineLyricConfig.fontPrimary,
                CineLyricConfig.fontSecondary, size, CineLyricConfig.shadow, rgb, alpha);
    }

    private static int textHeight(TextTexture.Entry texture, int size) {
        if (texture != null) {
            return Math.max(1, Math.round(texture.guiHeight()));
        }
        // Vanilla bitmap font: 9 GUI units tall at scale 1.
        return Math.max(1, Math.round(size));
    }

    private static void draw(GuiGraphics graphics, String text, TextTexture.Entry texture, int size,
                             int centerX, int topY, double maxWidth, int screenWidth, int rgb, float alpha) {
        if (texture != null) {
            float textWidth = texture.guiWidth();
            float fit = (float) Math.min(1.0, maxWidth / Math.max(1.0, textWidth));
            float scale = texture.unitsPerTexel * fit;
            float fittedWidth = textWidth * fit;
            float left = clampLeft(Math.round(centerX - fittedWidth / 2.0F), fittedWidth, screenWidth);
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(left, topY, 0.0F);
            pose.scale(scale, scale, 1.0F);
            // Colour and opacity are baked into the texture, so the blit needs no shader tint.
            // Full-size blit: the short overload assumes a 256x256 atlas, which would sample
            // a wrong UV region (and wrap) for a long lyric line -> "colour TV static".
            graphics.blit(texture.id, 0, 0, 0.0F, 0.0F, texture.width, texture.height,
                    texture.width, texture.height);
            pose.popPose();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        float scale = size / 9.0F;
        float fit = (float) Math.min(1.0, maxWidth / Math.max(1.0, font.width(text) * scale));
        float finalScale = scale * fit;
        float textWidth = font.width(text) * finalScale;
        float left = clampLeft(Math.round(centerX - textWidth / 2.0F), textWidth, screenWidth);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(left, topY, 0.0F);
        pose.scale(finalScale, finalScale, 1.0F);
        int color = withAlpha(rgb, alpha);
        graphics.drawString(font, text, 0, 0, color, CineLyricConfig.shadow);
        pose.popPose();
    }

    private static float clampLeft(float left, float textWidth, int screenWidth) {
        if (textWidth >= screenWidth - 8) {
            return 4.0F;
        }
        return Math.max(4.0F, Math.min(screenWidth - 4.0F - textWidth, left));
    }

    /** True when the "translation" is just the same line again. */
    private static boolean sameText(String main, String sub) {
        return normalizeText(main).equals(normalizeText(sub));
    }

    private static String normalizeText(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
