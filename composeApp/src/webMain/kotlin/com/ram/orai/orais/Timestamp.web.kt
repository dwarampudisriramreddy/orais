package com.ram.orai.orais

import kotlinx.browser.window

actual fun getCurrentTimestamp(): Long = window.performance.now().toLong()
