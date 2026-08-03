# QUERY_ALL_PACKAGES Play 정책 증적

이 문서는 issue #904 `[릴리즈 안전] QUERY_ALL_PACKAGES Play 정책 증적과 대체 가능성 점검`의 저장소 기준 source of truth다.

## 현재 결정

- 상태: `QUERY_ALL_PACKAGES`는 **유지하되, 앱 선택 picker 경계로만 제한**한다.
- 사용자-facing 목적: 사용자가 수동 잠금, 타이머 잠금, 루틴 잠금의 차단 대상을 직접 고를 수 있도록 설치된 launchable app 목록을 보여준다.
- 코드 소유권: broad package visibility query는 `app/src/main/java/com/uiery/keep/appselection/InstalledAppRepository.kt`만 소유한다.
- 정책 소유권: filtering/sorting/자기 앱 제외/launch intent 없는 앱 제외는 `SelectableAppPolicy`가 소유하고 JVM 테스트로 고정한다.
- UI 경계: onboarding/Home/Routine picker UI는 `InstalledAppRepository`/`SelectableAppPolicy` 경계를 소비할 수 있지만 `PackageManager.getInstalledApplications(...)`, `queryIntentActivities(...)`, raw package scan을 직접 소유하지 않는다.
- 차단 면제 경계: `AndroidBlockExemptPackageProvider`는 홈 런처/설정/전화/문자/기본 결제 앱을 `resolveActivity(...)`와 role API로 **단건 조회**만 하고 설치 앱을 열거하지 않는다. 결과는 `BlockExemptPackagePolicy`가 차단 대상에서 제외하는 데만 쓰이며 picker 후보를 만드는 데 쓰이지 않는다.

## 왜 필요한가

Stopit의 핵심 기능은 사용자가 선택한 앱을 차단하는 것이다. Android 11+ package visibility가 제한된 상태에서 일부 앱만 보이면 사용자는 실제로 차단하고 싶은 앱을 찾지 못할 수 있고, 루틴/타이머/수동 잠금의 core value가 깨진다.

필요한 사용자 시나리오:

1. 사용자가 온보딩 또는 Home/Routine app picker를 연다.
2. 설치된 launchable apps 목록에서 차단 대상 앱을 직접 고른다.
3. Stopit은 선택된 앱 package를 로컬 상태/Room/DataStore 계약에 저장하고, AccessibilityService가 foreground package와 비교해 차단한다.
4. package visibility query는 이 선택 목록을 구성하는 순간에만 필요하다.

## 데이터 최소화 / 금지 경계

허용:

- 설치 앱 목록을 읽어 **launchable app picker 후보**를 만든다.
- `SelectableAppPolicy`로 launch intent 없는 앱, Stopit 자기 앱, 빈/중복 후보를 제외한다.
- app label/icon은 사용자에게 선택 목록을 보여주기 위한 표시 metadata로만 사용한다.

금지:

- 설치 앱 전체 목록을 analytics, Crashlytics, FCM, Discord, 서버, 파일 export, 광고 SDK, 제3자 SDK로 전송하지 않는다.
- raw package list, app label list, 전체 설치 앱 snapshot을 GA4 parameter/user property로 남기지 않는다.
- 앱 선택 picker가 아닌 기능에서 broad installed-app scan을 새로 만들지 않는다.
- Play 정책 설명을 “usage analytics”, “personalization profiling”, “ad targeting”처럼 보이게 쓰지 않는다.

관련 analytics privacy 계약:

- 차단 앱 지표는 `docs/BLOCKED_APP_ANALYTICS_PRIVACY_CONTRACT.md`의 `blocked_app_category_bucket` 계약을 따른다.
- `blocked_app_package`, 앱 이름, 선택 앱 목록 원문은 신규 GA4 등록/조회 대상이 아니다.

## 대체 가능성 검토

| 대안 | 검토 결과 | 현재 판단 |
| --- | --- | --- |
| `<queries>`에 특정 package를 열거 | 사용자가 어떤 앱을 차단할지 사전에 알 수 없다. 인기 앱 whitelist 방식은 long-tail 앱, 로컬 앱, 새로 설치한 앱을 누락한다. | 핵심 picker 품질을 떨어뜨리므로 단독 대안으로 부적합 |
| launcher intent query만 사용 | launchable app 후보를 찾는 데 도움은 되지만, 기기/런처/OEM/visibility 제한에 따라 사용자가 기대하는 전체 선택 목록이 불완전해질 수 있다. 현재 구현은 broad scan 후 launch intent로 필터링한다. | 보조 필터로 유지, 권한 제거 대안으로는 추가 검증 필요 |
| 사용자가 package name을 직접 입력 | 일반 사용자에게 과도하게 어렵고 오입력/신뢰 하락 가능성이 높다. | UX 대안으로 부적합 |
| Usage Access 기반 최근 사용 앱만 제안 | 권한 부담이 더 커지고, 아직 선택하지 않은 앱/새 앱/공부 모드 차단 대상이 빠질 수 있다. #119 개인화 discovery와 별개다. | 권한 축이 다르므로 QUERY_ALL_PACKAGES 대체로 보지 않음 |
| 사용자가 시스템 picker/share sheet로 앱을 고름 | Android 표준 API로 설치 앱 multi-select blocker picker를 안정적으로 제공하지 않는다. | 현재 제품 요구에는 부적합 |

축소/제거 재검토 기준:

- Android platform이 사용자 주도 installed-app multi-select picker를 제공한다.
- Play 정책 또는 review feedback이 broad visibility를 불허하고, 제한 목록/사용자 검색 방식으로도 activation/retention 손실이 허용 가능하다는 지표가 나온다.
- Stopit이 “사용자가 직접 여러 앱을 골라 차단”하는 core UX를 다른 권한/흐름으로 대체한다는 대표님 decision이 있다.

## Play Console 권한 선언 문안

### 한국어

스탑잇은 사용자가 직접 차단할 앱을 선택할 수 있도록 설치된 앱 목록을 조회합니다. 이 목록은 수동 잠금, 타이머 잠금, 루틴 잠금에서 차단 대상 앱을 고르는 앱 선택 화면에만 사용됩니다. 스탑잇은 launchable app 후보를 표시한 뒤 사용자가 선택한 앱만 로컬 차단 설정에 저장하며, 설치 앱 전체 목록이나 앱 이름/package 목록을 analytics, 광고, 프로파일링, 서버 전송 또는 제3자 공유 목적으로 사용하지 않습니다.

### English

StopIt uses broad package visibility only so users can choose which installed apps they want to block. The installed-app list is used for the app-selection picker in manual locks, timer locks, and routine locks. StopIt shows launchable app candidates, stores only the apps selected by the user for local blocking settings, and does not send the full installed-app list, app names, or package lists to analytics, advertising, profiling, servers, or third parties.

### Play review note / release PR evidence template

```md
QUERY_ALL_PACKAGES evidence:
- Purpose: app-selection picker for user-selected blocking targets.
- Code boundary: `InstalledAppRepository.loadSelectableApps()` owns `PackageManager.getInstalledApplications(...)`; `SelectableAppPolicy` filters launchable apps and excludes Stopit itself.
- User value: users must see installed launchable apps to choose what to block for manual/timer/routine locks.
- Data minimization: full installed-app list is never sent to analytics/server/ad SDK/third parties; analytics uses category buckets only where needed.
- Verification: `python3 -m unittest scripts.tests.test_query_all_packages_policy_contract scripts.tests.test_android_manifest_contract -v` and `./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.appselection.*'`.
```

## 릴리즈/QA 운영 체크

릴리즈 PR 또는 Play 정책 대응 PR에서 아래를 확인한다.

- [ ] `AndroidManifest.xml`의 `QUERY_ALL_PACKAGES` 주석이 app-selection picker / `InstalledAppRepository` / `SelectableAppPolicy` 목적을 유지한다.
- [ ] broad installed-app scan은 `InstalledAppRepository.kt` 밖으로 늘어나지 않았다.
- [ ] picker UI가 `PackageManager.getInstalledApplications(...)` 또는 `queryIntentActivities(...)`를 직접 호출하지 않는다.
- [ ] `SelectableAppPolicyTest`가 launch intent 없는 앱 제외, Stopit 자기 package 제외, 정렬 안정성을 계속 검증한다.
- [ ] Play Console 권한 선언 또는 review note에는 위 한국어/영어 문안 중 현재 제출 언어에 맞는 문안을 사용한다.
- [ ] analytics/docs 변경이 동반되면 raw app package/name/list 금지와 `blocked_app_category_bucket` 계약을 깨지 않는다.

## 검증 명령

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_query_all_packages_policy_contract scripts.tests.test_android_manifest_contract -v
./gradlew :app:testDevDebugUnitTest --tests 'com.uiery.keep.appselection.*'
./gradlew :app:assembleProdDebug
```

로컬 docs-lane에서 Gradle prerequisites가 없으면 Python contract test와 `git diff --check`를 먼저 실행하고, Gradle 검증은 PR CI 또는 Android prerequisites가 있는 lane에서 채운다.
