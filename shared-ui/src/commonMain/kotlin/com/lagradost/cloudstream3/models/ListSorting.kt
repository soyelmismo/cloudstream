package com.lagradost.cloudstream3.models

import cloudstream.shared_ui.generated.resources.*
import org.jetbrains.compose.resources.StringResource

enum class ListSorting(val stringRes: StringResource) {
    Query(Res.string.none),
    RatingHigh(Res.string.sort_rating_desc),
    RatingLow(Res.string.sort_rating_asc),
    UpdatedNew(Res.string.sort_updated_new),
    UpdatedOld(Res.string.sort_updated_old),
    AlphabeticalA(Res.string.sort_alphabetical_a),
    AlphabeticalZ(Res.string.sort_alphabetical_z),
    ReleaseDateNew(Res.string.sort_release_date_new),
    ReleaseDateOld(Res.string.sort_release_date_old),
}
