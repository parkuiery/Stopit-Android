package com.uiery.keep.analytics

import com.uiery.keep.BuildConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdMobConfigTest {
    @Test
    fun flavorBuildConfigProvidesExpectedAdMobApplicationAndBannerUnits() {
        val placements = AdPlacement.entries.associateBy { it.analyticsPlacement }

        assertEquals(
            setOf(
                "block_top",
                "home_bottom",
                "lock_bottom",
                "menu_bottom",
                "routine_list_bottom",
                "routine_empty_bottom",
            ),
            placements.keys,
        )

        when (BuildConfig.FLAVOR) {
            "dev" -> {
                assertEquals("ca-app-pub-3940256099942544~3347511713", BuildConfig.ADMOB_APPLICATION_ID)
                placements.values.forEach { placement ->
                    assertEquals("ca-app-pub-3940256099942544/6300978111", placement.adUnitId)
                    assertFalse(placement.adUnitId.contains("1537867411423705"))
                }
            }
            "prod" -> {
                assertEquals("ca-app-pub-1537867411423705~6734784292", BuildConfig.ADMOB_APPLICATION_ID)
                assertEquals("ca-app-pub-1537867411423705/5467753282", placements.getValue("block_top").adUnitId)
                assertEquals("ca-app-pub-1537867411423705/5120253017", placements.getValue("home_bottom").adUnitId)
                assertEquals("ca-app-pub-1537867411423705/7892727021", placements.getValue("lock_bottom").adUnitId)
                assertEquals("ca-app-pub-1537867411423705/3270829732", placements.getValue("menu_bottom").adUnitId)
                assertEquals("ca-app-pub-1537867411423705/7750072748", placements.getValue("routine_list_bottom").adUnitId)
                assertEquals("ca-app-pub-1537867411423705/9271028233", placements.getValue("routine_empty_bottom").adUnitId)
            }
            else -> error("Unexpected flavor: ${BuildConfig.FLAVOR}")
        }
    }

    @Test
    fun productionAdMobInventoryIsDeclaredOnlyInFlavorConfig() {
        val buildGradle = File("build.gradle.kts").readText()

        listOf(
            "ca-app-pub-1537867411423705~6734784292",
            "ca-app-pub-1537867411423705/5467753282",
            "ca-app-pub-1537867411423705/5120253017",
            "ca-app-pub-1537867411423705/7892727021",
            "ca-app-pub-1537867411423705/3270829732",
            "ca-app-pub-1537867411423705/7750072748",
            "ca-app-pub-1537867411423705/9271028233",
        ).forEach { productionId ->
            assertTrue("$productionId should be declared in the prod flavor config", buildGradle.contains(productionId))
        }

        val mainSources = File("src/main")
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "xml") }
            .filterNot { it.invariantSeparatorsPath.endsWith("AndroidManifest.xml") }
            .joinToString(separator = "\n") { it.readText() }

        assertFalse(
            "Production AdMob ids must not be hardcoded in Kotlin/XML call sites outside Gradle flavor config",
            mainSources.contains("ca-app-pub-1537867411423705"),
        )
    }
}
