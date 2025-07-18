param(
        [Parameter(Mandatory=$false)][string]$seed,
        [Parameter(Mandatory=$false)][string]$hlt,
        [Parameter(Mandatory=$false)][string]$playerId
)

if (Test-Path bot.debug) {
	Remove-Item bot.debug
}

.\dist.ps1

Remove-Item stats\*.stats -Force
$size = (20,25,25,30,30,30,35,35,35,40,40,40,45,45,50) | Get-Random
$size = (20) | Get-Random
$player_count = (2,2,2,2,2,3,3,3,3,4,4,4,5,5,6) | Get-Random
$player_count = (2) | Get-Random



if (Test-Path env:playerId) {
	$player_count = $playerId
}

$myBot = "python bots\MyBot\MyBot.py"
$myBot = "python MyBot.py"

$players = New-Object System.Collections.ArrayList($null)
# $players.add($myBot)

# $players.add("python bots\ComboBot\MyBot.py")


$opponents = New-Object System.Collections.ArrayList($null)
# $opponents.add("python bots\BreachBot\MyBot.py")
# $opponents.add("python bots\ComboBot\MyBot.py")
# $opponents.add("python bots\RefBot\MyBot.py")
$opponents.add("python bots\ExploreBot\MyBot.py")
# $opponents.add("python bots\PypyBot\MyBot.py")
# $opponents.add("python bots\MyBot\MyBot.py")
# $opponents.add("python bots\PypyBot\MyBot.py")





for ($i=1; $i -lt $player_count; $i++)
{
  $opponent = $opponents | Get-Random
  $players.add($opponent)
}

$players.add($myBot)



$children = Get-ChildItem [1-9]*.hlt
$old_replays = Get-ChildItem reload[1-9]*.hlt

$players

if ($hlt) {
	..\halite_reload\bin\halite_reload.exe $hlt $seed $players
} else {
	if ($seed) {
		$seed="-s $seed"
	}
	.\halite.exe -d "$size $size" $players $seed 
}

Remove-Item $children -Force
Remove-Item $old_replays -Force

Move-Item *.log error.log -Force
# python printStats.py
#(python printStats.py | out-string -stream | sls -Pattern "(lineno|networking|hlt|MyBot)"| out-string -stream)

