package com.uiery.keep.feature.onboarding.notification

internal sealed interface PostNotificationPermissionResultAction {
    data object RecordGrantAndContinue : PostNotificationPermissionResultAction
    data object RecordDenialAndContinue : PostNotificationPermissionResultAction
}

internal fun resolvePostNotificationPermissionResultAction(
    isGranted: Boolean,
): PostNotificationPermissionResultAction = when {
    isGranted -> PostNotificationPermissionResultAction.RecordGrantAndContinue
    else -> PostNotificationPermissionResultAction.RecordDenialAndContinue
}
