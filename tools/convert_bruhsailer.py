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
  - auto-generates a `tldr` (1-3 words) from the first line of each step
    description; override by editing the output JSON directly
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


# Quest name detection — normalized keyword → canonical wiki name.
# Longer keywords come first so "Dragon Slayer II" beats "Dragon Slayer I".
QUEST_NAMES: dict[str, str] = {
    # F2P
    "cook's assistant":               "Cook's Assistant",
    "rune mysteries":                 "Rune Mysteries",
    "x marks the spot":               "X Marks the Spot",
    "the restless ghost":             "The Restless Ghost",
    "restless ghost":                 "The Restless Ghost",
    "misthalin mystery":              "Misthalin Mystery",
    "demon slayer":                   "Demon Slayer",
    "imp catcher":                    "Imp Catcher",
    "doric's quest":                  "Doric's Quest",
    "witch's potion":                 "Witch's Potion",
    "romeo & juliet":                 "Romeo & Juliet",
    "romeo and juliet":               "Romeo & Juliet",
    "sheep shearer":                  "Sheep Shearer",
    "ernest the chicken":             "Ernest the Chicken",
    "vampire slayer":                 "Vampire Slayer",
    "pirate's treasure":              "Pirate's Treasure",
    "dragon slayer ii":               "Dragon Slayer II",
    "dragon slayer 2":                "Dragon Slayer II",
    "dragon slayer i":                "Dragon Slayer I",
    "dragon slayer":                  "Dragon Slayer I",
    "black knights' fortress":        "Black Knights' Fortress",
    "goblin diplomacy":               "Goblin Diplomacy",
    "the corsair curse":              "The Corsair Curse",
    "corsair curse":                  "The Corsair Curse",
    "prince ali rescue":              "Prince Ali Rescue",
    "shield of arrav":                "Shield of Arrav",
    "the knight's sword":             "The Knight's Sword",
    "knight's sword":                 "The Knight's Sword",
    "below ice mountain":             "Below Ice Mountain",
    # Members
    "waterfall quest":                "Waterfall Quest",
    "tree gnome village":             "Tree Gnome Village",
    "the grand tree":                 "The Grand Tree",
    "grand tree":                     "The Grand Tree",
    "fight arena":                    "Fight Arena",
    "hazeel cult":                    "Hazeel Cult",
    "sheep herder":                   "Sheep Herder",
    "plague city":                    "Plague City",
    "sea slug":                       "Sea Slug",
    "clock tower":                    "Clock Tower",
    "the holy grail":                 "Holy Grail",
    "holy grail":                     "Holy Grail",
    "tribal totem":                   "Tribal Totem",
    "fishing contest":                "Fishing Contest",
    "merlin's crystal":               "Merlin's Crystal",
    "one small favour":               "One Small Favour",
    "mountain daughter":              "Mountain Daughter",
    "between a rock":                 "Between a Rock...",
    "the feud":                       "The Feud",
    "ghosts ahoy":                    "Ghosts Ahoy",
    "haunted mine":                   "Haunted Mine",
    "tarn's lair":                    "Tarn's Lair",
    "the tourist trap":               "The Tourist Trap",
    "tourist trap":                   "The Tourist Trap",
    "watchtower":                     "Watchtower",
    "witch's house":                  "Witch's House",
    "underground pass":               "Underground Pass",
    "desert treasure ii":             "Desert Treasure II",
    "desert treasure 2":              "Desert Treasure II",
    "desert treasure i":              "Desert Treasure I",
    "desert treasure":                "Desert Treasure I",
    "lunar diplomacy":                "Lunar Diplomacy",
    "dream mentor":                   "Dream Mentor",
    "the eyes of glouphrie":          "The Eyes of Glouphrie",
    "eyes of glouphrie":              "The Eyes of Glouphrie",
    "darkness of hallowvale":         "Darkness of Hallowvale",
    "fairy tale iii":                 "Fairy Tale III - Battle at Ork's Rift",
    "fairy tale ii":                  "Fairy Tale II - Cure a Queen",
    "fairy tale i":                   "Fairy Tale I - Growing Pains",
    "rum deal":                       "Rum Deal",
    "swan song":                      "Swan Song",
    "recipe for disaster":            "Recipe for Disaster",
    "contact!":                       "Contact!",
    "in aid of the myreque":          "In Aid of the Myreque",
    "in search of the myreque":       "In Search of the Myreque",
    "a tail of two cats":             "A Tail of Two Cats",
    "elemental workshop ii":          "Elemental Workshop II",
    "elemental workshop i":           "Elemental Workshop I",
    "elemental workshop":             "Elemental Workshop I",
    "devious minds":                  "Devious Minds",
    "shadow of the storm":            "Shadow of the Storm",
    "animal magnetism":               "Animal Magnetism",
    "bone voyage":                    "Bone Voyage",
    "forgettable tale of a drunken dwarf": "Forgettable Tale...",
    "forgettable tale":               "Forgettable Tale...",
    "the hand in the sand":           "The Hand in the Sand",
    "hand in the sand":               "The Hand in the Sand",
    "rag and bone man ii":            "Rag and Bone Man II",
    "rag and bone man i":             "Rag and Bone Man I",
    "rag and bone man":               "Rag and Bone Man I",
    "making friends with my arm":     "Making Friends with My Arm",
    "beneath cursed sands":           "Beneath Cursed Sands",
    "monkey madness ii":              "Monkey Madness II",
    "monkey madness 2":               "Monkey Madness II",
    "monkey madness i":               "Monkey Madness I",
    "monkey madness":                 "Monkey Madness I",
    "song of the elves":              "Song of the Elves",
    "sins of the father":             "Sins of the Father",
    "mourning's end part ii":         "Mourning's End Part II",
    "mourning's end part i":          "Mourning's End Part I",
    "mourning's end":                 "Mourning's End Part I",
    "the fremennik exiles":           "The Fremennik Exiles",
    "fremennik exiles":               "The Fremennik Exiles",
    "the fremennik isles":            "The Fremennik Isles",
    "fremennik isles":                "The Fremennik Isles",
    "the fremennik trials":           "The Fremennik Trials",
    "fremennik trials":               "The Fremennik Trials",
    "royal trouble":                  "Royal Trouble",
    "horror from the deep":           "Horror from the Deep",
    "throne of miscellania":          "Throne of Miscellania",
    "roving elves":                   "Roving Elves",
    "zogre flesh eaters":             "Zogre Flesh Eaters",
    "big chompy bird hunting":        "Big Chompy Bird Hunting",
    "regicide":                       "Regicide",
    "biohazard":                      "Biohazard",
    "scorpion catcher":               "Scorpion Catcher",
    "lost city":                      "Lost City",
    "legends' quest":                 "Legends' Quest",
    "heroes' quest":                  "Heroes' Quest",
    "death plateau":                  "Death Plateau",
    "troll stronghold":               "Troll Stronghold",
    "troll romance":                  "Troll Romance",
    "my arm's big adventure":         "My Arm's Big Adventure",
    "eadgar's ruse":                  "Eadgar's Ruse",
    "dwarf cannon":                   "Dwarf Cannon",
    "murder mystery":                 "Murder Mystery",
    "wanted!":                        "Wanted!",
    "recruitment drive":              "Recruitment Drive",
    "family crest":                   "Family Crest",
    "icthlarin's little helper":      "Icthlarin's Little Helper",
    "enakhra's lament":               "Enakhra's Lament",
    "the golem":                      "The Golem",
    "spirits of the elid":            "Spirits of the Elid",
    "dealing with scabaras":          "Dealing with Scabaras",
    "the tale of the righteous":      "The Tale of the Righteous",
    "a kingdom divided":              "A Kingdom Divided",
    "the ascent of arceuus":          "The Ascent of Arceuus",
    "sleeping giants":                "Sleeping Giants",
    "priest in peril":                "Priest in Peril",
    "nature spirit":                  "Nature Spirit",
    "tower of life":                  "Tower of Life",
    "the great brain robbery":        "The Great Brain Robbery",
    "making history":                 "Making History",
    "shades of mort'ton":             "Shades of Mort'ton",
    "the queen of thieves":           "The Queen of Thieves",
    "client of kourend":              "Client of Kourend",
    "the forsaken tower":             "The Forsaken Tower",
    "secrets of the north":           "Secrets of the North",
    "perilous moons":                 "Perilous Moons",
    "children of the sun":            "Children of the Sun",
    "twilight's promise":             "Twilight's Promise",
    "the frozen door":                "The Frozen Door",
    "into the tombs":                 "Into the Tombs",
    "the dig site":                   "The Dig Site",
    "dig site":                       "The Dig Site",
    "tai bwo wannai trio":            "Tai Bwo Wannai Trio",
    "in search of knowledge":         "In Search of Knowledge",
    "land of the goblins":            "Land of the Goblins",
    "temple of the eye":              "Temple of the Eye",
    "defender of varrock":            "Defender of Varrock",
    "garden of tranquillity":         "Garden of Tranquillity",
    "enlightened journey":            "Enlightened Journey",
    "ratcatchers":                    "Ratcatchers",
    "cabin fever":                    "Cabin Fever",
    "as a first resort":              "As a First Resort...",
    "barbarian training":             "Barbarian Training",
    "cold war":                       "Cold War",
    "jungle potion":                  "Jungle Potion",
    "the slug menace":                "The Slug Menace",
    "slug menace":                    "The Slug Menace",
    "his faithful servants":          "His Faithful Servants",
    "architectural alliance":         "Architectural Alliance",
    "getting ahead":                  "Getting Ahead",
    "a ribbiting tale":               "A Ribbiting Tale of a Lily Pad Labour",
    "a night at the theatre":         "A Night at the Theatre",
}

_QUEST_ACTION_WORDS = (
    "complete", "finish", "do ", "doing ", "done ", "start ", "begin ", "unlock",
    "quest", "story", "storyline",
)

# Normalized item name (lowercase) → OSRS item ID.
# Covers the items that appear most often in GIM progression guides.
ITEM_IDS: dict = {
    # ── Currency ────────────────────────────────────────────────────────────────
    "gp": 995, "coins": 995, "gold": 995, "coin": 995,
    # ── Runes ───────────────────────────────────────────────────────────────────
    "air rune": 556,  "air runes": 556,
    "water rune": 555, "water runes": 555,
    "earth rune": 557, "earth runes": 557,
    "fire rune": 554,  "fire runes": 554,
    "mind rune": 558,  "mind runes": 558,
    "chaos rune": 562, "chaos runes": 562,
    "death rune": 560, "death runes": 560,
    "nature rune": 561,"nature runes": 561,
    "law rune": 563,   "law runes": 563,
    "soul rune": 566,  "soul runes": 566,
    "blood rune": 565, "blood runes": 565,
    "cosmic rune": 564,"cosmic runes": 564,
    "astral rune": 9075, "astral runes": 9075,
    "mud rune": 4698,  "mud runes": 4698,
    "pure essence": 7936, "rune essence": 1436,
    # ── Logs ────────────────────────────────────────────────────────────────────
    "logs": 1511, "log": 1511,
    "oak logs": 1521, "oak log": 1521,
    "willow logs": 1519, "willow log": 1519,
    "teak logs": 6333, "teak log": 6333,
    "maple logs": 1517, "maple log": 1517,
    "yew logs": 1515, "yew log": 1515,
    "magic logs": 1513, "magic log": 1513,
    # ── Bars ────────────────────────────────────────────────────────────────────
    "bronze bar": 2349,
    "iron bar": 2351,
    "steel bar": 2353,
    "mithril bar": 2359,
    "adamantite bar": 2361,
    "runite bar": 2363,
    "gold bar": 2357, "gold bars": 2357,
    "silver bar": 2355,
    # ── Tools ───────────────────────────────────────────────────────────────────
    "hammer": 2347,
    "chisel": 1755,
    "saw": 8794,
    "tinderbox": 590,
    "knife": 946,
    "spade": 952,
    "rope": 954,
    "bucket": 1925, "empty bucket": 1925,
    "pot": 1931,
    "jug": 1935,
    "needle": 1733,
    "thread": 1734,
    "shears": 1735,
    "glassblowing pipe": 1785,
    "pestle and mortar": 233, "pestle/mortar": 233,
    "lockpick": 1523,
    "swamp tar": 1939,
    "bronze wire": 1794,
    "lantern lens": 4540,
    "candle": 33,
    # ── Fishing ─────────────────────────────────────────────────────────────────
    "fishing rod": 307,
    "fly fishing rod": 309,
    "small fishing net": 303,
    "lobster pot": 301,
    "feather": 314, "feathers": 314,
    "fishing bait": 313, "bait": 313,
    # ── Pickaxes ────────────────────────────────────────────────────────────────
    "bronze pickaxe": 1265,
    "iron pickaxe": 1267,
    "steel pickaxe": 1269,
    "mithril pickaxe": 1271,
    "adamant pickaxe": 1273, "addy pickaxe": 1273,
    "rune pickaxe": 1275,
    "dragon pickaxe": 11920,
    # ── Axes ────────────────────────────────────────────────────────────────────
    "bronze axe": 1351,
    "iron axe": 1349,
    "steel axe": 1353,
    "mithril axe": 1355,
    "adamant axe": 1357, "addy axe": 1357,
    "rune axe": 1359,
    "magic axe": 6739,
    # ── Staves ──────────────────────────────────────────────────────────────────
    "staff of air": 1381, "air staff": 1381,
    "staff of water": 1383, "water staff": 1383,
    "staff of earth": 1385, "earth staff": 1385,
    "staff of fire": 1387, "fire staff": 1387,
    "iban staff": 1409,
    "dramen staff": 772,
    "dramen branch": 771,
    # ── Swords & scimitars ───────────────────────────────────────────────────────
    "bronze sword": 1277,
    "iron sword": 1279,
    "steel sword": 1281,
    "mithril sword": 1285,
    "adamant sword": 1287, "addy sword": 1287,
    "rune sword": 1289,
    "iron scimitar": 1323,
    "steel scimitar": 1325,
    "mithril scimitar": 1329,
    "adamant scimitar": 1331,
    "rune scimitar": 1333,
    # ── Arrows ──────────────────────────────────────────────────────────────────
    "bronze arrow": 882, "bronze arrows": 882,
    "iron arrow": 884, "iron arrows": 884,
    "steel arrow": 886, "steel arrows": 886,
    # ── Armour ──────────────────────────────────────────────────────────────────
    "iron chainbody": 1101,
    "bronze chainbody": 1103,
    "rune platelegs": 1079,
    "berserker helm": 3751,
    "granite body": 10568,
    "leather gloves": 1059,
    "leather boots": 1061,
    # ── Nails & construction ─────────────────────────────────────────────────────
    "steel nail": 1539, "steel nails": 1539,
    "iron nail": 4820, "iron nails": 4820,
    "bronze nail": 4819, "bronze nails": 4819,
    "mithril nail": 4822, "mithril nails": 4822,
    "plank": 960, "planks": 960,
    "oak plank": 8778, "oak planks": 8778,
    "teak plank": 8780, "teak planks": 8780,
    "mahogany plank": 8782, "mahogany planks": 8782,
    # ── Crafting ────────────────────────────────────────────────────────────────
    "soft clay": 1761,
    "molten glass": 1775,
    "soda ash": 1781,
    "bucket of sand": 1783,
    "uncut sapphire": 1623, "sapphire": 1607,
    "uncut emerald": 1621, "emerald": 1605,
    "uncut ruby": 1619, "ruby": 1603,
    "uncut diamond": 1617, "diamond": 1601,
    "gold ring": 1635,
    "tiara mould": 5523,
    "necklace mould": 1597,
    "sickle mould": 2976,
    "silk": 950,
    "woad leaves": 1793,
    "vial of water": 227,
    "empty vial": 229, "vial": 229,
    "pestle and mortar": 233,
    # ── Farming ─────────────────────────────────────────────────────────────────
    "seed dibber": 5343,
    "rake": 5341,
    "watering can": 5340,
    "plant pot": 5352,
    "gardening trowel": 5325, "trowel": 5325,
    "compost": 6032,
    "supercompost": 6034,
    "ultracompost": 21483,
    "magic secateurs": 7409,
    "barley seed": 5305, "barley seeds": 5305,
    "cabbage seed": 5324, "cabbage seeds": 5324,
    "onion seed": 5319, "onion seeds": 5319,
    "marigold seed": 5096,
    "harralander seed": 5294, "harralander seeds": 5294,
    # ── Food ────────────────────────────────────────────────────────────────────
    "swordfish": 373, "raw swordfish": 371,
    "lobster": 379, "raw lobster": 377,
    "salmon": 329, "raw salmon": 331,
    "trout": 333, "raw trout": 335,
    "cod": 339, "raw cod": 341,
    "chicken": 2140, "raw chicken": 2138,
    "bread": 2309,
    "cabbage": 1965,
    "onion": 1957,
    "potato": 1942,
    "garlic": 1332,
    "redberries": 1951,
    "white berries": 239,
    "jangerberry": 247,
    "cadava berry": 753, "cadava berries": 753,
    "ugthanki dung": 4261,
    # ── Potions ─────────────────────────────────────────────────────────────────
    "prayer potion": 2434, "prayer potions": 2434,
    "prayer pot": 2434, "prayer pots": 2434,
    "energy potion": 3008, "energy potions": 3008,
    "super attack": 2436,
    "super strength": 2440,
    "super defence": 2442,
    "antipoison": 2446, "antipoisons": 2446,
    "antidote++": 5952,
    "superantipoison": 2448,
    "guam leaf": 249,
    "marrentill": 207,
    "tarromin": 205,
    "harralander": 255,
    "marrentill potion(unf)": 97,
    # ── Drinks ──────────────────────────────────────────────────────────────────
    "beer": 1917,
    "asgarnian ale": 1905,
    "wizard's mind bomb": 1907,
    "vodka": 1993,
    # ── Jewellery & teleports ────────────────────────────────────────────────────
    "games necklace": 3853,
    "amulet of magic": 1727,
    "amulet of accuracy": 4071,
    "necklace of passage": 21149,
    "ring of charos": 4202, "ring of charos(a)": 4202,
    "digsite pendant": 11194,
    "chronicle": 23360,
    "ghostspeak amulet": 552,
    "catspeak amulet": 2693,
    "dueling ring": 2552,
    "barcrawl card": 1491,
    "poh tab": 8013,
    "commorb": 6809,
    # ── Misc quest / skilling items ──────────────────────────────────────────────
    "waterskin": 1823, "waterskins": 1823,
    "pie dish": 2313,
    "dragon bones": 536,
    "bones": 526,
    "bucket of sap": 4687,
    "scorpion cage": 4006,
    "key": 983,
    "package": 2516,
    "iron bar": 2351,
    "lyre": 3691,
    "brooch": 4041,
    "diary": 4046,
}

_SKIP_ITEMS = frozenset({
    "tbd", "n/a", "none", "etc", "etc.", "cash stack",
    "melee gear", "range gear", "mage gear", "standard rex gear",
    "food", "high healing food", "teleport",
})


def _split_items(text: str) -> list:
    """Split by comma while respecting parentheses depth."""
    parts, current, depth = [], [], 0
    for ch in text:
        if ch == '(':
            depth += 1
            current.append(ch)
        elif ch == ')':
            depth -= 1
            current.append(ch)
        elif ch == ',' and depth == 0:
            parts.append(''.join(current))
            current = []
        else:
            current.append(ch)
    if current:
        parts.append(''.join(current))
    return parts


def _strip_note_parens(text: str) -> str:
    """Strip parenthetical notes but keep short item-name modifiers like (a), (unf), (4)."""
    import re as _re
    def _replace(m):
        content = m.group(1)
        # Keep if short and only alphanumeric/+- (item modifiers)
        if _re.match(r'^[a-z0-9+\-]{1,6}$', content, _re.IGNORECASE):
            return m.group(0)
        return ''
    return _re.sub(r'\(([^)]*)\)', _replace, text)


def _parse_qty_and_name(raw: str) -> tuple:
    """Return (quantity, normalized_name) from a single item token."""
    import re as _re
    raw = _strip_note_parens(raw).strip().rstrip('.')
    # Match optional leading number with optional k/m suffix
    m = _re.match(r'^([\d.,]+)\s*([km])?\s+(.+)$', raw, _re.IGNORECASE)
    if m:
        try:
            qty = float(m.group(1).replace(',', ''))
        except ValueError:
            qty = 1.0
        suffix = (m.group(2) or '').lower()
        if suffix == 'k':
            qty *= 1000
        elif suffix == 'm':
            qty *= 1_000_000
        name = m.group(3).strip().lower()
        return int(qty), name
    return 1, raw.lower()


def parse_items_needed(text: str) -> list:
    """Parse the metadata.items_needed free-text into [{itemId, quantity}] list."""
    if not text or text.lower().strip() in ('none', 'tbd', 'n/a', ''):
        return []
    results = []
    for part in _split_items(text):
        part = part.strip()
        if not part:
            continue
        qty, name = _parse_qty_and_name(part)
        if name in _SKIP_ITEMS or not name:
            continue
        item_id = ITEM_IDS.get(name)
        if item_id is not None:
            results.append({"itemId": item_id, "quantity": qty})
    return results


def detect_quest_name(text: str) -> Optional[str]:
    """Return canonical quest name if the description looks like a quest step."""
    if not text:
        return None
    hay = _norm(text)
    if not any(w in hay for w in _QUEST_ACTION_WORDS):
        return None
    best_key = ""
    best_name = None
    for keyword, name in QUEST_NAMES.items():
        if keyword in hay and len(keyword) > len(best_key):
            best_key = keyword
            best_name = name
    return best_name


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
    """Last-mention wins across all location keywords.

    Previously, any hit in QUEST_LOCATIONS caused an early return, so a
    passing mention of a quest name (e.g. "Monkey Madness II" as a future
    goal) could override the step's actual destination (e.g. "wintertodt").
    Now both dictionaries are scanned together and the keyword whose last
    occurrence sits furthest right in the text wins.

    Tie-breaking (same position):
      1. Quest-start location beats a general location.
      2. Within the same dictionary, the longer keyword wins.
    """
    if not text:
        return None
    haystack = _norm(text)

    best_pos = -1
    best_coords = None
    best_key_len = 0
    best_is_quest = False

    for keyword, coords in QUEST_LOCATIONS.items():
        pos = haystack.rfind(keyword)
        if pos < 0:
            continue
        if pos > best_pos or (pos == best_pos and (not best_is_quest or len(keyword) > best_key_len)):
            best_pos = pos
            best_key_len = len(keyword)
            best_coords = coords
            best_is_quest = True

    for keyword, coords in LOCATIONS.items():
        pos = haystack.rfind(keyword)
        if pos < 0:
            continue
        # A general location only wins if it appears strictly later than the
        # current best, or ties at the same position with no quest match yet.
        if pos > best_pos or (pos == best_pos and not best_is_quest and len(keyword) > best_key_len):
            best_pos = pos
            best_key_len = len(keyword)
            best_coords = coords
            best_is_quest = False

    return best_coords


_TLDR_ARTICLES = frozenset({"a", "an", "the"})
_TLDR_LEADING_NUM = __import__("re").compile(r"^\d+[.)]\s*")


def generate_tldr(description: str) -> str:
    """Return a 1-3 word summary from the first line of a step description.

    Articles (a/an/the) are skipped so the result leads with an action word
    or noun.  The caller can override the generated value by editing the JSON.
    """
    first_line = description.split("\n")[0].strip()
    first_line = _TLDR_LEADING_NUM.sub("", first_line)  # drop "1. " / "2) " prefixes
    words = []
    for word in first_line.split():
        clean = word.strip(".,!?:;()-")
        if clean.lower() not in _TLDR_ARTICLES and clean:
            words.append(clean.capitalize())
        if len(words) >= 3:
            break
    return " ".join(words)


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

    tldr = generate_tldr(description)
    out = {"id": next_id, "tldr": tldr, "description": description}

    coords = detect_location(description)
    if coords is not None:
        x, y, plane = coords
        out["location"] = {"x": x, "y": y, "plane": plane}

    link = first_link(content)
    if link:
        out["questHelperLink"] = link

    quest_name = detect_quest_name(description)
    if quest_name:
        out["questName"] = quest_name

    items_text = src_step.get("metadata", {}).get("items_needed", "")
    items = parse_items_needed(items_text)
    if items:
        out["requiredItems"] = items

    return out


def convert(src: dict) -> Tuple[dict, dict]:
    """Returns (converted_guide, stats)."""
    out = {
        "guideName": (src.get("title") or "Imported guide").strip(),
        "chapters": [],
    }
    if src.get("updatedOn"):
        out["version"] = str(src["updatedOn"])

    stats = {"steps": 0, "tldr": 0, "located": 0, "links": 0, "quests": 0, "items": 0, "dropped_empty": 0}
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
                if step.get("tldr"):
                    stats["tldr"] += 1
                if "location" in step:
                    stats["located"] += 1
                if "questHelperLink" in step:
                    stats["links"] += 1
                if "questName" in step:
                    stats["quests"] += 1
                if "requiredItems" in step:
                    stats["items"] += 1
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
        "({tldr} with auto-tldr, {located} with detected location, "
        "{links} with quest-helper link, {quests} with quest name, "
        "{items} with required items, {dropped_empty} empty steps dropped)\n"
        "Tip: edit 'tldr' fields in the output JSON to customise the 1-3 word step headers.\n"
        .format(**stats)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
