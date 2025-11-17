package com.example.sonicwavev4.core

import com.example.sonicwavev4.utils.OfflineTestModeManager

enum class AppMode {
    ONLINE,
    OFFLINE
}

/**
 * Gets the current application mode based on the offline test manager.
 *
 * TODO(stage1+):
 *  - This is a temporary implementation for Stage 0.
 *  - Later this logic should be moved into an AppStateRepository and
 *    exposed as a reactive stream (e.g. StateFlow) via DI.
 *
 * NOTE(Stage0):
 *  - For Stage 0, DO NOT implement AppStateRepository or DI.
 *    Keep this simple helper as-is.
 */
fun currentAppMode(): AppMode {
    return if (OfflineTestModeManager.isOfflineMode()) {
        AppMode.OFFLINE
    } else {
        AppMode.ONLINE
    }
}
