# One-shot installer for Windows. Run from the project folder:
#   powershell -ExecutionPolicy Bypass -File install.ps1
$ErrorActionPreference = "Stop"
$mc   = Join-Path $env:APPDATA ".minecraft"
$mods = Join-Path $mc "mods"
$tmp  = Join-Path $env:TEMP "companion-install"
New-Item -ItemType Directory -Force -Path $mods, $tmp | Out-Null
$h = @{ "User-Agent" = "companion-installer" }

function Step($m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function Have($c) { return [bool](Get-Command $c -ErrorAction SilentlyContinue) }

Step "Java 17+"
if (-not (Have java)) { winget install -e --id EclipseAdoptium.Temurin.17.JDK --accept-package-agreements --accept-source-agreements }
if (-not (Have java)) { Write-Host "Java was installed; close this window, open a new one and run the script again." -ForegroundColor Yellow; exit 1 }

Step "Ollama"
if (-not (Have ollama)) { winget install -e --id Ollama.Ollama --accept-package-agreements --accept-source-agreements }

Step "Fabric Loader for 1.20.1"
$inst = (Invoke-RestMethod "https://meta.fabricmc.net/v2/versions/installer" -Headers $h | Where-Object { $_.stable } | Select-Object -First 1).url
Invoke-WebRequest $inst -OutFile "$tmp\fabric-installer.jar"
java -jar "$tmp\fabric-installer.jar" client -mcversion 1.20.1 -dir $mc

function Get-Modrinth($slug) {
    try {
        $u = "https://api.modrinth.com/v2/project/$slug/version?game_versions=%5B%221.20.1%22%5D&loaders=%5B%22fabric%22%5D"
        $f = (Invoke-RestMethod $u -Headers $h | Select-Object -First 1).files | Where-Object { $_.primary } | Select-Object -First 1
        Invoke-WebRequest $f.url -OutFile (Join-Path $mods $f.filename)
        Write-Host "  installed $($f.filename)"
    } catch { Write-Host "  could not install $slug automatically: $($_.Exception.Message)" -ForegroundColor Yellow }
}
Step "Fabric API, MaLiLib, Litematica"
"fabric-api", "malilib", "litematica" | ForEach-Object { Get-Modrinth $_ }

Step "Baritone"
try {
    $rel = Invoke-RestMethod "https://api.github.com/repos/cabaletta/baritone/releases?per_page=30" -Headers $h |
        Where-Object { $_.tag_name -like "v1.10.*" } | Select-Object -First 1
    $a = $rel.assets | Where-Object { $_.name -like "baritone-standalone-fabric*" -and $_.name -notlike "*sources*" } | Select-Object -First 1
    Invoke-WebRequest $a.browser_download_url -OutFile (Join-Path $mods $a.name)
    Write-Host "  installed $($a.name)"
} catch {
    Write-Host "  could not install Baritone automatically. Download the Fabric 1.20.1 jar from https://github.com/cabaletta/baritone/releases into $mods" -ForegroundColor Yellow
}

Step "Building the Companion mod"
& .\gradlew.bat build
$jar = Get-ChildItem "build\libs\autonomous-companion-*.jar" | Where-Object { $_.Name -notlike "*sources*" } | Select-Object -First 1
Copy-Item $jar.FullName $mods -Force
Write-Host "  installed $($jar.Name)"

Step "AI model (about 5 GB, takes a while)"
Start-Process ollama -ArgumentList "serve" -WindowStyle Hidden -ErrorAction SilentlyContinue
Start-Sleep 5
ollama pull llama3.1:8b

Write-Host "`nDone. Start Minecraft with the 'fabric-loader-1.20.1' profile, join a world and type  !help" -ForegroundColor Green
