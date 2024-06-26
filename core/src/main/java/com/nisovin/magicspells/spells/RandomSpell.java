package com.nisovin.magicspells.spells;

import java.util.List;
import java.util.ArrayList;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.castmodifiers.ModifierSet;

public class RandomSpell extends InstantSpell {

	private List<String> rawOptions;

	private RandomOptionSet options;

	private final boolean pseudoRandom;
	private final ConfigData<Boolean> checkIndividualCooldowns;
	private final ConfigData<Boolean> checkIndividualModifiers;
	
	public RandomSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		rawOptions = getConfigStringList("spells", null);

		pseudoRandom = getConfigBoolean("pseudo-random", true);
		checkIndividualCooldowns = getConfigDataBoolean("check-individual-cooldowns", true);
		checkIndividualModifiers = getConfigDataBoolean("check-individual-modifiers", true);
	}
	
	@Override
	public void initialize() {
		super.initialize();
		
		options = new RandomOptionSet();
		for (String s : rawOptions) {
			String[] split = s.split(" ");
			Subspell spell = new Subspell(split[0]);
			int weight = 0;
			try {
				weight = Integer.parseInt(split[1]);
			} catch (NumberFormatException e) {
				// No op
			}

			if (spell.process() && weight > 0) options.add(new SpellOption(spell, weight));
			else MagicSpells.error("Invalid spell option on RandomSpell '" + internalName + "': " + s);
		}
		
		rawOptions.clear();
		rawOptions = null;
	}

	@Override
	public CastResult cast(SpellData data) {
		boolean checkIndividualCooldowns = this.checkIndividualCooldowns.get(data);
		boolean checkIndividualModifiers = this.checkIndividualModifiers.get(data);

		RandomOptionSet set = options;
		if (checkIndividualCooldowns || checkIndividualModifiers) {
			set = new RandomOptionSet();
			for (SpellOption o : options.randomOptionSetOptions) {
				if (checkIndividualCooldowns && o.spell.getSpell().onCooldown(data.caster())) continue;
				if (checkIndividualModifiers) {
					ModifierSet modifiers = o.spell.getSpell().getModifiers();
					if (modifiers != null && !modifiers.check(data.caster())) continue;
				}
				set.add(o);
			}
		}
		if (set.randomOptionSetOptions.isEmpty()) return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		Subspell spell = set.choose();
		if (spell == null) return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		SpellCastResult result = spell.subcast(data);
		if (result.fail()) return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		playSpellEffects(data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	private static class SpellOption {

		private Subspell spell;
		private int weight;
		private int adjustedWeight;

		private SpellOption(Subspell spell, int weight) {
			this.spell = spell;
			this.weight = weight;
			adjustedWeight = weight;
		}
		
	}

	private class RandomOptionSet {

		private List<SpellOption> randomOptionSetOptions = new ArrayList<>();
		private int total = 0;

		private void add(SpellOption option) {
			randomOptionSetOptions.add(option);
			total += option.adjustedWeight;
		}

		private Subspell choose() {
			int r = random.nextInt(total);
			int x = 0;
			Subspell spell = null;
			for (SpellOption o : randomOptionSetOptions) {
				if (r < o.adjustedWeight + x && spell == null) {
					spell = o.spell;
					if (pseudoRandom) o.adjustedWeight = 0;
					else break;
				} else {
					x += o.adjustedWeight;
					if (pseudoRandom) o.adjustedWeight += o.weight;
				}
			}
			return spell;
		}
		
	}
	
}
