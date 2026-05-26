# Play Store ASO 실행 런북

이 문서는 GitHub issue #65 `[성장] Play Console ASO 시안 반영 및 14·30일 유입 회복 검증`의 저장소 기준 실행 문서다.

이전 closed issue #15는 **카피/스크린샷/측정 초안 정리** 단계였고, 현재 문서는 그 초안을 실제 Play Console 반영과 14일·30일 검증까지 이어가기 위한 운영 기준을 담는다.

## 현재 상태 요약

- 상태: 대표님 수동 작업으로 실제 Play Console ASO copy/스크린샷 반영 완료
- 저장소 기준 남은 작업:
  - Play Console 반영 일시/범위 기록
  - 반영 직전/직후 baseline 보강
  - 14일/30일 성과 검증 루프 기록
- 완료된 외부 수동 작업:
  - Play Console listing copy 반영
  - Play Console 스크린샷 업로드
- 후속 수동 기록 작업:
  - listing 전환율 / 평점 / 리뷰 수 기록
  - 반영 당시 실제 노출값과 저장소 문서 일치 여부 재확인

> 운영 메모: 이 이슈의 실제 Play Console 반영은 저장소 CI가 아니라 대표님 수동 배포로 처리될 수 있다. 따라서 repo/CI 자동화 흔적이 없다는 이유만으로 미반영으로 판단하지 않는다.

## 지표/근거

### 실행 트리거

- 분석 기간: 최근 30일 (`30daysAgo..yesterday`)
- 비교 기간: 직전 30일 (`60daysAgo..31daysAgo`)
- 핵심 수치:
  - `newUsers`: 203 / 이전 377 = `-46.2%`
  - `activeUsers`: 457 / 이전 612 = `-25.3%`
  - `sessions`: 4,636 / 이전 6,214 = `-25.4%`
  - `Organic Search` 신규 사용자: 178 / 전체 신규 사용자 203 = `87.7%`
  - `Direct` 신규 사용자: 25 / 전체 신규 사용자 203 = `12.3%`
- 확인 소스:
  - 2026-05-24 GA4 스냅샷
  - GitHub issue #65
  - 과거 docs 준비 이슈 #15 / PR #22

### 해석

- 사용성/참여율 급락보다 **신규 유입 병목**이 더 크다.
- 신규 유입의 대부분이 `Organic Search`에 쏠려 있어, 회복을 위해서는 앱 내부 퍼널만이 아니라 **Play Store listing copy / screenshot / social proof**를 같이 손봐야 한다.
- 따라서 이 이슈의 저장소 산출물은 “문구 초안”에 머무르면 안 되고, **실제 반영 체크리스트 + baseline + 검증 로그**까지 포함해야 한다.

## 메시지 원칙

1. 사용자 노출 앱명은 `StopIt / 스탑잇` 기준으로 통일한다.
2. 핵심 가치는 `유혹 앱 차단`, `타이머 잠금`, `루틴 잠금`, `긴급 해제`, `잠금 기록` 순으로 보여준다.
3. Android 앱의 실제 강점 기준으로 쓴다. iOS/막연한 자기계발 문구는 줄인다.
4. 과장보다 신뢰를 택한다. 접근성 권한, 루틴 보호, 긴급 해제의 제한성을 명확히 쓴다.
5. KR listing을 기준 문서로 두고, EN은 의미를 유지하는 범위에서 간결하게 번역한다.

## 타겟 사용 상황

### 핵심 대상
- 공부/업무 중 자꾸 특정 앱을 열어 집중이 깨지는 사용자
- 특정 시간대에 앱 차단 루틴이 필요한 사용자
- 강한 차단은 원하지만 완전 비가역 잠금은 부담스러운 사용자

### JTBD
- "공부하거나 일할 때 유혹 앱을 바로 막고 싶다."
- "원하는 시간 동안만 확실하게 잠그고 싶다."
- "정말 필요할 때만 제한적으로 풀 수 있어야 안심된다."

## 최종 반영 권장안

### 앱 제목

- KR: **스탑잇 - 집중 앱 차단, 루틴 잠금**
- EN: **StopIt - App Blocker for Focus**

대체 후보:
1. 스탑잇 - 앱 차단 타이머 & 집중 루틴
2. 스탑잇 - 유혹 앱 잠금, 공부 집중 도우미

### 짧은 설명

- KR: **유혹 앱을 선택하고, 타이머·루틴으로 잠그고, 필요할 때만 긴급 해제하세요.**
- EN: **Block distracting apps with timers, routines, and limited emergency unlock.**

### 긴 설명

#### KR long description

스탑잇은 공부, 업무, 수면 전처럼 집중이 필요한 시간에 유혹 앱 사용을 바로 막아주는 Android 앱입니다.

원하는 앱을 선택한 뒤 직접 켜서 잠글 수도 있고, 타이머나 반복 루틴으로 자동 잠금을 설정할 수도 있습니다. 실제로 앱을 열었을 때 차단 화면이 동작해 "설정만 해두고 안 막히는" 앱이 아니라, 집중이 깨지는 순간을 바로 끊어주는 데 초점을 맞췄습니다.

### 이런 분에게 맞아요
- 공부를 시작하면 SNS·영상 앱부터 열게 되는 분
- 업무 시간에 메신저·커뮤니티·쇼핑 앱 사용을 줄이고 싶은 분
- 수면 전 특정 앱 사용을 제한하고 싶은 분
- 완전 삭제 대신, 필요한 시간에만 확실하게 차단하고 싶은 분

### 핵심 기능
- **유혹 앱 선택 차단**: 방해되는 앱만 골라서 잠글 수 있어요.
- **타이머 잠금**: 지금부터 몇 분/몇 시간 동안 앱 사용을 막을 수 있어요.
- **루틴 잠금**: 요일과 시간대를 정해 자동으로 차단할 수 있어요.
- **긴급 해제**: 정말 필요할 때만 제한된 횟수와 시간으로 임시 해제가 가능해요.
- **잠금 기록 확인**: 얼마나 자주, 얼마나 오래 버텼는지 기록으로 볼 수 있어요.
- **루틴 보호/삭제 방지**: 잠금 습관이 쉽게 무너지지 않도록 보호 장치를 둘 수 있어요.

### 스탑잇이 다른 점
- 단순 알림 앱이 아니라 실제 차단 동작에 집중합니다.
- "무조건 막기"보다 긴급 해제 같은 안전장치를 함께 제공합니다.
- 루틴, 기록, 차단 성공 경험을 통해 집중 습관을 쌓도록 설계했습니다.

### 이런 흐름으로 시작하세요
1. 자주 열어버리는 앱을 선택합니다.
2. 타이머 잠금 또는 루틴 잠금을 설정합니다.
3. 차단이 실제로 동작하는지 확인합니다.
4. 잠금 기록을 보며 집중 시간을 늘립니다.

스탑잇은 의지력에만 기대지 않고, 유혹이 생기는 순간 앱 사용을 끊을 수 있도록 돕습니다.

#### EN long description

StopIt helps you block distracting apps when you need to focus.

Select the apps that break your concentration, then lock them instantly, by timer, or on recurring routines. Instead of only reminding you, StopIt is built to block access when you actually open the app.

### Best for
- students who open social apps while studying
- workers who want fewer distractions during deep work
- people who want a routine-based app blocker at night or during work hours

### Key features
- block selected distracting apps
- start a focus timer lock instantly
- automate blocking with recurring routines
- allow emergency unlock only when truly needed
- review lock history and focus progress
- protect routines and prevent easy backtracking

StopIt is designed for practical focus: real blocking, routine support, and a safer fallback than all-or-nothing locking.

## 스크린샷 구성안

스크린샷은 `설정 → 즉시 잠금 → 루틴 → 실제 차단 → 긴급 해제 → 기록` 흐름으로 보여준다.

| 순서 | 화면 | 핵심 메시지 | 캡션 초안 |
| --- | --- | --- | --- |
| 1 | 앱 선택 | 유혹 앱만 골라서 차단 | 자꾸 열게 되는 앱만 골라서 차단하세요 |
| 2 | 홈 타이머 잠금 | 바로 시작 가능한 집중 잠금 | 지금 바로 타이머로 집중 시간을 시작하세요 |
| 3 | 루틴 화면 | 반복되는 집중 시간을 자동화 | 요일과 시간대로 앱 차단 루틴을 만드세요 |
| 4 | 차단 화면 | 실제 사용 순간에 차단 동작 | 앱을 열면 바로 차단되어 흐름이 끊기지 않아요 |
| 5 | 긴급 해제 | 완전 강제 대신 안전한 예외 처리 | 정말 필요할 때만 제한적으로 긴급 해제 |
| 6 | 잠금 기록 | 집중 성과를 확인 | 잠금 기록으로 내가 버틴 시간을 확인하세요 |

### 스크린샷 제작 메모
- 1~2장은 "왜 필요한지"보다 "무엇을 바로 할 수 있는지"를 보여준다.
- 긴 텍스트보다 1문장 가치 제안을 사용한다.
- 긴급 해제는 소개하되, 차단 강도를 약하게 보이게 만들지 않는다.
- 기록 화면은 단순 통계보다 "집중을 이어온 증거"로 표현한다.

### 스크린샷 자산 체크리스트

| 슬롯 | 필요 화면 | 캡션 확정 | 실제 캡처 | 최종 편집본 | Play Console 반영 |
| --- | --- | --- | --- | --- | --- |
| 1 | 앱 선택 | [ ] | [ ] | [ ] | [ ] |
| 2 | 홈 타이머 잠금 | [ ] | [ ] | [ ] | [ ] |
| 3 | 루틴 화면 | [ ] | [ ] | [ ] | [ ] |
| 4 | 차단 화면 | [ ] | [ ] | [ ] | [ ] |
| 5 | 긴급 해제 | [ ] | [ ] | [ ] | [ ] |
| 6 | 잠금 기록 | [ ] | [ ] | [ ] | [ ] |

## 반영 전 baseline 기록

원래는 실제 Play Console 반영 직전에 아래 표를 채우는 절차였다. 현재는 대표님 수동 배포가 먼저 완료된 상태이므로, 확인 가능한 항목은 사후라도 최대한 복원해 기록한다. `listing 전환율`, `평점`, `리뷰 수`, `현재 listing copy`는 저장소에서 자동 조회할 수 없으므로 수동 기록이 필요하다.

| 항목 | 값 | 기록 방법 |
| --- | --- | --- |
| baseline 기록일 | `TODO` | 수동 입력 |
| 현재 KR 제목 | `TODO` | Play Console 수동 확인 |
| 현재 KR 짧은 설명 | `TODO` | Play Console 수동 확인 |
| 현재 KR 긴 설명 버전명 | `TODO` | Play Console 수동 확인 |
| 현재 EN 제목 | `TODO` | Play Console 수동 확인 |
| 현재 EN 짧은 설명 | `TODO` | Play Console 수동 확인 |
| 현재 EN 긴 설명 버전명 | `TODO` | Play Console 수동 확인 |
| 현재 스크린샷 버전/메모 | `TODO` | Play Console 수동 확인 |
| 최근 30일 `newUsers` | `203` | issue #65 기준 |
| 최근 30일 `Organic Search` 신규 사용자 | `178` | issue #65 기준 |
| 최근 30일 `activeUsers` | `457` | issue #65 기준 |
| 최근 30일 `sessions` | `4,636` | issue #65 기준 |
| listing 전환율 | `TODO` | Play Console 수동 확인 |
| rating count | `TODO` | Play Console 수동 확인 |
| 평균 평점 | `TODO` | Play Console 수동 확인 |
| 최근 리뷰 톤 메모 | `TODO` | 최근 리뷰 5~10개 수동 요약 |

## Play Console 반영 절차

### 1. 반영 직전 준비

- [ ] baseline 표를 채운다.
- [ ] KR/EN 제목·짧은 설명·긴 설명 최종안을 이 문서 기준으로 확정한다.
- [ ] 스크린샷 6장 최종본 파일명을 정리한다.
- [ ] 반영 담당자와 반영 시간을 기록한다.

### 2. 실제 반영

- [ ] KR listing copy 반영
- [ ] EN listing copy 반영
- [ ] 스크린샷 6장 업로드/정렬 반영
- [ ] 저장 후 실제 노출값 재확인

### 3. 반영 직후 기록

- [x] 반영 사실 기록
- [x] 반영자 기록
- [x] 적용 범위 기록 (copy only / screenshots only / both)
- [ ] 정확한 반영 시각 / 당시 Play Console 노출값 보강
- [ ] 반영 후 저장소 문서와 Play Console 값이 일치하는지 재확인

## 실행 로그

| 단계 | 상태 | 일시 | 담당 | 메모 |
| --- | --- | --- | --- | --- |
| baseline 기록 | 보강 필요 | `TODO` | `TODO` | 반영 전 수치/노출값을 사후 복원해야 함 |
| KR listing 반영 | 완료 | `2026-05-27 01:18 KST 이전` | 대표님 | 대표님 수동 배포 완료 사실 확인 |
| EN listing 반영 | 완료 | `2026-05-27 01:18 KST 이전` | 대표님 | 대표님 수동 배포 완료 사실 확인 |
| 스크린샷 반영 | 완료 | `2026-05-27 01:18 KST 이전` | 대표님 | Play Console 실제 반영 완료 |
| 14일 점검 | 예정 | `TODO` | `TODO` | |
| 30일 점검 | 예정 | `TODO` | `TODO` | |

## 14일 / 30일 검증 포맷

### 14일 후 확인
- `Organic Search` 신규 사용자 변화
- 전체 `newUsers` 변화
- listing 전환율 변화
- 리뷰 수 증가 여부
- 평점 변화 여부
- `activeUsers` 동반 회복 조짐

### 30일 후 확인
- 신규 사용자 회복 여부
- `activeUsers` / `sessions` 동반 회복 여부
- ASO 카피 유지 vs 2차 수정 결정
- 스크린샷 교체 필요 여부

### 기록 표

| 시점 | newUsers | Organic Search 신규 사용자 | activeUsers | sessions | listing 전환율 | rating count | 평균 평점 | 판단 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| baseline | 203 | 178 | 457 | 4,636 | `TODO` | `TODO` | `TODO` | 반영 전 |
| +14일 | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` |
| +30일 | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` | `TODO` |

## 브랜딩 점검 메모

현재 repo에는 `StopIt`과 `Keep` 흔적이 혼재해 있다.

- 사용자 노출 앱명은 `StopIt / 스탑잇`
- 일부 영문 문자열에 `Keep` 표현이 남아 있음

ASO 반영 전 체크:

- Play listing copy는 전부 `StopIt` 기준으로 통일
- 인앱 사용자 노출 문자열의 `Keep` 잔재는 별도 UI 카피 정리 이슈로 분리 검토

## 키워드/카피 가드

### 유지할 표현
- 앱 차단
- 집중
- 타이머 잠금
- 루틴 잠금
- 긴급 해제
- 잠금 기록

### 피할 표현
- 막연한 동기부여 문구만 강조하는 표현
- iPhone/iOS 중심 문구
- 실제 기능보다 과장된 금욕/중독 치료 표현
- 광고성 과장 문구 (`최고`, `완벽`, `100%`) 남발

## 저장소 기준 완료 범위

이 문서와 연결된 저장소 작업이 완료되었다고 볼 수 있는 조건:

- [x] KR/EN listing copy 권장안이 문서화되어 있다.
- [x] 스크린샷 6장 구성과 캡션 방향이 문서화되어 있다.
- [x] baseline / 반영 / 14일 / 30일 기록 표가 준비되어 있다.
- [x] #15 준비 단계와 #65 실행 단계의 차이가 문서에 명시되어 있다.

이 이슈 자체(#65)가 닫히려면 추가로 필요한 조건:

- [ ] Play Console에 실제 copy/스크린샷이 반영된다.
- [ ] 반영 일시와 범위가 실행 로그에 기록된다.
- [ ] 14일 또는 30일 후 전후 비교 결과가 남는다.

## 운영 메모

- 실제 Play Console 반영은 수동 작업이지만, 저장소 기준 source of truth는 이 문서로 유지한다.
- 스크린샷 최종본이 정리되면 파일 저장 위치나 디자인 원본 경로를 이 문서에 추가한다.
- ASO 실험 2차 수정이 생기면 baseline과 변경 이력을 같은 문서에 누적한다.
- 저장소 자동화는 Play Console 값을 직접 읽지 못하므로, 외부 반영/측정 값은 수동 기록을 전제로 한다.
