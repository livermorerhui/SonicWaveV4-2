# Heartbeat fix summary

## Modified files
- apps/android/src/main/java/com/example/sonicwavev4/utils/DeviceHeartbeatManager.kt
- apps/android/src/main/java/com/example/sonicwavev4/utils/HeartbeatManager.kt

## Behavior boundaries
- Device/App heartbeat now keeps running while the process is in foreground regardless of offline mode toggles. Lifecycle (foreground/background) still controls start/stop.
- User heartbeat continues to depend on being online with a valid session; offline mode stops only the user heartbeat.

## Verification steps
1. Start backend and admin-web services.
2. Install and launch the app on an emulator.
3. Log in online and confirm device heartbeat starts.
4. Switch to offline mode and wait multiple intervals; device heartbeat continues.
5. Switch back to online mode; device heartbeat continues.
6. Press Home to background the app; heartbeats pause. Return to foreground; heartbeats resume.
