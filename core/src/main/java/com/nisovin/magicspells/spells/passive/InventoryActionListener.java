package com.nisovin.magicspells.spells.passive;

import java.util.EnumSet;

import org.jetbrains.annotations.NotNull;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import com.nisovin.magicspells.util.Name;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

@Name("inventoryaction")
public class InventoryActionListener extends PassiveListener {

	private final EnumSet<InventoryAction> actions = EnumSet.noneOf(InventoryAction.class);

	@Override
	public void initialize(@NotNull String var) {
		if (var.isEmpty()) return;
		for (String s : var.replace(" ", "").split(",")) {
			try {
				InventoryAction action = InventoryAction.valueOf(s.toUpperCase());
				actions.add(action);
			} catch (IllegalArgumentException e) {
				MagicSpells.error("Invalid inventory action '" + s + "' in inventory trigger on passive spell '" + passiveSpell.getInternalName() + "'");
			}
		}
	}

	@OverridePriority
	@EventHandler
	public void onInventoryOpen(InventoryOpenEvent event) {
		if (!isCancelStateOk(event.isCancelled())) return;

		if (!actions.isEmpty() && !actions.contains(InventoryAction.OPEN)) return;

		HumanEntity caster = event.getPlayer();
		if (!canTrigger(caster)) return;

		boolean casted = passiveSpell.activate(caster);
		if (cancelDefaultAction(casted)) event.setCancelled(true);
	}

	@OverridePriority
	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		if (!actions.isEmpty() && !actions.contains(InventoryAction.CLOSE)) return;

		HumanEntity caster = event.getPlayer();
		if (!canTrigger(caster)) return;

		passiveSpell.activate(caster);
	}

	private enum InventoryAction {

		OPEN,
		CLOSE

	}

}
