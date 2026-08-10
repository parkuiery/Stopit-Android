package com.uiery.keep.domain.repeatblock

/**
 * 앱이 어떤 부류인지 답한다.
 *
 * 판단 근거(시스템이 밝힌 카테고리·이름 짐작)는 구현이 정하고, 제안 정책은 결과만 받는다.
 * 정책이 직접 PackageManager 를 들여다보면 순수 로직이 아니게 되어 기기 없이는 검증할 수
 * 없다.
 */
fun interface AppCategoryResolver {
    fun categoryOf(packageName: String): RepeatBlockCategoryBucket

    companion object {
        /** 시스템에 물어볼 수 없는 자리(테스트·미리보기)에서 쓰는 이름 기반 판단. */
        val FromPackageName = AppCategoryResolver(::repeatBlockCategoryFromPackageName)
    }
}
