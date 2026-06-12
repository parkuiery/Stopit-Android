# Locale String Quality Contract

이 문서는 #729 다국어 문자열 품질 정리의 source of truth다. Stopit은 권한·차단·긴급해제처럼 신뢰가 중요한 화면이 많기 때문에, shipped locale copy는 단순 key parity뿐 아니라 사용자가 실제로 읽는 문구의 브랜드·fallback·오타 품질까지 release gate에서 확인한다.

## 브랜드 표기 기준

| 표면 | 기준 |
| --- | --- |
| 기본/영문 및 비한국어 locale | `StopIt` |
| 한국어 사용자 노출 문자열 | `스탑잇` |
| 문서/내부 설명 | 저장소·패키지·legacy 맥락에서는 `Keep` 허용. 사용자 노출 copy의 제품명으로는 사용하지 않는다. |
| 리소스 key / 코드 식별자 | 기존 `keep_*` identifier는 내부 호환성 때문에 허용한다. 표시 문자열 검증 대상은 value text다. |

## High-traffic locale guard

#729에서 시작한 `home_status_*_description` guard는 #764 이후 Home title/primary CTA와 Goal Lock detail status까지 포함한다. 홈 첫 진입·앱 선택 없음·보호 활성 상태, 그리고 목표 잠금 완료/종료/진행 상태는 사용자가 잠금 신뢰도를 바로 판단하는 high-traffic surface이므로 지원 locale에서 default English 원문을 그대로 복사해 두지 않는다.

#778의 루틴 템플릿 공유 payload는 화면 안 copy가 아니라 앱 밖으로 나가는 성장 루프 공유문이므로 같은 품질 기준을 적용한다. `routine_template_share_chooser_title`만 resource-backed인 상태를 payload localization 완료로 보지 않고, payload title/body/category/repeat/time-window/duration label까지 resource-backed template/provider로 검증한다.

현재 자동 guard 대상:

- `home_status_no_selected_apps_description`
- `home_status_first_lock_ready_description`
- `home_status_ready_description`
- `home_status_keep_active_description`
- `home_status_no_selected_apps_title`
- `home_primary_cta_select_apps`
- `home_primary_cta_start_now`
- `goal_lock_detail_status_completed`
- `goal_lock_detail_status_ended`
- `goal_lock_detail_status_active`

허용하지 않는 상태:

- non-default `values-*`에 default English 문장이 그대로 남아 있음
- Home title/CTA나 Goal Lock status가 영어 fallback으로 남아 있음
- 한국어 문자열에 확인된 오타(`함꼐`, `잠궈줘요`)가 재유입됨
- 사용자 노출 문자열에 legacy `Keep` 브랜드가 제품명처럼 노출됨

## 검증 명령

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_locale_string_quality_contract -v
python3 -m unittest scripts.tests.test_locale_string_parity scripts.tests.test_user_facing_brand_strings scripts.tests.test_korean_brand_copy_contract -v
./gradlew -q help --task :app:assembleProdDebug
```

## 수동 검수 메모

정책 민감 copy는 번역이 완벽한 마케팅 문구인지보다 의미 보존이 우선이다.

- 앱 선택 없음: “최소 1개 앱을 선택해야 보호를 시작할 수 있다.”
- 앱 선택 title/CTA: “차단할 앱을 먼저 고른다 / 차단할 앱을 선택한다.”
- 첫 잠금 준비: “지금 시작해 첫 실제 차단을 확인하거나 나중을 위한 타이머를 설정할 수 있다.”
- 준비 완료: “지금 차단 시작 / 앱 변경 / 계획된 세션 타이머 설정.”
- 보호 활성: “StopIt이 현재 시간을 보호 중이며, 세션 조정이 필요할 때만 보조 행동을 사용한다.”
- Goal Lock 상태: “목표 잠금이 완료됨 / 종료됨 / 진행 중임”을 구분한다. 상태 문구는 detail screen 신뢰 표면이므로 영어 fallback을 허용하지 않는다.

## PR / release evidence template

```md
## Locale string quality evidence
- Issue: #729 / #764
- Changed locale files:
- Automated checks:
  - `python3 -m unittest scripts.tests.test_locale_string_quality_contract -v`
  - `python3 -m unittest scripts.tests.test_locale_string_parity scripts.tests.test_user_facing_brand_strings scripts.tests.test_korean_brand_copy_contract -v`
  - `./gradlew -q help --task :app:assembleProdDebug`
- High-traffic surfaces checked:
  - `home_status_no_selected_apps_description`
  - `home_status_first_lock_ready_description`
  - `home_status_ready_description`
  - `home_status_keep_active_description`
  - `home_status_no_selected_apps_title`
  - `home_primary_cta_select_apps`
  - `home_primary_cta_start_now`
  - `goal_lock_detail_status_completed`
  - `goal_lock_detail_status_ended`
  - `goal_lock_detail_status_active`
- Sensitive copy meaning preserved: pass / fail
- Manual device/screenshot spot-check: pass / fail / not collected
- Notes:
```

`Closes #729`/`Closes #764`는 오타 수정, high-traffic locale fallback 제거, Home title/CTA와 Goal Lock status guard 문서화, static guard, resource/variant sanity check가 모두 통과했을 때 사용할 수 있다. 실제 다국어 문장 품질의 원어민 검수나 device screenshot은 release evidence로 추가하면 좋지만, 이 계약의 repo-internal 완료 조건은 위 자동 guard와 의미 보존 메모다.
