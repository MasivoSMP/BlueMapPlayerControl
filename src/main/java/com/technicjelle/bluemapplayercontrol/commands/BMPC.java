package com.technicjelle.bluemapplayercontrol.commands;

import com.technicjelle.bluemapplayercontrol.BlueMapPlayerControl;
import de.bluecolored.bluemap.api.BlueMapAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class BMPC implements CommandExecutor, TabCompleter {
	private final BlueMapPlayerControl plugin;

	public BMPC(BlueMapPlayerControl plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
		if (BlueMapAPI.getInstance().isPresent()) {
			BlueMapAPI api = BlueMapAPI.getInstance().get();

			if (args.length == 0) {
				plugin.sendConfiguredMessage(sender, "base-command");
				return true;
			}

			String subCommand = args[0].toLowerCase(Locale.ROOT);
			Boolean shouldBeVisible = switch (subCommand) {
				case "mostrar" -> true;
				case "ocultar" -> false;
				default -> null;
			};

			if (shouldBeVisible == null) {
				plugin.sendConfiguredMessage(sender, "invalid-subcommand", "subcommand", args[0]);
				return true;
			}

			// === SELF ===
			if (args.length == 1) {
				if (!(sender instanceof Player player)) {
					plugin.sendConfiguredMessage(sender, "must-be-player");
					return true;
				}

				setSelfVisibility(api, sender, player.getUniqueId(), shouldBeVisible);
				return true;
			}

			// === OTHER ===
			if (!othersAllowed(sender)) {
				plugin.sendConfiguredMessage(sender, "no-permission-others");
				return true;
			}

			String targetName = args[1];
			List<Entity> targets = Bukkit.selectEntities(sender, targetName);
			if (targets.isEmpty()) {
				plugin.sendConfiguredMessage(sender, "player-not-found", "player", targetName);
				return true;
			}

			boolean foundPlayer = false;
			for (Entity target : targets) {
				if (!(target instanceof Player targetPlayer)) {
					continue;
				}
				foundPlayer = true;
				setOtherVisibility(api, sender, targetPlayer, shouldBeVisible);
			}

			if (!foundPlayer) {
				plugin.sendConfiguredMessage(sender, "player-not-found", "player", targetName);
			}
			return true;
		}

		return false;
	}

	private void setSelfVisibility(BlueMapAPI blueMapAPI, CommandSender sender, UUID senderUUID, boolean shouldBeVisible) {
		blueMapAPI.getWebApp().setPlayerVisibility(senderUUID, shouldBeVisible);
		plugin.setPlayerVisibilityPreference(senderUUID, shouldBeVisible);
		plugin.sendConfiguredMessage(sender, shouldBeVisible ? "self-visible" : "self-invisible");
	}

	private void setOtherVisibility(BlueMapAPI api, @NotNull CommandSender sender, Player targetPlayer, boolean shouldBeVisible) {
		api.getWebApp().setPlayerVisibility(targetPlayer.getUniqueId(), shouldBeVisible);
		plugin.setPlayerVisibilityPreference(targetPlayer.getUniqueId(), shouldBeVisible);
		plugin.sendConfiguredMessage(sender, shouldBeVisible ? "other-visible" : "other-invisible", "player", targetPlayer.getName());
	}

	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
		List<String> completions = new ArrayList<>();
		if (args.length == 1) {
			completions.add("mostrar");
			completions.add("ocultar");
		} else if (args.length == 2 && othersAllowed(sender)) {
			if (args[0].equalsIgnoreCase("mostrar") || args[0].equalsIgnoreCase("ocultar")) {
				for (Player player : sender.getServer().getOnlinePlayers()) {
					completions.add(player.getName());
				}
				completions.add("@a");
				completions.add("@p");
				completions.add("@r");
				completions.add("@s");
			}
		}
		return completions;
	}

	private boolean othersAllowed(CommandSender sender) {
		return sender.isOp() || sender.hasPermission("bmpc.others");
	}
}
