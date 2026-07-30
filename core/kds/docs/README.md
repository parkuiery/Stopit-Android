# KDS documentation

KDS 문서는 StopIt에서 SEED를 일관되게 해석하고 구현하기 위한 개발 계약입니다.
SEED의 문서를 복제하지 않고 원문을 연결하며, 이 저장소에서 필요한 Android API,
StopIt 브랜드 적용, 접근성 및 검증 기준만 기록합니다.

## Source precedence

충돌이 있을 때 다음 순서로 판단합니다.

1. StopIt 제품 동작·안전 정책과 루트 [`DESIGN.md`](../../../DESIGN.md)
2. 이 디렉터리의 KDS 가이드와 실제 `core/kds` 공개 API
3. SEED Foundations, Components, Patterns 원문
4. 내부 구현에 사용하는 Material 3 기본값

SEED와 다르게 적용한 부분은 해당 컴포넌트 문서의 `StopIt adaptation`에 반드시
기록합니다. 설명되지 않은 차이는 의도된 커스터마이징으로 간주하지 않습니다.

## Documentation sections

| 섹션 | SEED 진입점 | KDS 문서 | 용도 |
|---|---|---|---|
| Get Started | [llms.txt](https://seed-design.io/get-started/llms.txt) | 이 문서 | 문서 사용 순서 |
| Foundations | [llms.txt](https://seed-design.io/foundations/llms.txt) | [foundations.md](foundations.md) | 토큰과 공통 원칙 |
| Components | [llms.txt](https://seed-design.io/components/llms.txt) | [components](components/README.md) | 컴포넌트별 계약 |
| Patterns | [llms.txt](https://seed-design.io/patterns/llms.txt) | [patterns.md](patterns.md) | 로딩 등 조합 패턴 |
| Design Guidelines | [llms.txt](https://seed-design.io/docs/llms.txt) · [full](https://seed-design.io/docs/llms-full.txt) | 루트 `DESIGN.md` | 마이그레이션·전역 결정 |
| React Library | [llms.txt](https://seed-design.io/react/llms.txt) · [full](https://seed-design.io/react/llms-full.txt) | 참고 전용 | 동작·구성 참고 |
| Breeze Utilities | [llms.txt](https://seed-design.io/breeze/llms.txt) · [full](https://seed-design.io/breeze/llms-full.txt) | 참고 전용 | 유틸리티 참고 |
| Lynx | [llms.txt](https://seed-design.io/lynx/llms.txt) · [full](https://seed-design.io/lynx/llms-full.txt) | 참고 전용 | 타 플랫폼 참고 |
| AI Integration | [llms.txt](https://seed-design.io/ai-integration/llms.txt) · [full](https://seed-design.io/ai-integration/llms-full.txt) | 이 문서 | AI 작업 절차 |
| Updates | [llms.txt](https://seed-design.io/updates/llms.txt) | 업데이트 점검 | 신규·변경 스펙 |
| Changelog | [llms.txt](https://seed-design.io/llms/react/updates/changelog.txt) | 업데이트 점검 | breaking change |

## Development workflow

UI를 새로 만들거나 수정할 때 다음 순서를 지킵니다.

1. 루트 `DESIGN.md`에서 제품 위계와 화면 패턴을 확인합니다.
2. [foundations.md](foundations.md)에서 관련 토큰과 접근성 규칙을 확인합니다.
3. [components](components/README.md)에서 사용할 KDS 컴포넌트 문서를 읽습니다.
4. 연결된 SEED 원문에서 Anatomy, Properties, Guidelines의 최신 내용을 확인합니다.
5. 기존 KDS API로 표현할 수 없을 때 feature에서 Material을 조립하지 말고 KDS를 확장합니다.
6. 변경된 API·상태·StopIt 차이를 컴포넌트 문서와 코드에 함께 반영합니다.
7. 단위 테스트, light/dark Preview, font scaling, TalkBack semantics 및 실제 화면을 검증합니다.

새 `Keep*.kt` 파일은 대응하는 컴포넌트 문서 없이 추가할 수 없습니다.
