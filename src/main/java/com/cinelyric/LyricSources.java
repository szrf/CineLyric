/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

/**
 * Finds whichever music player is installed and hands back the lyric line that is playing.
 *
 * <p>MuoniumPlayer and DangoMusic are both optional and their lyric stores are quite
 * different, so each backend lives in its own class: a missing mod is then a plain
 * {@code Class.forName} miss instead of a hard link error, and the game starts with either
 * mod installed, both, or neither.
 *
 * <p>The user can force a backend in the settings ({@link CineLyricConfig#musicSource}).
 * In {@code auto} mode the installed backends are tried in order every frame until one of
 * them actually has a line, so an idle player never masks the other one.
 */
public final class LyricSources {

    /** One rendered lyric line. {@code translation} may be empty. */
    public static final class Line {
        public final String main;
        public final String translation;
        public final float alpha;

        public Line(String main, String translation, float alpha) {
            this.main = main == null ? "" : main;
            // Chinese originals are never translated by CineLyric: keep the text in the main
            // line only, so a player-supplied Chinese translation cannot appear a second time.
            this.translation = CineLyricConfig.skipChineseTranslation
                    && CineLyricConfig.containsChinese(this.main)
                    ? "" : (translation == null ? "" : translation);
            this.alpha = alpha;
        }
    }

    public interface Source {
        /** Current line, or {@code null} while nothing is playing. */
        Line current();

        /** Whether this backend's player is actually playing, not just paused on a stale line. */
        default boolean playing() {
            return false;
        }
    }

    private static Source[] chain = new Source[0];
    private static Source[] every = new Source[0];
    private static boolean resolved;
    private static boolean incomplete;
    private static long retryAt;
    private static String active = "none";

    private LyricSources() {
    }

    /** Drops the cached backends so the next call re-detects (after switching mode). */
    public static synchronized void reset() {
        resolved = false;
        incomplete = false;
        chain = new Source[0];
        every = new Source[0];
        active = "none";
    }

    /** Which backend produced the last line, for the log. */
    public static synchronized String activeName() {
        return active;
    }

    public static synchronized Line current() {
        // a backend can be temporarily unavailable while its mod is still booting, so a
        // failed detection is retried instead of being remembered forever
        if (resolved && incomplete && System.nanoTime() >= retryAt) {
            resolved = false;
        }
        resolve();
        // A paused player keeps its last line forever, which used to mask the other player
        // whenever both were installed. Prefer whichever one is actually playing; when both
        // are, chain order decides (MuoniumPlayer first, as the "first" source).
        Line line = pick(chain, true);
        if (line == null) {
            line = pick(chain, false);
        }
        if (line == null && every.length > chain.length) {
            // The forced source is idle while the other player is actually playing. Silence is
            // never what the user wants here, so fall back to the playing player. The rescue is
            // deliberately silent about noteActive: otherwise every frame would flip the tag
            // between the rescuing backend and "fallback" and flood the diagnostics.
            Line rescue = pick(every, true, false);
            if (rescue != null) {
                if (!"fallback".equals(active)) {
                    active = "fallback";
                    Diag.note("[sources] forced mode="
                            + CineLyricConfig.normalizeSource(CineLyricConfig.musicSource)
                            + " was silent -> using the player that is actually playing");
                }
                line = rescue;
            }
        }
        return line;
    }

    private static Line pick(Source[] from, boolean onlyPlaying) {
        return pick(from, onlyPlaying, true);
    }

    private static Line pick(Source[] from, boolean onlyPlaying, boolean note) {
        for (Source candidate : from) {
            try {
                if (onlyPlaying && !candidate.playing()) {
                    continue;
                }
                Line line = candidate.current();
                if (line != null && (!line.main.isEmpty() || !line.translation.isEmpty())) {
                    if (note) {
                        noteActive(candidate, onlyPlaying);
                    }
                    return line;
                }
            } catch (Throwable ignored) {
                // try the next backend
            }
        }
        return null;
    }

    private static void noteActive(Source candidate, boolean playing) {
        String name = candidate instanceof MuoniumSource ? "MuoniumPlayer" : "DangoMusic";
        String tag = name + (playing ? " (playing)" : " (idle)");
        if (!tag.equals(active)) {
            active = tag;
            Diag.note("[sources] active -> " + tag);
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        String mode = CineLyricConfig.normalizeSource(CineLyricConfig.musicSource);
        boolean allowMuonium = !"dango".equals(mode);
        boolean allowDango = !"muonium".equals(mode);

        java.util.List<Source> found = new java.util.ArrayList<>(2);
        java.util.List<Source> installed = new java.util.ArrayList<>(2);
        if (MuoniumSource.present()) {
            try {
                Source source = new MuoniumSource();
                installed.add(source);
                if (allowMuonium) {
                    found.add(source);
                }
            } catch (Throwable ignored) {
                incomplete = true;
                retryAt = System.nanoTime() + 2_000_000_000L;
            }
        }
        if (DangoSource.present()) {
            try {
                Source source = new DangoSource();
                installed.add(source);
                if (allowDango) {
                    found.add(source);
                }
            } catch (Throwable broken) {
                incomplete = true;
                retryAt = System.nanoTime() + 2_000_000_000L;
                Diag.note("[dango] init failed", broken);
            }
        }
        chain = found.toArray(new Source[0]);
        every = installed.toArray(new Source[0]);
        StringBuilder names = new StringBuilder();
        for (Source s : chain) {
            names.append(s instanceof MuoniumSource ? "muonium " : "dango ");
        }
        Diag.note("[sources] mode=" + mode + " chain=[" + names.toString().trim() + "]");
    }

    /** Names of the backends that are currently wired, for diagnostics. */
    public static synchronized String chainNames() {
        resolve();
        StringBuilder names = new StringBuilder();
        for (Source s : chain) {
            names.append(s instanceof MuoniumSource ? "muonium " : "dango ");
        }
        String out = names.toString().trim();
        return out.isEmpty() ? "none" : out;
    }

    /** Writes a snapshot of every backend that can explain itself (called from the settings UI). */
    public static synchronized void dumpBackends() {
        resolve();
        for (Source s : every) {
            if (s instanceof DangoSource dango) {
                dango.dump();
            }
        }
    }
}
