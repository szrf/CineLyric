/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(CineLyricMod.MOD_ID)
public final class CineLyricMod {

    public static final String MOD_ID = "cinelyric";

    public CineLyricMod(IEventBus modBus, ModContainer container) {
        CineLyricConfig.load();
        if (isClient()) {
            FontLibrary.reload();
            CineLyricConfig.ensureFontSelection();
            CineLyricClient.init(modBus, container);
        }
        System.out.println("[CineLyric] mod constructed, font=" + CineLyricConfig.fontSource
                
                + " size=" + CineLyricConfig.fontSize + " y=" + CineLyricConfig.yPercent
                + " x=" + CineLyricConfig.xPercent);
    }

    private static boolean isClient() {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
