package com.uiery.keep.domain.lock

/**
 * 잠금이 실제로 막는 대상. 앱만 막는 잠금과 웹사이트만 막는 잠금은 사용자에게
 * 다른 약속이므로 안내 문구도 달라야 한다.
 */
enum class LockTargetKind {
    Apps,
    Websites,
    AppsAndWebsites,
    ;

    companion object {
        fun of(
            hasApps: Boolean,
            hasWebsites: Boolean,
        ): LockTargetKind = when {
            hasApps && hasWebsites -> AppsAndWebsites
            hasWebsites -> Websites
            // 루틴 잠금처럼 이 화면이 대상 목록을 들고 있지 않은 경우가 있다. 그때는
            // 지금까지의 앱 문구를 그대로 유지해 없던 대상을 새로 주장하지 않는다.
            else -> Apps
        }
    }
}
