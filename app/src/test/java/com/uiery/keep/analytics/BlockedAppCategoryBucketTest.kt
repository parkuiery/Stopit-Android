package com.uiery.keep.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockedAppCategoryBucketTest {
    @Test
    fun packageNamesResolveToPrivacySafeCategoryBuckets() {
        assertEquals(BlockedAppCategoryBucket.SOCIAL, blockedAppCategoryBucketForPackage("com.instagram.android"))
        assertEquals(BlockedAppCategoryBucket.VIDEO, blockedAppCategoryBucketForPackage("com.google.android.youtube"))
        assertEquals(BlockedAppCategoryBucket.GAME, blockedAppCategoryBucketForPackage("com.roblox.client"))
        assertEquals(BlockedAppCategoryBucket.COMMUNICATION, blockedAppCategoryBucketForPackage("com.whatsapp"))
        assertEquals(BlockedAppCategoryBucket.SHOPPING, blockedAppCategoryBucketForPackage("com.amazon.mShop.android.shopping"))
        assertEquals(BlockedAppCategoryBucket.BROWSER, blockedAppCategoryBucketForPackage("com.android.chrome"))
        assertEquals(BlockedAppCategoryBucket.PRODUCTIVITY, blockedAppCategoryBucketForPackage("com.google.android.apps.docs"))
        assertEquals(BlockedAppCategoryBucket.UNKNOWN, blockedAppCategoryBucketForPackage("com.example.privateapp"))
    }
}
