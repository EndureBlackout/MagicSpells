package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.collect.Multimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedListMultimap;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.VariableMod;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.castmodifiers.ModifierSet;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.util.managers.VariableManager;

public class LoopSpell extends TargetedSpell implements TargetedEntitySpell, TargetedLocationSpell {

	private final static ThreadLocalRandom random = ThreadLocalRandom.current();
	private static boolean deathRegistered;

	private final Multimap<UUID, Loop> activeLoops = HashMultimap.create();

	private final int iterations;

	private final long delay;
	private final long duration;
	private final long interval;

	private final double yOffset;

	private final boolean targeted;
	private final boolean pointBlank;
	private final boolean stopOnFail;
	private final boolean cancelOnDeath;
	private final boolean passTargeting;
	private final boolean requireEntityTarget;
	private final boolean castRandomSpellInstead;
	private final boolean skipFirstLoopModifiers;
	private final boolean skipFirstVariableModsLoop;
	private final boolean skipFirstLoopTargetModifiers;
	private final boolean skipFirstLoopLocationModifiers;
	private final boolean skipFirstVariableModsTargetLoop;

	private final String strFadeSelf;
	private final String strFadeTarget;

	private Subspell spellOnEnd;
	private String spellOnEndName;

	private List<Subspell> spells;
	private List<String> spellNames;

	private List<String> varModsLoop;
	private List<String> varModsTargetLoop;

	private Multimap<String, VariableMod> variableModsLoop;
	private Multimap<String, VariableMod> variableModsTargetLoop;

	private List<String> loopModifierStrings;
	private List<String> loopTargetModifierStrings;
	private List<String> loopLocationModifierStrings;

	private ModifierSet loopModifiers;
	private ModifierSet loopTargetModifiers;
	private ModifierSet loopLocationModifiers;

	public LoopSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		iterations = getConfigInt("iterations", 0);

		delay = getConfigLong("delay", 0);
		duration = getConfigLong("duration", 0);
		interval = getConfigLong("interval", 20);

		yOffset = getConfigDouble("y-offset", 0);

		targeted = getConfigBoolean("targeted", true);
		pointBlank = getConfigBoolean("point-blank", false);
		stopOnFail = getConfigBoolean("stop-on-fail", false);
		passTargeting = getConfigBoolean("pass-targeting", true);
		cancelOnDeath = getConfigBoolean("cancel-on-death", false);
		requireEntityTarget = getConfigBoolean("require-entity-target", false);
		castRandomSpellInstead = getConfigBoolean("cast-random-spell-instead", false);

		boolean skipFirst = getConfigBoolean("skip-first", false);
		skipFirstLoopModifiers = getConfigBoolean("skip-first-loop-modifiers", skipFirst);
		skipFirstVariableModsLoop = getConfigBoolean("skip-first-variable-mods-loop", skipFirst);
		skipFirstLoopTargetModifiers = getConfigBoolean("skip-first-loop-target-modifiers", skipFirst);
		skipFirstLoopLocationModifiers = getConfigBoolean("skip-first-loop-location-modifiers", skipFirst);
		skipFirstVariableModsTargetLoop = getConfigBoolean("skip-first-variable-mods-target-loop", skipFirst);

		strFadeSelf = getConfigString("str-fade-self", "");
		strFadeTarget = getConfigString("str-fade-target", "");
		spellOnEndName = getConfigString("spell-on-end", null);

		spellNames = getConfigStringList("spells", null);
		varModsLoop = getConfigStringList("variable-mods-loop", null);
		varModsTargetLoop = getConfigStringList("variable-mods-target-loop", null);
		loopModifierStrings = getConfigStringList("loop-modifiers", null);
		loopTargetModifierStrings = getConfigStringList("loop-target-modifiers", null);
		loopLocationModifierStrings = getConfigStringList("loop-location-modifiers", null);
	}

	@Override
	public void initialize() {
		super.initialize();

		if (cancelOnDeath && !deathRegistered) {
			deathRegistered = true;
			registerEvents(new DeathListener());
		}

		if (spellOnEndName != null) {
			spellOnEnd = new Subspell(spellOnEndName);
			if (!spellOnEnd.process()) {
				MagicSpells.error("LoopSpell '" + internalName + "' has an invalid spell-on-end '" + spellOnEndName + "' defined!");
				spellOnEnd = null;
			}
		}
		spellOnEndName = null;

		if (spellNames != null && !spellNames.isEmpty()) {
			spells = new ArrayList<>();

			for (String spellName : spellNames) {
				Subspell spell = new Subspell(spellName);
				if (!spell.process()) {
					MagicSpells.error("LoopSpell '" + internalName + "' has an invalid spell '" + spellName + "' defined!");
					continue;
				}

				spells.add(spell);
			}

			if (spells.isEmpty()) spells = null;
		}
		spellNames = null;
	}

	@Override
	protected void initializeVariables() {
		super.initializeVariables();

		if (varModsLoop != null && !varModsLoop.isEmpty()) {
			variableModsLoop = LinkedListMultimap.create();

			for (String s : varModsLoop) {
				try {
					String[] data = s.split(" ", 2);
					variableModsLoop.put(data[0], new VariableMod(data[1]));
				} catch (Exception e) {
					MagicSpells.error("Invalid variable-mods-loop option for spell '" + internalName + "': " + s);
				}
			}

			if (variableModsLoop.isEmpty()) variableModsLoop = null;
		}

		if (varModsTargetLoop != null && !varModsTargetLoop.isEmpty()) {
			variableModsTargetLoop = LinkedListMultimap.create();

			for (String s : varModsTargetLoop) {
				try {
					String[] data = s.split(" ", 2);
					variableModsTargetLoop.put(data[0], new VariableMod(data[1]));
				} catch (Exception e) {
					MagicSpells.error("Invalid variable-mods-target-loop option for spell '" + internalName + "': " + s);
				}
			}

			if (variableModsTargetLoop.isEmpty()) variableModsTargetLoop = null;
		}

		varModsLoop = null;
		varModsTargetLoop = null;
	}

	@Override
	protected void initializeModifiers() {
		super.initializeModifiers();

		if (loopModifierStrings != null && !loopModifierStrings.isEmpty())
			loopModifiers = new ModifierSet(loopModifierStrings, this);

		if (loopTargetModifierStrings != null && !loopTargetModifierStrings.isEmpty())
			loopTargetModifiers = new ModifierSet(loopTargetModifierStrings, this);

		if (loopLocationModifierStrings != null && !loopLocationModifierStrings.isEmpty())
			loopLocationModifiers = new ModifierSet(loopLocationModifierStrings, this);

		loopModifierStrings = null;
		loopTargetModifierStrings = null;
		loopLocationModifierStrings = null;
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			LivingEntity entityTarget = null;
			Location locationTarget = null;

			if (targeted) {
				if (requireEntityTarget) {
					TargetInfo<LivingEntity> info = getTargetedEntity(caster, power);

					if (info != null) {
						entityTarget = info.getTarget();
						power = info.getPower();
					}
				} else if (pointBlank) {
					locationTarget = caster.getLocation();
				} else {
					Block block = getTargetedBlock(caster, power);

					if (block != null) {
						locationTarget = block.getLocation();
						locationTarget.add(0.5, yOffset + 0.5, 0.5);
					}
				}

				if (entityTarget == null && locationTarget == null) return noTarget(caster);
			}

			initLoop(caster, entityTarget, locationTarget, power, args);

			if (entityTarget != null) {
				sendMessages(caster, entityTarget, args);
				return PostCastAction.NO_MESSAGES;
			}
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		initLoop(caster, target, null, power, null);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		initLoop(null, target, null, power, null);
		return true;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power) {
		initLoop(caster, null, target.clone().add(0, yOffset, 0), power, null);
		return true;
	}

	@Override
	public boolean castAtLocation(Location target, float power) {
		initLoop(null, null, target.clone().add(0, yOffset, 0), power, null);
		return true;
	}

	public Multimap<UUID, Loop> getActiveLoops() {
		return activeLoops;
	}

	public boolean isActive(LivingEntity entity) {
		return activeLoops.containsKey(entity.getUniqueId());
	}

	public void cancelLoops(LivingEntity entity) {
		Collection<Loop> loops = activeLoops.removeAll(entity.getUniqueId());
		loops.forEach(l -> l.cancel(false));
	}

	public void cancelLoops(UUID uuid) {
		Collection<Loop> loops = activeLoops.removeAll(uuid);
		loops.forEach(l -> l.cancel(false));
	}

	@Override
	protected void turnOff() {
		activeLoops.forEach((target, loop) -> loop.cancel(false));
		activeLoops.clear();
	}

	private void initLoop(LivingEntity caster, LivingEntity targetEntity, Location targetLocation, float power, String[] args) {
		Loop loop = new Loop(caster, targetEntity, targetLocation, power, args);
		if (targetEntity != null) activeLoops.put(targetEntity.getUniqueId(), loop);
	}

	public class Loop implements Runnable {

		private final LivingEntity caster;

		private final LivingEntity targetEntity;
		private final Location targetLocation;
		private final String[] args;
		private final float power;

		private final int taskId;
		private int count;

		private Loop(LivingEntity caster, LivingEntity targetEntity, Location targetLocation, float power, String[] args) {
			this.caster = caster;

			this.targetLocation = targetLocation;
			this.targetEntity = targetEntity;
			this.power = power;
			this.args = args;

			taskId = MagicSpells.scheduleRepeatingTask(this, delay, interval);
			if (duration > 0) MagicSpells.scheduleDelayedTask(this::cancel, duration);
		}

		@Override
		public void run() {
			if (targetEntity != null && (cancelOnDeath || !(targetEntity instanceof Player)) && !targetEntity.isValid()) {
				cancel();
				return;
			}

			if (variableModsLoop != null && (!skipFirstVariableModsLoop || count > 0) && caster instanceof Player playerCaster) {
				VariableManager variableManager = MagicSpells.getVariableManager();
				Player playerTarget = targetEntity instanceof Player t ? t : null;

				for (Map.Entry<String, VariableMod> entry : variableModsLoop.entries()) {
					VariableMod mod = entry.getValue();
					if (mod == null) continue;

					variableManager.processVariableMods(entry.getKey(), mod, playerCaster, playerCaster, playerTarget);
				}
			}

			if (variableModsTargetLoop != null && (!skipFirstVariableModsTargetLoop || count > 0) && targetEntity instanceof Player playerTarget) {
				VariableManager variableManager = MagicSpells.getVariableManager();
				Player playerCaster = caster instanceof Player p ? p : null;

				for (Map.Entry<String, VariableMod> entry : variableModsTargetLoop.entries()) {
					VariableMod mod = entry.getValue();
					if (mod == null) continue;

					variableManager.processVariableMods(entry.getKey(), mod, playerTarget, playerCaster, playerTarget);
				}
			}

			if (loopModifiers != null && (!skipFirstLoopModifiers || count > 0) && !loopModifiers.check(caster)) {
				cancel();
				return;
			}

			if (targetEntity != null && loopTargetModifiers != null && (!skipFirstLoopTargetModifiers || count > 0) && !loopTargetModifiers.check(caster, targetEntity)) {
				cancel();
				return;
			}

			if (targetLocation != null && loopLocationModifiers != null && (!skipFirstLoopLocationModifiers || count > 0) && !loopLocationModifiers.check(caster, targetLocation)) {
				cancel();
				return;
			}

			if (spells != null) {
				if (castRandomSpellInstead) {
					Subspell spell = spells.get(random.nextInt(spells.size()));
					if (!cast(spell)) return;
				} else {
					for (Subspell spell : spells)
						if (!cast(spell))
							return;
				}
			}

			if (caster != null) {
				if (targetEntity != null) playSpellEffects(caster, targetEntity);
				else if (targetLocation != null) playSpellEffects(caster, targetLocation);
				else playSpellEffects(EffectPosition.CASTER, caster);
			} else {
				if (targetEntity != null) playSpellEffects(EffectPosition.TARGET, targetEntity);
				else if (targetLocation != null) playSpellEffects(EffectPosition.TARGET, targetLocation);
			}


			count++;
			if (iterations > 0 && count >= iterations) cancel();
		}

		private boolean cast(Subspell spell) {
			boolean success;

			if (targetEntity != null) {
				if (spell.isTargetedEntitySpell())
					success = spell.castAtEntity(caster, targetEntity, power, passTargeting);
				else if (spell.isTargetedLocationSpell())
					success = spell.castAtLocation(caster, targetEntity.getLocation(), power);
				else {
					PostCastAction action = spell.cast(caster, power);
					success = action == PostCastAction.HANDLE_NORMALLY || action == PostCastAction.NO_MESSAGES;
				}
			} else if (targetLocation != null) {
				if (spell.isTargetedLocationSpell())
					success = spell.castAtLocation(caster, targetLocation, power);
				else {
					PostCastAction action = spell.cast(caster, power);
					success = action == PostCastAction.HANDLE_NORMALLY || action == PostCastAction.NO_MESSAGES;
				}
			} else {
				PostCastAction action = spell.cast(caster, power);
				success = action == PostCastAction.HANDLE_NORMALLY || action == PostCastAction.NO_MESSAGES;
			}

			if (stopOnFail && !success) {
				cancel();
				return false;
			}

			return true;
		}

		public LoopSpell getSpell() {
			return LoopSpell.this;
		}

		public LivingEntity getCaster() {
			return caster;
		}

		private void cancel() {
			cancel(true);
		}

		private void cancel(boolean remove) {
			MagicSpells.cancelTask(taskId);

			if (targetEntity != null) {
				if (remove) activeLoops.remove(targetEntity.getUniqueId(), this);
				playSpellEffects(EffectPosition.DELAYED, targetEntity);
			} else if (targetLocation != null) playSpellEffects(EffectPosition.DELAYED, targetLocation);
			else if (caster != null) playSpellEffects(EffectPosition.DELAYED, caster);

			if (caster != null || targetEntity != null) {
				Player playerCaster = caster instanceof Player p ? p : null;
				Player playerTarget = targetEntity instanceof Player p ? p : null;

				String casterName = caster != null ? getTargetName(caster) : "";
				String targetName = targetEntity != null ? getTargetName(targetEntity) : "";

				if (playerCaster != null)
					sendMessage(prepareMessage(strFadeSelf, playerCaster, playerTarget), playerCaster, args,
						"%a", casterName, "%t", targetName);

				if (playerTarget != null)
					sendMessage(prepareMessage(strFadeTarget, playerCaster, playerTarget), playerTarget, args,
						"%a", casterName, "%t", targetName);
			}

			if (spellOnEnd != null) {
				if (spellOnEnd.isTargetedEntitySpell() && targetEntity != null)
					spellOnEnd.castAtEntity(caster, targetEntity, power, passTargeting);
				else if (spellOnEnd.isTargetedLocationSpell() && (targetEntity != null || targetLocation != null))
					spellOnEnd.castAtLocation(caster, targetEntity != null ? targetEntity.getLocation() : targetLocation, power);
				else spellOnEnd.cast(caster, power);
			}
		}

	}

	private static class DeathListener implements Listener {

		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		public void onDeath(PlayerDeathEvent event) {
			UUID uuid = event.getEntity().getUniqueId();

			List<Spell> spells = MagicSpells.getSpellsOrdered();
			for (Spell spell : spells) {
				if (!(spell instanceof LoopSpell loopSpell) || !loopSpell.cancelOnDeath) continue;
				loopSpell.cancelLoops(uuid);
			}
		}

	}

}