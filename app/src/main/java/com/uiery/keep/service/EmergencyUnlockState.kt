package com.uiery.keep.service

data class EmergencyUnlockData(
    val unlockedApps: Set<String> = emptySet(),
    val expireTimeMillis: Long = 0L,
) {
    companion object {
        val EMPTY = EmergencyUnlockData()
    }
}

object EmergencyUnlockState {
    @Volatile
    var current: EmergencyUnlockData = EmergencyUnlockData.EMPTY
}
