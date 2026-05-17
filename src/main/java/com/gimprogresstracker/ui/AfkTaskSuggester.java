package com.gimprogresstracker.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import net.runelite.api.Skill;

final class AfkTaskSuggester
{
	private static final Random RANDOM = new Random();

	static final class AfkTask
	{
		final String title;
		final String detail;
		final Skill skill;
		final int requiredLevel;

		AfkTask(String title, String detail, Skill skill, int requiredLevel)
		{
			this.title = title;
			this.detail = detail;
			this.skill = skill;
			this.requiredLevel = requiredLevel;
		}
	}

	private static final List<AfkTask> ALL_TASKS = new ArrayList<>();

	static
	{
		// Woodcutting
		ALL_TASKS.add(new AfkTask("Cut oaks", "Oak trees near a bank — great low-attention WC XP. Draynor or Varrock recommended.", Skill.WOODCUTTING, 15));
		ALL_TASKS.add(new AfkTask("Cut willows", "Willows at Draynor Village. Very AFK, fast XP, close to a bank.", Skill.WOODCUTTING, 30));
		ALL_TASKS.add(new AfkTask("Cut teaks", "Teak trees at Tai Bwo Wannai or Miscellania. One of the most AFK WC methods.", Skill.WOODCUTTING, 35));
		ALL_TASKS.add(new AfkTask("Cut maples", "Maples at Seer's Village — decent XP, very low attention needed.", Skill.WOODCUTTING, 45));
		ALL_TASKS.add(new AfkTask("Cut yews", "Yews are highly AFK and sellable. Good passive income for the team.", Skill.WOODCUTTING, 60));
		ALL_TASKS.add(new AfkTask("Cut magic logs", "Magic trees are extremely AFK. Bank nearby for profit.", Skill.WOODCUTTING, 75));

		// Fishing
		ALL_TASKS.add(new AfkTask("Fish shrimp", "Net fishing at Lumbridge swamp or Draynor — completely AFK.", Skill.FISHING, 1));
		ALL_TASKS.add(new AfkTask("Fly-fish trout", "Fly-fish at Barbarian Village or Lumbridge. Great XP, very AFK.", Skill.FISHING, 20));
		ALL_TASKS.add(new AfkTask("Fish lobsters", "Lobsters at Karamja — useful food for the team, decent AFK XP.", Skill.FISHING, 40));
		ALL_TASKS.add(new AfkTask("Fish swordfish", "Swordfish/tuna at Karamja. Good food supplies, very AFK.", Skill.FISHING, 50));
		ALL_TASKS.add(new AfkTask("Fish monkfish", "Monkfish at Piscatoris (after Swan Song) — excellent AFK XP.", Skill.FISHING, 62));
		ALL_TASKS.add(new AfkTask("Fish sharks", "Sharks at Fishing Guild — highly AFK and one of the best foods in the game.", Skill.FISHING, 76));
		ALL_TASKS.add(new AfkTask("Fish anglerfish", "Anglerfish at Piscarillius — very AFK with best-in-slot healing food.", Skill.FISHING, 82));

		// Mining
		ALL_TASKS.add(new AfkTask("Mine iron ore", "Iron at Varrock West/East or Al-Kharid. Useful early smithing supply.", Skill.MINING, 15));
		ALL_TASKS.add(new AfkTask("Mine gold (AFK)", "Gold ore in Al-Kharid or Crafting Guild — great AFK XP with Varrock armour.", Skill.MINING, 40));
		ALL_TASKS.add(new AfkTask("Mine amethyst", "Amethyst in the Mining Guild — very AFK and produces useful ranging ammo.", Skill.MINING, 92));

		// Combat (AFK)
		ALL_TASKS.add(new AfkTask("Train at crabs", "Sand crabs, rock crabs, or ammonite crabs — AFK melee XP anywhere.", Skill.ATTACK, 1));
		ALL_TASKS.add(new AfkTask("NMZ guthans", "Nightmare Zone with Guthans set — extremely AFK combat XP and points.", Skill.ATTACK, 70));

		// Farming
		ALL_TASKS.add(new AfkTask("Run herb patches", "Plant and harvest herb patches — do a full run then AFK on something else.", Skill.FARMING, 9));
		ALL_TASKS.add(new AfkTask("Do tree runs", "Plant tree saplings for passive XP gains every ~5 hours.", Skill.FARMING, 15));
		ALL_TASKS.add(new AfkTask("Fruit tree runs", "Fruit tree runs take ~16 hours to grow — check on them while you AFK.", Skill.FARMING, 27));

		// Thieving
		ALL_TASKS.add(new AfkTask("Pickpocket knights", "Ardougne knights are AFK thieving — good GP and XP at low attention.", Skill.THIEVING, 55));
		ALL_TASKS.add(new AfkTask("Blackjack bandits", "Blackjacking Menaphite Thugs — fast XP with minimal active clicking.", Skill.THIEVING, 65));

		// Fletching
		ALL_TASKS.add(new AfkTask("String bows", "String unstrung bows bought from GE — fastest Fletching XP, nearly AFK.", Skill.FLETCHING, 10));
		ALL_TASKS.add(new AfkTask("Cut arrow shafts", "Cut logs into arrow shafts — instant XP, useful ranging supplies.", Skill.FLETCHING, 1));

		// Crafting
		ALL_TASKS.add(new AfkTask("Spin flax", "Spin flax into bowstrings at Lumbridge castle wheel — easy Crafting XP.", Skill.CRAFTING, 10));
		ALL_TASKS.add(new AfkTask("Cut gems", "Cut uncut gems from drops or GE — fast Crafting XP in small bursts.", Skill.CRAFTING, 20));

		// Firemaking / Minigame
		ALL_TASKS.add(new AfkTask("Wintertodt", "Wintertodt is very AFK Firemaking XP and gives supply crates for the team.", Skill.FIREMAKING, 50));

		// Hunter
		ALL_TASKS.add(new AfkTask("Catch chinchompas", "Red/grey chinchompas at Feldip Hills — profitable AFK-ish hunter.", Skill.HUNTER, 53));

		// Smithing
		ALL_TASKS.add(new AfkTask("Smelt cannonballs", "Smelt iron + coal into cannonballs — great passive GP for the GIM team.", Skill.SMITHING, 35));
		ALL_TASKS.add(new AfkTask("Blast Furnace", "Blast Furnace for cheap bars — efficient XP and metal supply for the team.", Skill.SMITHING, 60));
	}

	private AfkTaskSuggester()
	{
	}

	static AfkTask suggest(Function<Skill, Integer> skillLevel, AfkTask exclude)
	{
		List<AfkTask> eligible = new ArrayList<>();
		for (AfkTask task : ALL_TASKS)
		{
			if (task == exclude)
			{
				continue;
			}
			if (skillLevel.apply(task.skill) >= task.requiredLevel)
			{
				eligible.add(task);
			}
		}
		if (eligible.isEmpty())
		{
			for (AfkTask task : ALL_TASKS)
			{
				if (task != exclude && task.requiredLevel <= 1)
				{
					eligible.add(task);
				}
			}
		}
		if (eligible.isEmpty())
		{
			return ALL_TASKS.get(0);
		}
		return eligible.get(RANDOM.nextInt(eligible.size()));
	}
}
