/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Draws one lyric line with the vanilla glyph atlas, without going through
 * {@code Font#drawString}.
 *
 * <p>There are two reasons not to use {@code Font#drawString}:
 * <ul>
 *   <li>ModernUI overwrites every {@code Font#drawInBatch} overload and ignores the
 *       {@code FontSet} passed to it, so a custom font could never be selected there.</li>
 *   <li>Letter spacing has to be part of the layout advance. Moving each glyph after the
 *       fact is exactly the "letters look nudged apart" artefact the user reported.</li>
 * </ul>
 *
 * <p>Every glyph is a {@link BakedGlyph} from a vanilla {@link FontSet}. The whole line is
 * laid out once, then rendered under one pose; spacing is added to the advance between
 * glyphs, so a gap is a real gap and the line width is correct before it is centred.
 */
public final class LyricTextRenderer {

    /** GUI text is drawn at full brightness in the HUD. */
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Cache of the real ascent/descent of a font atlas, used for baselines and block height. */
    private static final Map<FontSet, Metrics> METRICS = new WeakHashMap<>();

    /** Per-set shared missing glyph, used to reject tofu that {@code getGlyphInfo} still reports. */
    private static final Map<FontSet, BakedGlyph> MISSING_BAKED = new WeakHashMap<>();

    private LyricTextRenderer() {
    }

    /**
     * Height of one line in GUI pixels. Custom fonts can be taller than the 9 px vanilla
     * atlas, so the position screen and the two-line block must use the real metrics
     * instead of assuming {@code size} is also the line height.
     */
    public static int lineHeight(int size) {
        if (size <= 0) {
            return 1;
        }
        FontSet set = FontLibrary.activeSet();
        Metrics metrics = metrics(set);
        int height = Math.round((metrics.ascent + metrics.descent) * size / 9.0F);
        return Math.max(1, height);
    }

    /** GUI-space ascent for the active font, used to centre prefix icons on the text. */
    public static int ascent(int size) {
        if (size <= 0) {
            return 0;
        }
        Metrics metrics = metrics(FontLibrary.activeSet());
        return Math.max(0, Math.round(metrics.ascent * size / 9.0F));
    }

    /**
     * Laid-out width of one line at {@code size}, including the configured letter spacing but
     * after the fit-to-{@code maxWidth} correction used by {@link #draw}. Needed by the title
     * renderer to align the text+icon group.
     */
    public static float width(String text, int size, double maxWidth) {
        return width(text, size, maxWidth, CineLyricConfig.letterSpacing);
    }

    /** Same as {@link #width(String, int, double)} but with an explicit letter spacing. */
    public static float width(String text, int size, double maxWidth, int letterSpacing) {
        if (text == null || text.isEmpty()) {
            return 0.0F;
        }
        FontSet fallback = FontLibrary.vanillaSet();
        FontSet primary = FontLibrary.activeSet();
        if (primary == null) {
            primary = fallback;
        }
        if (primary == null) {
            return 0.0F;
        }
        float baseWidth = 0.0F;
        int count = 0;
        for (int codePoint : text.codePoints().toArray()) {
            Picked picked = pick(primary, fallback, codePoint);
            if (picked == null) {
                continue;
            }
            baseWidth += picked.advance;
            count++;
        }
        if (count == 0) {
            return 0.0F;
        }
        int spacing = Math.max(0, letterSpacing);
        float gaps = spacing * (float) Math.max(0, count - 1);
        float baseScale = Math.max(0.25F, size / 9.0F);
        float room = (float) Math.max(1.0, maxWidth - gaps);
        float fit = Math.min(1.0F, room / Math.max(1.0F, baseWidth * baseScale));
        return baseWidth * baseScale * fit + gaps;
    }

    public static void draw(GuiGraphics graphics, String text, int size, int centerX, int topY,
                            double maxWidth, int screenWidth, int rgb, float alpha) {
        draw(graphics, text, size, centerX, topY, maxWidth, screenWidth, rgb, alpha,
                CineLyricConfig.shadow, null);
    }

    /**
     * Draws a line with an optional per-character colour array (rainbow song titles) and an
     * explicit shadow flag. {@code colors} may be shorter than the text; the last colour is
     * repeated.
     */
    public static void draw(GuiGraphics graphics, String text, int size, int centerX, int topY,
                            double maxWidth, int screenWidth, int rgb, float alpha,
                            boolean shadow, int[] colors) {
        draw(graphics, text, size, centerX, topY, maxWidth, screenWidth, rgb, alpha,
                shadow, colors, CineLyricConfig.letterSpacing);
    }

    /** Same as above but with an explicit letter spacing (used by the independent title spacing). */
    public static void draw(GuiGraphics graphics, String text, int size, int centerX, int topY,
                            double maxWidth, int screenWidth, int rgb, float alpha,
                            boolean shadow, int[] colors, int letterSpacing) {
        if (text == null || text.isEmpty() || alpha <= 0.0F) {
            return;
        }
        FontSet fallback = FontLibrary.vanillaSet();
        FontSet primary = FontLibrary.activeSet();
        if (primary == null) {
            primary = fallback;
        }
        if (primary == null) {
            fallbackDraw(graphics, text, size, centerX, topY, maxWidth, screenWidth, rgb, alpha);
            return;
        }

        List<Glyph> glyphs = new ArrayList<>();
        float baseWidth = 0.0F;
        for (int codePoint : text.codePoints().toArray()) {
            Picked picked = pick(primary, fallback, codePoint);
            if (picked == null) {
                continue;
            }
            glyphs.add(new Glyph(picked.glyph, picked.advance));
            baseWidth += picked.advance;
        }
        if (glyphs.isEmpty()) {
            return;
        }

        Metrics metrics = metrics(primary);
        if (fallback != null && fallback != primary) {
            Metrics fallbackMetrics = metrics(fallback);
            metrics = new Metrics(Math.max(metrics.ascent, fallbackMetrics.ascent),
                    Math.max(metrics.descent, fallbackMetrics.descent));
        }
        // BakedGlyph.render takes the BASELINE y, not the top of the line. The old code
        // passed 0 here, which made custom TTFs sit a few pixels above the position the
        // user picked in 位置调整. Offset by the atlas ascent instead.
        float baseline = metrics.ascent;
        int spacing = Math.max(0, letterSpacing);
        float gaps = spacing * (float) Math.max(0, glyphs.size() - 1);
        float baseScale = Math.max(0.25F, size / 9.0F);
        // The gaps are fixed GUI pixels; the glyphs shrink to fit around them. That keeps
        // "字间距 2" exactly 2 GUI pixels even when a long line has to be reduced.
        float room = (float) Math.max(1.0, maxWidth - gaps);
        float fit = Math.min(1.0F, room / Math.max(1.0F, baseWidth * baseScale));
        float finalScale = baseScale * fit;
        float totalWidth = baseWidth * finalScale + gaps;
        float left = clampLeft(Math.round(centerX - totalWidth / 2.0F), totalWidth, screenWidth);
        float gapUnits = finalScale <= 0.0F ? 0.0F : spacing / finalScale;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(snapToPixel(left), snapToPixel(topY), 0.0F);
        pose.scale(finalScale, finalScale, 1.0F);
        Matrix4f matrix = pose.last().pose();

        float a = Math.max(0.0F, Math.min(1.0F, alpha));
        Map<RenderType, VertexConsumer> buffers = new HashMap<>();
        float x = 0.0F;
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            int glyphRgb = colors == null || colors.length == 0 ? rgb
                    : colors[Math.min(i, colors.length - 1)];
            float r = ((glyphRgb >> 16) & 0xFF) / 255.0F;
            float g = ((glyphRgb >> 8) & 0xFF) / 255.0F;
            float b = (glyphRgb & 0xFF) / 255.0F;
            if (glyph.glyph != null) {
                RenderType type = glyph.glyph.renderType(Font.DisplayMode.NORMAL);
                VertexConsumer consumer = buffers.computeIfAbsent(type,
                        key -> graphics.bufferSource().getBuffer(key));
                if (shadow) {
                    // Vanilla's shadow is the same glyph offset by one GUI pixel and dimmed to 25%.
                    glyph.glyph.render(false, x + 1.0F, baseline + 1.0F, matrix, consumer,
                            r * 0.25F, g * 0.25F, b * 0.25F, a, FULL_BRIGHT);
                }
                glyph.glyph.render(false, x, baseline, matrix, consumer, r, g, b, a, FULL_BRIGHT);
            }
            if (i + 1 < glyphs.size()) {
                x += glyph.advance + gapUnits;
            }
        }
        // Direct BakedGlyph rendering bypasses Font#drawString, so nothing else flushes
        // these buffers. Without this the last atlas page (and sometimes the whole line)
        // stays in the BufferSource and never reaches the screen.
        graphics.flush();
        pose.popPose();
    }

    /**
     * {@link FontSet#getGlyphInfo} never returns {@code null}; unsupported characters come
     * back as the set's shared missing-glyph instance. A noncharacter is a safe probe for
     * that instance because no real font maps it.
     */
    private static boolean isMissing(FontSet set, GlyphInfo info) {
        return info == null || (set != null && info == set.getGlyphInfo(0x10FFFF, false));
    }

    /**
     * Resolves one codepoint to a drawable glyph, preferring the active font and falling
     * back per character to the vanilla set. A codepoint is only accepted when both its
     * {@link GlyphInfo} and its {@link BakedGlyph} are real; otherwise the custom atlas
     * would silently draw its missing-glyph box (the "huge/strange glyph" symptom).
     */
    private static Picked pick(FontSet primary, FontSet fallback, int codePoint) {
        boolean space = Character.isWhitespace(codePoint);
        Picked picked = pickFrom(primary, codePoint, space);
        if (picked != null) {
            return picked;
        }
        if (fallback != null && fallback != primary) {
            picked = pickFrom(fallback, codePoint, space);
            if (picked != null) {
                return picked;
            }
        }
        if (space) {
            return new Picked(null, 4.0F);
        }
        // Last resort: the game's own question mark, still from a real glyph.
        picked = pickFrom(primary, '?', false);
        if (picked != null) {
            return picked;
        }
        if (fallback != null && fallback != primary) {
            return pickFrom(fallback, '?', false);
        }
        return null;
    }

    private static Picked pickFrom(FontSet set, int codePoint, boolean space) {
        if (set == null) {
            return null;
        }
        GlyphInfo info;
        try {
            info = set.getGlyphInfo(codePoint, false);
        } catch (Throwable ignored) {
            return null;
        }
        if (isMissing(set, info)) {
            return null;
        }
        if (space) {
            return new Picked(null, info.getAdvance(false));
        }
        BakedGlyph glyph;
        try {
            glyph = set.getGlyph(codePoint);
        } catch (Throwable ignored) {
            return null;
        }
        if (glyph == null || isMissingBaked(set, glyph)) {
            return null;
        }
        return new Picked(glyph, info.getAdvance(false));
    }

    private static boolean isMissingBaked(FontSet set, BakedGlyph glyph) {
        BakedGlyph missing = MISSING_BAKED.get(set);
        if (missing == null) {
            try {
                missing = set.getGlyph(0x10FFFF);
            } catch (Throwable ignored) {
                return false;
            }
            if (missing != null) {
                MISSING_BAKED.put(set, missing);
            }
        }
        return missing != null && glyph == missing;
    }

    /**
     * Reads the atlas ascent/descent once per {@link FontSet}. These are the numbers vanilla
     * uses to place a glyph relative to its baseline; using {@code size} alone loses the
     * custom font's extra top/bottom space and shifts the line relative to the slider.
     */
    private static Metrics metrics(FontSet set) {
        if (set == null) {
            return new Metrics(0.0F, 9.0F);
        }
        Metrics cached = METRICS.get(set);
        if (cached != null) {
            return cached;
        }
        float ascent = -1.0F;
        float descent = -1.0F;
        try {
            Field upField = BakedGlyph.class.getDeclaredField("up");
            Field downField = BakedGlyph.class.getDeclaredField("down");
            upField.setAccessible(true);
            downField.setAccessible(true);
            for (int codePoint : new int[] {'A', 'M', 'g', 0x56FD}) {
                BakedGlyph glyph = set.getGlyph(codePoint);
                if (glyph == null || isMissingBaked(set, glyph)) {
                    continue;
                }
                float up = upField.getFloat(glyph);
                float down = downField.getFloat(glyph);
                ascent = Math.max(ascent, -up);
                descent = Math.max(descent, down);
            }
        } catch (Throwable ignored) {
            // Reflection can be blocked; fall back to the 9 px vanilla line box.
        }
        if (ascent < 0.0F || descent < 0.0F || ascent + descent < 1.0F) {
            ascent = 0.0F;
            descent = 9.0F;
        }
        Metrics result = new Metrics(ascent, descent);
        METRICS.put(set, result);
        return result;
    }

    /**
     * Last-resort path used only if reflecting the game's FontSet fails. ModernUI may
     * intercept this call, but it at least keeps the lyric visible.
     */
    private static void fallbackDraw(GuiGraphics graphics, String text, int size, int centerX,
                                     int topY, double maxWidth, int screenWidth, int rgb,
                                     float alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) {
            return;
        }
        Font font = minecraft.font;
        float scale = Math.max(0.25F, size / 9.0F);
        float fit = (float) Math.min(1.0, maxWidth / Math.max(1.0, font.width(text) * scale));
        float finalScale = scale * fit;
        float textWidth = font.width(text) * finalScale;
        float left = clampLeft(Math.round(centerX - textWidth / 2.0F), textWidth, screenWidth);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(snapToPixel(left), snapToPixel(topY), 0.0F);
        pose.scale(finalScale, finalScale, 1.0F);
        int color = withAlpha(rgb, alpha);
        graphics.drawString(font, text, 0, 0, color, CineLyricConfig.shadow);
        pose.popPose();
    }

    /** Rounds a GUI-space coordinate onto the physical pixel grid. */
    private static float snapToPixel(float value) {
        var window = Minecraft.getInstance().getWindow();
        if (window == null) {
            return value;
        }
        double guiScale = Math.max(1.0, window.getGuiScale());
        return (float) (Math.round(value * guiScale) / guiScale);
    }

    private static float clampLeft(float left, float textWidth, int screenWidth) {
        if (textWidth >= screenWidth - 8) {
            return 4.0F;
        }
        return Math.max(4.0F, Math.min(screenWidth - 4.0F - textWidth, left));
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private record Glyph(BakedGlyph glyph, float advance) {
    }

    private record Picked(BakedGlyph glyph, float advance) {
    }

    private record Metrics(float ascent, float descent) {
    }
}
