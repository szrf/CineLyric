/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Only apply the letterbox mixin when Ji and at least one lyric source are present,
 * so the game keeps starting if either mod is removed.
 *
 * <p>Every interface method is implemented explicitly on purpose: the Mixin build
 * shipped with NeoForge 21.1 (sponge-mixin 0.16.4) declares them abstract, while newer
 * builds declare them default. Implementing them keeps one jar working with both.</p>
 */
public final class CineLyricMixinPlugin implements IMixinConfigPlugin {

    private static final String JI_LETTERBOX =
            "com/ji/afkcinematic/variants/v1_21_1/render/LetterboxRenderer.class";
    private static final String MUONIUM_CLOUD_MUSIC =
            "com/muoniumplayer/core/ncm/music/CloudMusic.class";
    private static final String DANGO_MUSIC_PLAYER =
            "me/sorihiko/dango/music/MusicPlayer.class";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        ClassLoader loader = CineLyricMixinPlugin.class.getClassLoader();
        boolean hasJi = loader.getResource(JI_LETTERBOX) != null;
        boolean hasMuonium = loader.getResource(MUONIUM_CLOUD_MUSIC) != null;
        boolean hasDango = loader.getResource(DANGO_MUSIC_PLAYER) != null;
        // The Dango hook only makes sense together with Dango and the camera it should follow.
        boolean dangoTarget = targetClassName.startsWith("me/sorihiko/dango/");
        boolean apply = dangoTarget
                ? hasJi && hasDango
                : hasJi && (hasMuonium || hasDango);
        System.out.println("[CineLyric] mixin gate mixin=" + mixinClassName
                + " target=" + targetClassName + " ji=" + hasJi
                + " muonium=" + hasMuonium + " dango=" + hasDango + " -> " + apply);
        return apply;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
        System.out.println("[CineLyric] mixin applied on " + targetClassName);
    }
}
