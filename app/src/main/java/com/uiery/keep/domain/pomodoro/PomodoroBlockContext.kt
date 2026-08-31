package com.uiery.keep.domain.pomodoro

import com.uiery.keep.datastore.PomodoroSessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject

/**
 * 차단 화면이 집중 세션을 설명하기 위해 필요한 것.
 *
 * 이 화면을 보는 사람은 자기가 세션을 걸어 둔 사람이지만, **왜 지금 막혔는지는 모른다.** 특히
 * 휴식 중이라면 "쉬는 중인데 왜 안 열리지"가 첫 반응이다. 그 순간이 이 기능의 핵심 계약을 다시
 * 말해야 하는 자리다.
 *
 * 세션 자체가 아니라 **화면이 쓸 값만** 담는다. 구간을 `isBreak` 로 눌러 두어 내부 enum 이 공개
 * UI 상태로 새지 않게 한다.
 */
data class PomodoroBlockContext(
    val isBreak: Boolean,
    val remainingSeconds: Int,
    val cycleIndex: Int,
    val cyclesPerSession: Int,
)

/**
 * 지금 집중 세션이 어느 구간인지 읽는다. 세션 자체는 넘기지 않는다.
 *
 * 차단 화면은 공개 표면이고 세션은 아니다 — `ParentModeBlockReasonSource` 와 같은 이유의 이음매다.
 */
fun interface PomodoroBlockContextSource {
    suspend fun blockContext(now: Instant): PomodoroBlockContext?
}

internal class StoredPomodoroBlockContextSource @Inject constructor(
    private val store: PomodoroSessionStore,
) : PomodoroBlockContextSource {
    /**
     * 세션이 없거나 이미 끝났으면 `null` — 그때 차단은 다른 잠금이 만든 것이고, 집중 세션 문구를
     * 얹으면 거짓말이 된다.
     */
    override suspend fun blockContext(now: Instant): PomodoroBlockContext? {
        val stored = store.readStoredSession() ?: return null
        val resolved = PomodoroPolicy.resolve(session = stored, now = now)
        if (resolved.status.isFinished) return null
        return PomodoroBlockContext(
            isBreak = resolved.phase.isBreak,
            remainingSeconds = PomodoroPolicy.remaining(session = resolved, now = now)
                .seconds
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt(),
            cycleIndex = resolved.cycleIndex,
            cyclesPerSession = resolved.cycle.cycles,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PomodoroBlockContextModule {
    @Binds
    abstract fun bindPomodoroBlockContextSource(
        source: StoredPomodoroBlockContextSource,
    ): PomodoroBlockContextSource
}
