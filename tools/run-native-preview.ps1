param(
    [switch]$BuildOnly,
    [switch]$Offline,
    [switch]$OpenPanel,
    [string]$JavaHome = 'C:\Program Files\Java\jdk-17'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$nativeRoot = Join-Path $projectRoot 'minecraft'
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) {
    throw 'Java 17 is required. Pass -JavaHome with your JDK 17 installation path.'
}
$savedJavaHome = $env:JAVA_HOME
$savedJavaOptions = $env:JAVA_TOOL_OPTIONS
$savedPath = $env:Path
$resultCode = 1
try {
    $env:JAVA_HOME = $JavaHome
    $env:Path = (Join-Path $JavaHome 'bin') + ';' + $savedPath
    # Work around JDK 17's AF_UNIX probe on affected Windows hosts. This deliberately
    # nonexistent path makes the JDK use loopback TCP for Gradle, without OS changes.
    $probePath = Join-Path $projectRoot ('.runtime\absent-unix-' + [guid]::NewGuid().ToString('N'))
    $env:JAVA_TOOL_OPTIONS = ($savedJavaOptions + ' "-Djdk.net.unixdomain.tmpdir=' + $probePath + '"').Trim()
    Push-Location $nativeRoot
    try {
        $taskName = if ($BuildOnly) { 'build' } else { 'runClient' }
        $gradleArguments = @('--no-daemon', $taskName)
        if ($Offline) {
            # No downloads are performed in this mode; skip only ForgeGradle's network preflight.
            $gradleArguments += @('--offline', '-Dnet.minecraftforge.gradle.check.certs=false')
        }
        if ($OpenPanel -and -not $BuildOnly) {
            $gradleArguments += @('-Dpixelmc.hub.autoOpen=true', '-Dpixelmc.hub.capturePreview=true')
        }
        & .\gradlew.bat @gradleArguments
        $resultCode = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $savedJavaHome
    $env:JAVA_TOOL_OPTIONS = $savedJavaOptions
    $env:Path = $savedPath
}
exit $resultCode
