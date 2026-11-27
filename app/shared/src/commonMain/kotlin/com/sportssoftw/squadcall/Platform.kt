package com.sportssoftw.squadcall

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform