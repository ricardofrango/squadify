package com.sportssoftw.squadify

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform