/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Bridges Ji's cinematic camera to Dango's world (3D) lyrics.
 *
 * <p>Dango anchors its floating 3D lyrics on the player's eyes, so during a Ji cinematic the
 * lyrics stay glued to the body while the shot flies off somewhere else. While Ji reports that
 * the cinematic camera owns the frame, this class hands Dango the render camera pose instead, so
 * the lyrics follow the picture the player is actually looking at.</p>
 *
 * <p>Only the anchor is replaced: Dango still spawns each line with its own random spread,
 * height offset and distance, and the line still hangs in the world exactly like it does for a
 * walking player. Nothing is re-pinned per frame, so the text keeps its "3D in the scene" feel
 * instead of becoming a screen-locked overlay.</p>
 *
 * <p>Everything here is reflective and best effort: without Ji (or if Ji renames its API) the
 * lookup fails, the bridge reports "no cinematic" and Dango keeps its own player-anchored pose.</p>
 */
public final class JiCinematic {

    private static final String MANAGER =
            "com.ji.afkcinematic.variants.v1_21_1.cinematic.CinematicManager";
    private static final String LETTERBOX =
            "com.ji.afkcinematic.variants.v1_21_1.render.LetterboxRenderer";
    private static final String ACTIVE_STATE = "CINEMATIC_ACTIVE";
    private static final String PLAYER_POSE = "me.sorihiko.dango.platform.PlayerPose";

    /** {@code CinematicManager.getState()} -> CinematicState enum constant. */
    private static Method stateMethod;
    private static boolean stateLookedUp;

    /** {@code LetterboxRenderer.isVisible()}, used when the state API is missing. */
    private static Method visibleMethod;
    private static boolean visibleLookedUp;

    /** {@code new PlayerPose(double, double, double, float, float)}. */
    private static Constructor<?> poseConstructor;
    private static boolean poseLookedUp;

    private static boolean announced;

    private JiCinematic() {
    }

    /**
     * Returns Dango's {@code PlayerPose} for the current cinematic camera, or {@code null} when
     * no cinematic is running (in which case Dango should keep its own pose).
     */
    public static Object cameraPose() {
        Object pose = poseOrNull();
        if (pose == null) {
            announced = false;
        }
        return pose;
    }

    private static Object poseOrNull() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return null;
        }
        if (!cinematic()) {
            return null;
        }
        Constructor<?> constructor = poseConstructor();
        if (constructor == null) {
            return null;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) {
            return null;
        }
        Vec3 pos = camera.getPosition();
        try {
            Object pose = constructor.newInstance(pos.x, pos.y, pos.z,
                    (Object) camera.getYRot(), camera.getXRot());
            if (!announced) {
                announced = true;
                Diag.note("[cinematic] 3d lyric anchor -> camera "
                        + String.format(java.util.Locale.ROOT, "%.1f / %.1f / %.1f", pos.x, pos.y, pos.z));
            }
            return pose;
        } catch (Throwable failure) {
            Diag.note("[cinematic] camera pose failed", failure);
            return null;
        }
    }

    /** True while Ji's cinematic camera is rendering the world instead of the player's eyes. */
    static boolean cinematic() {
        Object state = managerState();
        if (state != null && ACTIVE_STATE.equals(String.valueOf(state))) {
            return true;
        }
        return letterboxVisible();
    }

    private static Object managerState() {
        if (!stateLookedUp) {
            stateLookedUp = true;
            try {
                Class<?> type = Class.forName(MANAGER, false, JiCinematic.class.getClassLoader());
                Method method = type.getMethod("getState");
                method.setAccessible(true);
                stateMethod = method;
            } catch (Throwable ignored) {
                stateMethod = null;
            }
        }
        if (stateMethod == null) {
            return null;
        }
        try {
            return stateMethod.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean letterboxVisible() {
        if (!visibleLookedUp) {
            visibleLookedUp = true;
            try {
                Class<?> type = Class.forName(LETTERBOX, false, JiCinematic.class.getClassLoader());
                Method method = type.getMethod("isVisible");
                method.setAccessible(true);
                visibleMethod = method;
            } catch (Throwable ignored) {
                visibleMethod = null;
            }
        }
        if (visibleMethod == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(visibleMethod.invoke(null));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Constructor<?> poseConstructor() {
        if (!poseLookedUp) {
            poseLookedUp = true;
            try {
                Class<?> type = Class.forName(PLAYER_POSE, false, JiCinematic.class.getClassLoader());
                Constructor<?> constructor = type.getConstructor(
                        double.class, double.class, double.class, float.class, float.class);
                constructor.setAccessible(true);
                poseConstructor = constructor;
            } catch (Throwable ignored) {
                poseConstructor = null;
            }
        }
        return poseConstructor;
    }
}
