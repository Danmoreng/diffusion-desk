Below is an \*\*improved, consolidated development plan\*\* that merges:



\* Your \*\*current roadmap + completed milestones\*\* (1–3, plus the post-review stabilization work). 

\* The \*\*architectural review recommendations\*\* (keep the multi-process stability, standardize APIs, modularize UI, close doc↔code gaps). 

\* Your \*\*new “Model Presets + Creative Assistant” direction\*\* (formalized bundles, tool-use chat, vision workflows). 

\* The \*\*VRAM arbitration strategy\*\* (predictive orchestration + surgical worker-level offloading). 

\* The \*\*DB design vision\*\* (params\_json, prompt library, tagging workflow) and your “DB hardening” milestone items.



I’ve structured it as \*\*releases/iterations\*\* with clear deliverables + “definition of done” and explicit dependencies—without guessing calendar dates.



---



\# MystiCanvas Improved Development Plan



\## 0) Guiding principles



1\. \*\*Stability through isolation stays the core\*\*: Orchestrator + specialized workers remains the default because it protects the app from experimental model crashes.

2\. \*\*Make the system predictable before making it “smart”\*\*: VRAM prediction, DB transactions, and schema versioning first; agentic features on top of a reliable substrate.

3\. \*\*Keep HTTP for IPC (for now)\*\*: latency is dominated by GPU work; prioritize debuggability + standardized contracts over swapping protocols early.

4\. \*\*Database becomes “Memory”\*\*: everything intelligence-related (presets, styles, search, jobs, assistant actions) should have a durable representation.



---



\## 1) Current baseline (what this plan assumes is already true)



\* Milestones \*\*1–3 completed\*\* (config system, SPA fallback, internal auth, WebSocket stream, SQLite persistence, history + gallery basics). 

\* Post-review hardening completed: \*\*RAII smart pointers\*\*, \*\*Orchestrator refactor into ServiceController + ResourceManager\*\*, \*\*atomic DB insert\_generation\_with\_tags\*\*.

\* Direction shift underway: moving toward \*\*Model Presets (bundles)\*\* and a \*\*Creative Assistant\*\*. 

\* Known pain point: \*\*VAE decode VRAM spikes / OOM\*\* and “silent failure / blank images” risk.



---



\# Release A — Platform Contracts \& Observability (foundation)



\### Goal



Make every core subsystem \*inspectable\*, \*restartable\*, and \*consistent\* so later features don’t become fragile.



\### Deliverables



\*\*A1. Standardize internal \& external API contracts\*\*



\* Every worker implements:



&nbsp; \* `/internal/health` with \*\*structured fields\*\* (loaded model, VRAM stats, state flags)

&nbsp; \* a consistent error shape (e.g., `{ error: { code, message, details } }`)

\* Orchestrator enforces:



&nbsp; \* \*\*internal token auth\*\* consistently on all internal endpoints

&nbsp; \* consistent timeouts + retries + “worker unavailable” responses



\*\*A2. Worker lifecycle resilience\*\*



\* Orchestrator monitors workers:



&nbsp; \* detect crash / non-responsive

&nbsp; \* restart worker

&nbsp; \* broadcast a WS “system alert” event to UI (with user-friendly text)



\*\*A3. Correlation IDs and structured logs\*\*



\* Add request IDs at Orchestrator boundary and propagate to workers (even over HTTP)

\* Structured logs (JSON lines) with: request\_id, worker, endpoint, duration, outcome

\* This becomes essential when jobs + assistant tools start chaining calls.



\### Definition of done



\* You can answer: “what happened?” for any failed generation/tagging request using logs + request\_id.

\* UI sees an alert when a worker restarts instead of “hanging”.



---



\# Release B — VRAM Management v2 (predictive + surgical)



\### Goal



Prevent OOM \*\*without brute-force unloading\*\*, and make behavior explainable to the user.



This is directly aligned with your VRAM doc and the “predictive + surgical” strategy in the ideas plan.



\### Deliverables



\*\*B1. Health-driven VRAM registry\*\*



\* Workers report VRAM usage via `/internal/health`

\* Orchestrator `ResourceManager` maintains a live registry of:



&nbsp; \* model weights footprint estimate

&nbsp; \* current allocated VRAM

&nbsp; \* “can soft unload” capabilities (KV purge etc.)



\*\*B2. Predictive arbitration (Orchestrator level)\*\*



\* Before SD generation:



&nbsp; \* compute expected VRAM = weights baseline + compute buffers + safety margin

&nbsp; \* decide:



&nbsp;   1. proceed

&nbsp;   2. soft unload LLM (purge KV)

&nbsp;   3. hard unload LLM worker only if needed



\*\*B3. Surgical worker-level mitigations (SD Worker)\*\*

Implement the missing “doc↔code gap” items called out in the review:



\* \*\*Dynamic VAE tiling\*\* based on resolution (prevents decode OOM)

\* \*\*VAE-on-CPU fallback\*\* when predicted VRAM is insufficient

\* \*\*OOM retry path\*\*: if CUDA alloc fails, re-init with conservative settings and retry once (and \*never\* silently output blanks)



\*\*B4. UI feedback loop\*\*



\* VRAM indicator upgraded to show:



&nbsp; \* “projected vs actual”

&nbsp; \* explicit events: “LLM KV purged”, “LLM unloaded”, “VAE moved to CPU”



\### Definition of done



\* A 1024×1024 generation that previously OOM’d \*\*completes\*\* by automatically switching to tiling/CPU decode when needed. 

\* User can see \*why\* performance changed (a notification/event, not a mystery).



---



\# Release C — Database Hardening v2 (complete Milestone 3.5 properly)



Your roadmap says you’re “ready to start” Milestone 3.5 (FTS5, jobs, assets, pagination, tagging improvements). This release turns that list into an ordered, dependency-aware implementation plan.



\## C1. Schema versioning \& migrations (do this first)



\* Add `PRAGMA user\_version` based migrations (or a simple `schema\_versions` table)

\* On startup:



&nbsp; \* backup DB before migration

&nbsp; \* migrate incrementally and idempotently

\* This unlocks safe rollout of every next DB feature.



\*\*DoD:\*\* You can ship new builds without breaking existing DBs.



\## C2. Keyset pagination for gallery (performance)



\* API: “fetch next page” using `(timestamp, id)` cursor instead of OFFSET

\* Add DB indexes already listed in roadmap and ensure queries use them. 



\*\*DoD:\*\* Gallery loads remain fast as history grows.



\## C3. Asset management: `generation\_files`



\* Implement `generation\_files` table (thumbs, previews, masks, etc.) 

\* Add background thumbnail creation as a job (ties into jobs table below)



\*\*DoD:\*\* A generation can safely have multiple artifacts, and UI uses thumbs by default.



\## C4. FTS5 search



\* Add FTS virtual table for prompts (+ optional notes)

\* Build endpoints:



&nbsp; \* search prompts

&nbsp; \* search by tag + prompt

\* This is a prerequisite for “assistant can search history” tools.



\*\*DoD:\*\* Search feels instant and scales.



\## C5. Tag normalization + aliases



\* Add:



&nbsp; \* `normalized\_name` on tags

&nbsp; \* `tag\_aliases` table to map synonyms (cat/feline) 

\* Ensure inserts normalize consistently (one canonical form)



\*\*DoD:\*\* Searching for “cat” finds “feline” images.



\## C6. Job queue (DB-backed)



\* Implement `jobs` table for background tasks:



&nbsp; \* auto-tag new generation

&nbsp; \* generate thumbnail

&nbsp; \* (later) embeddings/similarity, vision analysis

\* Add a simple job runner service in Orchestrator:



&nbsp; \* claim job, mark running, mark done/failed, retry policy



\*\*DoD:\*\* Background features are robust across restarts (no lost work).



---



\# Release D — Model Presets System (data formalization + VRAM prediction backbone)



This is the “Palette + Brain” idea: presets aren’t just UI convenience; they’re what makes VRAM prediction and assistant intelligence \*reliable\*.



\### Deliverables



\*\*D1. Preset schema\*\*



\* `image\_presets` table:



&nbsp; \* unet\_path, vae\_path, clip paths, etc.

&nbsp; \* `vram\_weights\_mb`

&nbsp; \* `preferred\_params` (JSON)

\* `llm\_presets` table:



&nbsp; \* model\_path, mmproj\_path (optional)

&nbsp; \* capabilities array, role, n\_ctx 



\*\*D2. Preset Manager UI\*\*



\* Create a view to:



&nbsp; \* assemble/edit presets

&nbsp; \* validate file existence

&nbsp; \* show VRAM estimate and “recommended resolution defaults”

\* Allow import/export preset JSON for sharing.



\*\*D3. Runtime integration\*\*



\* Orchestrator uses preset metadata when:



&nbsp; \* predicting VRAM (Release B)

&nbsp; \* selecting default params (UI + backend)

&nbsp; \* deciding what to unload/keep warm



\### Definition of done



\* A user selects “Preset: SDXL Portrait Fast” and everything needed is configured consistently—no manual “pick 3–4 files correctly” workflow. 



---



\# Release E — Creative Assistant v1 (tool-use, grounded in your DB + presets)



This release focuses on \*\*tooling and guardrails\*\*, not “vision magic” yet.



\### Deliverables



\*\*E1. Assistant UI\*\*



\* Integrated sidebar chat drawer (persistent) 



\*\*E2. Tool calling / function routing\*\*

Define a small, high-signal set of tools (JSON-RPC style as in your ideas doc):



\* `get\_styles()`

\* `apply\_style(style\_name)`

\* `enhance\_prompt(user\_input)`

\* `search\_history(query)` (requires Release C4 FTS5)



\*\*E3. Permissions + safety rails\*\*



\* Tools are executed by Orchestrator (not directly by the LLM worker)

\* Strict schemas; reject free-form tool args

\* “Max tool calls per turn” + “no recursive tool loops” guard

\* Tool results are summarized back to the model to keep context small.



\*\*E4. Style Library (DB-backed)\*\*



\* Implement `prompt\_library` (or “styles”) table (already in DB concept doc)

\* UI: save style from prompt snippet, apply style to current prompt



\### Definition of done



\* Assistant can: find prior images (“show my last cyberpunk rainforest prompts”), apply a saved style, and produce an enhanced prompt—without being flaky or opaque.



---



\# Release F — Vision + Auto-Tagging v2 (image-grounded intelligence)



This is where you upgrade from “prompt parsing” tags to “look at the pixels” tags. The architectural review explicitly calls this as the future direction.



\### Deliverables



\*\*F1. Vision-capable LLM presets\*\*



\* Use `mmproj\_path` for LLaVA/Qwen-VL style models as described in your ideas doc. 



\*\*F2. Image handoff mechanism (pragmatic first)\*\*



\* Start with passing \*\*image file paths\*\* (or a temp file) to the LLM worker for analysis.

\* Keep shared memory as a later optimization (review suggests shared memory only if needed, and disk is fine today).



\*\*F3. Job-queued vision tagging\*\*



\* New job type: `vision\_autotag\_generation`

\* When a generation completes:



&nbsp; \* enqueue job

&nbsp; \* job runner executes when resources permit (respect VRAM arbitration states)

&nbsp; \* store tags with `source='vision\_auto'`



\*\*F4. Visual feedback loop (assistant feature)\*\*



\* “Analyze last image and suggest prompt adjustments”

\* Output is structured suggestions that can be applied as prompt edits



\### Definition of done



\* Auto-tags match \*image content\*, not just what the user wrote.

\* Tagging is robust (job-based), doesn’t block generation, and respects VRAM arbitration.



---



\# Release G — Distribution \& Packaging (Milestone 5, but with refined IPC stance)



Your roadmap lists \*\*Named Pipes IPC\*\* as Milestone 5. The architectural review suggests \*\*keeping HTTP\*\* because the bottleneck isn’t IPC latency and debug ergonomics are valuable.



\### Recommended adjustment



\* Treat “Named Pipes” as an \*\*optional hardening path\*\* for installer-grade builds (security/permissions), not as a mandatory performance upgrade.



\### Deliverables



\*\*G1. Release build automation\*\*



\* One-command release build (MSVC)

\* Embed icon/version metadata 



\*\*G2. Installer\*\*



\* Inno Setup/NSIS

\* Bundle:



&nbsp; \* Orchestrator + workers

&nbsp; \* WebUI dist

&nbsp; \* default config

\* First-run wizard (model download / set paths) 



\*\*G3. IPC hardening decision\*\*



\* Keep HTTP loopback + transient auth token as default (already aligns with your security approach).

\* If you later add Named Pipes:



&nbsp; \* do it behind a build flag

&nbsp; \* preserve the HTTP protocol as a fallback for debug/dev



\### Definition of done



\* A non-developer can install and run MystiCanvas with minimal manual setup.



---



\## Cross-cutting backlog (keep these always-on)



\### Code quality \& maintainability



\* Continue RAII expansion beyond SD ctx (upscaler ctx, DB handles, file handles). 

\* Reduce “God components” in Vue:



&nbsp; \* extract `VramIndicator.vue`, `ModelSelector.vue`

&nbsp; \* move business rules into Pinia actions 



\### Configuration hygiene



\* Move hardcoded prompts (tagger system prompt, assistant prompt templates) into `config.json` to tune without rebuilds.



\### UX polish that pays dividends



\* Explicit system status (what’s loaded, what’s offloaded, what’s queued)

\* “One-click reuse recipe” stays central (history → inject params)



---



\# Suggested priority order (if you only do 3 things next)



1\. \*\*Release C (DB hardening) up through jobs + FTS5\*\*

&nbsp;  Because it unlocks presets + assistant tools safely.



2\. \*\*Release B (VRAM v2) worker-level fixes (tiling / VAE-on-CPU / retry)\*\*

&nbsp;  Because it eliminates the biggest reliability killer and makes your “keep both models warm” vision achievable.



3\. \*\*Release D (Presets)\*\*

&nbsp;  Because it formalizes everything and becomes the spine for VRAM prediction + model-aware prompting. 



---



If you want, I can also turn this into a \*\*checkbox-style GitHub roadmap\*\* (epics → issues → acceptance criteria) or a \*\*Kanban-ready backlog\*\* (Now / Next / Later) using the same structure—without changing the technical intent from your docs.



