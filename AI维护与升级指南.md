# CineLyric AI 维护与升级指南

本文件同时面向两类读者：

- **人类作者**：想把项目交给 AI 继续维护，需要知道该打包什么、该说什么,请阅读§1。
- **AI 编程助手**：这是你的上下文入口。请先读 §0 的执行协议，再按 §2 的文件顺序阅读源码。

当前基线：

- Minecraft **1.21.1**
- NeoForge **21.1.233**
- CineLyric **1.0.2**
- 客户端 mod，核心是 Ji AFK Cinematic 黑边渲染末尾的歌词/歌名注入

> 命名说明：本指南使用当前源码的真实类名。旧文档里的 `CineLyricRenderer` / `CustomFontManager` 已不存在，实际对应 `LyricTextRenderer` / `FontLibrary`。AI 不要凭旧名字新建类，也不要把现有类改名。

---

## 0. AI 读取与执行协议（MUST）

你是 CineLyric 的维护代理，不是重新设计者。按下面顺序工作：

### 0.1 开工前必须先输出

在改任何文件之前，先给出一段简短计划，包含：

1. 你读到的基线版本与目标版本；
2. 你准备改的文件清单（只列真实存在的路径）；
3. 你不准备改的文件及原因；
4. 当前环境无法验证的风险。

不要先写代码再补计划。

### 0.2 阅读顺序（不要跳）

1. 本文件 §0–§5；
2. `README.md` 的功能与实现要点；
3. `src/main/java/com/cinelyric/CineLyricConfig.java`（配置字段是很多 bug 的源头）；
4. `src/main/java/com/cinelyric/FontLibrary.java`（字体来源与 `FontSet` 生命周期）；
5. `src/main/java/com/cinelyric/LyricTextRenderer.java`（真正的字形绘制与回退）；
6. `src/main/java/com/cinelyric/CineLyricOverlay.java`（注入、布局、预览、延迟）；
7. `SongTitleRenderer.java`、`SongInfo.java` 与各 `*Screen.java`；
8. `src/main/java/com/cinelyric/mixin/*.java` 与 `src/main/resources/cinelyric.mixins.json`。

### 0.3 任务路由

| 现象 | 先看 | 不要先动 |
|---|---|---|
| 编译错误 | `build.ps1` classpath、目标 jar、报错文件 | 不要先改业务逻辑 |
| 字体不显示/缺字/大字形 | `LyricTextRenderer.pick`、`FontLibrary.customSet` | 不要改回 `Font#drawString` |
| 歌名位置/对齐/延迟 | `SongTitleRenderer`、`CineLyricTitleScreen`、`CineLyricDelayScreen` | 不要改歌词布局 |
| 配置不保存/旧配置失效 | `CineLyricConfig` 的 `Data`、`load`、`save` | 不要直接写 JSON 文件 |
| Mixin 注入失败 | `LetterboxRendererMixin`、`DangoPlatformMixin`、`cinelyric.mixins.json` | 不要换注入目标逃避问题 |

### 0.4 硬性禁止（MUST NOT）

1. 禁止用 `Font#drawString` / `Font#drawInBatch` 画歌词或歌名。ModernUI 会覆盖它并忽略自定义 `FontSet`。
2. 禁止用逐字 `pose.translate` 制造字间距。字距必须进入 advance 和总宽度。
3. 禁止删除「自定义字体可能缺字、作者不维护」的界面警告与文档声明。
4. 禁止为了让编译通过而删除功能、吞掉异常或伪造测试结果。
5. 禁止新增运行时依赖，除非用户明确同意；MuoniumPlayer 只用于编译期反射。
6. 禁止改版本号之外的发布信息而不更新 `neoforge.mods.toml`、`build.ps1`、README、安装说明中的对应版本。

### 0.5 交付契约

最终回复必须包含：

- 修改后的**完整 Java 文件**（不是只有 diff 片段）；
- 修改说明：每个文件为什么改、对应哪个 API 变更；
- 实际执行的构建命令与结果；
- 无法验证的运行时风险；
- 若有配置字段变更，给出旧配置 → 新配置的迁移说明。

编译失败时，先贴出**第一条真实错误**和完整堆栈，不要继续猜。

### 0.6 验收命令

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

通过标准：

- `Built: ...CineLyric-NeoForge-<版本>.jar`
- jar 内含 `com/cinelyric/LyricTextRenderer.class`、`FontLibrary.class`、`SongTitleRenderer.class`；
- 启动日志没有 `CineLyric` 异常；
- 三种字体引擎各进一次设置页，主句、译文、歌名都能显示。

### 0.7 给 AI 的一句话上下文

> 这是 Minecraft 1.21.1 / NeoForge 21.1.233 的客户端歌词 mod。渲染核心是 `LyricTextRenderer`，字体来源是 `FontLibrary`，配置唯一入口是 `CineLyricConfig`。任何升级都必须保住“直接 `BakedGlyph.render`、真实 advance 字距、逐字缺字回退、ModernUI 绕过”这四件事。

### 0.8 机器可读上下文（AI 优先解析）

如果调用方支持结构化上下文，直接使用下面这段，不必再从自然语言里猜：

```yaml
project: CineLyric
type: minecraft-client-mod
baseline:
  minecraft: "1.21.1"
  neoforge: "21.1.233"
  mod: "1.0.2"
source_root: "src/main/java/com/cinelyric"
entrypoint: "CineLyricMod"
injection_point: "LetterboxRendererMixin / Ji AFK Cinematic letterbox"
renderer:
  text: "LyricTextRenderer"
  fonts: "FontLibrary"
  title: "SongTitleRenderer"
  metadata: "SongInfo"
config:
  file: "config/cinelyric.json"
  schema: "CineLyricConfig.Data"
  save_api: "CineLyricConfig.save()"
ui:
  main: "CineLyricConfigScreen"
  font_pack: "CineLyricFontScreen"
  title: "CineLyricTitleScreen"
  icon: "CineLyricIconScreen"
  translation: "CineLyricTranslationScreen"
  delay: "CineLyricDelayScreen"
invariants:
  - "render glyphs via BakedGlyph.render, never Font#drawString/drawInBatch"
  - "letter spacing must be part of advance and total width"
  - "per-character fallback: primary -> secondary -> vanilla -> '?'"
  - "validate both GlyphInfo and BakedGlyph missing instances"
  - "whitespace advances only; never submit its BakedGlyph"
  - "custom fonts: TTF only; CFF OTF falls back to vanilla"
  - "ModernUI must not take over lyric/title rendering"
forbidden:
  - "deleting the custom-font warning"
  - "faking build or runtime test results"
  - "adding runtime dependencies without user approval"
  - "renaming public classes or config keys without migration"
acceptance:
  build: "powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\\build.ps1"
  jar: "build/CineLyric-NeoForge-<version>.jar"
  required_classes: ["LyricTextRenderer", "FontLibrary", "SongTitleRenderer", "CineLyricConfig"]
  runtime: ["no CineLyric exception in latest.log", "vanilla/custom/player engines all render"]
```

### 0.9 收到源码包时的最短路径

1. 解压 `-源码.zip`；
2. 读本文件 §0.8 的 YAML，确认版本与文件树；
3. 运行一次 `build.ps1`，先拿到基线；
4. 按 §0.3 路由到目标文件，不要全仓库乱改；
5. 改完按 §0.6 验收，再按 §0.5 输出完整文件与风险。

---

## 1. 升级前要准备什么

1. 完整源码：`src/main/java/com/cinelyric/`，以及 `src/main/resources/`（`neoforge.mods.toml`、`cinelyric.mixins.json`）。
2. 当前能编译的基线：先运行一次 `build.ps1`，确认旧版本能出 jar，再改代码。
3. 目标版本的官方资料：
   - Minecraft 目标版本的 Changelog / 数据包与资源包格式变更；
   - NeoForge 目标版本的 Changelog、Breaking Changes、Mixin 版本要求；
   - 重点看 `net.minecraft.client.gui.Font`、`FontSet`、`BakedGlyph`、`GlyphInfo`、`GuiGraphics`、`RenderType`、`ResourceManager` 的签名变化。
4. 依赖 jar：
   - 原版 client jar（SRG 命名）；
   - NeoForge universal；
   - FML loader；
   - Mixin / ASM；
   - `MuoniumPlayer-1.21.1-neoforge-2.5.2.jar`（只用于编译期反射目标，运行时不硬依赖）。
5. 测试环境：至少准备一个装了 Ji AFK Cinematic + MuoniumPlayer 或 DangoMusic 的客户端；装了 ModernUI 的客户端也要测，因为本项目专门绕开它。

---

## 2. 核心架构（先读这 8 个文件）

| 文件 | 职责 |
|---|---|
| `CineLyricOverlay.java` | 注入 Ji 黑边渲染末尾；计算上下黑边、主句/译文布局、歌名调用；`beginPreview` / `beginDelayPreview` |
| `LyricTextRenderer.java` | 真正画字：逐码点取 `FontSet` 的 `GlyphInfo` / `BakedGlyph`，把字距算进 advance，整行一次 pose 变换；缺字逐字回退原版 |
| `FontLibrary.java` | 字体引擎：原版 `FontSet`、`config/cinelyric/fonts/` 的 TTF、Muonium 播放器字体；TTF 用原版 FreeType 加载 |
| `SongInfo.java` | 反射读取 Muonium / Dango 当前曲目：标题、歌手、播放状态 |
| `SongTitleRenderer.java` | 歌名绘制：屏幕中心原点、左/中/右/自定义、彩虹、动态双音符、延迟联动 |
| `CineLyricConfig.java` | `config/cinelyric.json` 的唯一配置入口；字段默认值、范围钳制、保存 |
| `CineLyricConfigScreen.java` / `CineLyricFontScreen.java` | 主设置页与字体包页；字体包页含“自定义字体可能缺字、作者不维护”的警告 |
| `CineLyricTitleScreen.java` / `CineLyricIconScreen.java` / `CineLyricTranslationScreen.java` / `CineLyricDelayScreen.java` | 歌名、音符图标、译文、延迟的二级/三级页面 |
| `LetterboxRendererMixin.java` / `DangoPlatformMixin.java` | Ji 黑边注入点；Dango 3D 歌词相机锚点 |

### 两条硬约束

1. **不要改回 `Font#drawString` / `Font#drawInBatch` 来画歌词或歌名。**
   ModernUI 会 `@Overwrite` 这些方法，并忽略传入的 `Font` / `FontSet`。本项目必须继续用 `BakedGlyph.render` 直接画，才能让自定义字体、真实字间距和逐字回退生效。
2. **不要逐字 `pose.translate` 来制造字距。**
   字距必须进入 advance 和总宽度计算，否则会退回“把每个字往后挪”的旧问题。

---

## 3. 给 AI 的标准升级提示词

把下面整段连同源码一起发给 AI。把方括号替换成实际版本。

```text
你现在是 CineLyric 模组的资深维护者。当前模组基于 Minecraft [旧MC版本] / NeoForge [旧NeoForge版本] 开发，现需升级至 Minecraft [新MC版本] / NeoForge [新NeoForge版本]。

请先阅读我提供的完整源码，重点看：
- com/cinelyric/LyricTextRenderer.java（字形渲染与缺字回退）
- com/cinelyric/FontLibrary.java（原版 FontSet、自定义 TTF、播放器字体）
- com/cinelyric/SongTitleRenderer.java / SongInfo.java（歌名与曲目信息）
- com/cinelyric/CineLyricConfig.java（配置字段与范围）
- com/cinelyric/CineLyricOverlay.java（Ji 黑边注入与布局）
- mixins（LetterboxRendererMixin / DangoPlatformMixin）

请完成以下任务：

1. API 迁移
   修复所有因 NeoForge/Minecraft API 变动导致的编译错误，特别是 FontSet、GlyphInfo、BakedGlyph、GuiGraphics、RenderType、ResourceManager、Mixin 相关的接口变更。
   如果 FontSet 的构造/重载/缺失字形行为变了，先写一个最小验证程序确认，再改渲染器。

2. 逻辑保持
   - 保持自定义字体的逐字回退：主字体 → 副字体 → 原版 → “?”，且 GlyphInfo 和 BakedGlyph 都要校验，不能把 missing glyph 画成方框或异常大字形。
   - 保持歌名/歌词/译文各自独立渲染，不经过 Font#drawInBatch，避免 ModernUI 接管。
   - 保持字间距是真实 advance 间隔，不是逐字位置微调。
   - 保持歌名屏幕中心原点、左/中/右/自定义四种对齐、自定义模式隐藏变暗背景。
   - 保持音符图标的彩色/跳动/幅度/大小/间距/垂直微调，以及延迟联动开关。
   - 保持译文字号 0=跟随主句、8–32 手动；保持中文自动跳过。

3. 配置兼容
   确保 config/cinelyric.json 的旧字段仍能读取；新增字段给默认值；废弃字段做平滑迁移，不要让旧配置启动即崩。
   所有新字段都要在 CineLyricConfig.Data 中保存，并在设置页给中文标签和 tooltip。

4. 验证与输出
   - 先运行 build.ps1，确保 0 编译错误。
   - 输出修改后的完整 Java 文件（不要只给 diff 片段），并附修改说明。
   - 列出你无法在当前环境验证的运行时风险。

注意：
- 自定义字体缺字是已知问题，作者不打算继续维护；不要删除界面上的警告，也不要承诺“彻底修复缺字”。
- CFF 轮廓的 .otf 原版 TTF 加载器不支持，会回退原版；只能保证 TrueType 轮廓。
- 这是客户端 mod，不要引入服务端专用 API。
```

---

## 4. 实际升级步骤（建议顺序）

1. **先编译旧源码**，确认基线可构建。
2. **只改 API 兼容层**：`build.ps1` 的 classpath、`neoforge.mods.toml` 的版本号、Mixin 配置。
3. **逐个文件修编译错误**，优先 `CineLyricConfig`、`FontLibrary`、`LyricTextRenderer`，最后 UI 页面。
4. **单独验证 `FontSet` 行为**：
   - 原版 `FontSet` 是否还能从资源包 `minecraft:default` 重建；
   - 自定义 TTF 的 `TrueTypeGlyphProviderDefinition` 构造是否变化；
   - `getGlyphInfo` / `getGlyph` 对缺失字符是否仍返回共享 missing 实例；
   - `BakedGlyph.render` / `renderType` 的签名是否变化。
5. **改完先跑无 ModernUI 的客户端**，再跑装 ModernUI 的客户端。
6. **三种字体引擎各测一遍**：原版、自定义 TTF、播放器字体。
7. **测试清单**：
   - 主句 40 字符英文完整显示；
   - 中文译文完整显示；
   - 自定义字体缺中文时逐字回退原版；
   - 歌名带 `-`、歌手、特殊符号；
   - 音符图标显示、彩色、跳动、幅度、大小、间距；
   - 自定义位置 X/Y 与背景隐藏；
   - 延迟页独立预览、音符跟随/不跟随；
   - 旧配置文件能启动；
   - 切换字体后不崩、不闪退。
8. **重新打包**：`-模组.zip`、`-源码.zip`，并更新 README / 安装说明 / 本指南中的版本号。

---

## 5. 已知坑

- **ModernUI**：不要试图用 `Minecraft.font` 直接画歌词；它拿到的是 ModernUI 的 `StandardFontSet`，`BakedGlyph.renderType` 会返回 `modern_text_normal`，在字形图集翻页处会吞掉后半行。原版档必须从 `minecraft:default` 重建 `FontSet`。
- **空格**：空格只推进 advance，不要提交 `BakedGlyph`；某些 provider 的空格 `RenderType` 纹理为空，先提交会把后文吞掉。
- **缺字**：`getGlyphInfo` 不返回 null；`getGlyph` 也可能返回共享 missing `BakedGlyph`。两者都要和 `U+10FFFF` 的身份比较。仍缺时用原版 `?`。
- **CFF OTF**：原版加载器要求 `FT_Get_Font_Format == "TrueType"`；CFF 会加载失败并回退原版。
- **Mixin**：Ji 的注入目标用字符串 targets + `remap = false`；升级后先确认类名/方法名还在。
- **BOM**：`build.ps1` 必须 UTF-8 with BOM；`neoforge.mods.toml` / `*.json` 不能带 BOM。
- **配置**：所有字段通过 `CineLyricConfig` 静态字段 + `Data` 读写；不要绕开它直接写 JSON。

---

## 6. 构建与打包

```powershell
# 需要 Windows PowerShell 5.1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

产物：

```text
build/CineLyric-NeoForge-1.21.1-1.0.2.jar
```

发布包至少包含：

```text
CineLyric-NeoForge-1.21.1-1.0.2/
├─ CineLyric-NeoForge-1.21.1-1.0.2.jar
├─ README.md
├─ 安装说明.txt
├─ AI维护与升级指南.md
├─ LICENSE / 协议说明.txt
├─ build.ps1
└─ 源码/src/main/java/com/cinelyric/
```

---

## 7. 声明

- 本模组按 MIT 协议发布，可自由修改、分发，但请保留版权与协议文件。
- 自定义字体缺字/空白/异常字形是已知问题，作者不打算继续维护或修复；源码里保留了回退逻辑和界面警告，接手者可以自行改进，但不要删除警告冒充“已彻底修复”。
- 本指南只是维护建议，不构成对任何版本兼容性的保证。
