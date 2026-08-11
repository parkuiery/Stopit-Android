package com.uiery.keep.domain.websiteblocking

import com.uiery.keep.model.RoutineModel

/**
 * 루틴 시간대의 웹 차단을 지금 상태에 맞춰 세우거나 내린다.
 *
 * 알람·부팅·루틴 화면이 모두 이 한 가지 계약을 통해 같은 판정을 돌린다. 계기마다 따로
 * 판단하면 창마다 다른 약속이 된다.
 */
fun interface RoutineWebsiteBlockingLauncher {
    fun apply(routines: List<RoutineModel>)
}
