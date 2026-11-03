# SPDX-License-Identifier: Apache-2.0
# Uses only version manifests/libraries/assets from the named installation; never launcher accounts.
# Default is prepare-only. Root/operator must pass -Launch to start the isolated packaged client.
[CmdletBinding()]
param(
    [Alias('PlanOnly')][switch]$PrepareOnly,
    [switch]$Launch,
    [switch]$OpenPanel,
    [Parameter(Mandatory=$true)][ValidateNotNullOrEmpty()][string]$MinecraftRoot,
    [Parameter(Mandatory=$true)][Alias('SourceVersion')][ValidateNotNullOrEmpty()][string]$VersionId,
    [string]$JavaExe = 'C:\Program Files\Java\jdk-17\bin\java.exe',
    [string]$ModJar,
    [ValidateRange(800,3840)][int]$Width = 1440,
    [ValidateRange(600,2160)][int]$Height = 1000,
    [ValidateRange(1024,8192)][int]$MaximumHeapMiB = 3072
)
$ErrorActionPreference = 'Stop'
if ($PrepareOnly -and $Launch) { throw 'Choose either -PrepareOnly or -Launch.' }
if (-not [Environment]::Is64BitOperatingSystem) { throw 'The packaged Skija runtime requires Windows x64.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$isolatedGameDir = [IO.Path]::GetFullPath((Join-Path $repositoryRoot '.runtime\client-instance'))
$libraryDirectory = [IO.Path]::GetFullPath((Join-Path $MinecraftRoot 'libraries'))
$assetsDirectory = [IO.Path]::GetFullPath((Join-Path $MinecraftRoot 'assets'))
$sourceAssetsDirectory = $assetsDirectory
$versionDirectory = Join-Path (Join-Path $MinecraftRoot 'versions') $VersionId
$manifestPath = Join-Path $versionDirectory ($VersionId + '.json')
$previewVersion = 'PixelMC-Hub-Packaged-Preview'
if (-not $ModJar) { $ModJar = Join-Path $repositoryRoot 'minecraft\build\libs\pixelmc-hub-forge-1.20.1-0.1.0-native-prototype.jar' }
$ModJar = [IO.Path]::GetFullPath($ModJar)
foreach ($required in @($manifestPath,$JavaExe,$ModJar)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required file is missing: $required" }
}
if ($ModJar -match '(thin-dev|sources|javadoc)\.jar$') { throw 'Use the full reobfuscated JarJar distributable, not a development jar.' }
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.inheritsFrom) { throw 'This helper requires the existing merged version manifest, not an unresolved inherited descriptor.' }
if ($manifest.mainClass -ne 'cpw.mods.bootstraplauncher.BootstrapLauncher') { throw 'The selected manifest is not a Forge BootstrapLauncher client.' }
$gamePatch = @($manifest.patches | Where-Object { $_.id -eq 'game' })
$forgePatch = @($manifest.patches | Where-Object { $_.id -eq 'forge' })
if ($gamePatch.Count -ne 1 -or $gamePatch[0].version -ne '1.20.1' -or $forgePatch.Count -ne 1 -or $forgePatch[0].version -ne '47.3.33' -or $forgePatch[0].inheritsFrom -ne '1.20.1') {
    throw 'Expected a fully merged Minecraft 1.20.1 + Forge 47.3.33 manifest.'
}
$javaVersionInfo = (& $JavaExe -XshowSettings:properties -version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersionInfo -notmatch 'java\.specification\.version\s*=\s*17\b' -or $javaVersionInfo -notmatch 'os\.arch\s*=\s*amd64\b') {
    throw 'Select a working x64 Java 17 runtime.'
}

function Test-LaunchRules($rules) {
    if (-not $rules -or @($rules).Count -eq 0) { return $true }
    $allowed = $false
    foreach ($rule in $rules) {
        $matches = $true
        if ($rule.os) {
            if ($rule.os.name -and $rule.os.name -notin @('windows','universal')) { $matches = $false }
            if ($rule.os.arch -and 'amd64' -notmatch $rule.os.arch) { $matches = $false }
            if ($rule.os.version -and [Environment]::OSVersion.Version.ToString() -notmatch $rule.os.version) { $matches = $false }
        }
        if ($rule.features) {
            foreach ($feature in $rule.features.PSObject.Properties) {
                $actual = $feature.Name -eq 'has_custom_resolution'
                if ($actual -ne [bool]$feature.Value) { $matches = $false }
            }
        }
        if ($matches) { $allowed = $rule.action -eq 'allow' }
    }
    return $allowed
}
function Resolve-ArtifactPath([string]$relative) {
    $absolute = [IO.Path]::GetFullPath((Join-Path $libraryDirectory $relative))
    if (-not $absolute.StartsWith($libraryDirectory + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Manifest library path escaped the read-only library root.' }
    return $absolute
}
function Test-Artifact($artifact) {
    $absolute = Resolve-ArtifactPath $artifact.path
    if (-not (Test-Path -LiteralPath $absolute -PathType Leaf)) { throw "Missing cached library: $($artifact.path)" }
    if ($artifact.sha1 -and (Get-FileHash -LiteralPath $absolute -Algorithm SHA1).Hash -ne $artifact.sha1) { throw "Cached library hash mismatch: $($artifact.path)" }
    return $absolute
}
$classPathFiles = [Collections.Generic.List[string]]::new()
$nativeJars = [Collections.Generic.List[string]]::new()
foreach ($library in $manifest.libraries) {
    if (-not (Test-LaunchRules $library.rules)) { continue }
    # Mojang's merged 1.20.1 manifest gives all three Windows native classifiers the same OS rule.
    if ($library.name -match ':natives-windows-(arm64|x86)$') { continue }
    if ($library.downloads.artifact) {
        $absolute = Test-Artifact $library.downloads.artifact
        if (-not $classPathFiles.Contains($absolute)) { $classPathFiles.Add($absolute) }
        if ($library.name -match ':natives-windows$') { $nativeJars.Add($absolute) }
    }
    if ($library.natives.windows) {
        $classifier = $library.natives.windows.Replace('${arch}','64')
        $nativeArtifact = $library.downloads.classifiers.$classifier
        if (-not $nativeArtifact) { throw "Missing Windows classifier in manifest: $($library.name)" }
        $nativeJars.Add((Test-Artifact $nativeArtifact))
    }
}
# Forge locates these patched runtime artifacts through libraryDirectory, beyond the bootstrap classpath.
$runtimeArtifacts = @(
    'net/minecraftforge/forge/1.20.1-47.3.33/forge-1.20.1-47.3.33-client.jar',
    'net/minecraftforge/forge/1.20.1-47.3.33/forge-1.20.1-47.3.33-universal.jar',
    'net/minecraftforge/fmlcore/1.20.1-47.3.33/fmlcore-1.20.1-47.3.33.jar',
    'net/minecraftforge/javafmllanguage/1.20.1-47.3.33/javafmllanguage-1.20.1-47.3.33.jar',
    'net/minecraftforge/lowcodelanguage/1.20.1-47.3.33/lowcodelanguage-1.20.1-47.3.33.jar',
    'net/minecraftforge/mclanguage/1.20.1-47.3.33/mclanguage-1.20.1-47.3.33.jar',
    'net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-extra.jar'
)
foreach ($relative in $runtimeArtifacts) {
    if (-not (Test-Path -LiteralPath (Resolve-ArtifactPath $relative) -PathType Leaf)) { throw "Missing patched Forge runtime artifact: $relative" }
}
$sourceClientJar = Join-Path (Join-Path (Join-Path $MinecraftRoot 'versions') $manifest.jar) ($manifest.jar + '.jar')
if (-not (Test-Path -LiteralPath $sourceClientJar -PathType Leaf)) { throw 'The merged manifest vanilla client jar is missing.' }
if ((Get-FileHash -LiteralPath $sourceClientJar -Algorithm SHA1).Hash -ne $manifest.downloads.client.sha1) { throw 'Cached vanilla client jar SHA-1 does not match the manifest.' }
$assetIndexPath = Join-Path (Join-Path $sourceAssetsDirectory 'indexes') ($manifest.assetIndex.id + '.json')
if (-not (Test-Path -LiteralPath $assetIndexPath -PathType Leaf)) { throw 'The cached Minecraft asset index is missing.' }
if ((Get-FileHash -LiteralPath $assetIndexPath -Algorithm SHA1).Hash -ne $manifest.assetIndex.sha1) {
    # Asset index 5 is refreshed by launchers as translations change. Preserve the shared cache;
    # use the manifest's immutable verified index locally while reusing all cached object files.
    $assetIndexPath = Join-Path $repositoryRoot ('.runtime\native-assets\' + $manifest.assetIndex.id + '-official.json')
    if (-not (Test-Path -LiteralPath $assetIndexPath -PathType Leaf)) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $assetIndexPath) | Out-Null
        Invoke-WebRequest -Uri $manifest.assetIndex.url -OutFile $assetIndexPath
    }
    if ((Get-FileHash -LiteralPath $assetIndexPath -Algorithm SHA1).Hash -ne $manifest.assetIndex.sha1) { throw 'The isolated immutable asset index does not match the manifest.' }
    $assetsDirectory = Join-Path $isolatedGameDir 'assets'
}
$assetIndex = Get-Content -LiteralPath $assetIndexPath -Raw -Encoding UTF8 | ConvertFrom-Json
$missingAssets = 0
foreach ($asset in $assetIndex.objects.PSObject.Properties) {
    $assetHash = [string]$asset.Value.hash
    if (-not (Test-Path -LiteralPath (Join-Path $sourceAssetsDirectory ('objects\' + $assetHash.Substring(0,2) + '\' + $assetHash)) -PathType Leaf)) { $missingAssets++ }
}
if ($missingAssets -ne 0) { throw "The read-only asset cache has $missingAssets missing objects; no downloads were attempted." }
$modArchive = [IO.Compression.ZipFile]::OpenRead($ModJar)
try {
    if (-not $modArchive.GetEntry('META-INF/mods.toml') -or -not $modArchive.GetEntry('META-INF/jarjar/metadata.json')) { throw 'The selected mod is not the complete Forge JarJar distribution.' }
    foreach ($nested in @('META-INF/jarjar/skija-shared-0.143.18.jar','META-INF/jarjar/types-0.2.0.jar','META-INF/jarjar/skija-windows-x64-0.143.18.jar')) {
        if (-not $modArchive.GetEntry($nested)) { throw "Missing packaged runtime: $nested" }
    }
} finally { $modArchive.Dispose() }

foreach ($directory in @($isolatedGameDir,(Join-Path $isolatedGameDir 'mods'),(Join-Path $isolatedGameDir 'natives'),(Join-Path $isolatedGameDir 'logs'),(Join-Path $isolatedGameDir 'runtime-cache'))) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}
if ($assetsDirectory -ne $sourceAssetsDirectory) {
    $isolatedIndexes = Join-Path $assetsDirectory 'indexes'
    New-Item -ItemType Directory -Force -Path $isolatedIndexes | Out-Null
    Copy-Item -LiteralPath $assetIndexPath -Destination (Join-Path $isolatedIndexes ($manifest.assetIndex.id + '.json')) -Force
    $objectsLink = Join-Path $assetsDirectory 'objects'
    $objectsTarget = Join-Path $sourceAssetsDirectory 'objects'
    if (-not (Test-Path -LiteralPath $objectsLink)) { New-Item -ItemType Junction -Path $objectsLink -Target $objectsTarget | Out-Null }
    else {
        $link = Get-Item -LiteralPath $objectsLink
        if ($link.LinkType -ne 'Junction' -or @($link.Target)[0] -ne $objectsTarget) { throw 'Unexpected isolated assets objects link; nothing was removed.' }
    }
}
$installedMod = Join-Path (Join-Path $isolatedGameDir 'mods') 'pixelmc-hub-preview.jar'
$unexpectedMods = @(Get-ChildItem -LiteralPath (Join-Path $isolatedGameDir 'mods') -File | Where-Object { $_.FullName -ne $installedMod })
if ($unexpectedMods.Count) { throw 'The isolated mods directory contains unexpected files; nothing was removed.' }
Copy-Item -LiteralPath $ModJar -Destination $installedMod -Force
$copiedClientJar = Join-Path (Join-Path $isolatedGameDir 'runtime-cache') ($previewVersion + '.jar')
Copy-Item -LiteralPath $sourceClientJar -Destination $copiedClientJar -Force
$classPathFiles.Add($copiedClientJar)
$nativeDirectory = Join-Path $isolatedGameDir 'natives'
$dllCount = 0
foreach ($nativeJar in $nativeJars) {
    $archive = [IO.Compression.ZipFile]::OpenRead($nativeJar)
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.Name -notlike '*.dll') { continue }
            # Take only the filename; no archive path may escape the isolated native directory.
            $destination = Join-Path $nativeDirectory ([IO.Path]::GetFileName($entry.Name))
            $source = $entry.Open()
            try {
                $target = [IO.File]::Create($destination)
                try { $source.CopyTo($target) } finally { $target.Dispose() }
            } finally { $source.Dispose() }
            $dllCount++
        }
    } finally { $archive.Dispose() }
}
if ($dllCount -lt 7) { throw 'Expected all Windows x64 LWJGL native DLLs to be present.' }
$replacements = @{
    auth_player_name='HubTester'; auth_uuid='00000000000040008000000000000001'; auth_access_token='0';
    clientid='0'; auth_xuid='0'; user_type='legacy'; version_type='release'; user_properties='{}';
    version_name=$previewVersion; game_directory=$isolatedGameDir; assets_root=$assetsDirectory;
    assets_index_name=[string]$manifest.assetIndex.id; natives_directory=$nativeDirectory;
    launcher_name='PixelMC-Hub-Local-Preview'; launcher_version='0.1'; library_directory=$libraryDirectory;
    classpath_separator=';'; classpath=($classPathFiles -join ';');
    resolution_width=[string]$Width; resolution_height=[string]$Height
}
function Expand-LaunchToken([string]$value) {
    foreach ($key in $replacements.Keys) { $value = $value.Replace('${' + $key + '}',[string]$replacements[$key]) }
    if ($value -match '\$\{') { throw 'Unresolved manifest launch token; refusing to launch.' }
    return $value
}
function Expand-ArgumentList($arguments) {
    foreach ($argument in $arguments) {
        if ($argument -is [string]) { Expand-LaunchToken $argument }
        elseif (Test-LaunchRules $argument.rules) { foreach ($value in @($argument.value)) { Expand-LaunchToken ([string]$value) } }
    }
}
$jvmArguments = @(Expand-ArgumentList $manifest.arguments.jvm)
$gameArguments = @(Expand-ArgumentList $manifest.arguments.game)
for ($i=0;$i -lt $jvmArguments.Count-1;$i++) {
    if ($jvmArguments[$i] -in @('-p','--module-path')) {
        foreach ($modulePath in $jvmArguments[$i+1].Split(';')) { if (-not (Test-Path -LiteralPath $modulePath -PathType Leaf)) { throw 'A bootstrap module-path jar is missing.' } }
    }
}
# Match the developer launcher workaround for affected Windows JDK17 AF_UNIX probes.
# The path deliberately must not exist; this makes JDK internals fall back to TCP loopback.
$unixProbePath = Join-Path $repositoryRoot ('.runtime\absent-unix-' + [guid]::NewGuid().ToString('N'))
if (Test-Path -LiteralPath $unixProbePath) { throw 'The AF_UNIX fallback probe path must not exist.' }
$javaErrorFile = Join-Path (Join-Path $isolatedGameDir 'logs') 'hs_err_pid%p.log'
$previewJvmArguments = @('-Xms512M',('-Xmx'+$MaximumHeapMiB+'M'),'-Dfile.encoding=UTF-8','-Dpixelmc_hub.previewEntry=true',('-Djdk.net.unixdomain.tmpdir='+$unixProbePath),('-XX:ErrorFile='+$javaErrorFile))
if ($OpenPanel) { $previewJvmArguments += @('-Dpixelmc.hub.autoOpen=true','-Dpixelmc.hub.capturePreview=true') }
$arguments = $previewJvmArguments + $jvmArguments + @([string]$manifest.mainClass) + $gameArguments
# Each argument occupies one quoted Java argfile line. Only fresh dummy offline identity is written.
$argumentLines = @($arguments | ForEach-Object { '"' + $_.Replace('\','\\').Replace('"','\"') + '"' })
$argumentFile = Join-Path (Join-Path $isolatedGameDir 'runtime-cache') 'packaged-preview.args'
[IO.File]::WriteAllLines($argumentFile,$argumentLines,[Text.UTF8Encoding]::new($false))
$summary = [ordered]@{
    preparationOnly = -not [bool]$Launch; openPanel=[bool]$OpenPanel; minecraft='1.20.1'; forge='47.3.33'; javaMajor=17;
    manifestIsFullyMerged=$true; gameDir=$isolatedGameDir; readOnlyLibraries=$libraryDirectory;
    readOnlyAssets=$sourceAssetsDirectory; launchAssetsRoot=$assetsDirectory; classPathJars=$classPathFiles.Count; windowsNativeJars=$nativeJars.Count;
    extractedDlls=$dllCount; verifiedAssetObjects=@($assetIndex.objects.PSObject.Properties).Count;
    modJar=$installedMod; modSha256=(Get-FileHash -LiteralPath $installedMod -Algorithm SHA256).Hash;
    offlineIdentity='HubTester / dummy UUID / token 0'; mainClass=[string]$manifest.mainClass
    unixDomainFallbackProbe=$unixProbePath; javaErrorFilePattern=$javaErrorFile
}
$summaryPath = Join-Path (Join-Path $isolatedGameDir 'runtime-cache') 'packaged-preview-verification.json'
[IO.File]::WriteAllText($summaryPath,($summary|ConvertTo-Json -Depth 4),[Text.UTF8Encoding]::new($false))
Write-Output ('Packaged preview prepared: Minecraft 1.20.1 / Forge 47.3.33 / Java17; '+$classPathFiles.Count+' classpath jars, '+$dllCount+' x64 native DLLs; exactly one isolated mod.')
Write-Output ('Verification: '+$summaryPath)
if ($Launch) {
    $stdout = Join-Path (Join-Path $isolatedGameDir 'logs') 'packaged-stdout.log'
    $stderr = Join-Path (Join-Path $isolatedGameDir 'logs') 'packaged-stderr.log'
    $process = Start-Process -FilePath $JavaExe -ArgumentList ('@'+$argumentFile) -WorkingDirectory $isolatedGameDir -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    Write-Output ('Launched isolated packaged preview; PID '+$process.Id+'.')
} else { Write-Output 'No client process started. Pass -Launch explicitly when ready.' }
