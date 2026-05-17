#!/usr/bin/env python3
"""
Convert a BRUHsailer-format guide JSON into the Vibe Steps Progress Tracker format.

Usage:
    python tools/convert_bruhsailer.py path/to/bruhsailer.json -o out.json

The BRUHsailer schema stores step text as an array of rich-text segments
({"text": "...", "bold": ..., "url": ..., "isLink": ...}). This script:

  - flattens each step's `content` (and any `nestedContent`) into a plain string
  - assigns sequential, unique integer step IDs
  - pulls the first hyperlink it finds into `questHelperLink`
  - heuristically fills `location` by keyword-matching the description against a
    built-in OSRS location dictionary (last mention wins, since that is usually
    the destination after movement verbs like "go to")
  - normalises curly/smart apostrophes before keyword matching so names like
    "Witch's Potion" and "Tarn's Lair" match dictionary entries written with
    straight apostrophes
  - drops empty steps, sections, and chapters
  - validates the result against the same rules GuideImporter enforces
"""

import argparse
import json
import sys
from typing import Optional, Tuple


def _norm(text: str) -> str:
    """Lowercase and replace curly/smart apostrophes with straight ones so
    dictionary keys (straight ') match source text (often curly U+2018/U+2019)."""
    return (
        text.lower()
        .replace("’", "'")   # right single quotation mark
        .replace("‘", "'")   # left single quotation mark
        .replace("ʼ", "'")   # modifier letter apostrophe
        .replace("`", "'")   # grave accent used as apostrophe
    )


# (x, y, plane) — OSRS world coordinates for common landmarks.
# Lowercase straight-apostrophe keys; longest match wins on position ties.
LOCATIONS = {
    # ── Cities & towns ──────────────────────────────────────────────────────────
    "tutorial island":        (3094, 3107, 0),
    "lumbridge swamp":        (3222, 3169, 0),
    "lumbridge":              (3222, 3218, 0),
    "draynor manor":          (3107, 3354, 0),
    "draynor village":        (3093, 3243, 0),
    "draynor":                (3093, 3243, 0),
    "varrock":                (3210, 3424, 0),
    "edgeville":              (3087, 3499, 0),
    "barbarian village":      (3082, 3420, 0),
    "falador":                (2965, 3380, 0),
    "rimmington":             (2956, 3216, 0),
    "port sarim":             (3024, 3217, 0),
    "al kharid":              (3293, 3174, 0),
    "al-kharid":              (3293, 3174, 0),
    "burthorpe":              (2898, 3540, 0),
    "taverley":               (2887, 3424, 0),
    "catherby":               (2816, 3434, 0),
    "seers' village":         (2725, 3491, 0),
    "seers village":          (2725, 3491, 0),
    "camelot":                (2757, 3477, 0),
    "east ardougne":          (2662, 3305, 0),
    "west ardougne":          (2528, 3300, 0),
    "ardougne":               (2662, 3305, 0),
    "yanille":                (2606, 3093, 0),
    "lletya":                 (2348, 3163, 0),
    "prifddinas":             (3203, 6105, 0),
    "tirannwn":               (2236, 3253, 0),
    "isafdar":                (2236, 3253, 0),
    "tree gnome stronghold":  (2461, 3447, 0),
    "gnome stronghold":       (2461, 3447, 0),
    "tree gnome village":     (2541, 3170, 0),
    "feldip hills":           (2542, 2961, 0),
    "fossil island":          (3760, 3879, 0),
    "karamja":                (2926, 3144, 0),
    "brimhaven":              (2757, 3178, 0),
    "shilo village":          (2852, 2954, 0),
    "tai bwo wannai":         (2790, 3060, 0),
    "ape atoll":              (2784, 2783, 0),
    "rellekka":               (2657, 3676, 0),
    "fremennik":              (2657, 3676, 0),
    "waterbirth island":      (2546, 3756, 0),
    "miscellania":            (2502, 3851, 0),
    "etceteria":              (2585, 3851, 0),
    "neitiznot":              (2336, 3805, 0),
    "jatizso":                (2410, 3805, 0),
    "lunar isle":             (2117, 3915, 0),
    "morytania":              (3422, 3461, 0),
    "canifis":                (3493, 3489, 0),
    "port phasmatys":         (3676, 3492, 0),
    "mort'ton":               (3494, 3290, 0),
    "burgh de rott":          (3493, 3211, 0),
    "darkmeyer":              (3590, 3337, 0),
    "great kourend":          (1641, 3673, 0),
    "kourend":                (1641, 3673, 0),
    "hosidius":               (1744, 3525, 0),
    "lovakengj":              (1500, 3760, 0),
    "shayzien":               (1500, 3624, 0),
    "piscarilius":            (1815, 3700, 0),
    "arceuus":                (1715, 3733, 0),
    "witchhaven":             (2836, 3350, 0),
    "pollnivneach":           (3356, 2956, 0),
    "nardah":                 (3426, 2891, 0),
    "sophanem":               (3298, 2781, 0),
    "keldagrim":              (2727, 3700, 0),
    "weiss":                  (2846, 3938, 0),
    "ferox enclave":          (3130, 3637, 0),
    "ferox":                  (3130, 3637, 0),
    "isle of souls":          (2199, 2876, 0),
    "port khazard":           (2658, 3151, 0),
    "mount quidamortem":      (1244, 3559, 0),
    "mount kebos":            (1244, 3559, 0),
    "ruins of uzer":          (3499, 3088, 0),
    "uzer":                   (3499, 3088, 0),

    # ── Guilds & notable buildings ───────────────────────────────────────────────
    "grand exchange":         (3164, 3486, 0),
    "fishing guild":          (2611, 3392, 0),
    "cooks' guild":           (3145, 3445, 0),
    "wizards' tower":         (3105, 3162, 0),
    "wizards tower":          (3105, 3162, 0),
    "monastery":              (3052, 3490, 0),
    "trollheim":              (2916, 3625, 0),
    "death plateau":          (2898, 3593, 0),
    "fight arena":            (2575, 3155, 0),
    "khazard battlefield":    (2522, 3252, 0),
    "bandit camp":            (3168, 2981, 0),
    "castle wars":            (2440, 3083, 0),
    "warriors' guild":        (2879, 3540, 0),
    "barbarian outpost":      (2538, 3567, 0),
    "rogues' den":            (3043, 4972, 0),
    "thieves' guild":         (3050, 3953, 0),
    "myths' guild":           (2461, 2851, 0),
    "crafting guild":         (2933, 3287, 0),
    "mining guild":           (3046, 9764, 0),
    "ranging guild":          (2658, 3439, 0),
    "magic guild":            (2700, 3406, 0),
    "legends' guild":         (2729, 3349, 0),
    "heroes' guild":          (2901, 3510, 0),
    "stronghold of security": (3081, 3420, 0),
    "lighthouse":             (2509, 3641, 0),
    "clock tower":            (2567, 3238, 0),
    "slayer tower":           (3428, 3550, 0),
    "dig site":               (3314, 3360, 0),
    "digsite":                (3314, 3360, 0),
    "paterdomus":             (3440, 3474, 0),
    "ancient cavern":         (1769, 5366, 0),
    "edgeville dungeon":      (3097, 9872, 0),
    "coal trucks":            (2585, 3481, 0),
    "tarn's lair":            (3165, 4603, 0),
    "haunted mine":           (3440, 3232, 0),
    "eagle's eyrie":          (2325, 3494, 0),
    "eagles' eyrie":          (2325, 3494, 0),

    # ── Skilling spots ───────────────────────────────────────────────────────────
    "blast furnace":          (1948, 4957, 0),
    "motherlode mine":        (3760, 5666, 0),
    "blast mine":             (1497, 3832, 0),
    "aerial fishing":         (1481, 3784, 0),
    "otto's grotto":          (2501, 3488, 0),
    "barbarian fishing":      (2501, 3488, 0),
    "ourania":                (2454, 3232, 0),
    "zmi":                    (2454, 3232, 0),
    "daeyalt":                (3590, 3337, 0),

    # ── Minigames ────────────────────────────────────────────────────────────────
    "wintertodt":             (1633, 3974, 0),
    "barbarian assault":      (2540, 3568, 0),
    "soul wars":              (2167, 2975, 0),
    "pest control":           (2657, 2660, 0),
    "void knights' outpost":  (2657, 2660, 0),
    "hallowed sepulchre":     (3655, 9882, 0),
    "mage training arena":    (3364, 3316, 0),
    "guardians of the rift":  (3628, 9514, 0),
    "pyramid plunder":        (3298, 2781, 0),
    "barrows":                (3565, 3314, 0),
    "zanaris":                (2452, 4473, 0),

    # ── Wilderness ───────────────────────────────────────────────────────────────
    "mage arena":             (3104, 3934, 0),
    "chaos altar":            (2946, 3820, 0),
    "demonic ruins":          (3290, 3870, 0),
    "fountain of rune":       (3380, 3906, 0),
    "wilderness resource arena": (3184, 3944, 0),
    "wilderness agility course": (3001, 3934, 0),

    # ── Abbreviations & shorthands ──────────────────────────────────────────────
    "ardy":                   (2662, 3305, 0),   # Ardougne shorthand
    "zeah":                   (1641, 3673, 0),   # Zeah continent = Great Kourend
    "misc":                   (2502, 3851, 0),   # Miscellania shorthand

    # ── Wilderness bosses ────────────────────────────────────────────────────────
    "artio":                  (3187, 3879, 0),
    "spindel":                (3307, 10256, 0),
    "vet'ion":                (3204, 3853, 0),
    "vetion":                 (3204, 3853, 0),
    "calvar'ion":             (3307, 10256, 0),
    "callisto":               (3284, 3851, 0),

    # ── Activities identified by item/reward name ────────────────────────────────
    "fire cape":              (2444, 5170, 0),   # Fight Caves
    "infernal cape":          (2445, 5170, 0),   # Inferno
    "corrupted gauntlet":     (3203, 6105, 0),   # Prifddinas
    "gauntlet":               (3203, 6105, 0),   # Prifddinas
    "chompy":                 (2542, 2961, 0),   # Feldip Hills
    "rantz":                  (2631, 2978, 0),   # Rantz NPC, Feldip Hills
    "sandstone quarry":       (3164, 2909, 0),   # Kharidian desert
    "sandstone":              (3164, 2909, 0),   # Kharidian desert quarry
    "paddewwa":               (3097, 9872, 0),   # Destination of Paddewwa teleport

    # ── Bosses & raids ───────────────────────────────────────────────────────────
    "zulrah":                 (2268, 3073, 0),
    "vorkath":                (2270, 4054, 0),
    "theatre of blood":       (3233, 4305, 0),
    "tob":                    (3233, 4305, 0),
    "chambers of xeric":      (1232, 3577, 0),
    "cox":                    (1232, 3577, 0),
    "tombs of amascut":       (3282, 9140, 0),
    "toa":                    (3282, 9140, 0),
    "god wars dungeon":       (2923, 3738, 0),
    "god wars":               (2923, 3738, 0),
    "gwd":                    (2923, 3738, 0),
    "corporeal beast":        (2977, 4383, 1),
    "cerberus":               (1240, 1255, 0),
    "alchemical hydra":       (1363, 10254, 0),
    "catacombs of kourend":   (1665, 10053, 0),
    "kraken cove":            (2280, 10017, 0),
    "inferno":                (2445, 5170, 0),
    "fight caves":            (2444, 5170, 0),
    "fight cave":             (2444, 5170, 0),
    "tzhaar":                 (2479, 5175, 0),
    "demonic gorillas":       (2082, 5716, 0),
    "mm2 tunnels":            (2082, 5716, 0),
}

# Quest start NPC tiles — checked before LOCATIONS so a specific quest name
# beats a generic city mention in the same step.
QUEST_LOCATIONS = {
    # ── F2P / early quests ───────────────────────────────────────────────────────
    "cook's assistant":       (3207, 3214, 0),
    "rune mysteries":         (3210, 3422, 0),
    "x marks the spot":       (3231, 3239, 0),
    "the restless ghost":     (3242, 3208, 0),
    "restless ghost":         (3242, 3208, 0),
    "misthalin mystery":      (3232, 3208, 0),
    "demon slayer":           (3215, 3424, 0),
    "imp catcher":            (3104, 3163, 0),
    "doric's quest":          (2951, 3450, 0),
    "witch's potion":         (2967, 3380, 0),
    "romeo & juliet":         (3211, 3424, 0),
    "sheep shearer":          (3189, 3273, 0),
    "ernest the chicken":     (3109, 3353, 0),
    "vampire slayer":         (3097, 3261, 0),
    "pirate's treasure":      (3045, 3204, 0),
    "dragon slayer":          (3079, 3508, 0),
    # ── Members quests ───────────────────────────────────────────────────────────
    "sheep herder":           (2543, 3398, 0),
    "hazeel cult":            (2625, 3273, 0),
    "tribal totem":           (2600, 3279, 0),
    "sea slug":               (2836, 3350, 0),
    "the golem":              (3499, 3088, 0),
    "clock tower quest":      (2567, 3238, 0),
    "between a rock":         (2731, 3713, 0),
    "mountain daughter":      (2802, 3659, 0),
    "lunar diplomacy":        (2626, 3692, 0),
    "the hand in the sand":   (2540, 3095, 0),
    "hand in the sand":       (2540, 3095, 0),
    "ghosts ahoy":            (3679, 3493, 0),
    "bone voyage":            (3760, 3879, 0),
    "recipe for disaster":    (3207, 3214, 0),
    "desert treasure":        (3168, 2981, 0),
    "spirits of the elid":    (3426, 2891, 0),
    "shadow of the storm":    (3291, 3077, 0),
    "forgettable tale":       (2721, 10166, 0),
    "priest in peril":        (3440, 3474, 0),
    "beneath cursed sands":   (3282, 2901, 0),
    "into the tombs":         (3282, 9140, 0),
    "nature spirit":          (3442, 3339, 0),
    "rag and bone man":       (3230, 3204, 0),
    "in search of the myreque": (3490, 3489, 0),
    "making friends with my arm": (3016, 10302, 0),
    "holy grail":             (2920, 3538, 0),
    "monkey madness":         (2462, 3500, 0),
    "underground pass":       (2434, 3314, 0),
    "roving elves":           (2347, 3163, 0),
    "song of the elves":      (3203, 6105, 0),
    "mourning's end":         (2547, 3425, 0),
    "the eyes of glouphrie":  (2461, 3449, 0),
    "one small favour":       (2770, 3475, 0),
    "swan song":              (2345, 3650, 0),
    "devious minds":          (2978, 3502, 0),
    "darkness of hallowvale": (3490, 3489, 0),
    "contact!":               (3298, 2781, 0),
    "dream mentor":           (2117, 3915, 0),
    "a tail of two cats":     (3087, 3499, 0),
    "elemental workshop":     (2711, 3484, 0),
    "watchtower":             (2549, 3114, 0),
    "the tourist trap":       (3304, 3109, 0),
    "big chompy bird hunting": (2542, 2961, 0),
    "zogre flesh eaters":     (2444, 3051, 0),
    "tree gnome village":     (2541, 3170, 0),
    "the grand tree":         (2461, 3494, 0),
    "fight arena":            (2575, 3155, 0),
    "plague city":            (2528, 3300, 0),
    "biohazard":              (2528, 3300, 0),
}


def flatten_content(segments) -> str:
    if not isinstance(segments, list):
        return ""
    return "".join(seg.get("text", "") for seg in segments if isinstance(seg, dict))


_IMAGE_EXTS = (".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg")


def _segment_url(seg: dict) -> Optional[str]:
    """Pull a URL off a content segment. BRUHsailer stores it either at the top
    level or nested under `formatting`. Both shapes occur in real files."""
    if not isinstance(seg, dict):
        return None
    if seg.get("isLink") and seg.get("url"):
        return seg["url"]
    fmt = seg.get("formatting") or {}
    if isinstance(fmt, dict) and fmt.get("isLink") and fmt.get("url"):
        return fmt["url"]
    return None


def _is_image_url(url: str) -> bool:
    u = url.lower().split("?", 1)[0].split("#", 1)[0]
    if u.endswith(_IMAGE_EXTS):
        return True
    if "i.imgur.com" in u:
        return True
    return False


def first_link(segments) -> Optional[str]:
    """Prefer wiki / quest reference links; skip inline image URLs."""
    if not isinstance(segments, list):
        return None
    candidates = []
    for seg in segments:
        url = _segment_url(seg)
        if not url or _is_image_url(url):
            continue
        candidates.append(url)
    if not candidates:
        return None
    for url in candidates:
        if "runescape.wiki" in url.lower():
            return url
    return candidates[0]


def detect_location(text: str) -> Optional[Tuple[int, int, int]]:
    """Last-mention wins (destinations appear after movement verbs).
    Quest-start NPC locations are preferred over generic city mentions."""
    if not text:
        return None
    haystack = _norm(text)

    best_pos = -1
    best_coords = None

    # Quest starts — if any quest name appears, use it (last mention).
    for keyword, coords in QUEST_LOCATIONS.items():
        pos = haystack.rfind(keyword)
        if pos > best_pos:
            best_pos = pos
            best_coords = coords
    if best_coords is not None:
        return best_coords

    # General locations — last mention, tie-break by keyword length.
    best_key_len = 0
    for keyword, coords in LOCATIONS.items():
        pos = haystack.rfind(keyword)
        if pos < 0:
            continue
        if pos > best_pos or (pos == best_pos and len(keyword) > best_key_len):
            best_pos = pos
            best_key_len = len(keyword)
            best_coords = coords
    return best_coords


def convert_step(src_step: dict, next_id: int) -> Optional[dict]:
    content = src_step.get("content", [])
    description = flatten_content(content).strip()

    nested = src_step.get("nestedContent", [])
    if isinstance(nested, list):
        for n in nested:
            if not isinstance(n, dict):
                continue
            nested_text = flatten_content(n.get("content", [])).strip()
            if nested_text:
                description += "\n  • " + nested_text

    if not description:
        return None

    out = {"id": next_id, "description": description}

    coords = detect_location(description)
    if coords is not None:
        x, y, plane = coords
        out["location"] = {"x": x, "y": y, "plane": plane}

    link = first_link(content)
    if link:
        out["questHelperLink"] = link

    return out


def convert(src: dict) -> Tuple[dict, dict]:
    """Returns (converted_guide, stats)."""
    out = {
        "guideName": (src.get("title") or "Imported guide").strip(),
        "chapters": [],
    }
    if src.get("updatedOn"):
        out["version"] = str(src["updatedOn"])

    stats = {"steps": 0, "located": 0, "links": 0, "dropped_empty": 0}
    next_id = 1

    for src_chapter in src.get("chapters", []) or []:
        chapter = {
            "name": (src_chapter.get("title") or src_chapter.get("name") or "").strip(),
            "sections": [],
        }
        for src_section in src_chapter.get("sections", []) or []:
            section = {
                "name": (src_section.get("title") or src_section.get("name") or "").strip(),
                "steps": [],
            }
            for src_step in src_section.get("steps", []) or []:
                step = convert_step(src_step, next_id)
                if step is None:
                    stats["dropped_empty"] += 1
                    continue
                section["steps"].append(step)
                stats["steps"] += 1
                if "location" in step:
                    stats["located"] += 1
                if "questHelperLink" in step:
                    stats["links"] += 1
                next_id += 1
            if section["steps"]:
                chapter["sections"].append(section)
        if chapter["sections"]:
            out["chapters"].append(chapter)

    return out, stats


def validate(guide: dict) -> None:
    """Mirror the checks in GuideImporter.validate so the converter fails fast."""
    if not guide.get("guideName"):
        raise ValueError("converted guide is missing 'guideName'")
    if not guide.get("chapters"):
        raise ValueError("converted guide has no chapters")

    seen_ids = set()
    total = 0
    for chapter in guide["chapters"]:
        for section in chapter.get("sections", []):
            for step in section.get("steps", []):
                total += 1
                sid = step.get("id")
                if sid in seen_ids:
                    raise ValueError(f"duplicate step id: {sid}")
                seen_ids.add(sid)
                if not step.get("description"):
                    raise ValueError(f"step {sid} has no description")
    if total == 0:
        raise ValueError("converted guide contains no steps")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Convert a BRUHsailer guide JSON into the Vibe Steps format."
    )
    parser.add_argument("input", help="path to BRUHsailer guide JSON")
    parser.add_argument("-o", "--output", help="output file (default: stdout)")
    args = parser.parse_args()

    with open(args.input, "r", encoding="utf-8") as f:
        src = json.load(f)

    converted, stats = convert(src)
    validate(converted)

    text = json.dumps(converted, indent=2, ensure_ascii=False)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(text)
    else:
        sys.stdout.write(text + "\n")

    sys.stderr.write(
        "converted {steps} steps "
        "({located} with detected location, {links} with quest-helper link, "
        "{dropped_empty} empty steps dropped)\n".format(**stats)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
