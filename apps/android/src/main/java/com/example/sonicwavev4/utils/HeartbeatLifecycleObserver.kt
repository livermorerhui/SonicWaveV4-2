package com.example.sonicwavev4.utils

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class HeartbeatLifecycleObserver(
    private val appContext: Context
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        HeartbeatOrchestrator.onAppForeground(appContext)
    }

    override fun onStop(owner: LifecycleOwner) {
        HeartbeatOrchestrator.onAppBackground()
    }
}
