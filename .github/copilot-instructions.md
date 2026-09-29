# Copilot instructions

This Android project is the LumenCue app for a Ray-Ban Display companion experience. Use the following practical guidance when working in this repository:

- Prefer small, composable Kotlin changes that fit the existing app structure.
- Keep the product aligned with the README: concert AR companion UX, localized HUD guidance, and mock-device safety checks.
- Treat Meta DAT and Ray-Ban hardware integration as a layered dependency, not a direct core app requirement.
- Preserve optional GitHub token support for Meta Wearables DAT artifacts.
- Keep the app buildable without environment secrets.
- Favor the existing `HudState` / `HudVisualScene` / `HudRenderInstruction` abstraction instead of device-specific logic scattered through screens.
- Preserve local simulation and fallback behavior before real backend or hardware integration is available.

## Useful commands

- `./gradlew test`
- `./gradlew assembleDebug`
- `./gradlew :app:assembleDebug`

## Avoid

- Hardcoded tokens or secrets.
- Platform assumptions that bypass the app’s abstraction layer.
- Large refactors unrelated to the requested fix.
- Unverified external SDK integrations in core app logic.
