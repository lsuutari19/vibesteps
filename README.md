# GIM Progress Tracker

A RuneLite plugin that helps Group Ironman teams follow custom, step-by-step
progression guides. Guides are imported from local JSON files; progress is
saved locally and can be shared with teammates by dropping the exported file
into a shared folder (Dropbox, OneDrive, etc.).

The plugin is local-only — no external server is contacted.

## Features (Phase 1)

- Sidebar panel showing current chapter / section / step
- Import any guide from a `.json` file on disk
- World-map marker and in-scene tile highlight for the current step's location
- Mark a step complete, or skip it
- Per-character progress saved to `.runelite/gim-progress-tracker/progress/`

## Guide JSON format

```json
{
  "guideName": "Efficient GIM Starter",
  "author": "PlayerName",
  "version": "1.0",
  "chapters": [
    {
      "name": "Early Game",
      "sections": [
        {
          "name": "Waterfall Quest Prep",
          "steps": [
            {
              "id": 1,
              "description": "Grab a rope from the general store",
              "location": { "x": 3212, "y": 3219, "plane": 0 },
              "requiredItems": [{ "itemId": 954, "quantity": 1 }],
              "skipIfBanked": true,
              "questHelperLink": null
            }
          ]
        }
      ]
    }
  ]
}
```

Step `id` values must be unique within a guide.

## Building

```
./gradlew build
```

The `run` task launches a development RuneLite client with the plugin
preloaded — useful only for compile-checking, since actual gameplay must be
verified by the user.
