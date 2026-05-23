# Vibe Steps Progress Tracker

A RuneLite plugin for following shared step-by-step progression guides.
Works for any account type — Group Ironman, regular accounts, or any group
of friends who want to track progress together.

Import a guide from a local JSON file, track your progress per character,
and optionally share your progress and location with teammates through a
shared folder (Dropbox, OneDrive, a network share, etc.).

The plugin is fully local — no external server is contacted.

## Features

### Step tracking
- Sidebar panel showing the current step with description, required items,
  location, and quest links
- Required items colour-coded by location: **green** = in inventory or
  equipped, **white** = in bank, **cyan** = in GIM shared storage,
  **red** = not found
- World-map marker for the current step's location coordinates
- **Mark as Completed**, **← Back** (undo last completed step), **Skip →**,
  and **Move to TO-DO** buttons on every step card

### Progress management
- **TO-DO section** — defer a step to revisit later; shown below the upcoming
  list with a blue accent and an Activate button to restore it
- **Upcoming steps** — shows the next 3 upcoming tasks: the first with a
  50-word description preview, the rest as their short TLDR title
- **Progress bar** — orange segment for completed/skipped steps, blue
  segment for TO-DO steps, with a `done • todo • left` counter below

### AFK suggestions
- Click **Suggest AFK task** to get a skill-level-appropriate activity to do
  while not actively following the guide
- Tasks are filtered by your current skill levels — nothing is suggested
  that you can't do yet
- **Regenerate** to get a different suggestion, **Dismiss** to hide it

### Teammates panel
- A second nav button (**GRP**) opens a separate Teammates panel
- Reads `*_progress.json` files from the configured shared folder and shows
  a card per teammate with:
  - Their name, optional status message, and when the file was last updated
  - Their current step (breadcrumb, TLDR heading, 20-word description preview)
  - A **Map** button that places a blue world-map marker at their step's
    location — works even if they are on a different guide
  - Progress stats: percentage done, step count, TO-DO count
  - Live location indicator if they have location sharing enabled
- If a teammate uses a different guide, their guide name and raw step counts
  are shown alongside their current step snapshot
- **Refresh** button re-reads the folder without restarting
- **Share my location** checkbox — periodically writes your world coordinates
  to the shared folder (see [Privacy](#privacy) below)
- **Status field** — type a short message (up to 60 characters) to display to
  your teammates; it is exported to the shared folder immediately when set
- Step info is available to teammates because the plugin snapshots your
  current step's text and coordinates into the exported file on every
  auto-export

### Other
- Per-character progress saved to `.runelite/gim-progress-tracker/progress/`
- Optional auto-export to a shared folder (Dropbox, OneDrive, etc.) so
  teammates can see your current step in the Teammates panel
- Compatible with all account types — not limited to Group Ironman

---

## Guide JSON format

Guides are plain JSON files. Load one with the **Import guide…** button in
the plugin panel, or set the path in the plugin config so it reloads
automatically on startup.

### Minimal example

```json
{
  "guideName": "Efficient Starter Guide",
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
| `location` | No | World coordinates `{ x, y, plane }`. Adds a world-map marker and enables the Map button in the Teammates panel. |
| `requiredItems` | No | Array of `{ itemId, quantity }`. Item IDs are OSRS item IDs. The plugin checks inventory, equipped gear, bank, and GIM shared storage. |
| `skipIfBanked` | No | Boolean hint (not yet enforced in UI). |
| `questHelperLink` | No | URL opened by the "Open Quest Helper link" button on the step card. |
| `questName` | No | Canonical OSRS quest name. Adds Wiki and Copy-Name buttons for Quest Helper integration. Auto-detected from the description if not set. |

### Rules

- Every `id` must be unique within the guide. Duplicates cause the import to fail.
- Steps are shown in the order they appear in the JSON (chapters → sections → steps).
- Empty sections and chapters are silently dropped on import.

---

## Creating a guide from scratch

Guides are JSON files. You can write one by hand, convert an existing
BRUHsailer guide (see below), or start from the minimal example above.

## Contributing

Create a fork of this repository, put your guide into the `guides/` directory and create a PR.
If you want to create a new feature just do it as you normally would in git and create a PR.

### Structure

```
Guide
└── chapters  (major milestones, e.g. "Early Game", "Mid Game")
    └── sections  (sub-topics, e.g. "Questing", "Skilling")
        └── steps  (individual tasks)
```

Use **chapters** for large phases of the game. Use **sections** to group
related tasks within a phase. There is no hard limit on depth or count — use
as many or as few as the content warrants.

### Writing good steps

The most important principle is **one clear action per step**. Avoid combining
two separate tasks into one step because the player cannot mark half a step done.

**Keep the `tldr` to 1–3 words** — it appears as the step card heading and in
the upcoming-steps list. Good examples: `"Get Rope"`, `"Waterfall Quest"`,
`"Train to 40 Att"`. Bad examples: `"Go to Barbarian Village and get things"`
(too long), `"Step 12"` (meaningless).

**Write the `description` for a first-time reader** — assume they know OSRS
but not your specific routing. Include:
- What to do and where ("Grab a rope from the general store in Lumbridge")
- Why it matters if it is not obvious ("needed for Waterfall Quest later")
- Any gotchas ("buy 2 — one is consumed on the quest")

**Add a `location`** whenever the step has a clear destination tile. This
places a world-map marker and enables the Map button in the Teammates panel.
Coordinates are `{ x, y, plane }` in world tiles (plane 0 = ground floor).

```json
"location": { "x": 3212, "y": 3219, "plane": 0 }
```

Coordinates can be found by:
- Enabling the RuneLite **Tile Markers** plugin and hovering the target tile
- Using the **Debug** overlay (right-click → "Walk here" shows coords in chat)
- Copying from the [OSRS Wiki map](https://oldschool.runescape.wiki/w/Map)

**Add `requiredItems`** for steps where the player must have specific items.
The plugin checks inventory, equipped gear, bank, and group storage and
colour-codes each item. Item IDs are on the [OSRS Wiki](https://oldschool.runescape.wiki)
in the item infobox, or visible in the RuneLite item search overlay.

```json
"requiredItems": [
  { "itemId": 954, "quantity": 1 },
  { "itemId": 590, "quantity": 1 }
]
```

**Link quests** with `questHelperLink` (a wiki or Quest Helper URL) and
`questName` (the exact OSRS quest name). The plugin auto-detects quest names
from the description, but setting `questName` explicitly is more reliable.

```json
"questName": "Waterfall Quest",
"questHelperLink": "https://oldschool.runescape.wiki/w/Waterfall_Quest"
```

### Step ID rules

- Every `id` must be a **unique integer** within the guide.
- IDs do not need to be sequential, but gaps can make debugging harder.
- Duplicates cause the import to fail with an error.

### Common pitfalls

| Mistake | Fix |
|---|---|
| One giant step covering several tasks | Split into one step per action |
| `tldr` is the full description | Keep it to 1–3 words; save detail for `description` |
| No `location` on a travel step | Add coordinates so the world-map arrow helps |
| Wrong item ID (e.g. noted vs unnoted) | Double-check on the wiki — noted items have different IDs |
| Missing `id` field | Every step must have a unique `id` |
| Duplicate `id` across steps | IDs must be globally unique within the guide file |

### Recommended workflow

1. Plan your chapters and sections on paper or in a spreadsheet first.
2. Write steps in small batches and import after each batch to catch errors early.
3. Test by running the guide yourself from step 1 — it is easy to miss an
   item or location that seemed obvious when writing.
4. Share the `.json` file with your team via the shared folder or directly.

### Import

```
Vibe Steps panel → Import guide… → select your .json file
```

Or set the **Guide file path** in plugin settings so it reloads automatically
on each RuneLite startup.

---

## Using an existing guide in BRUHsailer format

Many popular guides are published in BRUHsailer format — a JSON with
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

## Team sync (shared folder)

The shared folder feature lets your whole team see each other's progress
without any server — just a folder you all have access to (Dropbox, OneDrive,
a network share, etc.). Works for any group of players, not just Group Ironman.

### Setup

1. In plugin settings → **Team sync**, set **Shared folder path** to the
   shared folder every teammate can read from.
2. Enable **Auto-export progress**. Whenever you mark a step, your progress
   file is written to that folder as `<name>_progress.json`.
3. Have each teammate do the same, writing to the same folder.

### What gets exported

Each exported file contains your completed/skipped/todo step IDs, plus a
snapshot of your current step's breadcrumb, TLDR, description, and location.
This snapshot lets teammates read your current step in the Teammates panel
even if they haven't imported the same guide.

### Viewing teammates

Click the **GRP** nav button to open the Teammates panel. Each teammate's
card shows:

- Their name, optional status message, and when the file was last updated
- Progress percentage and step count (if they share the same guide)
  or their guide name and raw step count (if they use a different one)
- Their current step: chapter › section breadcrumb, step heading, and a
  short description preview
- A **Map** button (if the step has coordinates) that places a blue marker
  on your world map
- A live location indicator if they have location sharing enabled

Hit **Refresh** to re-read the folder at any time.

> **Note:** The current step info in the Teammates panel only updates when
> a teammate marks a step while auto-export is enabled. Teammates who have
> never exported, or who export infrequently, may show stale or no step info.

### Setting your status

Type a short message (up to 60 characters) in the **Status** field at the top
of the Teammates panel. It is exported to the shared folder immediately and
appears below your name on each teammate's card. Clear the field to remove it.

### Live location sharing

Enable **Share my location** in the Teammates panel header. Your world
coordinates are then written to the shared folder roughly every 12 seconds
and shown on teammate cards as a green **● Live** indicator with a **Map**
button.

---

## Privacy

All data shared by this plugin stays within the shared folder you configure.

- **No external server is involved.** The plugin never contacts any third-party
  service.
- **Progress files** are written to your shared folder when you mark a step
  (if auto-export is enabled).
- **Live location** is written to your shared folder periodically while the
  **Share my location** toggle is on. Only people with read access to that
  folder can see your coordinates.
- **Status messages** are exported to your shared folder immediately when you
  set them.
- To stop sharing any data, disable the relevant toggle or clear the shared
  folder path in plugin settings.

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
| Per-character progress | `.runelite/gim-progress-tracker/progress/<name>_progress.json` |
| Auto-export (shared folder) | Configured in plugin settings; filename `<name>_progress.json` |

Progress files are plain JSON and can be inspected or reset by deleting them.
