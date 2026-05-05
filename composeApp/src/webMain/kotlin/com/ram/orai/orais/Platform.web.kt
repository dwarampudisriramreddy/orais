package com.ram.orai.orais

class WebPlatform : Platform {
    override val name: String = "Web"
}

actual fun getPlatform(): Platform = WebPlatform()
actual fun getPlatformName(): String = "Web"
