package com.uiery.kds

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepCardContractTest {

    private val source = File("src/main/java/com/uiery/kds/KeepCard.kt").readText()

    /**
     * SEED의 `bg.disabled`는 light 모드에서 `layer-basement`와 같은 `gray-200`이다. 화면 캔버스 위의
     * 카드에 그대로 쓰면 대비가 1.00:1이 되어 카드가 담은 정보까지 사라진다. read-only는 그 경우를
     * 위한 상태이므로 variant container를 유지해야 한다.
     */
    @Test
    fun `read only cards keep their container instead of dissolving into the canvas`() {
        assertTrue(
            "clickable KeepCard 는 readOnly 를 제공해야 한다",
            source.contains("readOnly: Boolean = false"),
        )
        assertTrue(
            "readOnly 는 disabled container 대신 variant container 를 유지해야 한다",
            Regex(
                """disabledContainerColor = if \(readOnly\) \{\s*keepCardContainerColor\(variant\)""",
            ).containsMatchIn(source),
        )
        assertTrue(
            "readOnly 의 content 는 읽을 수 있어야 하므로 foreground.disabled 를 쓰지 않는다",
            Regex(
                """disabledContentColor = if \(readOnly\) \{\s*KeepTheme\.semanticColors\.foreground\.muted""",
            ).containsMatchIn(source),
        )
        assertTrue(
            "readOnly 카드는 눌리지 않아야 한다",
            source.contains("enabled = enabled && !readOnly"),
        )
    }
}
