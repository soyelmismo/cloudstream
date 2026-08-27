package com.lagradost.cloudstream3.shared.syncproviders

object SyncConfig {
    var anilistKey: String = "1396"
    var malKey: String = "6114d00ca681b7701d1e15001a155507"
    var simklClientId: String = "a557876a3ff898ef47754f9a0c649eb0709d6c41dd0c72c1c69c6cf55ac510c4"
    var simklClientSecret: String = ""

    fun init(
        anilistKey: String? = null,
        malKey: String? = null,
        simklClientId: String? = null,
        simklClientSecret: String? = null,
    ) {
        anilistKey?.takeIf { it.isNotBlank() && it != "null" }?.let { this.anilistKey = it }
        malKey?.takeIf { it.isNotBlank() && it != "null" }?.let { this.malKey = it }
        simklClientId?.takeIf { it.isNotBlank() && it != "null" }?.let { this.simklClientId = it }
        simklClientSecret?.takeIf { it.isNotBlank() && it != "null" }?.let { this.simklClientSecret = it }
    }
}
