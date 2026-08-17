"""GA4 커스텀 측정기준 등록은 되돌릴 수 없다. 그래서 쓰기 전에 막는 쪽을 고정한다.

측정기준은 삭제가 아니라 아카이브만 되고, EVENT 범위는 속성당 50개가 상한이다. 잘못 쓴 것을
치울 방법이 없으므로 비용이 전부 사전 확인에 몰린다.

특히 속성 오지정이 현실적인 위험이다. 같은 계정 아래 `stopit-be785`(com.uiery.keep,
프로덕션), `stopit-dev`(com.uiery.keep.dev), 그리고 오래된 `keep-dev-40446`이 함께 있다.
이름만 보고 고르거나 문서에서 복사한 숫자를 믿으면 dev 속성에 프로덕션 계측을 등록하게 된다.
이름은 그 사실을 말해주지 않는다. Android 데이터 스트림 패키지가 말해준다.
"""

import pathlib
import sys
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from scripts.ga4_custom_dimension_registrar import (  # noqa: E402
    DIMENSION_SETS,
    EVENT_SCOPE_LIMIT,
    PRODUCTION_PACKAGE,
    PRODUCTION_PROPERTY,
    RegistrarError,
    assert_within_event_limit,
    plan,
)


def dimension(parameter_name: str, scope: str = "EVENT") -> dict:
    return {"parameterName": parameter_name, "scope": scope}


class PlanTest(unittest.TestCase):
    def test_already_registered_dimensions_are_not_recreated(self):
        # 중복 생성은 되돌릴 수 없는 낭비다. 상한이 50이라 자리도 함께 잃는다.
        specs = DIMENSION_SETS["website-blocking"]
        existing = [dimension(spec.parameter_name) for spec in specs]

        missing, already = plan(specs, existing)

        self.assertEqual([], missing)
        self.assertEqual(len(specs), len(already))

    def test_only_missing_dimensions_are_planned(self):
        specs = DIMENSION_SETS["website-blocking"]
        existing = [dimension("entry_surface"), dimension("outcome")]

        missing, already = plan(specs, existing)

        self.assertEqual(["entry_surface"], [spec.parameter_name for spec in already])
        self.assertNotIn("entry_surface", [spec.parameter_name for spec in missing])
        self.assertIn("website_blocking_status", [spec.parameter_name for spec in missing])


class EventLimitTest(unittest.TestCase):
    def test_batch_that_would_exceed_the_cap_is_refused_before_writing(self):
        # 한도를 배치 도중에 만나면 일부만 등록된 상태로 남고, 그 일부도 되돌릴 수 없다.
        specs = DIMENSION_SETS["website-blocking"]
        existing = [dimension(f"filler_{i}") for i in range(EVENT_SCOPE_LIMIT - 1)]

        with self.assertRaises(RegistrarError) as caught:
            assert_within_event_limit(list(specs), existing)

        self.assertIn(str(EVENT_SCOPE_LIMIT), str(caught.exception))

    def test_user_scoped_dimensions_do_not_consume_the_event_cap(self):
        existing = [dimension(f"u_{i}", scope="USER") for i in range(60)]

        assert_within_event_limit(list(DIMENSION_SETS["website-blocking"]), existing)


class ProductionTargetTest(unittest.TestCase):
    def test_default_target_is_the_production_property_and_package(self):
        # 기본값이 dev 를 가리키면 아무도 눈치채지 못한 채 잘못된 속성에 쌓인다.
        self.assertEqual("502544175", PRODUCTION_PROPERTY)
        self.assertEqual("com.uiery.keep", PRODUCTION_PACKAGE)
        self.assertFalse(PRODUCTION_PACKAGE.endswith(".dev"))

    def test_website_blocking_set_carries_no_domain_shaped_axis(self):
        # docs/WEBSITE_BLOCKING_VPN_SPIKE.md 의 Privacy Rules. 차단 도메인은 개수조차
        # 싣지 않는다. 측정기준으로 만들면 GA4 쪽에 영구적인 수집 자리가 생긴다.
        # `site` 를 넣으면 `website_blocking_status` 가 걸린다. 이름에 web**site** 가
        # 들어가는 것은 정상이므로, 실제로 도메인 값을 실을 축만 본다.
        for spec in DIMENSION_SETS["website-blocking"]:
            for forbidden in ("domain", "hostname", "url"):
                self.assertNotIn(
                    forbidden,
                    spec.parameter_name,
                    f"{spec.parameter_name} 이 도메인 성격 축으로 보인다",
                )

    def test_display_name_matches_parameter_name(self):
        # 표시 이름과 파라미터가 어긋나면 GA4 탐색·런북 원장·이벤트 딕셔너리가 서로 다른
        # 문자열을 가리키게 되고, 어느 쪽이 진짜인지 확인할 방법이 사라진다.
        for spec in DIMENSION_SETS["website-blocking"]:
            self.assertEqual(spec.parameter_name, spec.display_name)


if __name__ == "__main__":
    unittest.main()
