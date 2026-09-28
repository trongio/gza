# Gza

An unofficial Android app for Tbilisi buses, metro and gondolas that answers one
question well: **when do I leave, and when will the bus really be here?**

It uses the same public gateway as the official TTC web app, and fixes what makes that
app confusing:

- A bus resting at its first or last stop is **waiting**, not "arriving in 0 min". Gza
  predicts when it will actually leave, and carries that down the whole route.
- Every departure comes with a **leave-by time**, based on your walk to the stop.
- Arrival predictions come from live GPS positions and learned travel times, not from
  the gateway's delay field, which is often wrong by up to an hour.
- A trip planner with arrive-by times, transfer risk, and commute reminders.

Status: **building the MVP**. See [PLAN.md](PLAN.md) for the full plan,
[docs/tasks/BACKLOG.md](docs/tasks/BACKLOG.md) for progress, and
[docs/TTC_API.md](docs/TTC_API.md) for the API as observed.

## Development

```sh
./tools/fetch-ttc-config.sh   # writes ttc.properties (gitignored) with the current gateway key
```

Not affiliated with Tbilisi Transport Company. All data belongs to its owners.

## How this repo is built

Development runs through a Claude Code "manager" session. Open Claude Code in this
folder and run:

```
/task          # take the next ready backlog task and drive it to a merged PR
/task status   # show backlog progress and what is next
/task T08      # take a specific task (if its dependencies are done)
```

The manager uses the subagents in `.claude/agents/` (planner, implementer, reviewer,
tester), works on a `task/*` branch, commits and pushes in small steps, and merges to
`main` only when review, tests and CI are green. Rules for all sessions: `CLAUDE.md`.
