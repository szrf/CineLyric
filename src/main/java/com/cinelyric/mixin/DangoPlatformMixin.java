/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric.mixin;

import com.cinelyric.JiCinematic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dango asks its platform for a {@code PlayerPose} (eye position + yaw/pitch) and draws its
 * world-space "3D" lyrics relative to it, so during a Ji cinematic the lyrics would stay at the
 * body while the camera is somewhere else. While the cinematic camera owns the frame we return
 * the camera pose instead, which puts the lyrics in front of the picture being watched.
 *
 * <p>Wrapped reflectively on both sides ({@link JiCinematic}) so there is no compile-time
 * dependency on DangoMusic or Ji, and no change at all outside a cinematic.</p>
 */
@Mixin(targets = "me.sorihiko.dango.modern.ModernPlatform", remap = false)
public abstract class DangoPlatformMixin {

    @Inject(method = "getPlayer", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void cinelyric$cameraPose(CallbackInfoReturnable<Object> cir) {
        Object pose = JiCinematic.cameraPose();
        if (pose != null) {
            cir.setReturnValue(pose);
        }
    }
}
