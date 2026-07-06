# Contributing to NofAR

Thank you for your interest in contributing. NofAR is an offline-first, privacy-preserving Android app — please keep changes aligned with [AGENTS.md](AGENTS.md) and the product spec in `internal/Requirements.md` (when available locally).

## Branch naming

- `feature/<short-description>` — new functionality
- `fix/<short-description>` — bug fixes
- `phase-<NN>-<short-description>` — scoped phase work (e.g. `phase-01-data-layer`)

Branch from `main` and keep pull requests focused on one feature or phase slice.

## Pull request checklist

Before opening a PR:

- [ ] Changes are scoped to the stated goal (no unrelated refactors)
- [ ] `./gradlew spotlessCheck detekt lint test` passes locally
- [ ] `./gradlew :app:assembleDebug` succeeds (or `assembleRelease` when touching release config)
- [ ] New behavior has unit or instrumented tests where appropriate
- [ ] No proprietary analytics, crash reporting, or network calls outside Prepare mode
- [ ] Feature modules depend only on `core:*`, not on other feature modules
- [ ] Phase acceptance criteria are met (if the PR implements a phase from `internal/phases/`)

## Code style

Follow [Now in Android](https://github.com/android/nowinandroid) conventions:

- Kotlin 2.x, Jetpack Compose (Material 3), unidirectional data flow
- Match existing naming and module boundaries in the repo
- Run `./gradlew spotlessCheck detekt lint` before pushing — Spotless (ktlint), Detekt, and Android Lint are enforced in CI
- Prefer self-explanatory code; comment only non-obvious business logic

## Commits

Use clear, imperative commit messages (e.g. `Phase 0: add navigation shell and design system`).

## License

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).
