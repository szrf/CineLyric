# CineLyric

本模组是为 **Ji AFK Cinematic** 的电影黑边补上歌词字幕的客户端模组。
Ji 播放镜头时会取消整个 HUD，CineLyric 直接注入它的黑边渲染末尾，把当前歌词重新画上去。
它本身不播放音乐、也不渲染电影镜头，只负责「画字」这一件事。

当前版本仅支持 **NeoForge 1.21.1**（只测试了 NeoForge **21.1.2.67**）。

## 适配

前置都是软依赖：**不装任何前置模组也能正常启动、不会报错**，只是模组将不会正常启动。
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
- 1.原版（装了「现代化 UI」就自动用它的字体）、自定义字体包、播放器字体。
- 2.自定义字体包：把 ttf/otf 丢进 `config/cinelyric/fonts/`，主字体、副字体都能选，缺字按 主 → 副 → 现代化UI → 原版 回退。
- 3.音乐播放器字体（实验型内容）
- 电影镜头期间，DangoMusic 的世界空间 3D 歌词以电影相机为锚点（只换锚点，随机散布、抬高、距离仍走 Dango 自己的算法，歌词依旧是真正的场景内 3D 文字）。
- 延迟显示（淡化-与光影不兼容，也懒得修）：进镜头后字幕延迟变清楚，时长可调，默认0 关闭（实验型内容）。
- 默认快捷键 L（与原版成就键冲突，按需求保留），可在设置页和原版「按键设置」里改。
- 音乐来源自动检测：「正在播放」的播放器优先，两个都在播时优先 MuoniumPlayer；手动锁定的一方空闲时会借用正在播放的另一个。

## 安装

1. Minecraft 1.21.1 + NeoForge 21.1（实测 21.1.2.67）。
2. 建议安装 Ji AFK Cinematic，以及 MuoniumPlayer 或 DangoMusic 其中之一（都不装也能启动，只是没有效果）。
   Muonium 还需要它依赖的 Kotlin for Forge。
3. 把 `CineLyric-NeoForge-1.21.1-1.0.1.jar` 放进 `.minecraft/mods/`。
4. 进游戏按 L 打开设置。

镜头播放中按 L 会先结束这次镜头再打开设置，重新按 L 即可重新打开——Ji 在打开任何界面时本来就会退场。

## 从源码构建

```powershell
./build.ps1
