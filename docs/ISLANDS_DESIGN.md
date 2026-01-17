Here’s how I’d approach “making Bootstrap look like JetBrains Islands” without rewriting your whole UI.



\## What you’re trying to match (the Islands “rules”)



From JetBrains’ own description + their theming guidelines, Islands is mostly about \*\*layout + surface hierarchy\*\*, not flashy colors:



\* \*\*“Islands” = separate working areas\*\* (editor/tool windows/panels) with \*\*clear separation + balanced spacing + rounded corners\*\*. (\[The JetBrains Blog]\[1])

\* \*\*Main window (canvas) vs islands (surfaces):\*\* in \*\*dark mode\*\*, the \*main window background is lighter\* than tool windows/editor; in \*\*light mode\*\*, it’s \*darker\*. They even recommend a \*\*minimum contrast ratio ~1.20:1\*\* between tool windows/editor and the main window background. (\[JetBrains Marketplace]\[2])

\* \*\*Borders are de-emphasized:\*\* sidebars around the main window (toolbar/stripes/status bar) have \*\*no border\*\*, and island borders should be basically invisible (border color = tool window background). (\[JetBrains Marketplace]\[2])

\* \*\*Geometry:\*\* default island radius is \*\*10px\*\* (their `Island.arc` value is double, but visual radius is ~10px). Spacing between islands is small but consistent (their “borderWidth” concept is essentially the gap). (\[JetBrains Marketplace]\[2])

\* \*\*Tabs:\*\* active tab should be instantly obvious via an \*\*underline + subtle background under the label\*\*. (\[JetBrains Marketplace]\[2])



Your current UI is already “IDE-like”, but the biggest differences vs Islands are:



1\. you need \*\*less “canvas showing through” between panels\*\* reduce the gaps,

2\. reduce \*\*strong flat blues\*\* on large surfaces (use accent mostly for \*states\*),

3\. unify \*\*radius + spacing system\*\* (Islands looks consistent everywhere),

4\. make \*\*active tab / selection\*\* states more “JetBrains-like” (underline + soft fill, not big blocks).



---



\## Plan to implement in a Vue + Bootstrap app



\### Phase 1 — Introduce a token layer (don’t start by hacking Bootstrap classes)



Create \*\*design tokens\*\* (CSS variables) for:



\* canvas background

\* island surface backgrounds (1–3 elevations)

\* separators/dividers

\* text (primary/muted/disabled)

\* accent + semantic colors

\* radius + spacing scale

\* focus ring



This gives you one place to tune until it “feels right”.



\### Phase 2 — Map tokens onto Bootstrap’s CSS variables (preferred)



Bootstrap 5.3+ exposes lots of CSS variables (`--bs-body-bg`, `--bs-border-radius`, etc). Override them \*\*after\*\* Bootstrap loads.



In Vue: import order in `main.ts` / `main.js`:



1\. bootstrap css

2\. your `islands.css` (token + overrides)

3\. any app-specific tweaks



Then set theme mode on `<html>`:



\* `<html data-bs-theme="dark">` / `"light"`

\* (optional) add your own: `data-ui="islands"` to scope overrides.



\### Phase 3 — Make the layout “islands-first”



This is the biggest visual shift:



\* Set the \*\*page background\*\* to your \*canvas\* token.

\* Wrap major regions (sidebar, center panel, right panel, bottom panel) in an `.island` wrapper.

\* Use \*\*gap\*\* between islands so canvas shows through (JetBrains explicitly emphasizes separation). (\[The JetBrains Blog]\[1])

\* Remove “outer window borders” around global bars to match their “no sidebar borders” guidance. (\[JetBrains Marketplace]\[2])



\### Phase 4 — Component-level alignment (small, targeted overrides)



Focus on the pieces users constantly see:



\* nav tabs (underline + background under label)

\* buttons (less saturated, more “quiet” defaults; accent mainly for primary + focus)

\* inputs (filled surfaces, subtle borders)

\* dropdowns/popovers/modals (slightly elevated surface)

\* list selections (soft fill + readable text)

\* dividers/resizers (more distinct on hover; JetBrains mentions resizing being easier due to more distinct borders). (\[The JetBrains Blog]\[1])



\### Phase 5 — QA loop: screenshot matching



Do side-by-side screenshots and tune:



\* canvas/surface contrast (hit the ~1.20:1 “feel”) (\[JetBrains Marketplace]\[2])

\* radius consistency (aim ~10px everywhere) (\[JetBrains Marketplace]\[2])

\* remove “random borders”, rely on spacing + subtle separators (\[JetBrains Marketplace]\[2])

\* active tab / selection legibility (\[JetBrains Marketplace]\[2])



---



\## Suggested Islands-like palette (start here, then tune)



JetBrains doesn’t publish a full web palette, but there are faithful ports that expose key colors. A VS Code “Islands” port lists core hues like background `#191a1c`, foreground `#bcbec4`, blue `#56a8f5`, green `#6aab73`, cyan `#2aacb8`, orange `#cf8e6d`, muted `#7a7e85`. (\[GitHub]\[3])



Use those as \*\*semantic/accent\*\*, and combine with an Islands-style \*\*surface stack\*\* (canvas slightly different from islands, per JetBrains guidance). (\[JetBrains Marketplace]\[2])



\### Dark mode tokens (practical web approximation)



\* \*\*Canvas (MainWindow.background):\*\* `#33313d` (slightly lighter than surfaces)

\* \*\*Island surface (ToolWindow/editor-ish):\*\* `#28292f`

\* \*\*Deep surface (inputs/inner panes):\*\* `#1c1e21` (close to the port’s `#191a1c`) (\[GitHub]\[3])

\* \*\*Elevated (menus/popovers):\*\* `#30323a`

\* \*\*Divider/separator:\*\* `rgba(255,255,255,.06)`

\* \*\*Text primary:\*\* `#bcbec4` (\[GitHub]\[3])

\* \*\*Text muted:\*\* `#7a7e85` (\[GitHub]\[3])

\* \*\*Primary accent:\*\* `#56a8f5` (\[GitHub]\[3])

\* \*\*Success:\*\* `#6aab73` (\[GitHub]\[3])

\* \*\*Info:\*\* `#2aacb8` (\[GitHub]\[3])

\* \*\*Warning:\*\* `#cf8e6d` (\[GitHub]\[3])



\### Light mode tokens (align with “canvas darker than islands”)



JetBrains explicitly recommends the opposite relationship in light mode. (\[JetBrains Marketplace]\[2])



\* \*\*Canvas:\*\* `#e6e8ee` (darker than islands)

\* \*\*Island surface:\*\* `#ffffff`

\* \*\*Alt surface:\*\* `#f5f6f8`

\* \*\*Divider:\*\* `rgba(0,0,0,.08)`

\* \*\*Text primary:\*\* `#000000` (\[GitHub]\[3])

\* \*\*Text muted:\*\* `#8c8c8c` (\[GitHub]\[3])

\* \*\*Primary accent:\*\* `#1750eb` (or `#0033b3` if you want deeper) (\[GitHub]\[3])



---



\## How to “overstyle Bootstrap” cleanly (without fighting it)



\### 1) Override Bootstrap CSS variables globally



Create something like `src/styles/islands.css` (or `.scss`) and set:



\* base geometry:



&nbsp; \* `--bs-border-radius: 10px` (Islands radius) (\[JetBrains Marketplace]\[2])

&nbsp; \* tweak `--bs-border-radius-sm`, `--bs-border-radius-lg` to stay consistent

\* base colors:



&nbsp; \* `--bs-body-bg` ← canvas

&nbsp; \* `--bs-secondary-bg` ← island surface

&nbsp; \* `--bs-tertiary-bg` ← elevated

&nbsp; \* `--bs-border-color` ← divider

&nbsp; \* `--bs-body-color` ← text

\* link + focus:



&nbsp; \* `--bs-link-color` ← accent

&nbsp; \* `--bs-focus-ring-color` ← accent with alpha



This gets you 60–70% of the way quickly.



\### 2) Add one utility class: `.island`



Instead of trying to restyle every component, define a wrapper:



\* background: surface token

\* radius: 10px

\* border: none (or 1px transparent)

\* spacing: your “island gap” is handled by layout `gap` on the parent grid



This directly matches “organized spaces / separation between working areas”. (\[The JetBrains Blog]\[1])



\### 3) Patch only the components where Bootstrap differs most from Islands



Give your coding agent a short target list:



\*\*Tabs\*\*



\* Use underline active indicator + subtle background under tab label (match JetBrains “underlined tab background” concept). (\[JetBrains Marketplace]\[2])



\*\*Buttons\*\*



\* Reduce borders; use quieter secondary buttons (surface fill)

\* Primary button: solid accent, but keep hover/active states gentle



\*\*Forms\*\*



\* Filled inputs (deep surface), minimal border until focus

\* Focus ring uses accent (not bright blue outline everywhere)



\*\*Dropdowns/Modals/Popovers\*\*



\* Elevated surface + subtle shadow

\* No strong borders



\*\*Sidebars / toolbars\*\*



\* Remove outer borders (JetBrains: no borders around main window stripes/toolbar/status). (\[JetBrains Marketplace]\[2])

\* Use hover backgrounds and small selection pills instead.



---



\## A concrete “handoff checklist” for your coding agent



1\. Add `islands.css` with tokens (dark/light) + Bootstrap variable overrides

2\. Ensure `<html data-bs-theme="dark|light">` is the only global theme switch

3\. Wrap top-level panels in `.island` and add layout `gap` so canvas shows through

4\. Restyle:



&nbsp;  \* `.nav-tabs` / `.nav-link` (underline + label background)

&nbsp;  \* `.btn`, `.btn-primary`, `.btn-outline-\*` (quiet, low-border)

&nbsp;  \* `.form-control`, `.form-select`, `.input-group` (filled, subtle focus)

&nbsp;  \* `.dropdown-menu`, `.modal-content`, `.popover` (elevated surface)

5\. Remove any “hard borders” on global bars; rely on spacing \& separators (\[JetBrains Marketplace]\[2])

6\. Screenshot compare + tune deltas (especially canvas vs island contrast) (\[JetBrains Marketplace]\[2])



If you want, paste (or upload) \*\*one representative Vue component\*\* (your main layout shell) and I’ll mark \*exactly\* where I’d introduce `.island`, where the gaps should go, and which Bootstrap components to override first for maximum Islands payoff.



\[1]: https://blog.jetbrains.com/platform/2025/12/meet-the-islands-theme-the-new-default-look-for-jetbrains-ides/ "https://blog.jetbrains.com/platform/2025/12/meet-the-islands-theme-the-new-default-look-for-jetbrains-ides/"

\[2]: https://plugins.jetbrains.com/docs/intellij/supporting-islands-theme.html "https://plugins.jetbrains.com/docs/intellij/supporting-islands-theme.html"

\[3]: https://github.com/gu-xiaohui/islands-theme "https://github.com/gu-xiaohui/islands-theme"



