$ErrorActionPreference = 'Stop'
$project = Join-Path $PSScriptRoot 'WiiUController.Windows.csproj'
$native = Join-Path $PSScriptRoot 'build/native'
$output = Join-Path $PSScriptRoot '../dist/windows'
# Recreate staging so old driver backends cannot enter a new ZIP.
if (Test-Path $output) { Remove-Item $output -Recurse -Force }
cmake -S (Join-Path $PSScriptRoot 'native') -B $native -A x64
if ($LASTEXITCODE -ne 0) { throw 'Native configure failed' }
cmake --build $native --config Release --parallel
if ($LASTEXITCODE -ne 0) { throw 'Native build failed' }
cmake --install $native --config Release --prefix $output
if ($LASTEXITCODE -ne 0) { throw 'Native staging failed' }
dotnet publish $project -c Release -r win-x64 --self-contained true -o $output
if ($LASTEXITCODE -ne 0) { throw 'Windows publish failed' }
Copy-Item (Join-Path $PSScriptRoot 'README.md') $output
Copy-Item (Join-Path $PSScriptRoot '../LICENSE') $output
Copy-Item (Join-Path $PSScriptRoot '../THIRD_PARTY_NOTICES.md') $output
$archive = Join-Path $PSScriptRoot '../dist/WiiU-Controller-Windows-x64.zip'
Compress-Archive -Path "$output/*" -DestinationPath $archive -Force
$digest = (Get-FileHash $archive -Algorithm SHA256).Hash.ToLowerInvariant()
"$digest  WiiU-Controller-Windows-x64.zip" | Set-Content (Join-Path $PSScriptRoot '../dist/Windows-SHA256SUMS.txt') -Encoding ascii
