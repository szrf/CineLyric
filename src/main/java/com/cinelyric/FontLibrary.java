/*
 * CineLyric - Ji AFK Cinematic 的电影黑边歌词字幕
 * Copyright (c) 2026 神之二法 & DeepSeek
 * SPDX-License-Identifier: MIT
 * 完整条款见仓库根目录的 LICENSE。
 */
package com.cinelyric;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Turns the font files in {@code config/cinelyric/fonts/} into vanilla {@link FontSet}s.
 *
 * <p>Every engine now uses the same glyph pipeline as the game itself:
 * <ul>
 *   <li>{@link #AUTO} - the vanilla {@code FontSet}, i.e. the game's own bitmap font.</li>
 *   <li>{@link #CUSTOM} - one {@code FontSet} built from the selected files with the same
 *       FreeType provider Minecraft uses for resource-pack fonts. The primary file is
 *       tried first, the secondary file only supplies characters the primary lacks.</li>
 *   <li>{@link #PLAYER} - the same thing, using the font file Muonium is currently using.</li>
 * </ul>
 *
 * <p>The font set is consumed by {@link LyricTextRenderer}, not by {@code Font#drawString}.
 * That matters because ModernUI overwrites {@code Font#drawInBatch} and ignores the
 * {@code FontSet} it is given; drawing the baked glyphs ourselves keeps custom fonts and
 * real letter spacing working whether ModernUI is installed or not.
 */
public final class FontLibrary {

    public static final String AUTO = "auto";
    public static final String CUSTOM = "custom";
    public static final String PLAYER = "player";
    /** Sentinel stored for "no font chosen" - both the primary and the secondary row allow it. */
    public static final String NONE = "-";

    /** Every font file found in the fonts folder, keyed by display name. */
    private static final Map<String, Path> FONTS = new LinkedHashMap<>();

    /** Built font sets, keyed by the selected files (or "player"). */
    private static final Map<String, FontSet> CUSTOM_SETS = new HashMap<>();

    /** Providers owned by each custom set, so their native FreeType faces can be closed. */
    private static final Map<String, List<GlyphProvider>> CUSTOM_PROVIDERS = new HashMap<>();

    /** Selections that already failed; retrying them every frame would only spam the log. */
    private static final Set<String> FAILED_CUSTOM = new HashSet<>();

    private static FontSet vanillaSet;
    private static List<GlyphProvider> vanillaProviders;
    private static FontSet playerSet;
    private static List<GlyphProvider> playerProviders;
    private static String playerKey;
    private static boolean vanillaTried;
    private static boolean failureLogged;

    private FontLibrary() {
    }

    /** The one folder the user drops .ttf/.otf files into. */
    public static Path root() {
        return FMLPaths.CONFIGDIR.get().resolve("cinelyric").resolve("fonts");
    }

    /** Rescans the fonts folder and drops every FontSet built from the old file list. */
    public static void reload() {
        invalidate();
        FONTS.clear();
        try {
            Files.createDirectories(root());
        } catch (Exception failure) {
            System.out.println("[CineLyric] could not create fonts folder: " + failure);
        }
        Path folder = root();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> stream = Files.list(folder)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> isFontFile(path.getFileName().toString()))
                        .sorted()
                        .forEach(path -> FONTS.put(displayName(path), path));
            } catch (Exception failure) {
                System.out.println("[CineLyric] could not scan " + folder + ": " + failure);
            }
        }
        System.out.println("[CineLyric] fonts found: " + FONTS.keySet());
    }

    /** Releases every font set this mod owns. Safe to call at any time. */
    public static void invalidate() {
        for (FontSet set : CUSTOM_SETS.values()) {
            close(set);
        }
        for (List<GlyphProvider> providers : CUSTOM_PROVIDERS.values()) {
            closeProviders(providers);
        }
        CUSTOM_SETS.clear();
        CUSTOM_PROVIDERS.clear();
        FAILED_CUSTOM.clear();
        close(vanillaSet);
        closeProviders(vanillaProviders);
        close(playerSet);
        closeProviders(playerProviders);
        vanillaSet = null;
        vanillaProviders = null;
        playerSet = null;
        playerProviders = null;
        playerKey = null;
        // The vanilla set is ours: it is rebuilt from the resource-pack font definitions,
        // not taken from Minecraft.font (ModernUI replaces that one with its own glyphs).
        vanillaTried = false;
    }

    /** Display names of every loaded font file, in folder order. */
    public static List<String> names() {
        return new ArrayList<>(FONTS.keySet());
    }

    public static int count() {
        return FONTS.size();
    }

    /** First font in the folder, used when nothing has been chosen yet. */
    public static String defaultPrimary() {
        return FONTS.isEmpty() ? "" : FONTS.keySet().iterator().next();
    }

    /** Second font in the folder, so primary and secondary differ by default. */
    public static String defaultSecondary(String primary) {
        for (String name : names()) {
            if (!name.equals(primary)) {
                return name;
            }
        }
        return NONE;
    }

    /** A pick is valid when it names a loaded file or is the explicit "无" sentinel. */
    public static boolean isPickable(String name) {
        return name != null && (NONE.equals(name) || FONTS.containsKey(name));
    }

    /** Human readable name of a chosen font; empty selection reads as "无". */
    public static String label(String name) {
        return name == null || name.isEmpty() || NONE.equals(name) ? "无" : name;
    }

    /** Whether the chosen custom fonts produced at least one usable file. */
    public static boolean customReady(String primary, String secondary) {
        return (primary != null && FONTS.containsKey(primary))
                || (secondary != null && FONTS.containsKey(secondary));
    }

    /** Short label for the settings screen. */
    public static String describe(String source) {
        if (CUSTOM.equals(source)) {
            return FONTS.isEmpty() ? "自定义字体（还没放字体文件）" : "自定义字体";
        }
        if (PLAYER.equals(source)) {
            return "播放器字体（实验）";
        }
        return "原版字体";
    }

    public static String normalize(String value) {
        if (CUSTOM.equals(value) || "pack".equals(value)) {
            return CUSTOM;
        }
        if (PLAYER.equals(value)) {
            return PLAYER;
        }
        return AUTO;
    }

    /**
     * The set the current configuration should draw with. Custom/player engines fall back
     * to the vanilla set whenever their file cannot be loaded, so a typo or a missing file
     * never makes the lyric disappear.
     */
    public static FontSet activeSet() {
        if (CUSTOM.equals(CineLyricConfig.fontSource)) {
            FontSet set = customSet(CineLyricConfig.fontPrimary, CineLyricConfig.fontSecondary);
            if (set != null) {
                return set;
            }
        } else if (PLAYER.equals(CineLyricConfig.fontSource)) {
            FontSet set = playerSet();
            if (set != null) {
                return set;
            }
        }
        return vanillaSet();
    }

    /**
     * Builds the vanilla font from the game's own resource-pack definitions.
     *
     * <p>We cannot use {@code Minecraft.font}: when ModernUI is installed its FontSet is
     * ModernUI's {@code StandardFontSet}, whose baked glyphs use ModernUI's
     * {@code modern_text_normal} render type. Drawing those glyphs directly only shows the
     * first glyph-atlas page (the rest is silently lost), which is exactly the 19-glyph
     * truncation we saw in game. A FontSet built here from the same providers uses vanilla
     * {@code BakedGlyph}/{@code GlyphRenderTypes}, so every atlas page reaches the batch.
     */
    public static FontSet vanillaSet() {
        if (vanillaTried) {
            return vanillaSet;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getTextureManager() == null
                || minecraft.getResourceManager() == null) {
            return null;
        }
        vanillaTried = true;
        try {
            vanillaSet = buildVanillaSet(minecraft);
        } catch (Throwable failure) {
            logFailure("vanilla FontSet unavailable", failure);
        }
        return vanillaSet;
    }

    private static FontSet buildVanillaSet(Minecraft minecraft) throws Exception {
        ResourceManager resources = minecraft.getResourceManager();
        ResourceLocation definitionId = ResourceLocation.withDefaultNamespace("default")
                .withPrefix("font/").withSuffix(".json");
        Resource definition = resources.getResource(definitionId).orElseThrow(
                () -> new IllegalStateException("missing " + definitionId));
        List<GlyphProvider.Conditional> providers = new ArrayList<>();
        collectProviders(definition, FontOption.Filter.ALWAYS_PASS, resources, providers,
                new HashSet<>());
        if (providers.isEmpty()) {
            throw new IllegalStateException("no vanilla font providers");
        }
        FontSet set = new FontSet(minecraft.getTextureManager(),
                ResourceLocation.fromNamespaceAndPath("cinelyric", "vanilla"));
        try {
            set.reload(providers, fontOptions(minecraft));
        } catch (Throwable failure) {
            for (GlyphProvider.Conditional provider : providers) {
                closeProvider(provider.provider());
            }
            throw failure;
        }
        vanillaProviders = new ArrayList<>(providers.size());
        for (GlyphProvider.Conditional provider : providers) {
            vanillaProviders.add(provider.provider());
        }
        return set;
    }

    /** Expands {@code reference} providers too, so default.json behaves like the game's loader. */
    private static void collectProviders(Resource definition,
                                         FontOption.Filter filter,
                                         ResourceManager resources,
                                         List<GlyphProvider.Conditional> providers,
                                         Set<ResourceLocation> visiting) throws Exception {
        for (GlyphProviderDefinition.Conditional conditional : readDefinitions(definition)) {
            GlyphProviderDefinition glyphs = conditional.definition();
            FontOption.Filter merged = filter.merge(conditional.filter());
            Either<GlyphProviderDefinition.Loader, GlyphProviderDefinition.Reference> unpacked =
                    glyphs.unpack();
            if (unpacked.left().isPresent()) {
                GlyphProvider provider = unpacked.left().get().load(resources);
                providers.add(new GlyphProvider.Conditional(provider, merged));
                continue;
            }
            GlyphProviderDefinition.Reference reference = unpacked.right().orElse(null);
            if (reference == null || !visiting.add(reference.id())) {
                continue;
            }
            ResourceLocation referenced = reference.id().withPrefix("font/").withSuffix(".json");
            Resource resource = resources.getResource(referenced).orElse(null);
            if (resource != null) {
                collectProviders(resource, merged, resources, providers, visiting);
            }
            visiting.remove(reference.id());
        }
    }

    private static List<GlyphProviderDefinition.Conditional> readDefinitions(Resource resource)
            throws Exception {
        JsonObject root;
        try (Reader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        return GlyphProviderDefinition.Conditional.CODEC.listOf()
                .parse(JsonOps.INSTANCE, root.get("providers"))
                .getOrThrow();
    }

    private static Set<FontOption> fontOptions(Minecraft minecraft) {
        Set<FontOption> options = new HashSet<>();
        if (minecraft.options != null) {
            if (Boolean.TRUE.equals(minecraft.options.forceUnicodeFont().get())) {
                options.add(FontOption.UNIFORM);
            }
            if (Boolean.TRUE.equals(minecraft.options.japaneseGlyphVariants().get())) {
                options.add(FontOption.JAPANESE_VARIANTS);
            }
        }
        return options;
    }

    /**
     * Builds (and caches) one FontSet from the chosen files. Multiple providers in one
     * FontSet give per-character fallback for free: the first provider with a glyph wins.
     */
    private static FontSet customSet(String primary, String secondary) {
        String key = "custom|" + primary + "|" + secondary;
        FontSet cached = CUSTOM_SETS.get(key);
        if (cached != null) {
            return cached;
        }
        if (FAILED_CUSTOM.contains(key)) {
            return null;
        }
        List<GlyphProvider.Conditional> providers = new ArrayList<>();
        List<GlyphProvider> owned = new ArrayList<>();
        StringBuilder idBuilder = new StringBuilder();
        addProvider(providers, owned, idBuilder, primary);
        addProvider(providers, owned, idBuilder, secondary);
        if (providers.isEmpty()) {
            FAILED_CUSTOM.add(key);
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getTextureManager() == null) {
            // The client is still starting up; do not remember this as a permanent failure.
            closeProviders(owned);
            return null;
        }
        String safe = idBuilder.length() == 0 ? "custom" : idBuilder.toString();
        ResourceLocation name = ResourceLocation.fromNamespaceAndPath("cinelyric",
                "custom/" + safe + "_" + Integer.toHexString(key.hashCode()));
        try {
            FontSet set = new FontSet(minecraft.getTextureManager(), name);
            set.reload(providers, Set.of());
            CUSTOM_SETS.put(key, set);
            CUSTOM_PROVIDERS.put(key, owned);
            return set;
        } catch (Throwable failure) {
            closeProviders(owned);
            FAILED_CUSTOM.add(key);
            logFailure("could not build custom FontSet", failure);
            return null;
        }
    }

    private static void addProvider(List<GlyphProvider.Conditional> providers,
                                    List<GlyphProvider> owned,
                                    StringBuilder idBuilder, String name) {
        if (name == null || name.isEmpty() || NONE.equals(name)) {
            return;
        }
        Path file = FONTS.get(name);
        if (file == null) {
            return;
        }
        GlyphProvider provider = loadProvider(file, name);
        if (provider == null) {
            return;
        }
        providers.add(new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS));
        owned.add(provider);
        if (idBuilder.length() > 0) {
            idBuilder.append('_');
        }
        idBuilder.append(sanitize(name));
    }

    /**
     * Loads a TTF/OTF file through the exact provider Minecraft uses for resource-pack
     * fonts. Only the resource lookup is replaced: a one-resource manager hands the loader
     * the file from {@code config/cinelyric/fonts/}.
     */
    private static GlyphProvider loadProvider(Path file, String displayName) {
        try {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("cinelyric",
                    sanitize(displayName));
            // The atlas is rendered at 9 * 6 px per glyph, then minified to the requested
            // GUI size. The old oversample=2 was visibly blocky on a 4x GUI scale.
            TrueTypeGlyphProviderDefinition definition = new TrueTypeGlyphProviderDefinition(
                    location, 9.0F, 6.0F,
                    TrueTypeGlyphProviderDefinition.Shift.NONE, "");
            GlyphProviderDefinition.Loader loader = definition.unpack().left().orElse(null);
            if (loader == null) {
                return null;
            }
            ResourceLocation resourceId = location.withPrefix("font/");
            Resource resource = new Resource(null, IoSupplier.create(file));
            return loader.load(new SingleResourceManager(resourceId, resource));
        } catch (Throwable failure) {
            logFailure("could not load font " + file.getFileName(), failure);
            return null;
        }
    }

    /** The Muonium player's active font, reloaded when the player switches font. */
    private static FontSet playerSet() {
        File file = PlayerFont.file();
        if (file == null || !file.isFile()) {
            return null;
        }
        String key = file.getAbsolutePath() + "|" + file.lastModified();
        if (playerSet != null && key.equals(playerKey)) {
            return playerSet;
        }
        close(playerSet);
        closeProviders(playerProviders);
        playerSet = null;
        playerProviders = null;
        playerKey = key;
        Path path = file.toPath();
        GlyphProvider provider = loadProvider(path, "player");
        Minecraft minecraft = Minecraft.getInstance();
        if (provider == null) {
            return null;
        }
        if (minecraft == null || minecraft.getTextureManager() == null) {
            closeProvider(provider);
            return null;
        }
        ResourceLocation name = ResourceLocation.fromNamespaceAndPath("cinelyric",
                "player_" + Integer.toHexString(key.hashCode()));
        try {
            FontSet set = new FontSet(minecraft.getTextureManager(), name);
            set.reload(List.of(new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS)),
                    Set.of());
            playerSet = set;
            playerProviders = List.of(provider);
            return set;
        } catch (Throwable failure) {
            closeProvider(provider);
            logFailure("could not build player FontSet", failure);
            return null;
        }
    }

    private static String displayName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static boolean isFontFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }

    /** ResourceLocation paths only allow lower-case ascii; keep names readable but safe. */
    private static String sanitize(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            builder.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' ? c : '_');
        }
        return builder.toString();
    }

    private static void close(FontSet set) {
        if (set == null) {
            return;
        }
        try {
            set.close();
        } catch (Throwable ignored) {
            // The texture manager may already be shutting down.
        }
    }

    private static void closeProviders(List<GlyphProvider> providers) {
        if (providers == null) {
            return;
        }
        for (GlyphProvider provider : providers) {
            closeProvider(provider);
        }
    }

    private static void closeProvider(GlyphProvider provider) {
        if (provider == null) {
            return;
        }
        try {
            provider.close();
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private static void logFailure(String what, Throwable failure) {
        if (!failureLogged) {
            failureLogged = true;
            System.out.println("[CineLyric] " + what + ": " + failure);
        }
    }

    /**
     * A ResourceManager that exposes exactly one file. Used only to satisfy the vanilla
     * TTF loader, which asks for {@code <location>.withPrefix("font/")}.
     */
    private record SingleResourceManager(ResourceLocation id, Resource resource)
            implements ResourceManager {

        @Override
        public Optional<Resource> getResource(ResourceLocation location) {
            return id.equals(location) ? Optional.of(resource) : Optional.empty();
        }

        @Override
        public Set<String> getNamespaces() {
            return Set.of(id.getNamespace());
        }

        @Override
        public List<Resource> getResourceStack(ResourceLocation location) {
            return getResource(location).map(List::of).orElse(List.of());
        }

        @Override
        public Map<ResourceLocation, Resource> listResources(String path,
                                                             java.util.function.Predicate<ResourceLocation> filter) {
            return getResource(id).filter(r -> filter.test(id))
                    .map(r -> Map.of(id, r)).orElse(Map.of());
        }

        @Override
        public Map<ResourceLocation, List<Resource>> listResourceStacks(
                String path, java.util.function.Predicate<ResourceLocation> filter) {
            return getResource(id).filter(r -> filter.test(id))
                    .map(r -> Map.<ResourceLocation, List<Resource>>of(id, List.of(r)))
                    .orElse(Map.of());
        }

        @Override
        public Stream<PackResources> listPacks() {
            return Stream.empty();
        }
    }

    /**
     * Isolated so that a missing Muonium mod only fails inside this class instead of
     * breaking the settings screen: UiFontLibrary is a Muonium compile-time dependency.
     */
    private static final class PlayerFont {
        static File file() {
            try {
                com.muoniumplayer.core.management.UiFont active =
                        com.muoniumplayer.core.management.UiFontLibrary.getActive();
                if (active == null) {
                    return null;
                }
                com.muoniumplayer.core.management.UiFont.Origin origin = active.getOrigin();
                // A system font is whatever the OS happened to pick; use vanilla instead.
                if (origin == com.muoniumplayer.core.management.UiFont.Origin.SYSTEM) {
                    return null;
                }
                if (origin != com.muoniumplayer.core.management.UiFont.Origin.EXTERNAL) {
                    return null;
                }
                File file = active.getFile();
                return file != null && file.isFile() ? file : null;
            } catch (Throwable ignored) {
                // Muonium missing, or the player font is unavailable.
                return null;
            }
        }
    }
}
