package com.uiery.keep

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.uiery.kds.theme.KeepTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BlockActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = createBlockActivityArgs(
            packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME),
            blockSource = intent.getStringExtra(EXTRA_BLOCK_SOURCE),
            rawRoutineId = intent.extras?.getCompat(EXTRA_ROUTINE_ID),
        )
        enableEdgeToEdge()
        setContent {
            KeepTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = KeepTheme.colors.background,
                ) { innerPadding ->
                    BlockScreen(
                        modifier = Modifier.padding(innerPadding),
                        packageName = args.packageName,
                        blockSource = args.blockSource,
                        routineId = args.routineId,
                        onClose = {
                            val homeIntent = Intent(Intent.ACTION_MAIN)
                            homeIntent.addCategory(Intent.CATEGORY_HOME)
                            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(homeIntent)
                            finishAffinity()
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_BLOCK_SOURCE = "block_source"
        const val EXTRA_ROUTINE_ID = "routine_id"
    }
}

internal data class BlockActivityArgs(
    val packageName: String,
    val blockSource: String,
    val routineId: String?,
)

internal fun createBlockActivityArgs(
    packageName: String?,
    blockSource: String?,
    rawRoutineId: Any?,
): BlockActivityArgs = BlockActivityArgs(
    packageName = packageName ?: "",
    blockSource = blockSource.orDefaultBlockSource(),
    routineId = normalizeRoutineIdExtra(rawRoutineId),
)

internal fun normalizeRoutineIdExtra(rawRoutineId: Any?): String? =
    when (rawRoutineId) {
        is String -> rawRoutineId.trim().takeIf { it.isNotEmpty() }
        is Number -> rawRoutineId.toLong().toString()
        else -> null
    }

@Suppress("DEPRECATION")
private fun Bundle.getCompat(key: String): Any? = get(key)
