package com.gettogether.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
