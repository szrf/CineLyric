/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only wiring: hotkey, settings screen entry points and the "missing mod" notice.
 *
 * <p>Ji cancels the vanilla key callback while a cinematic is playing, so
 * {@link KeyMapping#consumeClick()} never fires there. The key is therefore polled
 * straight from GLFW (whose internal key state Ji does not cancel) with an edge
 * detector of our own. The binding itself still lives in a normal {@link KeyMapping},
 * so it is rebindable in Options → Controls, and this screen can rebind it too.
 */
final class CineLyricClient {

    private static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.cinelyric.config", InputConstants.KEY_L, "key.categories.misc");

    /** What 1.0 – 1.1.7 shipped; vanilla Options still stores it, so it is migrated once. */
    private static final int LEGACY_KEY = InputConstants.KEY_F8;

    private static boolean keyWasDown;
    private static boolean warned;
    private static int lastMappingKey = -1;

    private CineLyricClient() {
    }

    static void init(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new CineLyricConfigScreen(parent));
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(OPEN_CONFIG));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onTick());
    }

    /** Rebinds the hotkey from the settings screen. */
    static void bind(int keyCode) {
        CineLyricConfig.keyCode = keyCode;
        try {
            OPEN_CONFIG.setKey(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
        } catch (Throwable ignored) {
            // keep the config value; polling still works
        }
        lastMappingKey = keyCode;
        CineLyricConfig.save();
    }

    static String keyName() {
        try {
            return InputConstants.Type.KEYSYM.getOrCreate(CineLyricConfig.keyCode).getDisplayName().getString();
        } catch (Throwable ignored) {
            return "L";
        }
    }

    private static void onTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) {
            return;
        }
        warnIfMissing(minecraft);

        // Vanilla Options → Controls is authoritative once the game has loaded the options.
        int mapped = readMappingKey();
        if (mapped == LEGACY_KEY && CineLyricConfig.keyCode == LEGACY_KEY) {
            // Both sides still hold the old F8 default: nothing was chosen on purpose, so
            // move to the new L default once instead of letting Options pin F8 forever.
            bind(InputConstants.KEY_L);
        } else if (mapped > 0 && mapped != lastMappingKey) {
            lastMappingKey = mapped;
            CineLyricConfig.keyCode = mapped;
        }

        int key = CineLyricConfig.keyCode;
        boolean down = key > 0 && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key);
        boolean pressed = down && !keyWasDown;
        if (pressed && minecraft.screen == null && minecraft.player != null) {
            // Ji cancels the whole Gui#render during a cinematic, which would also hide this
            // settings screen, so stop the cinematic first (the screen has its own preview).
            cancelJiCinematic();
            minecraft.setScreen(new CineLyricConfigScreen(null));
        }
        keyWasDown = down;
    }

    /** KeyMapping exposes no getter for the bound key, so read the private field. */
    private static int readMappingKey() {
        try {
            java.lang.reflect.Field field = KeyMapping.class.getDeclaredField("key");
            field.setAccessible(true);
            Object bound = field.get(OPEN_CONFIG);
            if (bound instanceof InputConstants.Key inputKey) {
                return inputKey.getValue();
            }
        } catch (Throwable ignored) {
            // fall back to the configured value
        }
        return -1;
    }

    private static void cancelJiCinematic() {
        try {
            Class<?> manager = Class.forName(
                    "com.ji.afkcinematic.variants.v1_21_1.cinematic.CinematicManager",
                    false, CineLyricClient.class.getClassLoader());
            Object cancelled = manager.getMethod("cancelForPause").invoke(null);
            if (!Boolean.TRUE.equals(cancelled)) {
                manager.getMethod("deactivateCinematic").invoke(null);
            }
        } catch (Throwable ignored) {
            // Ji not installed, or its API changed: nothing to cancel.
        }
    }

    private static void warnIfMissing(Minecraft minecraft) {
        if (warned || minecraft.player == null) {
            return;
        }
        boolean hasJi = present("com.ji.afkcinematic.variants.v1_21_1.render.LetterboxRenderer");
        boolean hasMusic = present("com.muoniumplayer.core.ncm.music.CloudMusic")
                || present("me.sorihiko.dango.music.MusicPlayer");
        warned = true;
        if (hasJi && hasMusic) {
            return;
        }
        minecraft.player.displayClientMessage(Component.literal(hasJi
                ? "[CineLyric] 没有找到音乐播放器：请安装 MuoniumPlayer 或 DangoMusic。"
                : "[CineLyric] 需要安装 Ji AFK Cinematic，字幕才会出现在电影黑边里。"), false);
    }

    private static boolean present(String name) {
        try {
            Class.forName(name, false, CineLyricClient.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
