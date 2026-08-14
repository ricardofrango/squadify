package com.ricardofrango.footballmanager

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform