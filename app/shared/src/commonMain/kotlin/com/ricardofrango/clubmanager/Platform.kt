package com.ricardofrango.clubmanager

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform