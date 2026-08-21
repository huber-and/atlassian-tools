#
# Copyright 2025-2026 Andreas Huber
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Windows launcher for atlassian-cli: checks GitHub for the latest release on
# every run, downloads it if it is not already cached, and then runs it with
# the arguments given to this script. See cli/README.md for the Linux/macOS
# equivalent (atlassian-cli) and for what the CLI itself accepts.
#
# Cache location: $env:ATLASSIAN_CLI_HOME, or %USERPROFILE%\.atlassian-cli if
# unset. Optional: set $env:GITHUB_TOKEN to raise GitHub's unauthenticated API
# rate limit.
#
# Run with:  powershell -ExecutionPolicy Bypass -File atlassian-cli.ps1 <args>

$ErrorActionPreference = "Stop"

$Repo = "huber-and/atlassian-tools"
$InstallDir = if ($env:ATLASSIAN_CLI_HOME) { $env:ATLASSIAN_CLI_HOME } else { Join-Path $env:USERPROFILE ".atlassian-cli" }
$VersionFile = Join-Path $InstallDir "version"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
	Write-Error "java was not found on PATH. atlassian-cli needs Java 21 or later."
	exit 1
}

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null

$currentVersion = $null
if (Test-Path $VersionFile) {
	$currentVersion = (Get-Content $VersionFile -Raw).Trim()
}

$headers = @{ "Accept" = "application/vnd.github+json" }
if ($env:GITHUB_TOKEN) {
	$headers["Authorization"] = "Bearer $env:GITHUB_TOKEN"
}

$latestVersion = $null
try {
	$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/latest" `
		-Headers $headers -TimeoutSec 5
	$latestVersion = $release.tag_name
} catch {
	if (-not $currentVersion) {
		Write-Error "No cached atlassian-cli and GitHub could not be reached to download one."
		exit 1
	}
	Write-Warning "Could not reach GitHub to check for updates, using cached $currentVersion."
}

if ($latestVersion -and ($latestVersion -ne $currentVersion)) {
	Write-Host "Downloading atlassian-cli $latestVersion..." -ForegroundColor Cyan
	$downloadUrl = "https://github.com/$Repo/releases/download/$latestVersion/atlassian-cli-$latestVersion-shaded.jar"
	$tempFile = Join-Path $InstallDir "download.tmp"
	try {
		Invoke-WebRequest -Uri $downloadUrl -OutFile $tempFile -Headers $headers -TimeoutSec 120
		Move-Item -Force $tempFile (Join-Path $InstallDir "atlassian-cli-$latestVersion.jar")
		if ($currentVersion -and ($currentVersion -ne $latestVersion)) {
			Remove-Item -Force (Join-Path $InstallDir "atlassian-cli-$currentVersion.jar") -ErrorAction SilentlyContinue
		}
		Set-Content -Path $VersionFile -Value $latestVersion -NoNewline
		$currentVersion = $latestVersion
	} catch {
		Remove-Item -Force $tempFile -ErrorAction SilentlyContinue
		if (-not $currentVersion) {
			Write-Error "Could not download atlassian-cli $latestVersion."
			exit 1
		}
		Write-Warning "Could not download atlassian-cli $latestVersion, using cached $currentVersion."
	}
}

$jarPath = Join-Path $InstallDir "atlassian-cli-$currentVersion.jar"
if (-not (Test-Path $jarPath)) {
	Write-Error "Expected $jarPath but it is missing."
	exit 1
}

& java -jar $jarPath @args
exit $LASTEXITCODE
