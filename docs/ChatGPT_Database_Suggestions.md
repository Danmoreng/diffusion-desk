SQLite 3 is a solid call for MystiCanvas, and there are a bunch of “DB design” upgrades that tend to pay off \*a lot\* in local-first creative tools like yours (gallery browsing, tags, lineage, reproducibility, and fast search). I’ll anchor these suggestions to the schema + workflows you already sketched (generations/tags/image\_tags/templates, Orchestrator as central DB host, import from `outputs/`). 



\## 1) Make the schema more future-proof with a “core columns + JSON params” pattern



You already have common columns (`prompt`, `seed`, `steps`, `cfg\_scale`, etc.). That’s great for filtering/sorting. 



But Stable Diffusion settings tend to grow: sampler/scheduler, denoise strength, clip skip, hires-fix settings, refiner settings, ControlNet(s), IP-Adapter, LoRAs, VAE choice, etc. If you model \*everything\* as columns, you’ll be migrating constantly.



A robust pattern:



\* Keep \*\*high-value filter columns\*\* as normal columns (timestamp, model\_id/hash, width/height, seed, steps, favorite, parent, etc.)

\* Add a `params\_json` column for “everything else”



Example:



```sql

ALTER TABLE generations ADD COLUMN params\_json TEXT; -- JSON string

```



Then store a JSON object like:



```json

{

&nbsp; "sampler": "dpmpp\_2m",

&nbsp; "scheduler": "karras",

&nbsp; "denoise": 0.45,

&nbsp; "lora": \[{"name":"foo","weight":0.8}],

&nbsp; "controlnet": \[{"model":"canny","weight":1.0}]

}

```



That keeps your DB stable, while still allowing you to surface new settings in the UI without a schema rewrite.



\## 2) Add the indexes that make gallery + filters feel instant



Your core tables are right, but \*indexes\* will decide whether the gallery is “snappy” at 10k+ images or feels sticky. 



Recommended indexes (starting point):



```sql

-- Fast sort + pagination by newest

CREATE INDEX IF NOT EXISTS idx\_generations\_timestamp\_id

ON generations(timestamp DESC, id DESC);



-- Fast filtering by model

CREATE INDEX IF NOT EXISTS idx\_generations\_model\_hash

ON generations(model\_hash);



-- Favorites view

CREATE INDEX IF NOT EXISTS idx\_generations\_favorite\_timestamp

ON generations(is\_favorite, timestamp DESC);



-- Lineage / parent lookups

CREATE INDEX IF NOT EXISTS idx\_generations\_parent\_uuid

ON generations(parent\_uuid);



-- Join performance

CREATE INDEX IF NOT EXISTS idx\_image\_tags\_tag

ON image\_tags(tag\_id, generation\_id);



CREATE INDEX IF NOT EXISTS idx\_image\_tags\_generation

ON image\_tags(generation\_id, tag\_id);

```



\### Keyset pagination (important!)



For an infinite scrolling gallery, prefer \*\*keyset pagination\*\* (a.k.a. seek pagination) over `OFFSET`, because `OFFSET` gets slower as the library grows.



Pattern:



\* “Give me the next 100 items older than (timestamp, id)”



```sql

SELECT \*

FROM generations

WHERE (timestamp < :ts)

&nbsp;  OR (timestamp = :ts AND id < :id)

ORDER BY timestamp DESC, id DESC

LIMIT 100;

```



This stays fast even at 100k+ rows.



\## 3) Tighten integrity: foreign keys, cascades, and “soft delete”



SQLite supports foreign keys, but you need to \*\*turn them on per connection\*\*:



```sql

PRAGMA foreign\_keys = ON;

```



Then consider improving relationships:



\### Use integer parent IDs internally (keep UUID for external)



Right now `parent\_uuid` references `generations(uuid)`. 

That works, but for performance and simplicity you can store both:



\* `parent\_id INTEGER` referencing `generations(id)`

\* keep `uuid` as the “public stable identifier”



\### Add ON DELETE behaviors



For example, if a generation is deleted, you probably want its tag links removed automatically:



```sql

CREATE TABLE image\_tags (

&nbsp; generation\_id INTEGER NOT NULL,

&nbsp; tag\_id INTEGER NOT NULL,

&nbsp; source TEXT DEFAULT 'user',

&nbsp; confidence REAL DEFAULT 1.0,

&nbsp; PRIMARY KEY(generation\_id, tag\_id),

&nbsp; FOREIGN KEY(generation\_id) REFERENCES generations(id) ON DELETE CASCADE,

&nbsp; FOREIGN KEY(tag\_id) REFERENCES tags(id) ON DELETE CASCADE

) WITHOUT ROWID;

```



(`WITHOUT ROWID` is nice for pure join tables.)



\### Soft delete (“Trash”) is super useful in creative apps



Instead of deleting rows (and risking “where did it go?” panic), add:



\* `deleted\_at DATETIME NULL`

\* filter it out by default



This also helps if the file disappears temporarily (external drive, etc.).



\## 4) Add a “files” table to handle thumbnails, previews, and future multi-output pipelines



Right now each generation has one `file\_path`. 

But you will likely end up with multiple artifacts per generation:



\* full-res PNG

\* thumbnail JPEG/WEBP

\* masked input, control images

\* upscaled version

\* metadata sidecar export

\* maybe even intermediate latents someday



Instead of adding more columns, model it cleanly:



```sql

CREATE TABLE generation\_files (

&nbsp; id INTEGER PRIMARY KEY AUTOINCREMENT,

&nbsp; generation\_id INTEGER NOT NULL,

&nbsp; kind TEXT NOT NULL,            -- 'final', 'thumb', 'preview', 'input', 'mask', ...

&nbsp; path TEXT NOT NULL,

&nbsp; sha256 TEXT,                   -- optional integrity/dedupe

&nbsp; bytes INTEGER,

&nbsp; width INTEGER,

&nbsp; height INTEGER,

&nbsp; created\_at DATETIME DEFAULT CURRENT\_TIMESTAMP,

&nbsp; FOREIGN KEY(generation\_id) REFERENCES generations(id) ON DELETE CASCADE,

&nbsp; UNIQUE(generation\_id, kind)

);



CREATE INDEX idx\_generation\_files\_generation\_kind

ON generation\_files(generation\_id, kind);

```



This pays off immediately once you add thumbnail caching + different preview sizes.



\## 5) Create a real “models” registry instead of only `model\_hash`



You currently store `model\_hash` on the generation row. 

That’s good, but you’ll want richer model metadata:



\* display name

\* path on disk

\* type (checkpoint, refiner, LoRA, VAE, embedding)

\* base family (SD1.5, SDXL, etc.)

\* version / hash

\* author/source metadata (optional)



Schema idea:



```sql

CREATE TABLE models (

&nbsp; id INTEGER PRIMARY KEY AUTOINCREMENT,

&nbsp; kind TEXT NOT NULL,        -- 'checkpoint','lora','vae','refiner',...

&nbsp; name TEXT NOT NULL,

&nbsp; file\_path TEXT,

&nbsp; hash TEXT UNIQUE,

&nbsp; metadata\_json TEXT

);



ALTER TABLE generations ADD COLUMN model\_id INTEGER;

-- and eventually prefer model\_id over model\_hash

```



This makes “filter by model” and “show me everything made with LoRA X” much easier.



\## 6) Full-text search: add FTS5 for prompts/notes with sync triggers



You mentioned prompt keyword search in the gallery. 

FTS is a huge quality-of-life feature.



Approach:



\* Add an FTS table with `prompt`, `negative\_prompt`, maybe `notes`

\* Use triggers so it stays in sync



Example layout:



```sql

CREATE VIRTUAL TABLE generations\_fts USING fts5(

&nbsp; prompt,

&nbsp; negative\_prompt,

&nbsp; content='generations',

&nbsp; content\_rowid='id'

);



-- Sync triggers (insert/update/delete)

```



Then searches become instant and feel “professional” even on big libraries.



\## 7) Tag quality: add aliases + normalization (prevents “cat vs feline” mess)



Your tagging plan (user tags + LLM auto-tags with `source` and `confidence`) is excellent. 

The one thing that bites later is synonyms and inconsistent casing/plurals.



Add:



\* a normalized tag key (`normalized\_name`)

\* a tag alias table (`tag\_aliases`) mapping “feline” → “cat”

\* optionally a “tag implication” system (advanced, but powerful): “portrait” implies “person”



Minimal:



```sql

ALTER TABLE tags ADD COLUMN normalized\_name TEXT;



CREATE TABLE tag\_aliases (

&nbsp; alias TEXT PRIMARY KEY,

&nbsp; tag\_id INTEGER NOT NULL,

&nbsp; FOREIGN KEY(tag\_id) REFERENCES tags(id) ON DELETE CASCADE

);

```



Then your auto-tagging can:



\* normalize candidate tags

\* map aliases to canonical tags

\* avoid polluting the tag list



\## 8) Add “collections” and “saved searches” (users love this)



A gallery becomes much more usable when users can:



\* create manual albums (collections)

\* create smart albums (saved filters/searches)



Tables:



```sql

CREATE TABLE collections (

&nbsp; id INTEGER PRIMARY KEY AUTOINCREMENT,

&nbsp; name TEXT NOT NULL,

&nbsp; kind TEXT NOT NULL DEFAULT 'manual',   -- 'manual' or 'smart'

&nbsp; query\_json TEXT,                      -- for smart collections: tags/model/date/text

&nbsp; created\_at DATETIME DEFAULT CURRENT\_TIMESTAMP

);



CREATE TABLE collection\_items (

&nbsp; collection\_id INTEGER NOT NULL,

&nbsp; generation\_id INTEGER NOT NULL,

&nbsp; PRIMARY KEY(collection\_id, generation\_id),

&nbsp; FOREIGN KEY(collection\_id) REFERENCES collections(id) ON DELETE CASCADE,

&nbsp; FOREIGN KEY(generation\_id) REFERENCES generations(id) ON DELETE CASCADE

) WITHOUT ROWID;

```



This tends to be a “didn’t know I needed it” feature until users have hundreds/thousands of images.



\## 9) A “jobs” table to orchestrate background tasks cleanly



You already have an Orchestrator and worker processes. 

A DB-backed job queue is a simple, extremely effective addition for:



\* LLM auto-tagging

\* thumbnail generation

\* embedding generation (semantic search)

\* rescans / integrity checks



Example:



```sql

CREATE TABLE jobs (

&nbsp; id INTEGER PRIMARY KEY AUTOINCREMENT,

&nbsp; kind TEXT NOT NULL,                 -- 'autotag','thumb','embed','rescan'

&nbsp; status TEXT NOT NULL,               -- 'queued','running','done','error'

&nbsp; priority INTEGER DEFAULT 0,

&nbsp; payload\_json TEXT NOT NULL,

&nbsp; error TEXT,

&nbsp; created\_at DATETIME DEFAULT CURRENT\_TIMESTAMP,

&nbsp; updated\_at DATETIME

);



CREATE INDEX idx\_jobs\_status\_priority

ON jobs(status, priority DESC, created\_at);

```



Then you can keep UI responsive and handle crashes/retries safely.



\## 10) Reproducibility + debugging: store “app + engine version” and an event log



For AI generation tools, users often ask: “why can’t I reproduce this output anymore?”



Two simple additions:



\* Store versions on each generation:



&nbsp; \* app version

&nbsp; \* SD engine version/build

&nbsp; \* model version/hash (you already have hash)

\* Store a small event log:



&nbsp; \* worker crashes

&nbsp; \* imports

&nbsp; \* migration results



This makes support and debugging far easier without sending any telemetry off-device (still privacy-first). 



\## 11) Operational best practices for SQLite in your Orchestrator



Because your Orchestrator is multi-threaded and the single gateway, it’s worth doing these from day one: 



\* \*\*One writer thread/connection\*\* (queue writes), many reader connections.

\* Always use transactions for batch operations (imports, tag inserts).

\* Set pragmatic connection PRAGMAs (typical desktop defaults):



&nbsp; \* `journal\_mode=WAL`

&nbsp; \* `foreign\_keys=ON`

&nbsp; \* `busy\_timeout=...` (prevents “database is locked” errors)

\* Keep a `schema\_migrations` table (or use `PRAGMA user\_version`) so upgrades are deterministic.



\## 12) Import / filesystem sync: make it resilient



You plan to import by scanning `outputs/` and reading sidecar JSONs. 

A few extra details make this rock-solid:



\* Track file integrity:



&nbsp; \* store `mtime`, `size`, optional `sha256`

\* Handle file moves:



&nbsp; \* if hash matches but path differs, update path rather than duplicating

\* Mark missing files:



&nbsp; \* set `missing\_at` instead of deleting rows



This avoids library corruption when users manually reorganize folders.



---



\### If you only implement 5 “extra” things beyond your current plan…



These give the biggest payoff fastest:



1\. \*\*Indexes + keyset pagination\*\* (gallery performance)

2\. \*\*FTS5\*\* for prompt search

3\. \*\*Jobs table\*\* (autotag/thumb/embed pipeline)

4\. \*\*Models registry\*\* (filtering + reproducibility)

5\. \*\*Tag alias/normalization\*\* (prevents taxonomy chaos)



If you want, I can also draft a “v1 schema” (DDL) that incorporates the above while staying close to what you already wrote, so you can drop it into the Orchestrator and start iterating immediately. 



