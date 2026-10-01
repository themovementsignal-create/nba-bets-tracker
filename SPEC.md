# Training App Spec

## Principles
- Strong-level simplicity: logging a set takes 1–2 taps, and extras never clutter the workout screen.
- Built for the gym floor: big tap targets, one-handed use, dark theme by default, works offline.
- My data is precious: local-first, regular backups, history is never lost.
- Units: kg, km, °C.

## Stack
Kotlin, Jetpack Compose, Material 3, Room (SQLite), Health Connect (Phase 3). Minimal dependencies.

## Phase 1: Core logger
- [ ] Exercise library with these types: weight × reps; bodyweight with added or assisted load (chin-ups); timed holds (dead hangs, isometrics); load × distance (sled push). Custom exercises allowed.
- [ ] Templates, seeded with:
  - Session 1, Athletic Lower: trap bar deadlift, Bulgarian split squat (alternative: goblet squat), sled push, step-down isometric, seated calf raise
  - Session 2, Push/Pull Upper: dead hang, chin-up, horizontal press, row, overhead press, face pull, cable crossover
  - Wednesday: basketball or conditioning
- [ ] Workout screen: last session's numbers beside each set, tap to complete a set, auto rest timer with vibration and notification, plate calculator with configurable bar weights (including trap bar)
- [ ] History, PRs, estimated 1RM (Epley), per-exercise progress charts
- [ ] Strong CSV import (I'll attach an export; make the parser tolerant)
- [ ] Export and backup to JSON + CSV via the share sheet; Android Auto Backup on
- [ ] Error log screen showing recent crashes and errors, with a copy button

## Phase 2: Extras
- [ ] Sauna log: rounds, minutes, °C, cold contrast; prefilled with 3 rounds × 12–15 min at 80–100°C
- [ ] NEAT: treadmill timer plus manual entry; weekly target of 3–4 sessions × 60 min
- [ ] Basketball and conditioning: duration + session RPE (1–10)
- [ ] Gear tracker: shoes, shirts, shorts and so on; default gear per session type; wear counted by sessions and age; replacement nudges at a set lifespan
- [ ] Sleep: tap at bed and wake, rate quality 1–5
- [ ] Morning check-in: sleep, soreness, energy (1–5 each)
- [ ] Niggle log: tap a body map region and side, severity 0–10, notes
- [ ] Bodyweight log with 7-day average; target 98 kg
- [ ] Protein quick tally with presets; target 160–175 g/day
- [ ] Daily supplement checklist (editable list)

## Phase 3: Data and dashboard
- [ ] Health Connect: read steps (and sleep if available)
- [ ] Dashboard: sessions vs a 7-per-month target, weekly load (session RPE × minutes) with spike flags, sauna and NEAT minutes, bodyweight trend, protein adherence, sleep vs performance
- [ ] Jump height test: record slow-mo video, step through frames, mark takeoff and landing; height = g·t²/8
- [ ] Venue profiles: equipment per gym, with automatic exercise substitutions when something's missing
- [ ] Travel mode: hotel-gym versions of my sessions

## Phase 4: Claude
- [ ] Weekly review summary
- [ ] Chat that proposes program changes as a diff I approve (in-app API vs MCP connector: decide later)
