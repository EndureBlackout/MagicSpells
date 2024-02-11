package com.nisovin.magicspells.listeners;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Spellbook;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;

import io.papermc.paper.event.player.AsyncChatEvent;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class MagicChatListener implements Listener {
	
	@EventHandler(ignoreCancelled = true)
	public void onPlayerChat(final AsyncChatEvent event) {
		handleIncantation(event.getPlayer(), Util.getStringFromComponent(event.message()));
	}
	
	@EventHandler(ignoreCancelled = true)
	public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
		boolean casted = handleIncantation(event.getPlayer(), event.getMessage());
		if (casted) event.setCancelled(true);
	}
	
	private boolean handleIncantation(Player player, String message) {
		if (message.contains(" ")) {
			String[] split = message.split(" ");
			Spell spell = MagicSpells.getIncantations().get(split[0].toLowerCase() + " *");
			if (spell != null) {
				Spellbook spellbook = MagicSpells.getSpellbook(player);
				if (spellbook.hasSpell(spell)) {
					String[] args = new String[split.length - 1];
					System.arraycopy(split, 1, args, 0, args.length);
					MagicSpells.scheduleDelayedTask(() -> spell.hardCast(new SpellData(player, 1f, args)), 0);
					return true;
				}
				return false;
			}
		}
		Spell spell = MagicSpells.getIncantations().get(message.toLowerCase());
		if (spell != null) {
			Spellbook spellbook = MagicSpells.getSpellbook(player);
			if (spellbook.hasSpell(spell)) {
				MagicSpells.scheduleDelayedTask(() -> spell.hardCast(new SpellData(player)), 0);
				return true;
			}
		}
		return false;
	}
	
}
