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

#729에서 시작한 `home_status_*_description` guard는 #764 이후 Home title/primary CTA와 Goal Lock detail status까지 포함한다. #890 이후에는 Goal Lock Home card/end action, Parent Mode active controls, Emergency Unlock duration helper, Routine Template Share repeat labels까지 포함한다. 홈 첫 진입·앱 선택 없음·보호 활성 상태, 목표 잠금 카드/상세 종료, 보호자 제어, 긴급해제 helper, 공유 payload 반복 라벨은 사용자가 잠금 신뢰도를 바로 판단하는 high-traffic surface이므로 지원 locale에서 default English 원문을 그대로 복사해 두지 않는다.

#778의 루틴 템플릿 공유 payload는 화면 안 copy가 아니라 앱 밖으로 나가는 성장 루프 공유문이므로 같은 품질 기준을 적용한다. `routine_template_share_chooser_title`만 resource-backed인 상태를 payload localization 완료로 보지 않고, payload title/body/category/repeat/time-window/duration label까지 resource-backed template/provider로 검증한다. #890에서는 `routine_template_share_repeat_weekday`, `routine_template_share_repeat_weekend`, `routine_template_share_repeat_daily`가 신규 fallback guard 대상이다.

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
- `home_goal_lock_card_title_pending`
- `home_goal_lock_card_title_active`
- `home_goal_lock_card_title_completed`
- `home_goal_lock_card_title_ended_early`
- `home_goal_lock_card_summary_pending`
- `home_goal_lock_card_summary_active`
- `home_goal_lock_card_summary_completed`
- `home_goal_lock_card_summary_ended_early`
- `home_goal_lock_card_lock_mode_all_day`
- `home_goal_lock_card_lock_mode_scheduled`
- `goal_lock_detail_end_confirmation`
- `goal_lock_detail_end_cancel`
- `goal_lock_detail_end_confirm`
- `goal_lock_detail_end_cta`
- `parent_mode_active_title`
- `parent_mode_active_accessibility_summary`
- `parent_mode_expired_title`
- `parent_mode_ended_title`
- `parent_mode_active_summary`
- `parent_mode_active_pin_notice`
- `parent_mode_active_extend_ten_minutes`
- `parent_mode_active_end_now`
- `emergency_unlock_duration_helper`
- `routine_template_share_repeat_weekday`
- `routine_template_share_repeat_weekend`
- `routine_template_share_repeat_daily`

허용하지 않는 상태:

- non-default `values-*`에 default English 문장이 그대로 남아 있음
- Home title/CTA, Goal Lock status/card/end action, Parent Mode active control, Emergency Unlock helper, Routine Template Share repeat label이 영어 fallback으로 남아 있음
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
- Goal Lock 상태/카드/종료 액션: “목표 잠금이 완료됨 / 종료됨 / 진행 중임”, “목표 잠금 시작 예정/진행/완료/조기 종료”, “하루 종일/특정 시간 잠금”, “목표 잠금을 끝내면 오늘부터 선택한 앱이 다시 열릴 수 있음”을 구분한다. 이 문구는 신뢰 표면이므로 영어 fallback을 허용하지 않는다.
- Parent Mode active: “보호자 PIN으로 세션을 연장하거나 종료할 수 있음”, “10분 연장”, “보호자 PIN으로 종료”, 그리고 TalkBack/accessibility summary의 세션 제목·시간·허용 앱 수 의미를 보존한다.
- Emergency Unlock helper: “필요 없으면 카운트다운이 끝나기 전에 취소할 수 있음”을 보존한다.
- Routine Template Share repeat label: `weekday` / `weekend` / `daily` 반복 범위를 공유 payload에서 구분한다.

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
