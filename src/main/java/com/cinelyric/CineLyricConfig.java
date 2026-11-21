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
    /** Separate size for the translation line; 0 follows the main line size. */
    public static int translationFontSize = 0;
    /** Extra pixels between characters; 0 is normal tracking. */
    public static int letterSpacing = 0;

    /** Draw the current song title above the lyric line. */
    public static boolean showSongTitle = true;
    /** Independent song-title font size. */
    public static int songTitleSize = 12;
    /** Independent pixels between title characters (does not affect the icon). */
    public static int songTitleLetterSpacing = 0;
    /** left / center / right / custom */
    public static String songTitleAlign = "center";
    /** Pixel offsets of the whole title block from the screen centre (custom mode only). */
    public static int songTitleX = 0;
    public static int songTitleY = 0;
    /** Rainbow/flowing colour instead of white. */
    public static boolean songTitleColorful = false;
    /** none / note (album art was removed in the final icon design) */
    public static String songTitleIcon = "note";
    /** 0 means "same as the title line height". */
    public static int songTitleIconWidth = 0;
    public static int songTitleIconHeight = 0;
    public static int songTitleIconGap = 4;
    public static int songTitleIconOffsetY = 0;
    /** Animate the note hue; off uses a fixed white note. */
    public static boolean songTitleNoteColor = true;
    /** Animate the note up/down; off keeps it still. */
    public static boolean songTitleNoteBounce = true;
    /** Peak vertical bounce in pixels. */
    public static int songTitleNoteBounceAmount = 2;
    /** True = the note hides together with a delayed title; false = note shows immediately. */
    public static boolean songTitleNoteFollowsDelay = true;
    /** Distance from the bar's outer edge to the first text line, in percent of screen height. */
    public static double yPercent = 4.0;
    /** Horizontal offset in percent of screen width, positive moves right. */
    public static double xPercent = 0.0;
    /** Which letterbox bar the subtitle lives in: {@code "bottom"} or {@code "top"}. */
    public static String barSide = "bottom";
    /** GLFW key code of the settings hotkey; the KeyMapping stays the source of truth when possible. */
    public static int keyCode = 76; // L (clashes with the vanilla advancements key, by request)
    public static boolean showTranslation = true;
    /** Skip the translated line entirely when the original lyric already contains Chinese. */
    public static boolean skipChineseTranslation = true;
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
    /** Title / translation controls use a wider range than the lyric line. */
    public static final int TITLE_FONT_MIN = 8;
    public static final int TITLE_FONT_MAX = 32;

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
        int translationFontSize = CineLyricConfig.translationFontSize;
        int letterSpacing = CineLyricConfig.letterSpacing;
        Boolean showSongTitle = CineLyricConfig.showSongTitle;
        int songTitleSize = CineLyricConfig.songTitleSize;
        int songTitleLetterSpacing = CineLyricConfig.songTitleLetterSpacing;
        String songTitleAlign = CineLyricConfig.songTitleAlign;
        int songTitleX = CineLyricConfig.songTitleX;
        int songTitleY = CineLyricConfig.songTitleY;
        boolean songTitleColorful = CineLyricConfig.songTitleColorful;
        String songTitleIcon = CineLyricConfig.songTitleIcon;
        int songTitleIconWidth = CineLyricConfig.songTitleIconWidth;
        int songTitleIconHeight = CineLyricConfig.songTitleIconHeight;
        int songTitleIconGap = CineLyricConfig.songTitleIconGap;
        int songTitleIconOffsetY = CineLyricConfig.songTitleIconOffsetY;
        boolean songTitleNoteColor = CineLyricConfig.songTitleNoteColor;
        boolean songTitleNoteBounce = CineLyricConfig.songTitleNoteBounce;
        int songTitleNoteBounceAmount = CineLyricConfig.songTitleNoteBounceAmount;
        boolean songTitleNoteFollowsDelay = CineLyricConfig.songTitleNoteFollowsDelay;
        double yPercent = CineLyricConfig.yPercent;
        double xPercent = CineLyricConfig.xPercent;
        String barSide = CineLyricConfig.barSide;
        int keyCode = CineLyricConfig.keyCode;
        boolean showTranslation = CineLyricConfig.showTranslation;
        Boolean skipChineseTranslation = CineLyricConfig.skipChineseTranslation;
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
            // 0 is meaningful: follow the main line size.
            translationFontSize = data.translationFontSize <= 0 ? 0
                    : clamp(data.translationFontSize, TITLE_FONT_MIN, TITLE_FONT_MAX);
            letterSpacing = clamp(data.letterSpacing, 0, 6);
            showSongTitle = data.showSongTitle == null || data.showSongTitle;
            songTitleSize = clamp(data.songTitleSize, TITLE_FONT_MIN, TITLE_FONT_MAX);
            songTitleLetterSpacing = clamp(data.songTitleLetterSpacing, 0, 10);
            songTitleAlign = normalizeAlign(data.songTitleAlign);
            songTitleX = clamp(data.songTitleX, -400, 400);
            songTitleY = clamp(data.songTitleY, -200, 200);
            songTitleColorful = data.songTitleColorful;
            songTitleIcon = normalizeIcon(data.songTitleIcon);
            songTitleIconWidth = clamp(data.songTitleIconWidth, 0, 128);
            songTitleIconHeight = clamp(data.songTitleIconHeight, 0, 128);
            songTitleIconGap = clamp(data.songTitleIconGap, -8, 64);
            songTitleIconOffsetY = clamp(data.songTitleIconOffsetY, -64, 64);
            songTitleNoteColor = data.songTitleNoteColor;
            songTitleNoteBounce = data.songTitleNoteBounce;
            songTitleNoteBounceAmount = clamp(data.songTitleNoteBounceAmount, 0, 8);
            songTitleNoteFollowsDelay = data.songTitleNoteFollowsDelay;
            yPercent = clamp(data.yPercent, 0.0, BAR_RATIO * 100.0);
            xPercent = clamp(data.xPercent, -50.0, 50.0);
            barSide = "top".equalsIgnoreCase(data.barSide) ? "top" : "bottom";
            keyCode = data.keyCode <= 0 ? 76 : data.keyCode;
            showTranslation = data.showTranslation;
            skipChineseTranslation = data.skipChineseTranslation == null || data.skipChineseTranslation;
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

    /** Only the known title alignments survive a config file edit. */
    public static String normalizeAlign(String value) {
        if ("left".equalsIgnoreCase(value)) {
            return "left";
        }
        if ("right".equalsIgnoreCase(value)) {
            return "right";
        }
        if ("custom".equalsIgnoreCase(value)) {
            return "custom";
        }
        return "center";
    }

    /** Only the note/none icon modes survive; album art was removed from the design. */
    public static String normalizeIcon(String value) {
        if ("note".equalsIgnoreCase(value) || "album".equalsIgnoreCase(value)) {
            return "note";
        }
        return "none";
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

    /** True when the lyric already contains a Han character. */
    public static boolean containsChinese(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fa5') {
                return true;
            }
        }
        return false;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
