# Vibe Steps Progress Tracker

A RuneLite plugin for Group Ironman teams that follow a shared step-by-step
progression guide. Import any guide from a local JSON file, track your
progress per character, and share progress files with teammates.

The plugin is fully local — no external server is contacted.

## Features

- Sidebar panel showing the current step with description, required items,
  location, and quest links
- Required items colour-coded by location: **green** = in inventory or equipped,
  **white** = in bank, **cyan** = in GIM shared storage, **red** = not found
- World-map marker for the current step's location coordinates
- **Mark as Completed**, **← Back** (undo last completed step), **Skip →**,
  and **Move to TO-DO** buttons on every step card
- **TO-DO section** — defer a step to revisit later; shown below the upcoming
  list with a blue accent and an Activate button to restore it
- **Upcoming steps** — shows the next 3 steps (first one with a 50-word
  preview, the rest as their short TLDR title)
- **Progress bar** — orange segment for completed/skipped, blue segment for
  TO-DO steps
- **AFK suggestion** — click "Suggest AFK task" to get a skill-level-appropriate
  activity while you're not following the guide; Regenerate or Dismiss as needed
- Per-character progress saved to `.runelite/gim-progress-tracker/progress/`
- Optional auto-export to a shared folder (Dropbox, OneDrive, etc.) for
  teammates to see your progress

---

## Guide JSON format

Guides are plain JSON files. Load one with the **Import guide…** button in
the plugin panel, or set the path in the plugin config so it reloads
automatically on startup.

### Minimal example

```json
{
  "guideName": "Efficient GIM Starter",
  "author": "YourName",
  "version": "1.0",
  "chapters": [
    {
      "name": "Early Game",
      "sections": [
        {
          "name": "Tutorial Island",
          "steps": [
            {
              "id": 1,
              "tldr": "Complete Tutorial",
              "description": "Finish Tutorial Island. Choose your look, enable 2FA."
            }
          ]
        }
      ]
    }
  ]
}
```

### Full step schema

```json
{
  "id": 1,
  "tldr": "Get Rope",
  "description": "Grab a rope from the general store in Lumbridge.",
  "location": { "x": 3212, "y": 3219, "plane": 0 },
  "requiredItems": [
    { "itemId": 954, "quantity": 1 }
  ],
  "skipIfBanked": true,
  "questHelperLink": "https://oldschool.runescape.wiki/w/Waterfall_Quest",
  "questName": "Waterfall Quest"
}
```

| Field | Required | Description |
|---|---|---|
| `id` | Yes | Unique integer within the guide |
| `tldr` | No | 1–3 word title shown in the step card header and upcoming list. If omitted, falls back to "Step N". |
| `description` | Yes | Full step text. Supports `\n` for line breaks. |
| `location` | No | World coordinates `{ x, y, plane }`. Adds a world-map marker. |
| `requiredItems` | No | Array of `{ itemId, quantity }`. Item IDs are OSRS item IDs. The plugin checks inventory, equipped gear, bank, and GIM shared storage. |
| `skipIfBanked` | No | Boolean hint (not yet enforced in UI). |
| `questHelperLink` | No | URL opened by the "Open Quest Helper link" button on the step card. |
| `questName` | No | Canonical OSRS quest name. Adds Wiki and Copy-Name buttons for Quest Helper integration. Auto-detected from the description if not set. |

### Rules

- Every `id` must be unique within the guide. Duplicates cause the import to fail.
- Steps are shown in the order they appear in the JSON (chapters → sections → steps).
- Empty sections and chapters are silently dropped on import.

---

## Creating a new guide from scratch

1. Create a `.json` file with the structure shown above.
2. Organise steps into chapters (major milestones) and sections (sub-tasks
   within a milestone). Use as many or as few as you need.
3. Assign sequential integer IDs starting at `1`.
4. Add a short `tldr` (1–3 words) to each step — this is shown as the step
   card heading and in the upcoming list. If you skip it, the converter will
   auto-generate one (see below).
5. Import the guide in RuneLite via **Vibe Steps → Import guide…** or by
   setting the file path in the plugin config.

**Item IDs** can be looked up on the [OSRS Wiki item pages](https://oldschool.runescape.wiki)
(the ID is in the infobox) or via the RuneLite item search overlay.

**Location coordinates** can be found by enabling the RuneLite "Tile
Markers" or "Debug" overlay and hovering over the target tile.

---

## Using an existing guide in BRUHsailer format

Many popular GIM guides are published in BRUHsailer format — a JSON with
nested rich-text content segments. The converter script at
`tools/convert_bruhsailer.py` converts these into the Vibe Steps format.

### Step 1 — Export the BRUHsailer guide as JSON

Open the guide in your browser, use the site's export function, or save the
raw JSON from the network tab.

### Step 2 — Run the converter

```bash
python tools/convert_bruhsailer.py path/to/bruhsailer.json -o guides/my-guide.json
```

The converter will:
- Flatten rich-text content into plain strings
- Assign sequential step IDs
- Auto-generate a `tldr` from the first 3 meaningful words of each step
- Detect OSRS locations by keyword-matching the description
- Detect quest names and pull in quest links
- Parse `items_needed` metadata into `requiredItems` with item IDs

After conversion the script prints a summary, e.g.:

```
converted 312 steps (312 with auto-tldr, 198 with detected location,
  44 with quest-helper link, 61 with quest name, 87 with required items,
  3 empty steps dropped)
Tip: edit 'tldr' fields in the output JSON to customise the 1-3 word step headers.
```

### Step 3 — Review and tidy the output

The converter does its best, but some things need a human touch:

- **`tldr`** — auto-generated from the first words of the description. Edit
  any that are unclear (e.g. change `"Use Rope On"` to `"Cross Rockslide"`).
- **Locations** — keyword matching may miss or misplace coordinates for steps
  with ambiguous text. Check steps where the world-map marker looks wrong.
- **Required items** — only items in the built-in dictionary are resolved to
  IDs. Unknown items are silently dropped; add them manually if needed.

### Step 4 — Import into RuneLite

```
Vibe Steps panel → Import guide… → select guides/my-guide.json
```

Or set the path in the plugin config so it auto-loads on startup.

---

## Building & running (developers)

```bash
./gradlew run
```

This launches a development RuneLite client with the plugin pre-loaded.
Log in using the [Jagex Account instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
to test in-game behaviour. Compile checks alone are not sufficient — verify
each feature works in-game before considering it done.

```bash
./gradlew build
./gradlew shadowJar   # produces a standalone fat-jar for distribution
```

---

## Progress storage

| Data | Location |
|---|---|
| Per-character progress | `.runelite/gim-progress-tracker/progress/<name>_<guide>.json` |
| Auto-export (shared folder) | Configured in plugin settings; filename `<name>_progress.json` |

Progress files are plain JSON and can be inspected or reset by deleting them.
