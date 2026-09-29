# AGENTS.md

## Project overview

This repository is the Android app for LumenCue, a concert AR companion built with Kotlin and Jetpack Compose. The app targets a Ray-Ban Display HUD experience, while keeping the smartphone UI and local simulation logic separated from device-specific Meta DAT integration.

Primary project details:
- App package: `com.k3i.lumencue`
- Gradle wrapper: `./gradlew`
- Compose UI app with Android SDK 36 target
- Optional Meta Wearables DAT dependency behind a GitHub token gate
- Local simulation data and fallback flows are preferred until official SDK integration is available

## Working rules

- Keep changes small and targeted to the relevant feature or screen.
- Prefer existing architecture patterns already used in the app (Compose screens, shared state, local simulation data, and Android resource patterns).
- Do not add hard-coded production credentials, GitHub tokens, or personal device identifiers.
- Treat Meta DAT / Ray-Ban Display integration as a future or optional integration layer; avoid directly coupling business logic to external SDK behavior unless the task explicitly demands it.
- If a task mentions concert package validation, HUD rendering, or display dispatch, prefer the existing abstraction layers (`HudState`, `HudVisualScene`, `HudRenderInstruction`, `ToolkitDisplayDocument`) instead of bypassing them.

## Build and validation commands

Run these from the repo root:

- `./gradlew test`
- `./gradlew assembleDebug`
- `./gradlew :app:assembleDebug`

For local env-specific builds:
- `GITHUB_TOKEN=... ./gradlew assembleDebug`
- or set `github_token` in `local.properties` when you need Meta DAT artifacts available

## Project-specific constraints

- The app is not a server-backed product yet; local JSON assets and `SharedPreferences` are the current default.
- Real Meta AI / Ray-Ban Display direct output is policy-dependent and should be abstracted behind safe, testable models.
- The app should remain functional even when the GitHub token is absent; DAT dependencies should be optional.
- Do not treat generated visual assets as final production HUD output. Prefer the Compose/HUD model pipeline over raw image-only rendering for important decisions.

## Preferred implementation style

- Keep Kotlin code idiomatic and readable.
- Favor compile-safe changes and consistent naming.
- For UI work, prefer Compose patterns already used by the project.
- For complex product logic, document assumptions and keep fallback behavior explicit.
- When adding new behavior around concerts, permissions, ticket state, or HUD dispatch, make the logic easy to test locally and easy to replace with real SDK integration later.

## When in doubt

- Follow the existing repository structure and naming conventions.
- Prefer local simulation and mock-device validation before introducing external platform dependencies.
- Keep the project aligned with the README’s product goals: AR companion UX, not a replacement for the real concert experience.
