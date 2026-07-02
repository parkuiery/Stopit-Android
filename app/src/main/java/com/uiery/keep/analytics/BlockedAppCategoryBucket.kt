package com.uiery.keep.analytics

object BlockedAppCategoryBucket {
    const val SOCIAL = "social"
    const val VIDEO = "video"
    const val GAME = "game"
    const val COMMUNICATION = "communication"
    const val SHOPPING = "shopping"
    const val BROWSER = "browser"
    const val PRODUCTIVITY = "productivity"
    const val UNKNOWN = "unknown"
}

private val socialPackageTokens = listOf(
    "instagram",
    "facebook",
    "tiktok",
    "twitter",
    "xhs",
    "snapchat",
    "pinterest",
)

private val videoPackageTokens = listOf(
    "youtube",
    "netflix",
    "twitch",
    "disney",
    "video",
    "ott",
)

private val gamePackageTokens = listOf(
    "game",
    "games",
    "roblox",
    "minecraft",
    "pubg",
    "riotgames",
)

private val communicationPackageTokens = listOf(
    "kakao",
    "whatsapp",
    "telegram",
    "discord",
    "messenger",
    "line",
    "messages",
)

private val shoppingPackageTokens = listOf(
    "shopping",
    "amazon",
    "coupang",
    "ebay",
    "aliexpress",
    "market",
)

private val browserPackageTokens = listOf(
    "chrome",
    "browser",
    "firefox",
    "samsung.android.app.sbrowser",
    "edge",
    "opera",
)

private val productivityPackageTokens = listOf(
    "docs",
    "notion",
    "evernote",
    "calendar",
    "office",
    "slack",
    "todo",
)

fun blockedAppCategoryBucketForPackage(packageName: String): String {
    val normalized = packageName.lowercase()
    return when {
        socialPackageTokens.any(normalized::contains) -> BlockedAppCategoryBucket.SOCIAL
        videoPackageTokens.any(normalized::contains) -> BlockedAppCategoryBucket.VIDEO
        gamePackageTokens.any(normalized::contains) -> BlockedAppCategoryBucket.GAME
        communicationPackageTokens.any(normalized::contains) -> BlockedAppCategoryBucket.COMMUNICATION
        shoppingPackageTokens.any(normalized::contains) -> BlockedAppCategoryBucket.SHOPPING
        browserPackageTokens.any(normalized::contains) -> BlockedAppCategoryBucket.BROWSER
        productivityPackageTokens.any(normalized::contains) -> BlockedAppCategoryBucket.PRODUCTIVITY
        else -> BlockedAppCategoryBucket.UNKNOWN
    }
}
