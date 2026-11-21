# CineLyric

为 **Ji AFK Cinematic** 的电影黑边补上歌词字幕，支持 **MuoniumPlayer** 和 **DangoMusic**。
Ji 播放镜头时会取消整个 HUD，CineLyric 直接注入它的黑边渲染末尾，把当前歌词重新画上去。

当前版本：**CineLyric-NeoForge-1.21.1-1.0.2**（build tag `1.0.2`：换句不再闪、行高按字体链计算；字幕统一走游戏原版字体管线，字间距是真正的字间空隙，不是把每个字往后挪；新增以屏幕中心为原点的歌名、动态音符前缀、彩虹歌名，以及译文独立字号和中文自动跳过）。

> **版本要求**：Minecraft 1.21.1 + NeoForge **最低 21.1.233**（启动器里显示为 `1.21.1-NeoForge_21.1.233`）。

## 功能

- 电影黑边里的双语歌词：主歌一行，译文一行（默认原文在上、译文在下，可一键换边）；主字号和译文字号分开调，译文字号 8–32，0 表示跟随主句。原文已经是中文时默认自动跳过译文，只显示主句。
- 歌名显示：以**屏幕中心**为 (0,0) 画「歌名 - 歌手」，默认开启；独立字号 8–32；歌名字间距 0–10（只作用于歌名，不影响音符）；对齐方式左 / 居中 / 右 / 自定义位置，只有自定义位置才展开 X/Y 像素偏移。自定义位置时预览会隐藏变暗背景，方便对准。
- 歌名前缀：只有**动态原版音符**一种；开关、彩色、跳动、跳动幅度、大小、与歌名的间距、垂直微调全部收在「音符图标详细设置…」三级页。音符默认随歌名延迟一起出现，可单独关掉「跟随延迟」让音符先出现。
- 彩色歌名：默认关闭；开启后从歌名到歌手逐字做彩虹流光，关闭时固定纯白 `#FFFFFF`。
- 字幕只落在黑边范围内（上下各 16%），自动居中；也可手动上下 / 左右微调。
- 字体引擎三档：原版字体、自定义字体包、播放器字体。
- **三档共用同一套原版字形管线**：从 `FontSet` 取 `GlyphInfo` / `BakedGlyph`，用原版字体图集绘制。原版档按游戏资源包里的 `minecraft:default` 字体定义重建 `FontSet`（不能直接用 `Minecraft.font`，否则装了现代化 UI 时拿到的是它的 `StandardFontSet`）；自定义档把 `config/cinelyric/fonts/` 里的 TTF 通过原版 FreeType 加载成 `FontSet`；播放器档取 MuoniumPlayer 当前外部字体文件，取不到就回退原版。
- 自定义字体包：把 `.ttf` 丢进 `config/cinelyric/fonts/`，主字体、副字体都能选；缺字按 主 → 副 → 原版 回退，仍缺就画 `?`。两个都选「无」时直接用原版。
- **警告：自定义字体模式仍可能出现缺字、空白或异常字形；此问题作者不打算维护，也不打算修复。** 优先使用原版字体或播放器字体。
- 字间距：单位是 GUI 像素（与字号同一单位），0–6。计算行宽时把 `字间距 × (字数-1)` 加进去，再按可用宽度算缩放；绘制时每个字前进 `advance + 字间距`。所以放大字号、缩小窗口时字距仍然均匀，不会出现“只有前几个字隔开、后面挤在一起”。
- 文字阴影按原版做法：向右下各偏移 1 个 GUI 像素，颜色压到 25% 亮度，透明度不变。
- 电影镜头期间，DangoMusic 的世界空间 3D 歌词以电影相机为锚点（只换锚点，随机散布、抬高、距离仍走 Dango 自己的算法）。
- 淡化：进镜头后字幕慢慢变清楚，时长可调，0 关闭。
- 默认快捷键 L（与原版成就键冲突，按需求保留），可在设置页和原版「按键设置」里改。
- 音乐来源自动检测：「正在播放」的播放器优先，两个都在播时优先 MuoniumPlayer。

## 安装

1. Minecraft 1.21.1 + NeoForge **21.1.233 或更高**。
   21.1.x 系列里 **233 是本模组的最低可用加载器版本**，低于 233 的 NeoForge 加载不了 CineLyric；建议直接用 21.1.x 的最新版。
2. 需要 Ji AFK Cinematic，以及 MuoniumPlayer 或 DangoMusic 其中之一（都装也行）。
   Muonium 还需要它依赖的 Kotlin for Forge。
3. 把 `CineLyric-NeoForge-1.21.1-1.0.2.jar` 放进 `.minecraft/mods/`。
4. 进游戏按 L 打开设置。

确认加载器版本：启动器版本列表里选 **1.21.1-NeoForge_21.1.233** 或更高；已经装过旧版的，把该实例的 NeoForge 升到 233 以上再进游戏。

镜头播放中按 L 会先结束这次镜头再打开设置，重新按 L 即可重新打开设置——Ji 在打开任何界面时本来就会退场。

## 字体说明

- 支持 `.ttf`；`.otf` 只有在内部格式是 TrueType 轮廓时才能被原版加载器接受，CFF 轮廓的 OTF 会加载失败并回退到原版字体。中文字体优先用 TTF。
- 自定义字体列表在启动时和点「重新扫描字体文件夹」时扫描；新增字体不用重启。
- 原版档使用游戏资源包里的默认字体定义，外观与游戏原版字体一致；装了「现代化 UI」也不会让歌词走它的 `drawInBatch`，因为那会绕过自定义 `FontSet` 和真实字间距。歌词由 CineLyric 自己按原版字形绘制。
- 主字号 5–15，字间距 0–6。字号就是 GUI 像素高度，缩放按 `字号 / 9` 计算；超长行会自动缩小以放进黑边。译文另有独立的「译文字号」8–32，0 表示跟随主句；改主字号不会再把译文一起带着变。歌名字号 8–32，位置与图标参数都在「歌名显示…」页。

## 从源码构建

```powershell
./build.ps1
```

`build.ps1` 顶部写死了 Minecraft 库目录（`$lib`）；换成你自己 `.minecraft/libraries` 的路径。
它还会从 `..\依赖库` 读 `MuoniumPlayer-1.21.1-neoforge-2.5.2.jar` 作为编译依赖（只用到类名，没有运行时依赖）；
没有这个 jar 就把那一行删掉，编译会跳过 Muonium 后端。

两个坑：

- `build.ps1` 必须保存为 **UTF-8 with BOM**（Windows PowerShell 5.1 否则按 ANSI 读中文路径）。
- `neoforge.mods.toml` / `*.json` **不能**带 BOM，否则 FML 报 `not a valid mod file`。

产物是 `build/CineLyric-NeoForge-1.21.1-1.0.2.jar`。

## AI 维护与升级

未来 Minecraft / NeoForge 大版本升级时，可直接把源码和本文件交给 AI。里面包含标准提示词模板、8 个核心文件的职责、API 迁移顺序、测试清单和已知坑：

[AI维护与升级指南.md](AI维护与升级指南.md)

## 目录结构

```
CineLyric-NeoForge-1.21.1-1.0.2/
├─ CineLyric-NeoForge-1.21.1-1.0.2.jar   可直接放进 mods 的成品
├─ 安装说明.txt                          玩家向说明（功能 / 安装 / 版本）
├─ AI维护与升级指南.md                   交给 AI 继续维护/升级的教程
├─ 上传到GitHub.md                       把这个项目传到 GitHub 的步骤
├─ LICENSE / 协议说明.txt                 MIT
├─ build.ps1                             构建脚本（需要改 $lib 路径）
└─ 源码/src/main/java/com/cinelyric/     全部源代码
```

## 实现要点

- 注入点：`com.ji.afkcinematic.variants.v1_21_1.render.LetterboxRenderer#render(GuiGraphics)` 的 TAIL（Ji 两个入口画黑边最终都走这里）。
  （`@Mixin(targets = ...)`, `remap = false`，字符串目标避开「不能 mixin 另一个 mixin」的问题。）
- 歌词来源：Muonium 每次按播放进度 `findCurrentLyric` 重算；Dango 走反射读它的歌词缓存。
  两个后端各自独立类加载，只装其中一个也能正常启动。
  `auto` 模式先只在「正在播放」的后端里按 Muonium → Dango 顺序取行，都为空才退回暂停时的最后一行。
- 3D 歌词锚点：`DangoPlatformMixin` 注入 `me.sorihiko.dango.modern.ModernPlatform#getPlayer`，电影镜头时返回电影相机位姿。
- 换句不淡入：新的一行从第一帧就是完全不透明，避免上一句消失到这一句出现之间的空白闪烁。
- 行高按字体链算，不按这一行实际用到的字算，中英混排换句时整块字幕不会上下跳；行高取 `FontSet` 里 `BakedGlyph` 的真实 ascent/descent，并按字号缩放。
- 字形渲染：`LyricTextRenderer` 逐码点取 `FontSet.getGlyphInfo` / `getGlyph`，把 `advance` 累加成行宽，在所有字之间加 `letterSpacing`，再统一算 `fit` 和居中位置。绘制时只对整行做一次 `pose.translate + scale`，不逐字 `translate`，也不按前缀 `font.width` 反复测量。`BakedGlyph.render` 接收的是基线，所以按字体的 ascent 偏移后再画，位置调整不会再和实际字幕错位。
- 自定义字体：`FontLibrary` 把 TTF 路径包装成单资源 `ResourceManager`，交给原版 `TrueTypeGlyphProviderDefinition`（size 9、oversample 6）加载；得到的 `GlyphProvider` 放进自建 `FontSet`。所有 provider 用同一套 9px 字模，缩放由外层统一做；oversample 提高后，4 倍界面比例下自定义字体的边缘更干净。
- 缺字探测：`FontSet.getGlyphInfo` 对不支持的字符不会返回 `null`，而是返回共享的 missing glyph；同时 `getGlyph` 也可能返回共享的 missing `BakedGlyph`。两者都做身份比较，任一为 missing 就回退到副字体 / 原版 / `?`，不会把缺字画成方框、豆腐块或异常大字形；空白字符只推进 advance，不提交字形。
- 阴影：每个字先按 `(x+1, y+1)`、RGB×0.25 画一遍，再画正常颜色；光值 `0xF000F0`（全亮）。
- 颜色与透明度：直接用 `BakedGlyph.render` 的 RGBA 参数，不经过 `setColor`，也不生成中间贴图。
- 歌名：`SongInfo` 用反射读 Muonium 的 `CloudMusic.currentlyPlaying` / Dango 的 `getShownTrack`，优先 `getMainTitle()`（Muonium 的 `getName()` 有时已带歌手），再拼歌手；`SongTitleRenderer` 同样用 `LyricTextRenderer` 的原版字形直接画，因此不会被 ModernUI 的 `drawInBatch` 覆盖。坐标原点是屏幕中心；左/右对齐时歌名块分别从中心向右/向左展开，自定义位置再叠加 X/Y，并隐藏变暗背景。歌名有独立 `songTitleLetterSpacing`，通过 `LyricTextRenderer` 的 spacing 重载传入，不会影响歌词和图标。前缀只有程序化 9×9 双音符贴图（不依赖可能缺失的 `particle/note.png`），可关彩色、可关跳动并单独调幅度，按时间浮动和 `HSB` 色相循环。彩虹模式逐字算色相。
- 译文模块：`CineLyricTranslationScreen` 集中显示开关、独立字号、中文自动跳过、上下位置和像素微调；原文匹配 `\u4e00`–`\u9fa5` 时 `LyricSources.Line` 直接把译文清空，避免中文串到译文行。

## 维护与反馈

当前状态：1.0.2 为稳定版，源码已交社区维护；作者后续不再做功能迭代，仅处理影响启动/崩溃/安全的严重问题。

报问题：在仓库 Issues 里提交，尽量附上 NeoForge 版本、日志（ logs/latest.log ）和复现步骤。

提需求：同样走 Issue，用 label  enhancement  标记即可。

想参与开发：clone 源码，按《AI维护与升级指南.md》配好环境后开发，改完提 Pull Request，说明改了什么、为什么改。

## 协议

MIT，见 [LICENSE](LICENSE) 与 [协议说明.txt](协议说明.txt)。

- 每个源文件头部都带 `SPDX-License-Identifier: MIT`，方便单独摘出去用。
- 模组 jar 里也打了一份 LICENSE（`META-INF/LICENSE`），分发时不会漏掉协议文本。
- `neoforge.mods.toml` 的 `license` 字段同样是 `MIT`。
- 在 GitHub 上建仓库时会自动识别根目录的 `LICENSE`，仓库页面会显示 **MIT license**。

## 📥 下载

- [查看全部历史版本](https://github.com/szrf/CineLyric/releases)
