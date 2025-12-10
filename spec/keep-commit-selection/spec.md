### Goal
Keep the user’s change selection (files/partials) in the Commit UI when a commit attempt fails (e.g., pre-commit hook returns non-zero), so they can fix the issue and immediately retry with the same selection and message.

---

### UX options (pick 1 to start, keep others as stretch)
- Default behavior + subtle banner (recommended)
  - When a commit fails, show a compact inline banner above the Commit panel saying: “Commit failed. Restore previous selection?” with actions: `Restore` | `Dismiss`. Persist the last selection automatically, but don’t apply it until the user clicks Restore. This avoids surprising users who intentionally cleared selection.
- Gear option in Commit UI
  - Add a toggle in the Commit toolwindow gear menu: `Auto-restore selection after failed commit`. If ON, re-apply immediately post-failure; if OFF, do nothing. Keep the inline banner but single action: `View details`.
- Settings page
  - A checkbox in Settings | Version Control | Commit: `Restore previous selection after failed commit` (with sub-option: `…only if restored within N minutes`, default 30).

Recommendation: implement the banner + persistent toggle. Users get discoverability and can opt-in/out quickly.

---

### What to persist
- Commit message text.
- Included changes set:
  - Files (Change keys) and ChangeList ID.
  - Partial inclusions (line ranges/hunks) when possible.
- Commit options snapshot (amend, sign-off, no-verify, push after commit, gpg sign, etc.).
- Timestamp + repo roots that were targeted.

Notes
- Start with file-level selection (95% of value). Partial-hunk preservation is a second iteration because it requires LST trackers.

---

### Key platform APIs you’ll use (IntelliJ Platform / PyCharm)
- Commit workflow and UI
  - `com.intellij.vcs.commit.CommitWorkflowHandler`
  - `com.intellij.vcs.commit.NonModalCommitWorkflowHandler`
  - `com.intellij.vcs.commit.CommitWorkflowListener` (project-level MessageBus topic)
  - `com.intellij.vcs.commit.CommitUi` / `CommitWorkflowUi` (access to “included changes”)
- Traditional checkin hooks (still supported in new UI)
  - `com.intellij.openapi.vcs.checkin.CheckinHandlerFactory`
  - `com.intellij.openapi.vcs.checkin.CheckinHandler`
  - `com.intellij.openapi.vcs.checkin.CheckinProjectPanel` (selection, message, options)
  - `com.intellij.openapi.vcs.changes.CommitContext`
- Changes/changelists
  - `com.intellij.openapi.vcs.changes.ChangeListManager`
  - `com.intellij.openapi.vcs.changes.Change`
  - `com.intellij.openapi.vcs.changes.LocalChangeList`
- Git
  - Your logic should be VCS-agnostic, but you can integration-test via Git using `git4idea.checkin.GitCheckinEnvironment` and a failing pre-commit hook.
- Storage
  - `@Service(Service.Level.PROJECT)` service for in-memory cache + `PersistentStateComponent` if you want cross-session restore.
  - `PropertiesComponent` for a simple boolean user toggle.

---

### High-level flow
1. Intercept “commit about to start”.
   - Capture a `SelectionSnapshot` from the Commit UI: included `Change`s, options, message, timestamp, roots.
2. Observe result of commit attempt.
   - If success → discard snapshot.
   - If failure (any VCS) → keep snapshot, show banner with “Restore selection”. If user enabled auto-restore, apply immediately.
3. Apply snapshot on demand.
   - Re-include the same `Change`s (and later, partial ranges if implemented), set commit message and options. Keep the current changelist context intact if it still exists.

---

### Data model
```kotlin
@Serializable
data class SelectionSnapshot(
    val changeListId: String?,
    val includedChangeIds: List<ChangeId>,
    val partialInclusions: List<PartialRange>? = null, // v2
    val commitMessage: String,
    val options: CommitOptionsSnapshot,
    val roots: List<VirtualFile>,
    val createdAt: Instant = Instant.now()
)

data class ChangeId(val beforePath: String?, val afterPath: String?, val revision: String?)

data class CommitOptionsSnapshot(
    val amend: Boolean,
    val signOff: Boolean,
    val pushAfterCommit: Boolean,
    val gpgSign: Boolean,
    val noVerify: Boolean
)
```

Store only what you need to re-identify `Change`s. Paths + before/after are usually enough; revision helps when files were moved.

---

### Implementation sketch

#### 1) Project service
```kotlin
@Service(Service.Level.PROJECT)
class FailedCommitSelectionService(private val project: Project) {
    @Volatile private var lastSnapshot: SelectionSnapshot? = null

    var autoRestore: Boolean
        get() = PropertiesComponent.getInstance(project).getBoolean(KEY_AUTO_RESTORE, true)
        set(v) { PropertiesComponent.getInstance(project).setValue(KEY_AUTO_RESTORE, v, true) }

    fun remember(snapshot: SelectionSnapshot) { lastSnapshot = snapshot }
    fun clear() { lastSnapshot = null }
    fun peek(): SelectionSnapshot? = lastSnapshot

    companion object { private const val KEY_AUTO_RESTORE = "restore.selection.after.failed.commit" }
}
```

#### 2) Hook into the commit lifecycle
Option A (modern): subscribe to commit workflow events.
```kotlin
class FailedCommitWorkflowListener(private val project: Project) : CommitWorkflowListener {
    override fun beforeCommitChecksStarted(sessionInfo: CommitSessionInfo) {
        val snapshot = CommitUiSnapshotter(project).snapshot()
        project.service<FailedCommitSelectionService>().remember(snapshot)
    }

    override fun commitFinished(result: CommitResult) {
        val service = project.service<FailedCommitSelectionService>()
        when (result) {
            is CommitResult.Success -> service.clear()
            is CommitResult.Failed -> showRestoreBannerOrAutoApply(project, service.peek())
        }
    }
}

class FailedCommitStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val connection = project.messageBus.connect()
        connection.subscribe(CommitWorkflowListener.TOPIC, FailedCommitWorkflowListener(project))
    }
}
```

Option B (compatible with classic API): `CheckinHandlerFactory`
```kotlin
class RememberSelectionCheckinHandlerFactory : CheckinHandlerFactory() {
    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
        object : CheckinHandler() {
            override fun beforeCheckin(): ReturnResult {
                val snapshot = CommitUiSnapshotter(panel.project).snapshotFromPanel(panel)
                panel.project.service<FailedCommitSelectionService>().remember(snapshot)
                return ReturnResult.COMMIT
            }

            override fun checkinSuccessful() {
                panel.project.service<FailedCommitSelectionService>().clear()
            }

            override fun checkinFailed(exception: List<VcsException>) {
                val service = panel.project.service<FailedCommitSelectionService>()
                showRestoreBannerOrAutoApply(panel.project, service.peek())
            }
        }
}
```

#### 3) Snapshotting and restoring
```kotlin
class CommitUiSnapshotter(private val project: Project) {
    fun snapshot(): SelectionSnapshot {
        val ui = CommitModeManager.getInstance(project).ui // obtain current Commit UI
        val included = ui.includedChanges
        val changeIds = included.map { it.toId() }
        val options = ui.commitOptions.toSnapshot()
        return SelectionSnapshot(
            changeListId = ui.commitState.changeListId,
            includedChangeIds = changeIds,
            commitMessage = ui.commitMessage,
            options = options,
            roots = ui.roots
        )
    }

    fun snapshotFromPanel(panel: CheckinProjectPanel): SelectionSnapshot {
        val changeIds = panel.selectedChanges.map { it.toId() }
        return SelectionSnapshot(
            changeListId = panel.selectedChangeList?.id,
            includedChangeIds = changeIds,
            commitMessage = panel.commitMessage,
            options = panel.commitOptions().toSnapshot(),
            roots = panel.roots
        )
    }
}

private fun Change.toId() = ChangeId(beforeRevision?.file?.path, afterRevision?.file?.path, afterRevision?.revisionNumber?.asString())
```

Restoration:
```kotlin
fun applySnapshot(project: Project, snapshot: SelectionSnapshot) {
    val ui = CommitModeManager.getInstance(project).ui
    val changesByPath = ChangeListManager.getInstance(project).allChanges.associateBy { it.toId() }
    val toInclude = snapshot.includedChangeIds.mapNotNull { changesByPath[it] }

    // Apply message and options first
    ui.setCommitMessage(snapshot.commitMessage)
    ui.commitOptions.applyFromSnapshot(snapshot.options)

    // Re-include
    ui.clearInclusions()
    ui.include(toInclude)

    // Optional: focus Commit toolwindow and scroll to selection
    CommitUiUtil.focusCommitUi(project)
}
```

Banner action:
```kotlin
fun showRestoreBannerOrAutoApply(project: Project, snapshot: SelectionSnapshot?) {
    if (snapshot == null) return
    val service = project.service<FailedCommitSelectionService>()
    if (service.autoRestore) {
        applySnapshot(project, snapshot)
        VcsNotifier.getInstance(project).notifyInfo("Selection restored", "Your previous commit selection was restored.")
    } else {
        EditorNotifications.getInstance(project).updateAllNotifications() // or show inline UI in Commit toolwindow
        // Build a lightweight in-panel notification with action `Restore`
    }
}
```

---

### Handling partial commits (v2)
- Use `PartialLocalLineStatusTracker` API to record `Range` inclusions per file.
- Snapshot by file path → list of included `Range` offsets.
- On restore: ensure `LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document)` is ready, then re-include ranges via `ChangeListManagerEx#scheduleAutomaticPartialCommit` or UI APIs that manipulate chunk inclusions.
- This part is trickier and may need to run after trackers are ready (invokeLater + wait for smart mode).

---

### Edge cases
- Files changed/moved between attempts → best-effort inclusion by `afterPath`; if not found, skip and show a note in the banner (e.g., “2 files could not be restored”).
- Changelist deleted → include in the default active changelist.
- Multiple repositories → store per-root and apply only to roots present in snapshot.
- Amend commits → if the option changed server-side or the last commit moved, respect the new repo state; do not auto-amend.
- Time guard → ignore and drop snapshot older than N minutes (configurable) to avoid stale re-applies next day.

---

### Testing strategy

#### 1) Fast unit tests (no VCS)
- Base: `LightPlatformTestCase` or `BasePlatformTestCase`.
- Test `FailedCommitSelectionService` and `CommitUiSnapshotter` with a fake UI adapter.
  - Given a fake panel with included changes A,B and message “X”, snapshot → contains A,B and “X”.
  - Apply snapshot to fake UI → included contains A,B and message “X”.

#### 2) Integration tests with Git (end-to-end)
- Base: `GitPlatformTest` or similar from `git4idea.tests`.
- Steps:
  1. Create temp Git repo with `GitTestUtil.setupRepo`. Create and stage multiple changes.
  2. Install failing pre-commit hook: write `.git/hooks/pre-commit` with `#!/bin/sh
     echo "fail" >&2; exit 1` and `chmod +x`.
  3. Open Commit toolwindow programmatically, include a subset of files, set commit message.
  4. Trigger commit via `CommitExecutor`/`CommitWorkflowHandler.performCommit`.
  5. Assert failure (non-zero), then verify selection restored:
     - `CommitUi.includedChanges` equals originally selected set.
     - Commit message equals original.
  6. Disable hook (or make it succeed), re-run commit, verify snapshot cleared.

Key helpers usually available in tests:
- `GitTestUtil`, `VcsTestUtil`, `PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()` to wait for UI.
- If using classic API: create a `CheckinProjectPanel` via `CommitDialogFactory` in tests or interact with `CommitWorkflowManager`.

#### 3) Partial inclusion tests (when implemented)
- Enable partial commit for a file (enable LST), select ranges, take snapshot, change file content (non-overlapping), restore, assert included chunks match by offsets or anchored by line markers.

#### 4) UI behavior tests (optional, slower)
- GUI: `com.intellij.testGuiFramework` to assert the banner appears and Restore button works. If too heavy, replace with headless assertion that a `Notification` was posted on failure and that `applySnapshot` ran.

---

### Performance and threading
- Capture and restore on EDT (`ApplicationManager.getApplication().invokeLater`) because Commit UI is Swing.
- Query `ChangeListManager` on background if heavy, then switch to EDT to apply inclusions.
- Debounce: avoid re-applying if user already changed selection after failure; compare current `includedChanges` with snapshot and skip if user edited.

---

### Telemetry (optional)
- Count how often failures happen, how often snapshot restore is used, and number of changes restored. Helps decide defaults.

---

### Rollout plan
- v1: File-level selection + commit message, banner with Restore, opt-in auto-restore setting, Git-only integration tests.
- v2: Partial-hunk restore, per-changelist restore, multi-repo polish, GUI tests.
- v3: Smart conflicts: if a file changed since snapshot, prompt user before including.

---

### Summary
Hook into the commit lifecycle (via `CommitWorkflowListener` or `CheckinHandler`), snapshot the included changes and commit message right before running checks, and re-apply them only if the commit fails. Start with file-level preservation and a banner-driven restore UX, cover it with a Git integration test that simulates a failing pre-commit hook, and iterate to partial hunks once the core flow is stable.