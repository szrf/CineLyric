# CineLyric

本模组是为 **Ji AFK Cinematic** 的电影黑边补上歌词字幕的客户端模组。
Ji 播放镜头时会取消整个 HUD，CineLyric 直接注入它的黑边渲染末尾，把当前歌词重新画上去。
它本身不播放音乐、也不渲染电影镜头，只负责「画字」这一件事。

当前版本仅支持 **NeoForge 1.21.1**（只测试了 NeoForge **21.1.267**）。

## 适配

前置都是软依赖：**不装任何前置模组也能正常启动、不会报错**，只是没有黑边可叠加、也没有歌词可显示。
要看到完整效果，建议安装：

- [Ji AFK Cinematic](https://modrinth.com/mod/ji-afk-cinematic)：提供电影黑边与镜头系统，没装则没有黑边可画。
- [MuoniumPlayer](https://github.com/zihe114514/MuoniumPlayer)：歌词来源之一，还需其依赖 **Kotlin for Forge**。
- [DangoMusic](https://github.com/Sorihiko/DangoMusic)：歌词来源之一。

MuoniumPlayer 与 DangoMusic 装其一即可，都装也可以（自动检测正在播放的一方）。

## 功能

- 电影黑边里的双语歌词：主歌一行，译文一行（默认原文在上、译文在下，可一键换边）。
- 字幕只落在黑边范围内（上下各 16%），自动居中；也可手动上下 / 左右微调。
- 默认纯白、无描边：贴图里透明像素填充字的颜色，缩放采样时不会混出一圈黑边。
- 字体引擎三档：
- 1.原版（装了「现代化 UI」就自动用它的字体）
- 2.自定义字体包：把 ttf/otf 丢进 `config/cinelyric/fonts/`，主字体、副字体都能选，缺字按 主 → 副 → 现代化UI → 原版 回退。
- 3.音乐播放器字体（实验型内容）
- 电影镜头期间，DangoMusic 的世界空间 3D 歌词以电影相机为锚点（只换锚点，随机散布、抬高、距离仍走 Dango 自己的算法，歌词依旧是真正的场景内 3D 文字）。
- 延迟显示（淡化-与光影不兼容，也懒得修）：进镜头后字幕延迟变清楚，时长可调，默认0 关闭（实验型内容）。
- 默认快捷键 L（与原版成就键冲突，按需求保留），可在设置页和原版「按键设置」里改。
- 音乐来源自动检测：「正在播放」的播放器优先，两个都在播时优先 MuoniumPlayer；手动锁定的一方空闲时会借用正在播放的另一个。

## 安装

1. Minecraft 1.21.1 + NeoForge 21.1（实测 21.1.267）。
2. 建议安装 Ji AFK Cinematic，以及 MuoniumPlayer 或 DangoMusic 其中之一（都不装也能启动，只是没有效果）。
   Muonium 还需要它依赖的 Kotlin for Forge。
3. 把 `CineLyric-NeoForge-1.21.1-1.0.1.jar` 放进 `.minecraft/mods/`。
4. 进游戏按 L 打开设置。

镜头播放中按 L 会先结束这次镜头再打开设置，重新按 L 即可重新打开——Ji 在打开任何界面时本来就会退场。



## 从源码构建

```powershell
./build.ps1
```

`build.ps1` 顶部写死了 Minecraft 库目录（`$lib`）；换成你自己 `.minecraft/libraries` 的路径。
它还会从 `..` 读 `MuoniumPlayer-1.21.1-neoforge-2.5.2.jar` 作为编译依赖（只用到类名，没有运行时依赖）；
没有这个 jar 就把那一行删掉，编译会跳过 Muonium 后端。

两个坑：

- `build.ps1` 必须保存为 **UTF-8 with BOM**（Windows PowerShell 5.1 否则按 ANSI 读中文路径）。
- `neoforge.mods.toml` / `*.json` **不能**带 BOM，否则 FML 报 `not a valid mod file`。

产物是 `build/CineLyric-NeoForge-1.21.1-1.0.1.jar`。

## 目录结构

```
CineLyric-NeoForge-1.21.1-1.0.1/
├─ CineLyric-NeoForge-1.21.1-1.0.1.jar   可直接放进 mods 的成品
├─ 上传到GitHub.md                       把这个项目传到 GitHub 的步骤
├─ LICENSE / 协议说明.txt                 MIT
├─ build.ps1                             构建脚本（需要改 $lib 路径）
└─ 源码/src/main/java/com/cinelyric/     全部源代码
```

## 实现要点

- 注入点：`com.ji.afkcinematic.variants.v1_21_1.render.LetterboxRenderer#render(GuiGraphics)` 的 TAIL（Ji 两个入口画黑边最终都走这里；早先挂在 `renderFromHud` 上，只有「隐藏 HUD」那条路径会画到）
  （`@Mixin(targets = ...)`, `remap = false`，字符串目标避开「不能 mixin 另一个 mixin」的问题）。
- 歌词来源：Muonium 每次按播放进度 `findCurrentLyric` 重算（旧版用它 HUD 刷新的 `currentLyric`，
  Ji 隐藏 HUD 后会冻结）；Dango 走反射读它的歌词缓存（1.1.4 起所有反射成员都显式 setAccessible）。
  两个后端各自独立类加载，只装其中一个也能正常启动。
  `auto` 模式先只在「正在播放」的后端里按 Muonium → Dango 顺序取行，都为空才退回暂停时的最后一行。
- 3D 歌词锚点：Dango 用它平台层的 `getPlayer()` 取得「眼睛位置 + 朝向」后按视角摆放世界歌词。
  `DangoPlatformMixin` 注入 `me.sorihiko.dango.modern.ModernPlatform#getPlayer` 的 RETURN：
  Ji 的 `CinematicManager.getState()` 为 `CINEMATIC_ACTIVE`（或黑边可见，作为兼容兜底）时，
  返回 `gameRenderer.getMainCamera()` 的机位与 `yaw/pitch` 组成的 `PlayerPose`，否则原样返回。
  两侧都走反射 / 字符串 mixin 目标，没有编译期依赖。
- 文字按**物理像素**栅格化（`窗口缩放 × GUI 缩放 × 2`）再贴回，避免大分辨率下发糊。
- 颜色与透明度烘进纹理像素，而不是用 `setColor` 着色——后者对 `blit` 无效。
- 透明像素填字的颜色（颜色外扩）：直通 alpha 的纹理在缩放采样时会把白色和透明黑插值成灰，
  看起来像每个字外面一圈黑描边；外扩后插值只在同色之间进行，只剩平滑的透明过渡。

## 协议

MIT，见 [LICENSE](LICENSE) 与 [协议说明.txt](协议说明.txt)。

- 每个源文件头部都带 `SPDX-License-Identifier: MIT`，方便单独摘出去用。
- 模组 jar 里也打了一份 LICENSE（`META-INF/LICENSE`），分发时不会漏掉协议文本。
- `neoforge.mods.toml` 的 `license` 字段同样是 `MIT`。
- 在 GitHub 上建仓库时会自动识别根目录的 `LICENSE`，仓库页面会显示 **MIT license**。


## 📥 下载

- [查看全部历史版本](https://github.com/szrf/CineLyric/releases)
