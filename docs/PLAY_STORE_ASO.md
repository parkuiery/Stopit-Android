# Play Store ASO 운영안

이 문서는 GitHub issue #15 `Play Store ASO 개선으로 신규 유입 회복`의 실행 초안이다.

## 지표/근거

- 분석 기간: 최근 30일 vs 직전 30일
- active users: 456 vs 624
- new users: 202 vs 383 (`-47.3%`)
- sessions: 4,681 vs 6,149
- engagement rate: 61.4% vs 62.9%
- 현재 신규 유입은 `Organic Search` 의존도가 높다.

해석:

- 사용성/참여율 급락보다 **신규 유입 병목**이 더 크다.
- 따라서 앱 기능 자체 개선과 별개로 **Play Store listing 메시지, 스크린샷, 리뷰 신뢰 신호**를 같이 손봐야 한다.

## 메시지 원칙

1. 앱 이름은 사용자 노출 기준으로 `StopIt / 스탑잇`을 쓴다.
2. 핵심 가치는 `유혹 앱 차단`, `타이머 잠금`, `루틴 잠금`, `긴급 해제`, `잠금 기록` 순으로 보여준다.
3. Android 앱의 실제 강점을 기준으로 쓴다. iOS/초기 카피, 막연한 자기계발 문구는 줄인다.
4. 과장보다 신뢰를 택한다. 접근성 권한/긴급 해제/루틴 보호 같은 현실 기능을 명확히 쓴다.
5. KR listing을 기준 문서로 두고, EN은 그 의미를 유지하는 범위에서 간결하게 번역한다.

## 타겟 사용 상황

### 핵심 대상
- 공부/업무 중 자꾸 특정 앱을 열어 집중이 깨지는 사용자
- 특정 시간대에 앱 차단 루틴이 필요한 사용자
- 강한 차단은 원하지만 완전 비가역 잠금은 부담스러운 사용자

### JTBD
- "공부하거나 일할 때 유혹 앱을 바로 막고 싶다."
- "원하는 시간 동안만 확실하게 잠그고 싶다."
- "정말 필요할 때만 제한적으로 풀 수 있어야 안심된다."

## 제목/설명 초안

### 앱 제목 후보

1. **스탑잇 - 집중 앱 차단, 루틴 잠금**
2. **스탑잇 - 앱 차단 타이머 & 집중 루틴**
3. **스탑잇 - 유혹 앱 잠금, 공부 집중 도우미**

추천 기본안:

- KR: **스탑잇 - 집중 앱 차단, 루틴 잠금**
- EN: **StopIt - App Blocker for Focus Routines**

### 짧은 설명 후보

1. 유혹 앱을 선택하고, 타이머·루틴으로 잠그고, 필요할 때만 긴급 해제하세요.
2. 공부와 업무에 방해되는 앱을 차단하고 집중 루틴을 자동으로 지키세요.
3. 선택한 앱을 바로 차단하고, 루틴과 기록으로 집중 습관을 만드세요.

추천 기본안:

- KR: **유혹 앱을 선택하고, 타이머·루틴으로 잠그고, 필요할 때만 긴급 해제하세요.**
- EN: **Block distracting apps with timers and routines, with emergency unlock only when needed.**

### 긴 설명 초안

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

스크린샷은 "설정 → 실제 차단 → 루틴 → 긴급 해제 → 기록" 흐름으로 보여준다.

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
- 긴급 해제는 기능 소개는 하되, 차단 강도를 약하게 보이게 만들지 않는다.
- 기록 화면은 단순 통계보다 "집중을 이어온 증거"로 표현한다.

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

## 브랜딩 점검 메모

현재 repo에는 `StopIt`과 `Keep` 흔적이 혼재해 있다.

- 사용자 노출 앱명은 `StopIt / 스탑잇`
- 일부 영문 문자열에 `Keep` 표현이 남아 있음

ASO 반영 전 체크:

- Play listing copy는 전부 `StopIt` 기준으로 통일
- 인앱 사용자 노출 문자열의 `Keep` 잔재는 별도 UI 카피 정리 이슈로 분리 검토

## 실행 체크리스트

- [ ] KR 제목/짧은 설명/긴 설명 최종안 확정
- [ ] EN 제목/짧은 설명/긴 설명 최종안 확정
- [ ] 6장 스크린샷 실제 캡처 또는 디자인 시안 준비
- [ ] Play Console listing 변경일 기록
- [ ] 변경 직전/14일 후/30일 후 `Organic Search` 신규 사용자 비교
- [ ] rating count / 평균 평점 / install 전환율 함께 확인

## 측정 계획

### 변경 직전 기록
- listing 변경일
- 현재 제목/짧은 설명/긴 설명
- 현재 스크린샷 버전
- 최근 30일 `new users`
- 최근 30일 `Organic Search` 신규 사용자
- 최근 30일 rating count / 평균 평점

### 14일 후 확인
- `Organic Search` 신규 사용자 추이
- store listing 방문 대비 설치 전환 변화
- 리뷰 수 증가 여부

### 30일 후 확인
- 신규 사용자 회복 여부
- active users / sessions 동반 회복 여부
- ASO 카피 유지 vs 2차 수정 결정

## 운영 메모

- 실제 Play Console 반영은 수동 작업이지만, 이 문서를 repo 기준 시안으로 유지한다.
- 스크린샷 최종본이 만들어지면 저장 위치와 변경일을 이 문서에 추가한다.
- 앱 가치가 바뀌면 issue #15를 닫기 전에 이 문서도 같이 갱신한다.
