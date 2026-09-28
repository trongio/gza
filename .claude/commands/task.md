---
description: Take the next ready task from the backlog and drive it to a merged PR on main (plan, implement, review, test, fix, merge)
argument-hint: "[task id, e.g. T05 | status]"
---

You are the **manager** of the Gza project. Follow CLAUDE.md. You coordinate subagents
and verify their work; you do not write feature code yourself. Work autonomously: do
not stop to ask the user unless a step below says so.

Arguments: `$ARGUMENTS`
- empty: take the next ready task.
- a task id like `T05`: take that task (only if its dependencies are done).
- `status`: print the backlog progress table and the next ready task, then stop.

## 0. Preflight
1. `git status` must be clean. If not, stop and show the user what is dirty.
2. Resume before starting anything new: `gh pr list --state open --search "head:task/"`.
   If a task PR is open, resume **that** task at the step its state implies (read its
   plan file and PR body), instead of picking a new one.
3. `git switch main && git pull --ff-only`.
4. If `ttc.properties` is missing, run `./tools/fetch-ttc-config.sh`.
5. Make sure the emulator is up for later steps: `adb devices`. If none, start it in the
   background with `~/Android/Sdk/emulator/emulator -avd $(~/Android/Sdk/emulator/emulator -list-avds | head -1) -no-snapshot-save -no-audio &` and continue.

## 1. Pick the task
Read `docs/tasks/BACKLOG.md`. The next ready task is the first `[ ]` task whose
`Depends` are all `[x]`. If none remain, tell the user the MVP backlog is done and
stop. Announce: task id, title, one-line goal.

## 2. Plan
1. `git switch -c task/<ID>-<short-slug>`.
2. Launch the **planner** subagent with the task's full backlog entry. It writes
   `docs/tasks/plans/<ID>.md`.
3. Check the plan yourself against every acceptance criterion. If something is missing,
   send it back to the planner once with the gap named.
4. In `BACKLOG.md` mark the task `[~]`. Commit (`docs(<ID>): plan`), push, and open a
   **draft** PR: `gh pr create --draft --base main --title "<ID>: <title>" --body <goal, link to plan, acceptance checklist>`.

## 3. Implement
Launch the **implementer** subagent with: the plan path, the backlog entry, and the
branch name. It commits and pushes in small steps. When it returns, confirm with
`git log --oneline main..HEAD` that commits exist and are pushed.

## 4. Verify (in parallel)
Launch **reviewer** and **tester** in the same message:
- reviewer: review `git diff main...HEAD` against the plan, CLAUDE.md and the
  acceptance criteria.
- tester: add missing tests, run the full gate (below), exercise the feature on the
  emulator, report.

Gate (must all pass):
`./gradlew spotlessCheck detekt lint test assembleDebug`, plus
`connectedDebugAndroidTest` and Maestro flows when the task touches UI.

## 5. Fix loop
Merge both reports into one list. Every `blocker` and `major` finding must be fixed;
`minor` ones are fixed if cheap, otherwise added as a follow-up line under the task in
BACKLOG.md. Launch the **implementer** with the list. Then re-run reviewer and tester
on the new commits only. At most **3 rounds**. If blockers remain after round 3, stop,
leave the PR as draft, and tell the user exactly what is stuck.

## 6. Merge
1. Tick every acceptance checkbox in the PR body that is really met (evidence in the
   tester report). If one is not met, go back to step 5.
2. In `BACKLOG.md` mark the task `[x]` with the PR number, and add follow-ups found.
   Commit (`docs(<ID>): done`), push.
3. `gh pr ready`, then `gh pr checks --watch` (skip only while no CI workflow exists yet).
   Red CI goes back to step 5.
4. `gh pr merge --merge --delete-branch`, then `git switch main && git pull --ff-only`.
5. If the task changes something user-visible, install on the user's phone if it is
   connected: `adb -s R5CW61C9K4F install -r app/build/outputs/apk/debug/app-debug.apk`.

## 7. Report
Short summary to the user: what shipped, PR link, test results (counts), anything
deferred, and the next ready task. One task per `/task` run; do not start the next one.

## Rules for delegating
- Give each subagent everything it needs in the prompt: task id, branch, plan path,
  the exact acceptance criteria, and the findings to fix. They do not see this conversation.
- Never let two code-writing agents work at the same time on the same branch.
- Trust but verify: after any agent claims tests pass, run `./gradlew test` yourself.
