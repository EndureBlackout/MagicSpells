package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.Set;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;

import de.slikey.effectlib.Effect;
import de.slikey.effectlib.effect.ModifiedEffect;

import com.nisovin.magicspells.util.*;
import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.zones.NoMagicZoneManager;
import com.nisovin.magicspells.spelleffects.SpellEffect;
import com.nisovin.magicspells.castmodifiers.ModifierSet;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.events.SpellPreImpactEvent;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spelleffects.util.EffectlibSpellEffect;
import com.nisovin.magicspells.spells.TargetedEntityFromLocationSpell;

public class HomingMissileSpell extends TargetedSpell implements TargetedEntitySpell, TargetedEntityFromLocationSpell {

	private NoMagicZoneManager zoneManager;

	private ModifierSet homingModifiers;
	private List<String> homingModifiersStrings;

	private final ConfigData<Vector> effectOffset;
	private final ConfigData<Vector> relativeOffset;
	private final ConfigData<Vector> targetRelativeOffset;

	private final ConfigData<Boolean> hitGround;
	private final ConfigData<Boolean> hitAirDuring;
	private final ConfigData<Boolean> stopOnHitTarget;
	private final ConfigData<Boolean> stopOnHitGround;
	private final ConfigData<Boolean> stopOnModifierFail;
	private final ConfigData<Boolean> hitAirAfterDuration;

	private String hitSpellName;
	private String airSpellName;
	private String groundSpellName;
	private String modifierSpellName;
	private String durationSpellName;
	private String entityLocationSpellName;

	private Subspell hitSpell;
	private Subspell airSpell;
	private Subspell groundSpell;
	private Subspell modifierSpell;
	private Subspell durationSpell;
	private Subspell entityLocationSpell;

	private final ConfigData<Double> maxDuration;

	private final ConfigData<Float> yOffset;
	private final ConfigData<Float> hitRadius;
	private final ConfigData<Float> verticalHitRadius;
	private final ConfigData<Float> projectileInertia;
	private final ConfigData<Float> projectileVelocity;

	private final ConfigData<Integer> tickInterval;
	private final ConfigData<Integer> airSpellInterval;
	private final ConfigData<Integer> specialEffectInterval;
	private final ConfigData<Integer> intermediateSpecialEffects;

	public HomingMissileSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		homingModifiersStrings = getConfigStringList("homing-modifiers", null);

		effectOffset = getConfigDataVector("effect-offset", new Vector(0, 0, 0));
		relativeOffset = getConfigDataVector("relative-offset", new Vector(0, 0.6, 0));
		targetRelativeOffset = getConfigDataVector("target-relative-offset", new Vector(0, 0.6, 0));

		hitGround = getConfigDataBoolean("hit-ground", false);
		hitAirDuring = getConfigDataBoolean("hit-air-during", false);
		stopOnHitTarget = getConfigDataBoolean("stop-on-hit-target", true);
		stopOnHitGround = getConfigDataBoolean("stop-on-hit-ground", false);
		stopOnModifierFail = getConfigDataBoolean("stop-on-modifier-fail", true);
		hitAirAfterDuration = getConfigDataBoolean("hit-air-after-duration", false);

		hitSpellName = getConfigString("spell", "");
		airSpellName = getConfigString("spell-on-hit-air", "");
		groundSpellName = getConfigString("spell-on-hit-ground", "");
		modifierSpellName = getConfigString("spell-on-modifier-fail", "");
		durationSpellName = getConfigString("spell-after-duration", "");
		entityLocationSpellName = getConfigString("spell-on-entity-location", "");

		maxDuration = getConfigDataDouble("max-duration", 20);

		yOffset = getConfigDataFloat("y-offset", 0.6F);
		hitRadius = getConfigDataFloat("hit-radius", 1.5F);
		verticalHitRadius = getConfigDataFloat("vertical-hit-radius", hitRadius);
		projectileInertia = getConfigDataFloat("projectile-inertia", 1.5F);
		projectileVelocity = getConfigDataFloat("projectile-velocity", 5F);

		tickInterval = getConfigDataInt("tick-interval", 2);
		airSpellInterval = getConfigDataInt("spell-interval", 20);
		specialEffectInterval = getConfigDataInt("special-effect-interval", 2);
		intermediateSpecialEffects = getConfigDataInt("intermediate-special-effect-locations", 0);
	}

	@Override
	public void initializeModifiers() {
		super.initializeModifiers();

		if (homingModifiersStrings != null && !homingModifiersStrings.isEmpty()) {
			homingModifiers = new ModifierSet(homingModifiersStrings, this);
			homingModifiersStrings = null;
		}
	}

	@Override
	public void initialize() {
		super.initialize();

		hitSpell = new Subspell(hitSpellName);
		if (!hitSpell.process()) {
			hitSpell = null;
			if (!hitSpellName.isEmpty())
				MagicSpells.error("HomingMissileSpell '" + internalName + "' has an invalid spell defined!");
		}
		hitSpellName = null;

		groundSpell = new Subspell(groundSpellName);
		if (!groundSpell.process()) {
			groundSpell = null;
			if (!groundSpellName.isEmpty())
				MagicSpells.error("HomingMissileSpell '" + internalName + "' has an invalid spell-on-hit-ground defined!");
		}
		groundSpellName = null;

		airSpell = new Subspell(airSpellName);
		if (!airSpell.process()) {
			airSpell = null;
			if (!airSpellName.isEmpty())
				MagicSpells.error("HomingMissileSpell '" + internalName + "' has an invalid spell-on-hit-air defined!");
		}
		airSpellName = null;

		durationSpell = new Subspell(durationSpellName);
		if (!durationSpell.process()) {
			durationSpell = null;
			if (!durationSpellName.isEmpty())
				MagicSpells.error("HomingMissileSpell '" + internalName + "' has an invalid spell-after-duration defined!");
		}
		durationSpellName = null;

		modifierSpell = new Subspell(modifierSpellName);
		if (!modifierSpell.process()) {
			if (!modifierSpellName.isEmpty())
				MagicSpells.error("HomingMissileSpell '" + internalName + "' has an invalid spell-on-modifier-fail defined!");
			modifierSpell = null;
		}
		modifierSpellName = null;

		entityLocationSpell = new Subspell(entityLocationSpellName);
		if (!entityLocationSpell.process()) {
			if (!entityLocationSpellName.isEmpty())
				MagicSpells.error("HomingMissileSpell '" + internalName + "' has an invalid spell-on-entity-location defined!");
			entityLocationSpell = null;
		}
		entityLocationSpellName = null;

		zoneManager = MagicSpells.getNoMagicZoneManager();
	}

	@Override
	public CastResult cast(SpellData data) {
		ValidTargetChecker checker = hitSpell != null ? hitSpell.getSpell().getValidTargetChecker() : null;
		TargetInfo<LivingEntity> info = getTargetedEntity(data, checker);
		if (info.noTarget()) return noTarget(info);
		data = info.spellData().location(data.caster().getLocation());

		new MissileTracker(data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public CastResult castAtEntity(SpellData data) {
		if (!data.hasCaster()) return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		data = data.location(data.caster().getLocation());
		new MissileTracker(data.location(data.caster().getLocation()));

		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public CastResult castAtEntityFromLocation(SpellData data) {
		new MissileTracker(data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	private class MissileTracker implements Runnable {

		private Location currentLocation;
		private Vector currentVelocity;
		private SpellData data;

		private final Set<EffectlibSpellEffect> effectSet;
		private final Map<SpellEffect, Entity> entityMap;
		private final Set<ArmorStand> armorStandSet;

		private final BoundingBox hitBox;
		private final long startTime;
		private final int taskId;

		private final Vector effectOffset;
		private final Vector targetRelativeOffset;

		private final double maxDuration;

		private final float velocityPerTick;
		private final float projectileInertia;

		private final int airSpellInterval;
		private final int specialEffectInterval;
		private final int intermediateSpecialEffects;

		private final boolean hitGround;
		private final boolean hitAirDuring;
		private final boolean stopOnHitTarget;
		private final boolean stopOnHitGround;
		private final boolean stopOnModifierFail;
		private final boolean hitAirAfterDuration;

		private int counter = 0;

		private MissileTracker(SpellData data) {
			startTime = System.currentTimeMillis();

			currentLocation = data.location();

			float yOffset = HomingMissileSpell.this.yOffset.get(data);

			Vector relativeOffset = HomingMissileSpell.this.relativeOffset.get(data);
			if (yOffset != 0.6f) relativeOffset = relativeOffset.clone().setY(yOffset);

			Vector startDir = currentLocation.getDirection();
			Vector horizOffset = new Vector(-startDir.getZ(), 0.0, startDir.getX()).normalize();
			currentLocation.add(horizOffset.multiply(relativeOffset.getZ()));
			currentLocation.add(startDir.multiply(relativeOffset.getX()));
			currentLocation.setY(currentLocation.getY() + relativeOffset.getY());

			currentVelocity = data.target().getLocation().subtract(currentLocation).toVector();
			currentLocation.setDirection(currentVelocity);
			data = data.location(currentLocation);
			this.data = data;

			float projectileVelocity = HomingMissileSpell.this.projectileVelocity.get(data);
			int tickInterval = HomingMissileSpell.this.tickInterval.get(data);
			velocityPerTick = projectileVelocity * tickInterval / 20;
			if (!currentVelocity.isZero()) currentVelocity.normalize().multiply(velocityPerTick);

			effectOffset = HomingMissileSpell.this.effectOffset.get(data);
			targetRelativeOffset = HomingMissileSpell.this.targetRelativeOffset.get(data);

			maxDuration = HomingMissileSpell.this.maxDuration.get(data) * TimeUtil.MILLISECONDS_PER_SECOND;

			projectileInertia = HomingMissileSpell.this.projectileInertia.get(data);

			airSpellInterval = HomingMissileSpell.this.airSpellInterval.get(data);
			specialEffectInterval = HomingMissileSpell.this.specialEffectInterval.get(data);

			intermediateSpecialEffects = Math.min(HomingMissileSpell.this.intermediateSpecialEffects.get(data), 0);

			float hitRadius = HomingMissileSpell.this.hitRadius.get(data);
			float verticalHitRadius = HomingMissileSpell.this.verticalHitRadius.get(data);
			hitBox = new BoundingBox(currentLocation, hitRadius, verticalHitRadius);

			hitGround = HomingMissileSpell.this.hitGround.get(data);
			hitAirDuring = HomingMissileSpell.this.hitAirDuring.get(data);
			stopOnHitTarget = HomingMissileSpell.this.stopOnHitTarget.get(data);
			stopOnHitGround = HomingMissileSpell.this.stopOnHitGround.get(data);
			stopOnModifierFail = HomingMissileSpell.this.stopOnModifierFail.get(data);
			hitAirAfterDuration = HomingMissileSpell.this.hitAirAfterDuration.get(data);

			effectSet = playSpellEffectLibEffects(EffectPosition.PROJECTILE, currentLocation, data);
			entityMap = playSpellEntityEffects(EffectPosition.PROJECTILE, currentLocation, data);
			armorStandSet = playSpellArmorStandEffects(EffectPosition.PROJECTILE, currentLocation, data);

			if (data.hasCaster()) playSpellEffects(EffectPosition.CASTER, data.caster(), data);

			taskId = MagicSpells.scheduleRepeatingTask(this, 0, tickInterval);
		}

		@Override
		public void run() {
			if ((data.hasCaster() && !data.caster().isValid()) || !data.target().isValid()) {
				stop();
				return;
			}

			if (!currentLocation.getWorld().equals(data.target().getWorld())) {
				stop();
				return;
			}

			if (zoneManager.willFizzle(currentLocation, HomingMissileSpell.this)) {
				stop();
				return;
			}

			if (homingModifiers != null && data.hasCaster()) {
				ModifierResult result = homingModifiers.apply(data.caster(), data);
				data = result.data();

				if (!result.check()) {
					if (modifierSpell != null) modifierSpell.subcast(data.noTarget());
					if (stopOnModifierFail) stop();
					return;
				}
			}

			if (maxDuration > 0 && startTime + maxDuration < System.currentTimeMillis()) {
				if (hitAirAfterDuration && durationSpell != null) durationSpell.subcast(data.noTarget());
				stop();
				return;
			}

			Location oldLocation = currentLocation.clone();

			// Calculate target location aka targetRelativeOffset
			Location targetLoc = data.target().getLocation();
			Vector startDir = targetLoc.getDirection();
			Vector horizOffset = new Vector(-startDir.getZ(), 0.0, startDir.getX()).normalize();
			targetLoc.add(horizOffset.multiply(targetRelativeOffset.getZ()));
			targetLoc.add(startDir.multiply(targetRelativeOffset.getX()));
			targetLoc.setY(targetLoc.getY() + targetRelativeOffset.getY());

			// Move projectile and calculate new vector
			currentLocation.add(currentVelocity);
			Vector oldVelocity = currentVelocity.clone();
			data = data.location(currentLocation);

			currentVelocity.multiply(projectileInertia);
			Vector force = targetLoc.clone().subtract(currentLocation).toVector();
			if (!force.isZero()) currentVelocity.add(force.normalize());
			currentVelocity.normalize().multiply(velocityPerTick);

			if (armorStandSet != null || entityMap != null) {
				// Changing the effect location
				Vector dir = currentLocation.getDirection().normalize();
				Vector offset = new Vector(-dir.getZ(), 0.0, dir.getX()).normalize();
				Location effectLoc = currentLocation.clone();
				effectLoc.add(offset.multiply(effectOffset.getZ()));
				effectLoc.add(effectLoc.getDirection().multiply(effectOffset.getX()));
				effectLoc.setY(effectLoc.getY() + effectOffset.getY());
				effectLoc = Util.makeFinite(effectLoc);

				if (armorStandSet != null) {
					for (ArmorStand armorStand : armorStandSet) {
						armorStand.teleportAsync(effectLoc);
					}
				}

				if (entityMap != null) {
					for (var entry : entityMap.entrySet()) {
						entry.getValue().teleportAsync(entry.getKey().applyOffsets(effectLoc.clone()));
					}
				}
			}

			if (stopOnHitGround && !BlockUtils.isPathable(currentLocation.getBlock())) {
				if (hitGround && groundSpell != null) groundSpell.subcast(data.noTarget());
				stop();
				return;
			}

			if (hitAirDuring && airSpellInterval > 0 && counter % airSpellInterval == 0 && airSpell != null)
				airSpell.subcast(data.noTarget());

			if (intermediateSpecialEffects > 0) playIntermediateEffectLocations(oldLocation, oldVelocity);

			// Update the location direction and play the effect
			currentLocation.setDirection(currentVelocity);
			playMissileEffect(currentLocation);
			if (effectSet != null) {
				Effect effect;
				Location effectLoc;
				for (EffectlibSpellEffect spellEffect : effectSet) {
					if (spellEffect == null) continue;
					effect = spellEffect.getEffect();
					if (effect == null) continue;

					effectLoc = spellEffect.getSpellEffect().applyOffsets(currentLocation.clone(), data);
					effect.setLocation(effectLoc);

					if (effect instanceof ModifiedEffect) {
						Effect modifiedEffect = ((ModifiedEffect) effect).getInnerEffect();
						if (modifiedEffect != null) modifiedEffect.setLocation(effectLoc);
					}
				}
			}

			if (specialEffectInterval > 0 && counter % specialEffectInterval == 0)
				playSpellEffects(EffectPosition.SPECIAL, currentLocation, data);

			counter++;

			if (hitSpell == null) return;

			hitBox.setCenter(currentLocation);
			if (hitBox.contains(targetLoc)) {
				SpellPreImpactEvent preImpact = new SpellPreImpactEvent(hitSpell.getSpell(), HomingMissileSpell.this, data.caster(), data.target(), data.power());
				EventUtil.call(preImpact);
				// Should we bounce the missile back?
				if (!preImpact.getRedirected()) {
					// Apparently didn't get redirected, carry out the plans
					hitSpell.subcast(data.noLocation());
					if (entityLocationSpell != null) entityLocationSpell.subcast(data.noTarget());

					playSpellEffects(EffectPosition.TARGET, data.target(), data);
					if (stopOnHitTarget) stop();
				} else {
					if (!data.hasCaster()) {
						stop();
						return;
					}

					currentVelocity.multiply(-1);
					data = data.power(preImpact.getPower()).invert();
				}
			}

		}

		private void playIntermediateEffectLocations(Location old, Vector movement) {
			int divideFactor = intermediateSpecialEffects + 1;
			movement.setX(movement.getX() / divideFactor);
			movement.setY(movement.getY() / divideFactor);
			movement.setZ(movement.getZ() / divideFactor);
			for (int i = 0; i < intermediateSpecialEffects; i++) {
				old = old.add(movement).setDirection(movement);
				playMissileEffect(old);
			}
		}

		private void playMissileEffect(Location loc) {
			playSpellEffects(EffectPosition.SPECIAL, loc, data);
		}

		private void stop() {
			playSpellEffects(EffectPosition.DELAYED, currentLocation, data);
			MagicSpells.cancelTask(taskId);
			if (effectSet != null) {
				for (EffectlibSpellEffect spellEffect : effectSet) {
					if (spellEffect == null) continue;
					if (spellEffect.getEffect() == null) continue;
					spellEffect.getEffect().cancel();
				}
				effectSet.clear();
			}
			if (armorStandSet != null) {
				for (ArmorStand armorStand : armorStandSet) {
					armorStand.remove();
				}
				armorStandSet.clear();
			}
			if (entityMap != null) {
				for (Entity entity : entityMap.values()) {
					entity.remove();
				}
				entityMap.clear();
			}
			currentLocation = null;
			currentVelocity = null;
		}

	}

}
