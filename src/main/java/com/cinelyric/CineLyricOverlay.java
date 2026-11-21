/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the current Muonium lyric line (original + translation) inside Ji's letterbox
 * bar. Used from the Ji HUD path, and reused by the settings preview.
 */
public final class CineLyricOverlay {

    /** Sample lines mirror a real English song with a Chinese translation: original on top. */
    public static final String SAMPLE_MAIN = "How many people loved your youthful face";
    public static final String SAMPLE_TRANSLATION = "多少人曾爱慕你年轻时的容颜";

    private static boolean loggedActive;
    private static boolean loggedError;

    /** When the current subtitle run started; used by the experimental delay feature. */
    private static long sessionStart;
    private static boolean previewDelay;
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
            SongTitleRenderer.draw(graphics);
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

    /** Starts the delay countdown when a settings page opens. Preview delay is off. */
    public static void beginPreview() {
        previewDelay = false;
        sessionStart = System.nanoTime();
    }

    /** Starts the delay countdown for the dedicated delay page, where the preview honours it. */
    public static void beginDelayPreview() {
        previewDelay = true;
        sessionStart = System.nanoTime();
    }

    /** "Preview" button: replay the delay from the start. */
    public static void restartPreview() {
        sessionStart = System.nanoTime();
    }

    /** Sample subtitle opacity for the settings preview (0 while the delay runs). */
    public static float previewAlpha() {
        return !previewDelay || delayElapsed() ? 1.0F : 0.0F;
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
        int subSize = CineLyricConfig.translationFontSize > 0
                ? Math.max(CineLyricConfig.TITLE_FONT_MIN,
                        Math.min(CineLyricConfig.TITLE_FONT_MAX, CineLyricConfig.translationFontSize))
                : mainSize;

        int mainHeight = main.isEmpty() ? 0 : textHeight(mainSize);
        int subHeight = sub.isEmpty() ? 0 : textHeight(subSize);
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
            LyricTextRenderer.draw(graphics, main, mainSize, centerX, mainTop, maxWidth, width,
                    CineLyricConfig.color, alpha);
        }
        if (!sub.isEmpty()) {
            LyricTextRenderer.draw(graphics, sub, subSize, centerX, subTop, maxWidth, width,
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

    /** Original on top and translation underneath by default; the other order is opt-in. */
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

    /**
     * The real line box of the active font: vanilla is 9 px, a custom TTF can have a taller
     * ascent/descent. The position screen and the two-line block use this so the selected
     * vertical position matches what is drawn.
     */
    private static int textHeight(int size) {
        return LyricTextRenderer.lineHeight(size);
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
