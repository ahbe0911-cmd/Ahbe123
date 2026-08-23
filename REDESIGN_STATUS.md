# Rooznegar v0.2 redesign status

Started on branch `rooznegar-redesign-v2`.

Implemented in the first pass:
- New purple/navy Material 3 design system inspired by the supplied reference without copying assets.
- Vazirmatn as the application typography family (regular/medium/bold).
- New Today dashboard with date hero card, timeline tasks, metrics and pinned notes.
- Refreshed bottom navigation and quick-add FAB.
- Jalali month calendar redesigned as a lightweight fixed 6x7 layout.
- Task filtering moved behind remembered derived state and lifecycle-aware collection.
- Lifecycle-aware settings state in the app shell.
- BootReceiver now respects the persistent-date-notification setting.
- Version bumped to `0.2.0-redesign`.

Next CI gate: `testDebugUnitTest assembleDebug` on the PR merge tree.
