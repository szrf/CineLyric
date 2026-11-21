/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import java.awt.Color;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Song title line, drawn with the same direct {@code BakedGlyph} path as the lyrics.
 *
 * <p>The title's origin is the centre of the screen. The three alignment modes decide whether
 * the title block starts at, is centred on, or ends at that origin; the custom mode adds the
 * two pixel offsets and is the only mode that exposes them in the title settings screen.
 *
 * <p>It deliberately does not call {@code Font#drawString}: ModernUI replaces that method and
 * would ignore the selected custom font. The title can be white or a per-character rainbow, and
 * always carries the animated note prefix (never an album cover or a local image).
 */
final class SongTitleRenderer {

    private static final float PREVIEW_ALPHA = 1.0F;

    private SongTitleRenderer() {
    }

    /** Live title for the HUD. */
    static void draw(GuiGraphics graphics) {
        draw(graphics, SongInfo.current(), false, true);
    }

    /** Sample title used by the settings preview (no delay applied). */
    static void drawPreview(GuiGraphics graphics) {
        draw(graphics, null, true, false);
    }

    /** Sample title used by the delay screen, where the preview follows the delay. */
    static void drawPreview(GuiGraphics graphics, boolean respectDelay) {
        draw(graphics, null, true, respectDelay);
    }

    private static void draw(GuiGraphics graphics, SongInfo info, boolean preview, boolean respectDelay) {
        if (!CineLyricConfig.showSongTitle) {
            return;
        }
        if (!preview && info == null) {
            return;
        }
        boolean delayed = respectDelay && !CineLyricOverlay.delayElapsed();
        if (delayed && CineLyricConfig.songTitleNoteFollowsDelay) {
            return;
        }
        String title;
        String artist;
        if (preview) {
            title = "示例歌名";
            artist = "示例艺术家";
        } else {
            title = info.title;
            artist = info.artist;
        }
        if (title == null || title.isEmpty()) {
            return;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int size = Math.max(CineLyricConfig.TITLE_FONT_MIN,
                Math.min(CineLyricConfig.TITLE_FONT_MAX, CineLyricConfig.songTitleSize));
        String text = delayed ? "" : title;
        if (!delayed && artist != null && !artist.isEmpty() && !title.contains(artist)) {
            text = title + " - " + artist;
        }
        int lineHeight = LyricTextRenderer.lineHeight(size);

        boolean hasIcon = "note".equals(CineLyricConfig.normalizeIcon(CineLyricConfig.songTitleIcon));
        int iconWidth = CineLyricConfig.songTitleIconWidth > 0
                ? CineLyricConfig.songTitleIconWidth : lineHeight;
        int iconHeight = CineLyricConfig.songTitleIconHeight > 0
                ? CineLyricConfig.songTitleIconHeight : lineHeight;
        int gap = hasIcon ? Math.max(-8, CineLyricConfig.songTitleIconGap) : 0;
        double maxTextWidth = Math.max(1.0, screenWidth * CineLyricConfig.MAX_WIDTH_RATIO
                - (hasIcon ? iconWidth + gap : 0));
        float textWidth = text.isEmpty() ? 0.0F
                : LyricTextRenderer.width(text, size, maxTextWidth,
                        CineLyricConfig.songTitleLetterSpacing);
        if (textWidth <= 0.0F && !hasIcon) {
            return;
        }
        float groupWidth = textWidth + (hasIcon ? iconWidth + gap : 0);

        // Screen-centre origin: (0, 0) is the middle of the GUI viewport, not the top-left.
        float centreX = screenWidth / 2.0F;
        float centreY = screenHeight / 2.0F;
        String align = CineLyricConfig.normalizeAlign(CineLyricConfig.songTitleAlign);
        float left = switch (align) {
            case "left" -> centreX;                         // block starts at the centre
            case "right" -> centreX - groupWidth;           // block ends at the centre
            default -> centreX - groupWidth / 2.0F;         // centre / custom
        };
        int offsetX = "custom".equals(align) ? CineLyricConfig.songTitleX : 0;
        int offsetY = "custom".equals(align) ? CineLyricConfig.songTitleY : 0;
        left += offsetX;
        left = Math.max(4.0F, Math.min(screenWidth - 4.0F - groupWidth, left));

        int top = Math.round(centreY - lineHeight / 2.0F + offsetY);
        top = Math.max(0, Math.min(screenHeight - lineHeight, top));

        if (hasIcon) {
            int bob = 0;
            if (CineLyricConfig.songTitleNoteBounce) {
                float phase = (System.currentTimeMillis() % 1800L) / 1800.0F;
                bob = Math.round((float) Math.sin(phase * Math.PI * 2.0)
                        * Math.max(0, CineLyricConfig.songTitleNoteBounceAmount));
            }
            int iconY = top + Math.max(0, (lineHeight - iconHeight) / 2)
                    + CineLyricConfig.songTitleIconOffsetY + bob;
            int iconRgb = 0xFFFFFF;
            if (CineLyricConfig.songTitleNoteColor) {
                float phase = (System.currentTimeMillis() % 5000L) / 5000.0F;
                iconRgb = Color.HSBtoRGB(phase, 0.68F, 1.0F) & 0xFFFFFF;
            }
            drawNote(graphics, Math.round(left), iconY, iconWidth, iconHeight, iconRgb);
        }

        if (text.isEmpty()) {
            return;
        }
        int[] colors = null;
        if (CineLyricConfig.songTitleColorful) {
            colors = rainbow(text.length());
        }
        float textCenter = left + (hasIcon ? iconWidth + gap : 0) + textWidth / 2.0F;
        LyricTextRenderer.draw(graphics, text, size, Math.round(textCenter), top,
                maxTextWidth, screenWidth, 0xFFFFFF, PREVIEW_ALPHA, CineLyricConfig.shadow, colors,
                CineLyricConfig.songTitleLetterSpacing);
    }

    /**
     * Animated vanilla-style note. The sprite is generated once (Minecraft has no note.png),
     * then bobbed and tinted with a slow hue cycle to read as a live particle rather than a
     * static icon. The tint is applied through the shader colour so the white sprite works.
     */
    private static void drawNote(GuiGraphics graphics, int x, int y, int width, int height, int rgb) {
        ResourceLocation id = Notes.ensure();
        if (id == null) {
            return;
        }
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        try {
            RenderSystem.setShaderColor(r, g, b, 1.0F);
            graphics.flush();
            graphics.blit(id, x, y, width, height, 0.0F, 0.0F, Notes.width, Notes.height,
                    Notes.width, Notes.height);
            graphics.flush();
        } catch (Throwable ignored) {
            // A missing icon must never take the title down with it.
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** Flowing rainbow, one colour per UTF-16 unit (spaces still consume a colour slot). */
    private static int[] rainbow(int length) {
        int[] colors = new int[Math.max(1, length)];
        float offset = (System.currentTimeMillis() % 4000L) / 4000.0F;
        for (int i = 0; i < colors.length; i++) {
            float hue = (offset + i * 0.025F) % 1.0F;
            colors[i] = Color.HSBtoRGB(hue, 0.72F, 1.0F) & 0xFFFFFF;
        }
        return colors;
    }

    /**
     * Vanilla-style note sprite. Minecraft's note particle is drawn procedurally rather than
     * from a texture, so there is no {@code particle/note.png} to load; this 9x9 pixel note
     * gives the same white music-note silhouette without shipping an image file.
     */
    private static final class Notes {
        private static final String[] PATTERN = {
                ".#######.",
                ".#.....#.",
                ".#.....#.",
                ".#.....#.",
                ".#.....#.",
                "###..###.",
                "###..###.",
                ".#....#..",
                "........."
        };
        private static ResourceLocation id;
        private static DynamicTexture texture;
        private static int width = 9;
        private static int height = 9;

        private static ResourceLocation ensure() {
            if (texture != null) {
                return id;
            }
            try {
                NativeImage image = new NativeImage(width, height, true);
                for (int y = 0; y < height; y++) {
                    String line = PATTERN[y];
                    for (int x = 0; x < width; x++) {
                        image.setPixelRGBA(x, y,
                                x < line.length() && line.charAt(x) == '#' ? 0xFFFFFFFF : 0);
                    }
                }
                DynamicTexture created = new DynamicTexture(image);
                created.setFilter(false, false);
                id = Minecraft.getInstance().getTextureManager()
                        .register("cinelyric_note", created);
                texture = created;
                return id;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
