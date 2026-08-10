package com.uiery.keep.data.repeatblock

import android.content.Context
import android.content.pm.ApplicationInfo
import com.uiery.keep.domain.repeatblock.AppCategoryResolver
import com.uiery.keep.domain.repeatblock.RepeatBlockCategoryBucket
import com.uiery.keep.domain.repeatblock.repeatBlockCategoryFromPackageName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * 설치된 앱이 어떤 부류인지 시스템이 아는 만큼 먼저 묻고, 모르면 이름으로 짐작한다.
 *
 * 이름 매칭만으로는 영문 키워드가 들어가지 않은 앱이 전부 Unknown 으로 떨어진다. 국내 앱
 * 대부분이 여기 해당해서, 저녁에 잠근 앱 수십 개가 통째로 한 무리가 되고 "저녁에 이 66개를
 * 루틴으로" 같은 아무도 만들지 않을 제안이 나왔다.
 *
 * 시스템 카테고리는 앱이 매니페스트에 `android:appCategory` 로 스스로 밝힌 값이라 표시
 * 언어와 무관하고, 있을 때는 이름보다 정확하다. 밝히지 않은 앱은 이름 매칭으로 넘긴다.
 */
class InstalledAppCategoryResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppCategoryResolver {
    // 한 번의 제안 계산에서 같은 패키지를 여러 번 묻는다. PackageManager 는 프로세스 밖
    // 호출이라 잠금 대상이 수십 개일 때 그대로 두면 눈에 띄게 느려진다.
    private val resolved = ConcurrentHashMap<String, RepeatBlockCategoryBucket>()

    override fun categoryOf(packageName: String): RepeatBlockCategoryBucket =
        resolved.computeIfAbsent(packageName) {
            declaredCategory(it) ?: repeatBlockCategoryFromPackageName(it)
        }

    private fun declaredCategory(packageName: String): RepeatBlockCategoryBucket? {
        val applicationInfo = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull() ?: return null

        return when (applicationInfo.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> RepeatBlockCategoryBucket.Social
            ApplicationInfo.CATEGORY_VIDEO -> RepeatBlockCategoryBucket.Video
            ApplicationInfo.CATEGORY_GAME -> RepeatBlockCategoryBucket.Game
            // 오디오·뉴스·지도·생산성처럼 대응하는 무리가 없는 카테고리는 여기서 답하지
            // 않는다. 무리를 늘리면 분석 지표의 값 집합이 함께 바뀌므로 별도 결정이 필요하고,
            // 그때까지는 이름 매칭이 맡거나 Unknown 으로 남아 패키지별로 따로 세어진다.
            else -> null
        }
    }
}
