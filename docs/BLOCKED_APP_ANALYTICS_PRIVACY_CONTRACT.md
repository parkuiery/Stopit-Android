# 차단 앱 analytics privacy 계약 (#611)

이 문서는 `app_block_intercepted`, `first_core_action_completed`, `core_action_completed` 계열 지표에서 차단 앱 식별자를 어떻게 다룰지 정하는 제품/개인정보 계약이다.

## 결론

`blocked_app_package` 원문 package name은 GA4/Firebase Analytics payload와 GA4 Admin custom dimension 등록 대상에서 **퇴역(deprecated)** 시킨다.

대체 축은 `blocked_app_category_bucket`을 기본값으로 한다. 제품 대시보드와 #13 GA4 queryability follow-through는 앱별 원문 분해가 아니라 category bucket, block source, selected-app-count, repeat/retention cohort를 조합해 해석한다.

## 왜 바꾸는가

- 사용자가 차단한 앱 package name은 습관, 관심사, 건강/금융/커뮤니케이션 앱 사용 패턴을 드러낼 수 있는 식별성 높은 데이터다.
- Stopit의 최근 성장/공유/개인화 계약은 모두 privacy-safe enum/bucket만 analytics에 남기는 방향으로 정리됐다.
- `blocked_app_package`를 GA4 custom dimension으로 더 잘 등록·조회하는 방향은 #13의 “계측 품질 개선”과 섞이면 개인정보 리스크를 고착시킨다.
- 제품 의사결정에는 대부분 “어떤 앱 원문인가”보다 “어떤 앱 카테고리/차단 맥락/반복 패턴인가”가 더 안전하고 충분하다.

## 정책

### 금지

아래 값은 GA4/Firebase Analytics payload, custom dimension registration ledger, PR/Issue evidence에 원문으로 남기지 않는다.

- Android package name (`com.example.app` 형태)
- 앱 label/name
- raw selected app list 또는 `lockApplications`
- raw LockHistory row/session list
- raw timestamp, raw retry count
- raw routine name, goal name

### 허용

외부 analytics에는 아래처럼 비식별 enum/bucket만 허용한다.

- `blocked_app_category_bucket`
  - `social`
  - `video`
  - `game`
  - `communication`
  - `shopping`
  - `browser`
  - `productivity`
  - `unknown`
- `block_source`: `manual_keep`, `timed_lock`, `routine`, `goal_lock`
- `blocking_mode`: 기존 첫 가치/반복 핵심 행동 모드 enum
- `selected_app_count` 또는 `selected_app_count_bucket`
- `repeat_count_bucket`, `time_bucket`, `day_type`처럼 반복 차단 제안에서 이미 쓰는 bucket

### local-only 허용

앱 내부 화면과 로컬 로직은 사용자가 직접 선택한 앱을 보여줘야 하므로 package/name을 로컬에서 사용할 수 있다.

- AccessibilityService 차단 판정
- Block/Lock 화면 표시
- LockHistory Top Apps 표시
- 반복 차단 루틴 추천 후보 산출
- backup/restore 또는 Room/DataStore 내부 상태

단, 이 값은 analytics payload, 공유 payload, 공개 QA evidence에 원문으로 남기지 않는다.

## 이벤트별 전환 계약

| 이벤트 | 기존/legacy 축 | 새 privacy-safe 축 | 전환 상태 |
| --- | --- | --- | --- |
| `app_block_intercepted` | `blocked_app_package` | `blocked_app_category_bucket`, `block_source`, `routine_id?`, `goal_lock_id?` | PR #617(`f8eb0ebe`) 이후 raw package payload 제거 및 category bucket 전환 완료 |
| `first_core_action_completed` | `blocked_app_package` | `blocked_app_category_bucket`, `blocking_mode`, `routine_id?`, `goal_lock_id?` | PR #617(`f8eb0ebe`) 이후 raw package payload 제거 및 category bucket 전환 완료 |
| `core_action_completed` | `blocked_app_package` | `blocked_app_category_bucket`, `blocking_mode`, `routine_id?`, `goal_lock_id?` | PR #617(`f8eb0ebe`) 이후 raw package payload 제거 및 category bucket 전환 완료 |

`routine_id`와 `goal_lock_id`는 이름/앱 원문이 아니라 내부 id다. 목표 이름, 루틴 이름, 앱 label/package를 이 id 대신 보내지 않는다.

## repo-internal 구현 상태

PR #615는 이 문서/GA4/Admin/metrics 계약을 먼저 고정했고, PR #617(`f8eb0ebef5fe31ba81bcb9c913ff13966cdb8f34`)은 Android analytics payload를 같은 계약에 맞췄다.

완료된 repo-internal 범위:

1. `KeepAnalyticsParam.BLOCKED_APP_PACKAGE`는 legacy/deprecated 축으로만 남고 신규 payload/registration 대상이 아니다.
2. `KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET` 또는 동등한 category bucket 축이 차단 이벤트 payload의 기본 분석 축이다.
3. `FirebaseKeepAnalytics.trackAppBlockIntercepted(...)`, `trackFirstCoreActionCompleted(...)`, `trackCoreActionCompleted(...)`는 raw package를 GA4/Firebase payload에 넣지 않는다.
4. 차단 앱 package는 앱 내부에서만 category bucket 판정에 사용한다.
   - 매핑 확신이 없으면 `unknown`으로 보낸다.
   - raw package allowlist를 GA4로 보내는 방식은 기본값이 아니다.
5. analytics payload regression은 아래를 잡아야 한다.
   - `blocked_app_package` 미전송
   - `blocked_app_category_bucket` 전송
   - 앱 이름/package/raw history/raw timestamp 미전송
6. GA4 Admin 등록은 `blocked_app_category_bucket`만 대상으로 한다. `blocked_app_package`는 새로 등록하지 않는다.

남은 작업은 새 Android 구현이 없어서가 아니라 release/tag/Play deploy, GA4 Admin metadata, D+14/D+30 readback 경계다.

## #13 / #14 경계

- #13은 남는 privacy-safe event/parameter의 GA4 queryability follow-through다.
- #611은 raw package 전송 정책 변경이다.
- 따라서 #13에서 activation custom dimension 등록을 진행하더라도 `blocked_app_package`를 Required registration 대상으로 되살리지 않는다.
- #14 activation funnel은 상위 이벤트 users 비율, `block_source`, `blocked_app_category_bucket`, `selected_app_count`로 해석하고, 특정 앱 package별 병목을 GA4에서 직접 쪼개지 않는다.

## release / readback 경계

문서 계약과 Android payload 전환은 #615/#617로 repo 내부에 반영됐다. 그래도 #611은 아직 닫지 않는다. 다음 외부/후속 경계가 남는다.

- PR #617 포함 commit이 `origin/main` / SemVer tag / Play deploy에 실제 포함됐는지 확인
- GA4 Admin에서 `customEvent:blocked_app_category_bucket` 등록 및 metadata 확인
- 배포 후 14일: `app_block_intercepted` / `first_core_action_completed` / `core_action_completed`가 category bucket으로 조회되는지 확인
- 배포 후 30일: category bucket이 제품 판단에 충분한지, `unknown` 비율이 과도한지 점검
- `blocked_app_package`는 신규 등록하지 않고, 기존 live 흔적이 있더라도 legacy baseline으로만 해석

## 보고 템플릿

```md
## #611 readback
- 분석 기간:
- 포함 버전 / SemVer tag / Play track:
- code-lane raw package 제거 PR:
- GA4 metadata:
  - customEvent:blocked_app_category_bucket: 확인/미확인
  - customEvent:blocked_app_package: 신규 등록하지 않음 / legacy only
- 핵심 수치:
  - app_block_intercepted users/events by blocked_app_category_bucket:
  - first_core_action_completed users/events by blocked_app_category_bucket:
  - unknown bucket 비율:
- 해석:
  - category bucket만으로 activation/runtime 판단이 충분한가:
  - 추가 로컬-only UX/QA evidence가 필요한가:
```

## 검증 명령

```bash
cd <repo-root>
python3 -m unittest scripts.tests.test_blocked_app_analytics_privacy_contract -v
python3 -m unittest scripts.tests.test_ga4_custom_dimension_registration_docs -v
rg -n 'blocked_app_package|blocked_app_category_bucket|BLOCKED_APP_PACKAGE' docs docs/ops/stopit scripts/tests
```
