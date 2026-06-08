# Accessibility Permission Copy Contract

Issue: Refs #642

이 문서는 온보딩 접근성 권한 화면의 copy가 Android Accessibility Service / Play Console disclosure 맥락과 충돌하지 않도록 고정하는 source of truth다.

## 문제

Stopit은 사용자가 직접 선택한 앱을 감지하고, 수동 잠금·타이머 잠금·루틴 잠금이 활성화된 동안 차단 화면을 표시하기 위해 Android Accessibility Service를 사용한다. 따라서 권한 요청 copy는 사용자가 어떤 Android 권한을 허용하는지 명확히 말해야 한다.

`Screen Time permission`은 Android 권한명이 아니다. iOS 기능명 또는 일반 기능명처럼 읽히기 때문에 다음 리스크가 있다.

- 사용자가 접근성 권한을 허용하는 이유를 정확히 이해하지 못한다.
- Play Console Accessibility declaration / store listing disclosure와 인앱 권한 copy가 서로 다른 용어처럼 보인다.
- 지원 locale에서 “Screen Time/스크린타임/화면 시간”이 권한명처럼 번역되어 정책·신뢰 surface가 흔들린다.

## Copy 계약

### 기본 원칙

- 권한명은 `Accessibility permission` 또는 locale별 Android 접근성/사용자 보조/특수 기능 권한에 해당하는 표현을 쓴다.
- `Screen Time permission`, `스크린타임 권한`, `화면 시간 권한`처럼 Android 권한명이 아닌 표현은 `accessibility_permission_required` / `accessibility_permission_description`에 쓰지 않는다.
- 설명 문구는 Stopit이 접근성 권한으로 하는 일을 좁게 말한다: 사용자가 선택한 distracting apps가 열릴 때 차단 화면을 표시한다.
- 광고, 프로파일링, 판매, 제3자 공유 목적이 아니라는 정책 경계는 Play Store / Accessibility declaration 문서에서 설명하며, 온보딩 화면 copy는 짧고 이해 가능해야 한다.
- “모든 앱을 막는다”처럼 과도하게 넓은 표현보다 “사용자가 선택한 앱을 막는다”는 표현을 우선한다.

### 현재 canonical English copy

```xml
<string name="accessibility_permission_required">Accessibility permission is required!</string>
<string name="accessibility_permission_description">Accessibility permission lets StopIt block the distracting apps you choose.</string>
```

### Locale handoff

지원 locale의 `accessibility_permission_required` / `accessibility_permission_description`은 위 의미를 유지해야 한다.

- `values-ko`: 접근성 권한
- `values-ja`: ユーザー補助の許可
- `values-zh`: 无障碍权限
- `values-de`: Bedienungshilfen-Berechtigung
- `values-fr`: autorisation d'accessibilité
- `values-it`: permesso di accessibilità
- `values-nl`: toegankelijkheidsmachtiging
- `values-pt`, `values-pt-rBR`: permissão de acessibilidade
- `values-ru`: разрешение специальных возможностей
- `values-es`: permiso de accesibilidad

번역 품질은 이후 native review로 더 다듬을 수 있지만, PR은 최소한 Android Accessibility permission 의미와 `Screen Time permission` 금지를 만족해야 한다.

## Play / disclosure alignment

`docs/PLAY_STORE_ASO.md`의 Play Console Accessibility declaration은 다음 경계를 유지한다.

- Stopit uses the Accessibility API to detect when user-selected distracting apps are opened.
- It immediately shows a blocking screen while a manual lock, timer lock, or routine lock is active.
- It may detect uninstall attempts during an active lock to reduce bypass.
- The access is used only for core app-blocking and lock-maintenance features, not advertising/profiling/sale/sharing.

인앱 권한 copy는 이 declaration의 짧은 사용자-facing 요약이어야 한다. Store listing이 `Accessibility API`라고 말하고 onboarding이 `Screen Time permission`이라고 말하는 상태는 허용하지 않는다.

## QA / release evidence

### 자동 guard

```bash
python3 -m unittest scripts.tests.test_accessibility_permission_copy_contract -v
python3 -m unittest scripts.tests.test_locale_string_parity -v
./gradlew --console=plain :app:lintProdRelease
```

`test_accessibility_permission_copy_contract`는 다음을 검사한다.

- 모든 shipped `values*/strings.xml`의 `accessibility_permission_required` / `accessibility_permission_description`에 `Screen Time permission` 계열 금지 표현이 남아 있지 않다.
- 이 계약 문서, `docs/QA_RUNTIME_CHECKLIST.md`, `docs/PLAY_STORE_ASO.md`, `docs/ops/stopit/product-context.md`가 서로 링크되어 있다.

### 수동 QA evidence template

- Locale(s): ko / en / es / ja / 기타 주요 locale
- 화면: 온보딩 접근성 권한 요청 화면
- 확인:
  - 권한 제목이 Android Accessibility/접근성 권한으로 읽힌다.
  - 설명이 “사용자가 선택한 앱을 Stopit이 차단하기 위한 권한”으로 이해된다.
  - `Screen Time permission` 또는 같은 의미의 권한명 오해 표현이 보이지 않는다.
  - TalkBack에서도 권한명과 설명이 같은 의미로 전달된다.
  - Play Console Accessibility declaration의 사용 목적과 충돌하지 않는다.

## Closure 기준

#642는 다음을 만족하면 repo-internal 범위에서 닫을 수 있다.

- 모든 shipped locale의 접근성 권한 copy가 Android Accessibility permission 맥락으로 정리되어 있다.
- 스페인어 리소스의 영어 fallback이 제거되어 있다.
- 이 문서와 QA checklist / Play Store ASO disclosure가 같은 경계를 가리킨다.
- static guard가 `Screen Time permission` 재유입을 막는다.
- PR 검증에 locale parity, 접근성 copy contract, release lint 또는 lint equivalent가 남아 있다.

남은 것은 release/tag/Play deploy와 실제 기기 screenshot/TalkBack spot-check일 수 있지만, issue 본문 acceptance의 repo-internal copy/documentation/guard 범위는 위 조건으로 충족한다.
