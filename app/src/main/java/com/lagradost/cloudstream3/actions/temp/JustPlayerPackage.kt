package com.lagradost.cloudstream3.actions.temp

import com.lagradost.cloudstream3.utils.txt

/** https://github.com/moneytoo/Player/ */
class JustPlayerPackage : SingleLinkExternalPlayerAction(
    appName = txt("JustPlayer"),
    packageName = "com.brouken.player",
    intentClass = "com.brouken.player.PlayerActivity"
)