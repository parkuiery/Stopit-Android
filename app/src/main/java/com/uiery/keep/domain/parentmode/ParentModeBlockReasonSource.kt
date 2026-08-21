package com.uiery.keep.domain.parentmode

import com.uiery.keep.data.parentmode.ParentModeSessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Reads why parent mode is blocking, without handing the session itself out.
 *
 * The block screen is public API surface and the parent mode session is not — it carries the allowed
 * package list, which the privacy contract keeps out of anything that leaves the feature. This
 * interface is the seam: callers learn the reason and nothing else.
 */
fun interface ParentModeBlockReasonSource {
    suspend fun blockReason(nowMillis: Long): ParentModeBlockReason?
}

internal class StoredParentModeBlockReasonSource @Inject constructor(
    private val store: ParentModeSessionStore,
) : ParentModeBlockReasonSource {
    override suspend fun blockReason(nowMillis: Long): ParentModeBlockReason? =
        ParentModeRuntimePolicy.blockReason(session = store.read(), nowMillis = nowMillis)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ParentModeBlockReasonModule {
    @Binds
    abstract fun bindParentModeBlockReasonSource(
        source: StoredParentModeBlockReasonSource,
    ): ParentModeBlockReasonSource
}
