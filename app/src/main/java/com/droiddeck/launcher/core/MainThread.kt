package com.droiddeck.launcher.core

import android.os.Handler
import android.os.Looper

/** 메인 스레드에 작업을 올린다. 스토어 제거 전 StoresState.post가 하던 역할. */
object MainThread {
    private val handler = Handler(Looper.getMainLooper())
    fun post(action: () -> Unit) { handler.post(action) }
}

object Nav {
    const val EXTRA_NAV = "droiddeck.nav"
}
