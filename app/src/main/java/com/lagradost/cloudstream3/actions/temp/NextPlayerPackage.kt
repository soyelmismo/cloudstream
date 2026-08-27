package com.lagradost.cloudstream3.actions.temp

import com.lagradost.cloudstream3.utils.txt

/** https://github.com/anilbeesetti/nextplayer */
class NextPlayerPackage : SingleLinkExternalPlayerAction(
    appName = txt("NextPlayer"),
    packageName = "dev.anilbeesetti.nextplayer",
    intentClass = "dev.anilbeesetti.nextplayer.feature.player.PlayerActivity"
)