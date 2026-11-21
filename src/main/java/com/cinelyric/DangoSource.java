/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DangoMusic backend, reached entirely through reflection.
 *
 * <p>DangoMusic is Kotlin and its lyric cache / now-playing state are private implementation
 * details, so the mod is never linked at compile time: every call is looked up lazily and any
 * mismatch degrades to "no subtitle" instead of a crash.
 *
 * <p>Dango only refreshes its own record while its HUD render event fires, and Ji cancels the
 * whole HUD during a cinematic shot. So instead of trusting that record we ask the module to
 * recompute it from the player state ({@code nowPlaying()}), fetch the lyric document for that
 * track ({@code lyricsFor(key)} / {@code requestLyrics(...)}) and pick the line ourselves.
 *
 * <p>Every reflected member is opened with {@link AccessibleObject#setAccessible(boolean)}. This
 * is not optional: {@code LyricsModule$NowPlaying} and {@code LyricsModule$Floating} are
 * package-private Kotlin classes, so their {@code public} getters are still inaccessible from
 * another mod's module. Missing that call used to abort the whole read (and therefore every
 * subtitle) with an {@code IllegalAccessException}, while the outer lookups kept succeeding.
 */
final class DangoSource implements LyricSources.Source {

    private static final String DANGOMUSIC = "me.sorihiko.dango.DangoMusic";
    private static final String MUSIC_PLAYER = "me.sorihiko.dango.music.MusicPlayer";
    private static final String TRACK = "me.sorihiko.dango.music.NeteaseTrack";
    private static final String LYRICS_MODULE = "me.sorihiko.dango.module.impls.music.LyricsModule";
    private static final String NOW_PLAYING = LYRICS_MODULE + "$NowPlaying";

    /** Warnings already written, so a per-frame lookup can never flood the diagnostics. */
    private static final Set<String> WARNED = new HashSet<>();

    private final Object musicPlayer;
    private final Object module;
    private final Method shownActiveMethod;
    private final Method shownTrackMethod;
    private final Method positionMethod;
    private final Method shownStateMethod;
    private final Method trackKeyMethod;
    private final Method nowPlayingMethod;
    private final Method requestLyricsMethod;
    private final Method lyricsForMethod;
    private final Method nowPlayingKeyMethod;
    private final Field activeField;
    private final Field cacheField;

    private Method linesMethod;
    private Method indexAtMethod;
    private Method textMethod;
    private Method lineTranslationMethod;
    private Method floatingLinesMethod;
    private Method floatingTranslationMethod;
    private long nextNote;
    private int dumpCount;

    static boolean present() {
        try {
            Class.forName(MUSIC_PLAYER, false, DangoSource.class.getClassLoader());
            Class.forName(DANGOMUSIC, false, DangoSource.class.getClassLoader());
            return true;
        } catch (Throwable missing) {
            Diag.note("[dango] not installed: " + missing.getClass().getSimpleName());
            return false;
        }
    }

    DangoSource() throws Exception {
        ClassLoader loader = DangoSource.class.getClassLoader();
        Class<?> playerClass = Class.forName(MUSIC_PLAYER, true, loader);
        this.musicPlayer = playerClass.getField("INSTANCE").get(null);
        this.shownActiveMethod = publicMethod(playerClass, "getShownActive");
        this.shownTrackMethod = publicMethod(playerClass, "getShownTrack");
        this.positionMethod = publicMethod(playerClass, "getShownPositionMs");
        this.shownStateMethod = publicMethod(playerClass, "getShownState");
        Class<?> trackClass = Class.forName(TRACK, false, loader);
        this.trackKeyMethod = publicMethod(trackClass, "getKey");

        Class<?> dangoClass = Class.forName(DANGOMUSIC, true, loader);
        Object dango = staticField(dangoClass, "instance");
        if (dango == null) {
            throw new IllegalStateException("DangoMusic instance is still null");
        }
        Object manager = invoke(publicMethod(dangoClass, "getModuleManager"), dango);
        Class<?> moduleClass = Class.forName(LYRICS_MODULE, true, loader);
        Method get = publicMethod(manager.getClass(), "get", Class.class);
        this.module = invoke(get, manager, moduleClass);
        if (this.module == null) {
            throw new IllegalStateException("lyric module not registered");
        }

        Class<?> nowPlayingClass = Class.forName(NOW_PLAYING, false, loader);
        this.nowPlayingMethod = declaredMethod(moduleClass, "nowPlaying");
        this.requestLyricsMethod = declaredMethod(moduleClass, "requestLyrics", nowPlayingClass);
        this.lyricsForMethod = declaredMethod(moduleClass, "lyricsFor", String.class);
        this.nowPlayingKeyMethod = declaredMethod(nowPlayingClass, "getKey");
        this.activeField = instanceField(moduleClass, "active");
        this.cacheField = instanceField(moduleClass, "cache");
        Diag.note("[dango] backend ready (" + moduleClass.getName() + ")");
    }

    // ------------------------------------------------------------------ reflection plumbing

    /** Opens a reflected member, and remembers the ones the JVM refused (module not opened). */
    private static boolean open(AccessibleObject member, String what) {
        try {
            member.setAccessible(true);
            return true;
        } catch (Throwable refused) {
            warn("cannot open " + what + ": " + describe(refused));
            return false;
        }
    }

    private static void warn(String message) {
        synchronized (WARNED) {
            if (!WARNED.add(message)) {
                return;
            }
        }
        Diag.note("[dango] " + message);
    }

    private static Method publicMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            Method method = owner.getMethod(name, params);
            open(method, owner.getSimpleName() + "." + name);
            return method;
        } catch (Throwable missing) {
            warn("missing method " + owner.getSimpleName() + "." + name);
            return null;
        }
    }

    /** Declared lookup first (Kotlin internals), public lookup as a fallback for other builds. */
    private static Method declaredMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            Method method = owner.getDeclaredMethod(name, params);
            open(method, owner.getSimpleName() + "." + name);
            return method;
        } catch (Throwable notDeclared) {
            try {
                Method method = owner.getMethod(name, params);
                open(method, owner.getSimpleName() + "." + name);
                return method;
            } catch (Throwable missingEither) {
                warn("missing method " + owner.getSimpleName() + "." + name);
                return null;
            }
        }
    }

    private static Field instanceField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            open(field, owner.getSimpleName() + "." + name);
            return field;
        } catch (Throwable missing) {
            warn("missing field " + owner.getSimpleName() + "." + name);
            return null;
        }
    }

    private static Object staticField(Class<?> owner, String name) throws Exception {
        Field field = instanceField(owner, name);
        if (field == null) {
            throw new NoSuchFieldException(owner.getName() + "." + name);
        }
        return field.get(null);
    }

    /** Unwraps the reflective wrapper so callers can report the real failure. */
    private static Object invoke(Method method, Object target, Object... args) throws Exception {
        if (method == null) {
            throw new NoSuchMethodException("reflective member was not found");
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw wrapper;
        }
    }

    /** Kotlin hands out empty strings freely; both matter as "no value" here. */
    private static String text(Object value) {
        if (!(value instanceof String string)) {
            return null;
        }
        String cleaned = CineLyricOverlay.clean(string);
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String describe(Throwable failure) {
        return failure.getClass().getName()
                + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
    }

    // ------------------------------------------------------------------ lyric lookup

    @Override
    public boolean playing() {
        // getShownActive() also reports a paused Dango, so the state enum decides.
        if (shownStateMethod == null) {
            return false;
        }
        try {
            Object state = invoke(shownStateMethod, musicPlayer);
            return state != null && "PLAYING".equals(state.toString());
        } catch (Throwable broken) {
            noteWhy("state read failed: " + describe(broken));
            return false;
        }
    }

    @Override
    public LyricSources.Line current() {
        try {
            // Idle players keep their last track around; honouring that would freeze the last
            // lyric line on screen forever after the music stops.
            if (shownActiveMethod != null
                    && !Boolean.TRUE.equals(invoke(shownActiveMethod, musicPlayer))) {
                noteWhy("player idle");
                return null;
            }
            if (nowPlayingMethod == null || lyricsForMethod == null) {
                return fromActive("no nowPlaying()/lyricsFor()");
            }
            Object nowPlaying = invoke(nowPlayingMethod, module);
            String key = nowPlaying == null ? null : text(invoke(nowPlayingKeyMethod, nowPlaying));
            if (key == null) {
                key = shownKey();
            }
            Object lyrics = key == null ? null : invoke(lyricsForMethod, module, key);
            if (lyrics == null) {
                if (requestLyricsMethod != null && nowPlaying != null) {
                    // Safe every frame: the module de-duplicates with `pending` and backs off on
                    // "not found" for 30 s. The document arrives on its worker, next frame hits.
                    invoke(requestLyricsMethod, module, nowPlaying);
                    noteWhy("lyric fetch requested for key=" + key);
                } else {
                    noteWhy("no lyric document for key=" + key);
                }
                return fromActive("no lyrics yet for key=" + key);
            }
            long position = ((Number) invoke(positionMethod, musicPlayer)).longValue();
            List<?> lines = lines(lyrics);
            int index = indexAt(lyrics, position);
            if (index < 0 || index >= lines.size()) {
                return fromActive("index " + index + " out of " + lines.size()
                        + " at " + position + "ms");
            }
            Object line = lines.get(index);
            return new LyricSources.Line(textOf(line), translationOf(line), 1.0F);
        } catch (Throwable broken) {
            noteWhy("read failed: " + describe(broken));
            return fromActive("read failed");
        }
    }

    /** The track the player itself would show; used when the module's record is missing. */
    private String shownKey() {
        try {
            Object track = invoke(shownTrackMethod, musicPlayer);
            return track == null ? null : text(invoke(trackKeyMethod, track));
        } catch (Throwable broken) {
            noteWhy("track key read failed: " + describe(broken));
            return null;
        }
    }

    /** Mirrors the module's own "floating" record: the rows it last drew, with translation. */
    private LyricSources.Line fromActive(String why) {
        try {
            Object floating = activeField == null ? null : activeField.get(module);
            if (floating == null) {
                noteWhy(why + "; floating=null");
                return null;
            }
            if (floatingLinesMethod == null) {
                floatingLinesMethod = declaredMethod(floating.getClass(), "getLines");
            }
            Object rows = invoke(floatingLinesMethod, floating);
            if (!(rows instanceof List<?> list) || list.isEmpty()) {
                noteWhy(why + "; floating rows empty");
                return null;
            }
            String main = CineLyricOverlay.clean(String.valueOf(list.get(0)));
            String translation = "";
            if (floatingTranslationMethod == null) {
                floatingTranslationMethod = declaredMethod(floating.getClass(), "getTranslation");
            }
            Object extra = invoke(floatingTranslationMethod, floating);
            if (extra instanceof List<?> extraRows && !extraRows.isEmpty()) {
                translation = CineLyricOverlay.clean(String.valueOf(extraRows.get(0)));
            }
            return main.isEmpty() && translation.isEmpty()
                    ? null : new LyricSources.Line(main, translation, 1.0F);
        } catch (Throwable broken) {
            noteWhy(why + "; floating read failed: " + describe(broken));
            return null;
        }
    }

    private void noteWhy(String why) {
        long now = System.nanoTime();
        if (now < nextNote) {
            return;
        }
        nextNote = now + 5_000_000_000L;
        Diag.note("[dango] " + why);
    }

    // ------------------------------------------------------------------ lyrics document

    private List<?> lines(Object lyrics) throws Exception {
        if (linesMethod == null) {
            linesMethod = declaredMethod(lyrics.getClass(), "getLines");
        }
        Object value = invoke(linesMethod, lyrics);
        return value instanceof List<?> list ? list : List.of();
    }

    private int indexAt(Object lyrics, long position) throws Exception {
        if (indexAtMethod == null) {
            indexAtMethod = declaredMethod(lyrics.getClass(), "indexAt", long.class);
        }
        return ((Number) invoke(indexAtMethod, lyrics, position)).intValue();
    }

    private String textOf(Object line) throws Exception {
        if (textMethod == null) {
            textMethod = declaredMethod(line.getClass(), "getText");
        }
        String value = text(invoke(textMethod, line));
        return value == null ? "" : value;
    }

    private String translationOf(Object line) throws Exception {
        if (lineTranslationMethod == null) {
            lineTranslationMethod = declaredMethod(line.getClass(), "getTranslation");
        }
        String value = text(invoke(lineTranslationMethod, line));
        return value == null ? "" : value;
    }

    /**
     * Full snapshot of what the reflection sees. Called whenever the settings screen opens, so
     * one launch is enough to explain a silent "no subtitle" without needing a cinematic shot.
     */
    void dump() {
        if (dumpCount >= 3) {
            return;
        }
        dumpCount++;
        try {
            boolean shownActive = shownActiveMethod != null
                    && Boolean.TRUE.equals(invoke(shownActiveMethod, musicPlayer));
            Object nowPlaying = nowPlayingMethod == null ? null : invoke(nowPlayingMethod, module);
            String key = nowPlaying == null ? null : text(invoke(nowPlayingKeyMethod, nowPlaying));
            String keySource = key == null ? "none" : "nowPlaying";
            if (key == null) {
                key = shownKey();
                keySource = key == null ? "none" : "track";
            }
            Object track = shownTrackMethod == null ? null : invoke(shownTrackMethod, musicPlayer);
            String trackKey = track == null ? null : text(invoke(trackKeyMethod, track));
            Object cache = cacheField == null ? null : cacheField.get(module);
            int cacheSize = cache instanceof Map<?, ?> map ? map.size() : -1;
            Object floating = activeField == null ? null : activeField.get(module);
            long position = positionMethod == null ? -1
                    : ((Number) invoke(positionMethod, musicPlayer)).longValue();
            Object lyrics = key == null || lyricsForMethod == null
                    ? null : invoke(lyricsForMethod, module, key);

            Diag.note("[dango] dump: shownActive=" + shownActive
                    + " key=" + key + " (" + keySource + ")"
                    + " track=" + (track == null ? "null" : track.getClass().getSimpleName() + "/" + trackKey)
                    + " cache=" + cacheSize
                    + " lyrics=" + (lyrics == null ? "null" : "present")
                    + " floating=" + (floating == null ? "null" : "present")
                    + " positionMs=" + position);

            if (cache instanceof Map<?, ?> map && cacheSize > 0) {
                StringBuilder keys = new StringBuilder();
                int n = 0;
                for (Object k : map.keySet()) {
                    keys.append(k).append(' ');
                    if (++n >= 4) {
                        break;
                    }
                }
                Diag.note("[dango] dump: cache keys " + keys);
            }
            if (lyrics != null) {
                List<?> lines = lines(lyrics);
                int index = indexAt(lyrics, position);
                Diag.note("[dango] dump: lines=" + lines.size() + " index=" + index);
                if (index >= 0 && index < lines.size()) {
                    Object line = lines.get(index);
                    Diag.note("[dango] dump: text=" + textOf(line)
                            + " | translation=" + translationOf(line));
                }
            } else if (nowPlaying != null && requestLyricsMethod != null) {
                // The settings page is a good moment to warm the cache; the module de-duplicates.
                invoke(requestLyricsMethod, module, nowPlaying);
                Diag.note("[dango] dump: lyric fetch requested");
            }
        } catch (Throwable broken) {
            Diag.note("[dango] dump failed", broken);
        }
    }
}
