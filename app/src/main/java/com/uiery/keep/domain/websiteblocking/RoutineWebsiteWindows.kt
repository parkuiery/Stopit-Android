package com.uiery.keep.domain.websiteblocking

import com.uiery.keep.model.RoutineModel
import com.uiery.keep.util.currentRoutineWindowEndDateTime
import com.uiery.keep.util.isRoutineActiveNow
import com.uiery.keep.util.toDayOfWeekList
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 저장된 루틴을 "지금 웹을 막고 있는 창"으로 옮긴다.
 *
 * 이 계산이 알람 수신자 안에 있으면 알람이 유일한 계기가 된다. 부팅 직후나 앱 진입처럼
 * 창 한가운데에서 다시 판정해야 하는 자리들이 같은 규칙을 쓸 수 있도록 여기에 둔다.
 */
fun List<RoutineModel>.toRoutineWebsiteWindows(
    nowDateTime: LocalDateTime = LocalDateTime.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<RoutineWebsiteWindow> = map { routine ->
    RoutineWebsiteWindow(
        isEnabled = routine.isEnabled,
        isActiveNow = isRoutineActiveNow(
            startTime = routine.startTime,
            endTime = routine.endTime,
            repeatDays = routine.repeatDays.toDayOfWeekList(),
            nowDateTime = nowDateTime,
        ),
        endEpochMillis = currentRoutineWindowEndDateTime(
            startTime = routine.startTime,
            endTime = routine.endTime,
            nowDateTime = nowDateTime,
        ).atZone(zoneId).toInstant().toEpochMilli(),
        websites = routine.lockWebsites.orEmpty().toSet(),
    )
}
