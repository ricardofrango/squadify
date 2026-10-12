package com.ricardofrango.squadify.presentation.ui.opponents

import io.ktor.http.encodeURLParameter

/** A maps search for [address]: opens the maps app on phones, or Google Maps in a browser. */
internal fun mapsUrl(address: String): String =
    "https://www.google.com/maps/search/?api=1&query=${address.trim().encodeURLParameter()}"
