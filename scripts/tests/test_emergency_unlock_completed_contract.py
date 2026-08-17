"""Static guard: emergency_unlock_completed must not be logged at grant time.

Background (#1167). `EmergencyUnlockCoordinator.completeUnlock` used to call
`trackEmergencyUnlockUsed` and `trackEmergencyUnlockCompleted` back to back with no branch
between them. The two events were therefore identical by construction -- same event count,
same user count, every single month -- so the emergency-unlock "completion rate" guardrail
in docs/ops/stopit/metrics-context.md was actually measuring the grant rate, and the
allowlisted pair burned the Amplitude per-device monthly cap twice for one signal.

Nothing in the type system stops the two calls from drifting back next to each other, and
a unit test asserting the recorded call list is easy to "fix" by adding the expected event
back. This guard states the structural rule instead: completion is reported by the teardown
path, from a reservation made at grant time.
"""

from __future__ import annotations

import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
UNLOCK_COORDINATOR = REPO_ROOT / "app/src/main/java/com/uiery/keep/service/EmergencyUnlockCoordinator.kt"
COMPLETION_COORDINATOR = (
    REPO_ROOT / "app/src/main/java/com/uiery/keep/analytics/EmergencyUnlockCompletionCoordinator.kt"
)
ACCESSIBILITY_SERVICE = (
    REPO_ROOT / "app/src/main/java/com/uiery/keep/service/KeepAccessibilityService.kt"
)
BACKUP_POLICY = (
    REPO_ROOT / "app/src/main/java/com/uiery/keep/datastore/BackupRestoreDataStoreKeyPolicy.kt"
)

COMPLETED_CALL = "trackEmergencyUnlockCompleted("
USED_CALL = "trackEmergencyUnlockUsed("
PENDING_KEYS = (
    "PENDING_EMERGENCY_UNLOCK_COMPLETION_REASON",
    "PENDING_EMERGENCY_UNLOCK_COMPLETION_DURATION_MINUTES",
    "PENDING_EMERGENCY_UNLOCK_COMPLETION_REMAINING",
)


class EmergencyUnlockCompletedContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.unlock_coordinator = UNLOCK_COORDINATOR.read_text(encoding="utf-8")
        self.completion_coordinator = COMPLETION_COORDINATOR.read_text(encoding="utf-8")
        self.service = ACCESSIBILITY_SERVICE.read_text(encoding="utf-8")
        self.backup_policy = BACKUP_POLICY.read_text(encoding="utf-8")

    def test_grant_path_does_not_log_completion(self) -> None:
        """The grant path owns `used` only; logging both there makes them identical."""
        self.assertIn(
            USED_CALL,
            self.unlock_coordinator,
            "The grant path must still record emergency_unlock_used.",
        )
        self.assertNotIn(
            COMPLETED_CALL,
            self.unlock_coordinator,
            "emergency_unlock_completed was logged at grant time again; it would once more "
            "be an exact duplicate of emergency_unlock_used (#1167).",
        )

    def test_grant_path_reserves_the_completion_payload(self) -> None:
        """Teardown cannot know reason/duration/remaining unless the grant path saves them."""
        self.assertIn(
            "completionCoordinator.reserve(",
            self.unlock_coordinator,
            "The grant path must reserve the completion payload for later delivery.",
        )

    def test_completion_is_logged_only_by_the_delivery_coordinator(self) -> None:
        self.assertEqual(
            self.completion_coordinator.count(COMPLETED_CALL),
            1,
            "EmergencyUnlockCompletionCoordinator must hold the single completion call site.",
        )

    def test_teardown_paths_deliver_pending_completion(self) -> None:
        """Both expiry paths converge on clearing state; both must report completion.

        One is the polling cleanup, the other the scheduled expiry callback. A window that
        ends through the path that forgets to deliver is a window that never completes.
        """
        self.assertEqual(
            self.service.count("emergencyUnlockCompletionCoordinator().deliverPending()"),
            2,
            "Both emergency-unlock teardown paths must deliver the pending completion; "
            "found a different number of delivery sites.",
        )
        self.assertIn(
            "fun emergencyUnlockCompletionCoordinator(): EmergencyUnlockCompletionCoordinator",
            self.service,
            "The service entry point must expose the completion coordinator.",
        )

    def test_pending_keys_are_reset_on_restore(self) -> None:
        """A restored device must not report completion of a window it never opened."""
        for key in PENDING_KEYS:
            self.assertIn(
                key,
                self.backup_policy,
                f"{key} must be classified in BackupRestoreDataStoreKeyPolicy.",
            )


if __name__ == "__main__":
    unittest.main()
