package com.gimprogresstracker.util;

import java.util.Optional;

/**
 * Heuristically detects an OSRS quest name from a step description string.
 * Uses longest-keyword matching so "Dragon Slayer II" beats "Dragon Slayer I".
 * Only reports a match when the description also contains a quest-action word
 * (complete, finish, quest, etc.) to reduce false positives on location-only
 * mentions (e.g. "go to Haunted Mine to mine ore").
 */
public final class QuestDetector
{
	// {normalized-keyword, canonical wiki name}
	// Keyword must be lowercase with straight apostrophes.
	// Longer entries for the same quest should appear before shorter ones so
	// "Dragon Slayer II" beats "Dragon Slayer I" via length comparison.
	private static final String[][] QUESTS = {
		// ── Free-to-play ─────────────────────────────────────────────────────────
		{"cook's assistant",               "Cook's Assistant"},
		{"rune mysteries",                 "Rune Mysteries"},
		{"x marks the spot",               "X Marks the Spot"},
		{"the restless ghost",             "The Restless Ghost"},
		{"restless ghost",                 "The Restless Ghost"},
		{"misthalin mystery",              "Misthalin Mystery"},
		{"demon slayer",                   "Demon Slayer"},
		{"imp catcher",                    "Imp Catcher"},
		{"doric's quest",                  "Doric's Quest"},
		{"witch's potion",                 "Witch's Potion"},
		{"romeo & juliet",                 "Romeo & Juliet"},
		{"romeo and juliet",               "Romeo & Juliet"},
		{"sheep shearer",                  "Sheep Shearer"},
		{"ernest the chicken",             "Ernest the Chicken"},
		{"vampire slayer",                 "Vampire Slayer"},
		{"pirate's treasure",              "Pirate's Treasure"},
		{"dragon slayer ii",               "Dragon Slayer II"},
		{"dragon slayer 2",                "Dragon Slayer II"},
		{"dragon slayer i",                "Dragon Slayer I"},
		{"dragon slayer",                  "Dragon Slayer I"},
		{"black knights' fortress",        "Black Knights' Fortress"},
		{"goblin diplomacy",               "Goblin Diplomacy"},
		{"the corsair curse",              "The Corsair Curse"},
		{"corsair curse",                  "The Corsair Curse"},
		{"prince ali rescue",              "Prince Ali Rescue"},
		{"shield of arrav",                "Shield of Arrav"},
		{"the knight's sword",             "The Knight's Sword"},
		{"knight's sword",                 "The Knight's Sword"},
		{"below ice mountain",             "Below Ice Mountain"},

		// ── Members – early/medium ────────────────────────────────────────────────
		{"waterfall quest",                "Waterfall Quest"},
		{"tree gnome village",             "Tree Gnome Village"},
		{"the grand tree",                 "The Grand Tree"},
		{"grand tree",                     "The Grand Tree"},
		{"fight arena",                    "Fight Arena"},
		{"hazeel cult",                    "Hazeel Cult"},
		{"sheep herder",                   "Sheep Herder"},
		{"plague city",                    "Plague City"},
		{"sea slug",                       "Sea Slug"},
		{"clock tower",                    "Clock Tower"},
		{"the holy grail",                 "Holy Grail"},
		{"holy grail",                     "Holy Grail"},
		{"tribal totem",                   "Tribal Totem"},
		{"fishing contest",                "Fishing Contest"},
		{"merlin's crystal",               "Merlin's Crystal"},
		{"one small favour",               "One Small Favour"},
		{"mountain daughter",              "Mountain Daughter"},
		{"between a rock",                 "Between a Rock..."},
		{"the feud",                       "The Feud"},
		{"ghosts ahoy",                    "Ghosts Ahoy"},
		{"haunted mine",                   "Haunted Mine"},
		{"tarn's lair",                    "Tarn's Lair"},
		{"the tourist trap",               "The Tourist Trap"},
		{"tourist trap",                   "The Tourist Trap"},
		{"watchtower",                     "Watchtower"},
		{"witch's house",                  "Witch's House"},
		{"underground pass",               "Underground Pass"},
		{"desert treasure ii",             "Desert Treasure II"},
		{"desert treasure 2",              "Desert Treasure II"},
		{"desert treasure i",              "Desert Treasure I"},
		{"desert treasure",                "Desert Treasure I"},
		{"lunar diplomacy",                "Lunar Diplomacy"},
		{"dream mentor",                   "Dream Mentor"},
		{"the eyes of glouphrie",          "The Eyes of Glouphrie"},
		{"eyes of glouphrie",              "The Eyes of Glouphrie"},
		{"darkness of hallowvale",         "Darkness of Hallowvale"},
		{"fairy tale iii",                 "Fairy Tale III - Battle at Ork's Rift"},
		{"fairy tale ii",                  "Fairy Tale II - Cure a Queen"},
		{"fairy tale i",                   "Fairy Tale I - Growing Pains"},
		{"rum deal",                       "Rum Deal"},
		{"swan song",                      "Swan Song"},
		{"recipe for disaster",            "Recipe for Disaster"},
		{"contact!",                       "Contact!"},
		{"in aid of the myreque",          "In Aid of the Myreque"},
		{"in search of the myreque",       "In Search of the Myreque"},
		{"a tail of two cats",             "A Tail of Two Cats"},
		{"elemental workshop ii",          "Elemental Workshop II"},
		{"elemental workshop i",           "Elemental Workshop I"},
		{"elemental workshop",             "Elemental Workshop I"},
		{"devious minds",                  "Devious Minds"},
		{"shadow of the storm",            "Shadow of the Storm"},
		{"animal magnetism",               "Animal Magnetism"},
		{"bone voyage",                    "Bone Voyage"},
		{"forgettable tale of a drunken dwarf", "Forgettable Tale..."},
		{"forgettable tale",               "Forgettable Tale..."},
		{"the hand in the sand",           "The Hand in the Sand"},
		{"hand in the sand",               "The Hand in the Sand"},
		{"rag and bone man ii",            "Rag and Bone Man II"},
		{"rag and bone man i",             "Rag and Bone Man I"},
		{"rag and bone man",               "Rag and Bone Man I"},
		{"making friends with my arm",     "Making Friends with My Arm"},
		{"beneath cursed sands",           "Beneath Cursed Sands"},
		{"monkey madness ii",              "Monkey Madness II"},
		{"monkey madness 2",               "Monkey Madness II"},
		{"monkey madness i",               "Monkey Madness I"},
		{"monkey madness",                 "Monkey Madness I"},
		{"song of the elves",              "Song of the Elves"},
		{"sins of the father",             "Sins of the Father"},
		{"mourning's end part ii",         "Mourning's End Part II"},
		{"mourning's end part i",          "Mourning's End Part I"},
		{"mourning's end",                 "Mourning's End Part I"},
		{"the fremennik exiles",           "The Fremennik Exiles"},
		{"fremennik exiles",               "The Fremennik Exiles"},
		{"the fremennik isles",            "The Fremennik Isles"},
		{"fremennik isles",                "The Fremennik Isles"},
		{"the fremennik trials",           "The Fremennik Trials"},
		{"fremennik trials",               "The Fremennik Trials"},
		{"royal trouble",                  "Royal Trouble"},
		{"horror from the deep",           "Horror from the Deep"},
		{"throne of miscellania",          "Throne of Miscellania"},
		{"roving elves",                   "Roving Elves"},
		{"zogre flesh eaters",             "Zogre Flesh Eaters"},
		{"big chompy bird hunting",        "Big Chompy Bird Hunting"},
		{"regicide",                       "Regicide"},
		{"biohazard",                      "Biohazard"},
		{"scorpion catcher",               "Scorpion Catcher"},
		{"lost city",                      "Lost City"},
		{"legends' quest",                 "Legends' Quest"},
		{"heroes' quest",                  "Heroes' Quest"},
		{"death plateau",                  "Death Plateau"},
		{"troll stronghold",               "Troll Stronghold"},
		{"troll romance",                  "Troll Romance"},
		{"my arm's big adventure",         "My Arm's Big Adventure"},
		{"eadgar's ruse",                  "Eadgar's Ruse"},
		{"dwarf cannon",                   "Dwarf Cannon"},
		{"murder mystery",                 "Murder Mystery"},
		{"wanted!",                        "Wanted!"},
		{"recruitment drive",              "Recruitment Drive"},
		{"family crest",                   "Family Crest"},
		{"icthlarin's little helper",      "Icthlarin's Little Helper"},
		{"enakhra's lament",               "Enakhra's Lament"},
		{"the golem",                      "The Golem"},
		{"spirits of the elid",            "Spirits of the Elid"},
		{"dealing with scabaras",          "Dealing with Scabaras"},
		{"the tale of the righteous",      "The Tale of the Righteous"},
		{"a kingdom divided",              "A Kingdom Divided"},
		{"the ascent of arceuus",          "The Ascent of Arceuus"},
		{"sleeping giants",                "Sleeping Giants"},
		{"priest in peril",                "Priest in Peril"},
		{"nature spirit",                  "Nature Spirit"},
		{"tower of life",                  "Tower of Life"},
		{"the great brain robbery",        "The Great Brain Robbery"},
		{"making history",                 "Making History"},
		{"shades of mort'ton",             "Shades of Mort'ton"},
		{"the queen of thieves",           "The Queen of Thieves"},
		{"client of kourend",              "Client of Kourend"},
		{"the forsaken tower",             "The Forsaken Tower"},
		{"secrets of the north",           "Secrets of the North"},
		{"perilous moons",                 "Perilous Moons"},
		{"children of the sun",            "Children of the Sun"},
		{"twilight's promise",             "Twilight's Promise"},
		{"the frozen door",                "The Frozen Door"},
		{"into the tombs",                 "Into the Tombs"},
		{"the dig site",                   "The Dig Site"},
		{"dig site",                       "The Dig Site"},
		{"tai bwo wannai trio",            "Tai Bwo Wannai Trio"},
		{"in search of knowledge",         "In Search of Knowledge"},
		{"land of the goblins",            "Land of the Goblins"},
		{"temple of the eye",              "Temple of the Eye"},
		{"defender of varrock",            "Defender of Varrock"},
		{"garden of tranquillity",         "Garden of Tranquillity"},
		{"enlightened journey",            "Enlightened Journey"},
		{"ratcatchers",                    "Ratcatchers"},
		{"cabin fever",                    "Cabin Fever"},
		{"as a first resort",              "As a First Resort..."},
		{"barbarian training",             "Barbarian Training"},
		{"cold war",                       "Cold War"},
		{"jungle potion",                  "Jungle Potion"},
		{"the slug menace",                "The Slug Menace"},
		{"slug menace",                    "The Slug Menace"},
		{"his faithful servants",          "His Faithful Servants"},
		{"architectural alliance",         "Architectural Alliance"},
		{"getting ahead",                  "Getting Ahead"},
		{"a ribbiting tale",               "A Ribbiting Tale of a Lily Pad Labour"},
		{"a night at the theatre",         "A Night at the Theatre"},
	};

	private static final String[] QUEST_ACTION_WORDS = {
		"complete", "finish", "do ", "doing ", "done ", "start ", "begin ", "unlock",
		"quest", "story", "storyline",
	};

	private QuestDetector()
	{
	}

	/**
	 * Returns the canonical OSRS wiki quest name if the description appears to be
	 * about completing a quest, otherwise empty.
	 */
	public static Optional<String> detectQuestName(String description)
	{
		if (description == null || description.isEmpty())
		{
			return Optional.empty();
		}
		String hay = norm(description);

		boolean hasQuestContext = false;
		for (String word : QUEST_ACTION_WORDS)
		{
			if (hay.contains(word))
			{
				hasQuestContext = true;
				break;
			}
		}
		if (!hasQuestContext)
		{
			return Optional.empty();
		}

		String bestName = null;
		int bestLen = 0;
		for (String[] pair : QUESTS)
		{
			String keyword = pair[0];
			if (hay.contains(keyword) && keyword.length() > bestLen)
			{
				bestName = pair[1];
				bestLen = keyword.length();
			}
		}
		return Optional.ofNullable(bestName);
	}

	private static String norm(String s)
	{
		return s.toLowerCase()
			.replace('’', '\'')
			.replace('‘', '\'')
			.replace('ʼ', '\'')
			.replace('`', '\'');
	}
}
