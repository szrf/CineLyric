/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric.mixin;

import com.cinelyric.CineLyricOverlay;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooked on Ji's bar painting itself, not on one of its two callers.
 *
 * <p>Ji paints the letterbox from two places: {@code renderFromHud(GuiGraphics, float)} (the HUD
 * is hidden, e.g. the sleep cinematic) and a HUD-frame listener (the cinematic runs with the HUD
 * still visible). Hooking {@code renderFromHud} therefore only worked for one of them — with the
 * HUD left on, the bars were drawn but the subtitle never got a frame, which looked exactly like
 * "subtitles are gone as soon as the cinematic starts".</p>
 *
 * <p>Both paths end in the private {@code render(GuiGraphics)}, and that method returns
 * immediately while no bar is visible, so drawing at its tail means: subtitle on top of the bars,
 * exactly once per frame, and never on screen without bars.</p>
 */
@Mixin(targets = "com.ji.afkcinematic.variants.v1_21_1.render.LetterboxRenderer", remap = false)
public abstract class LetterboxRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("TAIL"),
            remap = false, require = 0)
    private static void cinelyric$renderLyrics(GuiGraphics graphics, CallbackInfo ci) {
        CineLyricOverlay.render(graphics);
    }
}
