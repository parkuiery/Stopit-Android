package com.uiery.kds

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepSwitchContractTest {

    @Test
    fun `large switch follows seed switchmark dimensions`() {
        val dimensions = keepSwitchDimensions(KeepSwitchSize.Large)

        assertEquals(52.dp, dimensions.trackWidth)
        assertEquals(32.dp, dimensions.trackHeight)
        assertEquals(3.dp, dimensions.padding)
        assertEquals(26.dp, dimensions.thumbSize)
        assertEquals(48.dp, dimensions.touchTarget)
    }

    @Test
    fun `compact switches preserve seed geometry and accessible touch target`() {
        val small = keepSwitchDimensions(KeepSwitchSize.Small)
        val medium = keepSwitchDimensions(KeepSwitchSize.Medium)

        assertEquals(26.dp, small.trackWidth)
        assertEquals(16.dp, small.trackHeight)
        assertEquals(12.dp, small.thumbSize)
        assertEquals(48.dp, small.touchTarget)

        assertEquals(38.dp, medium.trackWidth)
        assertEquals(24.dp, medium.trackHeight)
        assertEquals(20.dp, medium.thumbSize)
        assertEquals(48.dp, medium.touchTarget)
    }

    @Test
    fun `every visual switch size keeps an accessible touch target`() {
        // 이전에는 상자를 touchTarget 으로만 잡았다. Modifier.size 는 부모 제약 안으로 맞춰지므로
        // 그보다 넓은 Large 트랙(52dp)이 48dp 로 눌렸다.
        val source = File("src/main/java/com/uiery/kds/KeepSwitch.kt").readText()

        assertTrue(source.contains("width = dimensions.containerWidth"))
        assertTrue(source.contains("height = dimensions.containerHeight"))

        KeepSwitchSize.entries.forEach { size ->
            val dimensions = keepSwitchDimensions(size)
            assertTrue(
                "$size: 터치 영역이 48dp 미만이다",
                dimensions.containerWidth >= 48.dp && dimensions.containerHeight >= 48.dp,
            )
        }
    }

    @Test
    fun `the box is never sized by the touch target alone`() {
        // Modifier.size 는 부모 제약 안으로 맞춰진다. 상자를 touchTarget(48dp)으로만 잡으면
        // Large 트랙(52dp)이 48dp 로 눌리는데, 켜짐 상태의 썸 위치는 눌리기 전 52dp 로 계산되어
        // 썸이 오른쪽 끝에 붙고 여백이 사라진다. 실제로 오른쪽 여백 0dp / 위아래 3dp 로 어긋났다.
        val source = File("src/main/java/com/uiery/kds/KeepSwitch.kt").readText()

        assertFalse(
            "상자를 touchTarget 으로만 잡으면 그보다 넓은 트랙이 눌린다",
            source.contains("width = dimensions.touchTarget"),
        )
    }

    @Test
    fun `the large track is wider than the minimum touch target`() {
        // 이 관계가 위 결함의 전제다. SEED 치수가 바뀌어 이 단언이 깨지면 상자 계산도 다시 본다.
        val large = keepSwitchDimensions(KeepSwitchSize.Large)

        assertTrue(
            "Large 트랙이 더 이상 터치 최소 크기보다 넓지 않다",
            large.trackWidth > large.touchTarget,
        )
    }

    @Test
    fun `the checked thumb keeps the same padding on every side`() {
        // 켜짐 상태의 썸은 trackWidth - padding - thumbSize 에 놓인다. 좌우 여백은 padding 이고
        // 위아래 여백은 (trackHeight - thumbSize) / 2 이므로, 둘이 같아야 사방이 고르게 보인다.
        KeepSwitchSize.entries.forEach { size ->
            val dimensions = keepSwitchDimensions(size)

            assertEquals(
                "$size: 켜짐 상태의 좌우 여백이 위아래와 다르다",
                (dimensions.trackHeight - dimensions.thumbSize) / 2,
                dimensions.padding,
            )
        }
    }
}
