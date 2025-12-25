# MystiCanvas Database & Smart Library Concept

This document outlines the design for a local persistence layer using SQLite to transform MystiCanvas from a generation tool into a searchable, AI-augmented creative asset manager.

## 1. Technology Choice: SQLite
- **Why:** Zero-configuration, serverless, single-file storage (`mysti.db`).
- **Integration:** C++ `sqlite3` library for the backend, exposed via JSON-REST endpoints to the Vue.js frontend.

## 2. Proposed Database Schema

### Table: `generations`
The master record of every image created.
| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | INTEGER (PK) | Unique identifier. |
| `timestamp` | DATETIME | ISO8601 creation time. |
| `image_path` | TEXT | Relative path to `outputs/`. |
| `prompt` | TEXT | The primary positive prompt. |
| `negative_prompt`| TEXT | The negative prompt used. |
| `seed` | BIGINT | The randomization seed. |
| `model_id` | TEXT | Filename of the checkpoint/GGUF used. |
| `params_json` | TEXT (JSON) | Steps, CFG, Sampler, Resolution, etc. |
| `is_favorite` | BOOLEAN | User-flagged favorite. |
| `rating` | INTEGER | 1-5 star rating. |
| `parent_id` | INTEGER | Link to original image (if this is an upscale). |

### Table: `tags`
Searchable keywords linked to generations.
| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | INTEGER (PK) | Unique identifier. |
| `generation_id` | INTEGER (FK) | Link to `generations`. |
| `tag_name` | TEXT | e.g., "Cyberpunk", "Portrait", "Sunset". |
| `source` | TEXT | "user" or "llm_auto". |

### Table: `prompt_library`
A curated collection of reusable styles and snippets.
| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | INTEGER (PK) | Unique identifier. |
| `label` | TEXT | User-friendly name (e.g., "Ghibli Style"). |
| `content` | TEXT | The actual prompt text snippet. |
| `category` | TEXT | "Style", "Character", "Lighting", etc. |

## 3. The LLM Auto-Tagging Workflow

The system utilizes the `llm_worker` to interpret and categorize Stable Diffusion prompts automatically.

1.  **Generation:** User generates an image.
2.  **Storage:** Orchestrator saves basic metadata (Prompt, Seed, Model) to the `generations` table.
3.  **Analysis (Background):** Orchestrator sends the prompt to the LLM with a system instruction:
    > "Analyze this image prompt. Extract 5 categories: Subject, Art Style, Mood, Lighting, and Color Palette. Return as a flat JSON array of keywords."
4.  **Enrichment:** The LLM-generated keywords are inserted into the `tags` table with `source='llm_auto'`.

**Example:**
- **Prompt:** "A robotic cat in a neon rainforest, vibrant colors, digital art style."
- **Auto-Tags:** `["Cat", "Robot", "Cyberpunk", "Rainforest", "Vibrant", "Digital Art", "Nature"]`.

## 4. Advanced Search Capabilities
With this structure, the WebUI can support complex queries:
- **Keyword Search:** Find images tagged "Nature" and "Portrait".
- **Similarity Search:** "Show me more images using the same model and seed range as this one."
- **Analytics:** "Which model have I used most this week?"

## 5. Implementation Roadmap

### Phase 1: Persistence
- Link `sqlite3` in `CMakeLists.txt`.
- Create database initialization logic on Orchestrator startup.
- Update `api_endpoints.cpp` to save every successful generation to the DB.

### Phase 2: History UI
- Create a new View in the WebUI to browse the database.
- Implement basic filtering (by Model, by Date).

### Phase 3: AI Intelligence
- Implement the "Background Tagger" service in the Orchestrator.
- Allow the user to "Re-Tag" existing history using the LLM.
- Add tag-based search to the Frontend.
