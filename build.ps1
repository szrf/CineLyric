# CineLyric build script (compile against the local PCL2 library jars, no Gradle needed)
# Run from Windows PowerShell:  powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
# (UTF-8 with BOM so Windows PowerShell 5.1 decodes the Chinese paths correctly)
$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$lib  = 'D:\已安装软件\娱乐软件\PCL2\.minecraft\libraries'
$javac = 'D:\JAVA\21\bin\javac.exe'
$jar   = 'D:\JAVA\21\bin\jar.exe'

# Must compile against the Mixin build that the game actually runs (0.16.4),
# otherwise IMixinConfigPlugin defaults/abstract methods can mismatch at runtime.
$classpath = @(
    "$lib\net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-srg.jar",
    "$lib\net\neoforged\fancymodloader\loader\4.0.45\loader-4.0.45.jar",
    "$lib\net\neoforged\bus\8.0.5\bus-8.0.5.jar",
    "$lib\net\neoforged\neoforge\21.1.233\neoforge-21.1.233-universal.jar",
    "$lib\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar",
    "$lib\com\mojang\brigadier\1.3.10\brigadier-1.3.10.jar",
    "$lib\net\fabricmc\sponge-mixin\0.16.4+mixin.0.8.7\sponge-mixin-0.16.4+mixin.0.8.7.jar",
    "$lib\org\ow2\asm\asm\9.10.1\asm-9.10.1.jar",
    "$lib\org\ow2\asm\asm-tree\9.10.1\asm-tree-9.10.1.jar",
    (Join-Path $root '..\MuoniumPlayer-1.21.1-neoforge-2.5.2.jar')
) -join ';'

$buildDir = Join-Path $root 'build'
$classes  = Join-Path $buildDir 'classes'
Remove-Item $buildDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $classes -Force | Out-Null

$sources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }

# -proc:none: the Mixin annotation processor would try to resolve the Ji target class
# (not on this classpath) and abort. No refmap is needed: every mixin reference is remap=false.
& $javac --release 21 -proc:none -encoding UTF-8 -Xlint:none -cp $classpath -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

Copy-Item (Join-Path $root 'src\main\resources\*') $classes -Recurse -Force

$outJar = Join-Path $buildDir 'CineLyric-NeoForge-1.21.1-1.0.1.jar'
& $jar --create --file $outJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }

Write-Host "Built: $outJar"
& $jar --list --file $outJar
