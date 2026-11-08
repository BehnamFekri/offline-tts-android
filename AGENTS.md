# BeniTTS — Project Instructions

Read the canonical shared instructions in BehnamFekri/Beni-Repository-HUB/AGENTS.md and docs/ai-workflow.md before working.

## Project identity
- Native Android application using Kotlin, Jetpack Compose, sherpa-onnx, and Piper voice models.
- Repository: BehnamFekri/offline-tts-android.
- Preserve offline speech generation and app-private model/audio handling.

## Before editing
1. Sync latest main; inspect README, current version, recent commits, tests, and open PRs.
2. Create a dedicated task branch; never edit main directly.
3. Review model licensing and attribution before changing/downloading voice assets.
4. Back up before destructive changes and preserve user-imported models and generated audio.

## Build and quality
- Use Gradle wrapper and existing test setup.
- Run unit tests, lint, and Android build when available; report blocked dependencies/tooling exactly.
- Do not claim successful checks that were not run.
- Keep development version codes incremental. Stable release requires complete testing, diff review, and explicit user approval.

## Product constraints
- Speech generation must remain offline after model import.
- Do not add cloud TTS, paid APIs, analytics, background services, or unnecessary permissions without explicit approval.
- Preserve Android Safe Area, RTL/Persian text behavior, and user data.
- Never include model binaries or secrets in generated Graphify output.
- Do not merge, publish, or distribute builds without user approval.
