/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.neoforged.fml.loading.FMLPaths;

/** Persisted settings for the subtitle overlay. */
public final class CineLyricConfig {

    /** One of {@link FontLibrary#AUTO}, {@link FontLibrary#CUSTOM}, {@link FontLibrary#PLAYER}. */
    public static String fontSource = FontLibrary.AUTO;
    /** Chosen font file names (without extension) when {@link #fontSource} is custom. */
    public static String fontPrimary = "";
    public static String fontSecondary = "";
    public static int fontSize = 9;
    /** Extra pixels between characters; 0 is normal tracking. */
    public static int letterSpacing = 0;
    /** Distance from the bar's outer edge to the first text line, in percent of screen height. */
    public static double yPercent = 4.0;
    /** Horizontal offset in percent of screen width, positive moves right. */
    public static double xPercent = 0.0;
    /** Which letterbox bar the subtitle lives in: {@code "bottom"} or {@code "top"}. */
    public static String barSide = "bottom";
    /** GLFW key code of the settings hotkey; the KeyMapping stays the source of truth when possible. */
    public static int keyCode = 76; // L (clashes with the vanilla advancements key, by request)
    public static boolean showTranslation = true;
    /** Extra vertical offset of the translation line, in pixels (negative moves it up). */
    public static int translationOffset = 0;
    /**
     * Where the translation goes. {@code "below"} (default) keeps the ORIGINAL line on top and
     * puts the translation under it, which is what subtitles look like everywhere else;
     * {@code "above"} is the old flipped order, still selectable.
     */
    public static String translationSide = "below";
    /** Set once the old "translation above" default has been moved to the new order. */
    static boolean sideMigrated;
    /** Automatic placement: centre the block inside the bar and keep it there. */
    public static boolean autoLayout = true;
    /** Experimental: hide new subtitles for this many ms after the cinematic starts; 0 = off. */
    public static int delayMillis = 0;
    /** Which lyric source to use: {@code "auto"}, {@code "muonium"} or {@code "dango"}. */
    public static String musicSource = "auto";
    public static boolean shadow = true;
    public static int color = 0xFFFFFF;
    public static int translationColor = 0xD8D8D8;

    /** Ji's letterbox bar takes this fraction of the screen height on top and bottom. */
    public static final float BAR_RATIO = 0.16F;
    /** A line wider than this fraction of the screen is scaled down. */
    public static final double MAX_WIDTH_RATIO = 0.92;

    public static final int FONT_MIN = 5;
    public static final int FONT_MAX = 15;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CineLyricConfig() {
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("cinelyric.json");
    }

    private static final class Data {
        String fontSource = CineLyricConfig.fontSource;
        String fontPrimary = CineLyricConfig.fontPrimary;
        String fontSecondary = CineLyricConfig.fontSecondary;
        int fontSize = CineLyricConfig.fontSize;
        int letterSpacing = CineLyricConfig.letterSpacing;
        double yPercent = CineLyricConfig.yPercent;
        double xPercent = CineLyricConfig.xPercent;
        String barSide = CineLyricConfig.barSide;
        int keyCode = CineLyricConfig.keyCode;
        boolean showTranslation = CineLyricConfig.showTranslation;
        int translationOffset = CineLyricConfig.translationOffset;
        String translationSide = CineLyricConfig.translationSide;
        boolean sideMigrated = CineLyricConfig.sideMigrated;
        boolean autoLayout = CineLyricConfig.autoLayout;
        int delayMillis = CineLyricConfig.delayMillis;
        String musicSource = CineLyricConfig.musicSource;
        boolean shadow = CineLyricConfig.shadow;
        int color = CineLyricConfig.color;
        int translationColor = CineLyricConfig.translationColor;
    }

    public static void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            Data data = GSON.fromJson(json, Data.class);
            if (data == null) {
                return;
            }
            boolean migrate = false;
            fontSource = FontLibrary.normalize(data.fontSource);
            fontPrimary = data.fontPrimary == null ? "" : data.fontPrimary;
            fontSecondary = data.fontSecondary == null ? "" : data.fontSecondary;
            fontSize = clamp(data.fontSize, FONT_MIN, FONT_MAX);
            letterSpacing = clamp(data.letterSpacing, 0, 6);
            yPercent = clamp(data.yPercent, 0.0, BAR_RATIO * 100.0);
            xPercent = clamp(data.xPercent, -50.0, 50.0);
            barSide = "top".equalsIgnoreCase(data.barSide) ? "top" : "bottom";
            keyCode = data.keyCode <= 0 ? 76 : data.keyCode;
            showTranslation = data.showTranslation;
            translationOffset = clamp(data.translationOffset, -12, 24);
            translationSide = "above".equals(data.translationSide) ? "above" : "below";
            if (!data.sideMigrated) {
                // 1.0 – 1.1.7 defaulted to "translation above", which read upside down for
                // English songs on DangoMusic; move that untouched default to the new order.
                translationSide = "below";
                sideMigrated = true;
                migrate = true;
            } else {
                sideMigrated = true;
            }
            autoLayout = data.autoLayout;
            delayMillis = clamp(data.delayMillis, 0, 60000);
            musicSource = normalizeSource(data.musicSource);
            shadow = data.shadow;
            color = data.color & 0xFFFFFF;
            translationColor = data.translationColor & 0xFFFFFF;
            if (migrate) {
                save();
            }
        } catch (Exception failure) {
            System.out.println("[CineLyric] could not read config: " + failure);
        }
    }

    public static void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.write(path, GSON.toJson(new Data()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception failure) {
            System.out.println("[CineLyric] could not write config: " + failure);
        }
    }

    /** Fills empty font picks so a freshly installed font folder works without configuring. */
    public static void ensureFontSelection() {
        java.util.List<String> names = FontLibrary.names();
        if (names.isEmpty()) {
            fontPrimary = "";
            if (fontSecondary == null || fontSecondary.isEmpty()) {
                fontSecondary = FontLibrary.NONE;
            }
            return;
        }
        if (!FontLibrary.isPickable(fontPrimary)) {
            fontPrimary = names.get(0);
        }
        if (!FontLibrary.isPickable(fontSecondary)) {
            // Default = the second file in the folder, so both lines differ out of the box.
            fontSecondary = FontLibrary.defaultSecondary(fontPrimary);
        }
    }

    /** Only the three known music-source values survive a config file edit. */
    public static String normalizeSource(String value) {
        if ("muonium".equalsIgnoreCase(value)) {
            return "muonium";
        }
        if ("dango".equalsIgnoreCase(value)) {
            return "dango";
        }
        return "auto";
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
