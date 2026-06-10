# Repository Guidelines

## Scope
This file applies to the repository root at `C:\StableDiffusion\diffusion-desk`.
More specific `AGENTS.md` files inside subdirectories take precedence for their subtree.

## Architecture
- `composeApp/`: Kotlin Multiplatform Compose desktop app. This is the current primary UI path for new frontend work.
- `webui/`: Legacy/deprecated Vue 3 + TypeScript frontend built with Vite. Do not add new product UI here unless the user explicitly asks for web UI changes.
- `src/`: Native C++ backend and workers.
- `libs/`: Git submodules and vendored upstream dependencies.
- `scripts/`: Primary build, run, and verification scripts.

## Working Rules
- Check git state before editing. This repo may have user-owned submodule worktrees with detached `HEAD`s.
- Write repository-facing documentation, code comments, identifiers, and user-visible product text in English unless a task explicitly requires another language. Conversation with the user may use their preferred language.
- Do not edit files under `libs/` unless the task explicitly requires changing vendored/upstream code.
- Treat `config.json`, `*.db`, `*.log`, and generated build outputs as local/runtime artifacts unless the task is specifically about them.
- For UI work, default to the Kotlin Compose Desktop app in `composeApp/`. Treat `webui/` as legacy/deprecated.
- Prefer targeted changes over broad refactors. This repository mixes Kotlin, TypeScript, PowerShell, shell scripts, CMake, and C++.

## Build And Run
- Full Windows build: `.\scripts\build.ps1`
- Run server on Windows: `.\scripts\run.ps1`
- Compose desktop shell: `.\gradlew :composeApp:run`
- Web UI build: run `npm install` and `npm run build` in `webui/`
- For repository build validation on Windows, prefer the provided PowerShell build script over direct ad hoc CMake build commands.

## Validation
- Prefer the smallest relevant validation for the change.
- Existing verification scripts live under `scripts/tests/`.
- For Compose desktop UI and shell work, validate with Gradle tasks scoped to `composeApp`.
- For legacy web UI work, validate from `webui/` only when web UI changes were explicitly requested.
- For backend/native changes on Windows, use `.\scripts\build.ps1` unless the user explicitly asks for a different build path.

## Git Notes
- The main project tracks upstream branches normally.
- Submodules in `libs/` are pinned by commit and may appear as detached `HEAD`.
- If a submodule shows as modified, verify whether it is a local nested-submodule/worktree change before assuming the superproject needs an update.
