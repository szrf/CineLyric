/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import java.lang.reflect.Field;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shared backdrop for the CineLyric screens: the vanilla menu scene when opened from the
 * main menu, the live world when opened in game, and no menu blur in either case.
 *
 * <p>The blur matters: ModernUI's "blur vanilla screens" option blurs the world behind
 * every screen, which turns the cinematic preview into a mosaic. It is switched off for
 * the lifetime of these screens only, then restored.
 */
public abstract class CineLyricScreen extends Screen {

    private static Field blurFlag;
    private static Object blurHandler;
    private static boolean blurSaved;
    private static boolean blurSuppressed;
    private boolean backdropDone;

    protected CineLyricScreen(Component title) {
        super(title);
    }

    /** Allows the backdrop to be painted once per frame; Screen.render calls renderBackground again. */
    protected final void beginFrame() {
        backdropDone = false;
    }

    /** Hides the dimmed backdrop for screens that need an unobstructed positioning view. */
    protected final void suppressBackdrop() {
        backdropDone = true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (backdropDone) {
            return;
        }
        backdropDone = true;
        if (minecraft != null && minecraft.level == null) {
            // Main menu: keep the game's own panorama instead of a flat colour.
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        } else {
            graphics.fill(0, 0, width, height, 0x33000000);
        }
    }

    @Override
    public void removed() {
        restoreBlur();
        super.removed();
    }

    /** Turns ModernUI's global screen blur off while this screen is open. */
    protected void suppressBlur() {
        try {
            Class<?> type = Class.forName("icyllis.modernui.mc.BlurHandler", false,
                    getClass().getClassLoader());
            Field flag = type.getField("sBlurForVanillaScreens");
            if (!blurSuppressed) {
                blurSaved = flag.getBoolean(null);
                blurSuppressed = true;
            }
            flag.setBoolean(null, false);
            Object handler = type.getField("INSTANCE").get(null);
            type.getMethod("closeEffect").invoke(handler);
            blurFlag = flag;
            blurHandler = handler;
        } catch (Throwable ignored) {
            // ModernUI missing: vanilla blur never runs because renderBackground is overridden.
        }
    }

    private static void restoreBlur() {
        if (!blurSuppressed) {
            return;
        }
        try {
            if (blurFlag != null) {
                blurFlag.setBoolean(null, blurSaved);
            }
            if (blurHandler != null) {
                blurHandler.getClass().getMethod("closeEffect").invoke(blurHandler);
            }
        } catch (Throwable ignored) {
            // nothing to restore
        } finally {
            blurSuppressed = false;
            blurFlag = null;
            blurHandler = null;
        }
    }

    /** Ji's letterbox bars, for the live preview. */
    protected final void paintCinematicBars(GuiGraphics graphics) {
        int bar = Math.round(height * CineLyricConfig.BAR_RATIO);
        graphics.fill(0, 0, width, bar, 0xFF000000);
        graphics.fill(0, height - bar, width, height, 0xFF000000);
    }
}
