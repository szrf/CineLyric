/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import java.util.List;

/**
 * Metadata for the track that is playing right now: title and artist only.
 *
 * <p>Both supported players are optional, so every player call is reflective: a missing mod
 * must only mean "no title", never a {@link NoClassDefFoundError}.
 */
final class SongInfo {

    final String title;
    final String artist;
    final String key;

    private SongInfo(String title, String artist, String key) {
        this.title = title;
        this.artist = artist;
        this.key = key;
    }

    private static volatile SongInfo cached;

    /** Current track, or {@code null} when neither player is playing. */
    static SongInfo current() {
        SongInfo muonium = muonium();
        if (muonium != null) {
            return muonium;
        }
        return dango();
    }

    private static SongInfo muonium() {
        try {
            Class<?> cloud = Class.forName("com.muoniumplayer.core.ncm.music.CloudMusic", false,
                    SongInfo.class.getClassLoader());
            Object player = cloud.getField("player").get(null);
            if (player == null || !Boolean.TRUE.equals(call(player, "isPlaying"))) {
                return null;
            }
            Object music = cloud.getField("currentlyPlaying").get(null);
            if (music == null) {
                return null;
            }
            String title = text(call(music, "getMainTitle"));
            if (title == null) {
                title = text(call(music, "getName"));
            }
            String artist = text(call(music, "getArtistsName"));
            if (title == null) {
                return null;
            }
            return remember(new SongInfo(title, artist == null ? "" : artist,
                    "muonium|" + title + "|" + artist));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SongInfo dango() {
        try {
            Class<?> playerClass = Class.forName("me.sorihiko.dango.music.MusicPlayer", false,
                    SongInfo.class.getClassLoader());
            Object player = playerClass.getField("INSTANCE").get(null);
            if (player == null) {
                return null;
            }
            Object track = call(player, "getShownTrack");
            if (track == null) {
                return null;
            }
            Object state = call(player, "getShownState");
            if (state != null && !"PLAYING".equals(state.toString())) {
                return null;
            }
            String title = text(call(track, "getName"));
            if (title == null) {
                return null;
            }
            String artist = artists(call(track, "getArtists"));
            return remember(new SongInfo(title, artist, "dango|" + title + "|" + artist));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SongInfo remember(SongInfo info) {
        SongInfo old = cached;
        if (old != null && old.key.equals(info.key)) {
            return old;
        }
        cached = info;
        return info;
    }

    private static String artists(Object value) {
        if (value instanceof List<?> list) {
            StringBuilder out = new StringBuilder();
            for (Object item : list) {
                String name = text(item);
                if (name == null) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(name);
            }
            return out.toString();
        }
        String text = text(value);
        return text == null ? "" : text;
    }

    private static Object call(Object target, String name) {
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String text(Object value) {
        if (!(value instanceof String string)) {
            return null;
        }
        String cleaned = CineLyricOverlay.clean(string);
        return cleaned.isEmpty() ? null : cleaned;
    }
}
