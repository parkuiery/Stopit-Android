package com.uiery.keep.domain.websiteblocking

import com.uiery.keep.model.RoutineModel

/**
 * 이 판정을 돌리게 만든 계기.
 *
 * 네 계기가 같은 판정을 돌리는 이유는 알람 하나가 실패해도(지연·누락·재부팅) 창이 통째로
 * 날아가지 않게 하려는 것이다. 그 이중화가 실제로 작동하는지는 "무엇이 세션을 세웠는가"를
 * 세어야만 알 수 있다. 알람이 거의 못 세우고 부팅·편집이 계속 구제하고 있다면 그건 이중화의
 * 성공이 아니라 알람 경로가 고장 났다는 신호다.
 *
 * docs/ROUTINE_WEBSITE_BLOCKING_TRIGGER_CONTRACT.md 가 계기 표의 source of truth다.
 */
enum class RoutineWebsiteBlockingTrigger {
    /** 루틴 시작 알람. 동의창을 띄울 수 없다. */
    ROUTINE_ALARM,

    /** 부팅 직후 재수화. 동의창을 띄울 수 없다. */
    BOOT,

    /** 루틴 생성·수정·삭제·ON/OFF. 웹사이트를 고르는 시점에 동의는 이미 받았다. */
    ROUTINE_EDIT,
}

/**
 * 루틴 시간대의 웹 차단을 지금 상태에 맞춰 세우거나 내린다.
 *
 * 알람·부팅·루틴 화면이 모두 이 한 가지 계약을 통해 같은 판정을 돌린다. 계기마다 따로
 * 판단하면 창마다 다른 약속이 된다. [trigger]는 판정을 바꾸지 않는다 — 무엇이 이 판정을
 * 불러냈는지 관측하기 위한 값이다.
 */
fun interface RoutineWebsiteBlockingLauncher {
    fun apply(
        routines: List<RoutineModel>,
        trigger: RoutineWebsiteBlockingTrigger,
    )
}
