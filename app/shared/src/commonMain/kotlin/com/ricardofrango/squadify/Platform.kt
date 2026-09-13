package com.ricardofrango.squadify

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform