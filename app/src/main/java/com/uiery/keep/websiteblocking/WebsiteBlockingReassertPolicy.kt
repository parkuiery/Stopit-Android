package com.uiery.keep.websiteblocking

/**
 * 홈으로 돌아왔을 때 웹 차단을 다시 세워야 하는지.
 *
 * 홈의 판정 효과는 판정값이 *바뀔 때만* 돈다. 창 안에서 서비스만 죽으면 판정은 계속
 * `Running` 이라 아무 일도 일어나지 않고, 그 창은 끝날 때까지 열린 채 남는다.
 *
 * 그렇다고 돌아올 때마다 무조건 다시 세우면 안 된다. 이미 서 있는 필터를 헛되이 다시
 * 만들게 되고, 물러나 재시도 중인 서비스의 백오프와 싸우게 되며, 동의를 거부한 사람에게는
 * 홈에 올 때마다 시스템 창을 다시 들이미는 꼴이 된다. 그래서 "아무것도 서 있지 않을 때"
 * 하나만 다시 세운다.
 */
object WebsiteBlockingReassertPolicy {
    fun shouldReassertOnResume(status: WebsiteBlockingStatus): Boolean =
        status == WebsiteBlockingStatus.Inactive
}
