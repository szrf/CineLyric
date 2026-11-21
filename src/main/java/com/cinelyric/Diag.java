/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tiny append-only diagnostic log ({@code config/cinelyric/diag.txt}).
 *
 * <p>When a music backend silently yields no lyric there is nothing visible to debug from the
 * outside, and the launcher swallows {@code System.out}. So the interesting steps write one
 * short line here instead.</p>
 *
 * <p>The file is recycled instead of being abandoned: a per-frame log storm used to fill the
 * 128 KiB budget and then silently switch diagnostics off for the rest of the session, which is
 * exactly when the interesting part started. Identical consecutive lines are also folded to one
 * per second for the same reason.</p>
 */
final class Diag {

    private static final File FILE = new File("config/cinelyric/diag.txt");
    private static final long MAX_BYTES = 128L * 1024L;
    private static final long REPEAT_WINDOW_NANOS = 1_000_000_000L;

    private static String lastMessage;
    private static long lastWriteAt;
    private static final Set<String> ONCE = Collections.synchronizedSet(new LinkedHashSet<>());

    private Diag() {
    }

    static void note(String message) {
        try {
            long now = System.nanoTime();
            if (message.equals(lastMessage) && now - lastWriteAt < REPEAT_WINDOW_NANOS) {
                return;
            }
            lastMessage = message;
            lastWriteAt = now;
            File parent = FILE.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            if (FILE.length() > MAX_BYTES) {
                recycle();
            }
            Files.writeString(FILE.toPath(),
                    LocalTime.now() + " " + message + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
            // diagnostics must never break the subtitle
        }
    }

    /** Writes the first line of a throwable, which is enough to recognise the failure. */
    static void note(String message, Throwable failure) {
        note(message + " -> " + failure.getClass().getName()
                + (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
    }

    /** Writes {@code message} the first time it is seen in this session. */
    static void noteOnce(String message) {
        if (ONCE.add(message)) {
            note(message);
        }
    }

    /** Keeps the previous run around as {@code diag.old.txt} instead of growing forever. */
    private static void recycle() {
        File previous = new File(FILE.getPath() + ".old");
        if (previous.exists() && !previous.delete()) {
            // the old copy is only a convenience; overwriting the current log still wins
        }
        if (!FILE.renameTo(previous)) {
            FILE.delete();
        }
        try {
            Files.writeString(FILE.toPath(),
                    LocalTime.now() + " [diag] rotated (previous run kept in diag.txt.old)"
                            + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
            // nothing else to do
        }
    }
}
