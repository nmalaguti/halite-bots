$botName = "MyBot"
$botPath = "$PSScriptRoot\bots\$botName"

function ZipFiles( $zipfilename, $sourcedir )
{
   Add-Type -Assembly System.IO.Compression.FileSystem
   $compressionLevel = [System.IO.Compression.CompressionLevel]::Optimal
   [System.IO.Compression.ZipFile]::CreateFromDirectory($sourcedir,
        $zipfilename, $compressionLevel, $false)
}

$configFiles = Get-ChildItem . *.config -rec
foreach ($file in $configFiles)
{
    (Get-Content $file.PSPath) |
    Foreach-Object { $_ -replace "Dev", "Demo" } |
    Set-Content $file.PSPath
}

if (Test-Path $botPath) {
	Remove-Item $botPath -Recurse -Force
}
if (-Not (Test-Path $botPath)) {
	mkdir $botPath	
}
cp $PSScriptRoot\*.py $botPath
cp $PSScriptRoot\install.sh $botPath
cp $PSScriptRoot\LANGUAGE $botPath
cp $PSScriptRoot\halite.exe $botPath
if (Test-Path $botPath\RandomBot.py) {
	Remove-Item $botPath\RandomBot.py
}
if (Test-Path $botPath\MyBotCopy.py) {
	Remove-Item $botPath\MyBotCopy.py
}
if (Test-Path $PSScriptRoot\MyBot.zip) {
	Remove-Item $PSScriptRoot\MyBot.zip -Force
}

$scripts = Get-ChildItem $botPath *.py -rec
foreach ($file in $scripts)
{
    (Get-Content $file.PSPath) |
    Foreach-Object { $_ -creplace "^(.*?[a-zA-Z_]*?\.debug)", "#$&" } |
    Set-Content $file.PSPath
}

ZipFiles $PSScriptRoot\MyBot.zip $botPath
cp $PSScriptRoot\MyBot.zip $botPath
	

