/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import com.muoniumplayer.core.ncm.music.AudioPlayer;
import com.muoniumplayer.core.ncm.music.CloudMusic;
import com.muoniumplayer.core.screens.ncm.LyricLine;

/**
 * MuoniumPlayer backend.
 *
 * <p>This class is only ever loaded when {@code CloudMusic} is on the classpath, so the
 * imports above never break a game that does not have Muonium installed.
 */
final class MuoniumSource implements LyricSources.Source {

    static boolean present() {
        try {
            Class.forName("com.muoniumplayer.core.ncm.music.CloudMusic", false,
                    MuoniumSource.class.getClassLoader());
            return true;
        } catch (Throwable missing) {
            return false;
        }
    }

    @Override
    public boolean playing() {
        AudioPlayer player = CloudMusic.player;
        try {
            return player != null && player.isPlaying();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public LyricSources.Line current() {
        AudioPlayer player = CloudMusic.player;
        // Muonium only refreshes CloudMusic.currentLyric from its own HUD lyric widget. Ji hides
        // the HUD during a cinematic, so that field freezes on whatever line was showing when the
        // shot started (and is null before the first refresh). Always ask the timeline for the
        // line at the current playback position instead; fall back to the cached field only when
        // the timeline has no lyrics yet.
        LyricLine line = null;
        if (player != null) {
            try {
                line = CloudMusic.findCurrentLyric(player.getCurrentTimeMillis());
            } catch (Throwable ignored) {
                line = null;
            }
        }
        if (line == null) {
            line = CloudMusic.currentLyric;
        }
        if (line == null) {
            return null;
        }
        return new LyricSources.Line(CineLyricOverlay.clean(line.lyric),
                CineLyricOverlay.clean(line.translationText), alpha(line, player));
    }

    /**
     * Opacity of the line that is showing.
     *
     * <p>This used to fade each line in and out. Two things went wrong with that: the fade
     * started at zero right after the line began, so the subtitle went blank for a moment and
     * then faded back in - read as "the second line blinks" - and, because the opacity is baked
     * into the rasterized texture, every step of the fade was a fresh AWT rasterization and
     * texture upload, which showed up as a hitch exactly at the line change. A sub-title simply
     * swaps: the new line is drawn at full opacity from its first frame.
     */
    private static float alpha(LyricLine line, AudioPlayer player) {
        return 1.0F;
    }
}
