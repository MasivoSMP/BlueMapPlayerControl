package com.technicjelle.bluemapplayercontrol;

import com.technicjelle.UpdateChecker;
import com.technicjelle.bluemapplayercontrol.commands.BMPC;
import de.bluecolored.bluemap.api.BlueMapAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class BlueMapPlayerControl extends JavaPlugin implements Listener {
	private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
	private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

	UpdateChecker updateChecker;
	BMPC executor;
	private final Map<UUID, Boolean> visibilityPreferences = new HashMap<>();
	private Connection sqliteConnection;
	private ExecutorService databaseExecutor;

	@Override
	public void onEnable() {
		getLogger().info("BlueMapPlayerControl enabled");

		saveDefaultConfig();
		getConfig().options().copyDefaults(true);
		saveConfig();

		loadVisibilityPreferences();

		new Metrics(this, 18378);

		updateChecker = new UpdateChecker("TechnicJelle", "BlueMapPlayerControl", getDescription().getVersion());
		updateChecker.checkAsync();

		BlueMapAPI.onEnable(api -> {
			updateChecker.logUpdateMessage(getLogger());
			applyVisibilityToOnlinePlayers(api);
		});

		PluginCommand mapaweb = Bukkit.getPluginCommand("mapaweb");
		executor = new BMPC(this);
		if(mapaweb != null) {
			mapaweb.setExecutor(executor);
			mapaweb.setTabCompleter(executor);
		} else {
			getLogger().warning("mapaweb is null. This is not good");
		}

		Bukkit.getPluginManager().registerEvents(this, this);

		BlueMapAPI.getInstance().ifPresent(this::applyVisibilityToOnlinePlayers);
	}

	@Override
	public void onDisable() {
		shutdownDatabaseExecutor();
		closeDatabaseConnection();
		getLogger().info("BlueMapPlayerControl disabled");
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		BlueMapAPI.getInstance().ifPresent(api -> applyVisibility(api, event.getPlayer()));
	}

	private void applyVisibilityToOnlinePlayers(BlueMapAPI api) {
		for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
			applyVisibility(api, onlinePlayer);
		}
	}

	private void applyVisibility(BlueMapAPI api, Player player) {
		boolean visibility = getPlayerVisibilityPreference(player.getUniqueId());
		api.getWebApp().setPlayerVisibility(player.getUniqueId(), visibility);
	}

	public boolean getPlayerVisibilityPreference(UUID playerUUID) {
		return visibilityPreferences.getOrDefault(playerUUID, getConfig().getBoolean("default-visibility", false));
	}

	public void setPlayerVisibilityPreference(UUID playerUUID, boolean visibility) {
		visibilityPreferences.put(playerUUID, visibility);
		saveVisibilityPreferenceAsync(playerUUID, visibility);
	}

	public void sendConfiguredMessage(CommandSender sender, String key) {
		sendConfiguredMessage(sender, key, Map.of());
	}

	public void sendConfiguredMessage(CommandSender sender, String key, String placeholderKey, String placeholderValue) {
		sendConfiguredMessage(sender, key, Map.of(placeholderKey, placeholderValue));
	}

	public void sendConfiguredMessage(CommandSender sender, String key, Map<String, String> placeholders) {
		String template = getConfig().getString("messages." + key, key);
		TagResolver[] resolvers = placeholders.entrySet().stream()
				.map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
				.toArray(TagResolver[]::new);
		try {
			Component component = MINI_MESSAGE.deserialize(template, resolvers);
			sender.sendMessage(LEGACY_SERIALIZER.serialize(component));
		} catch (Exception exception) {
			getLogger().warning("Invalid MiniMessage format for messages." + key + ": " + exception.getMessage());
			sender.sendMessage(template);
		}
	}

	private void loadVisibilityPreferences() {
		initializeDatabase();
		loadVisibilityPreferencesFromDatabase();
	}

	private void initializeDatabase() {
		if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
			getLogger().warning("Could not create plugin data folder");
			return;
		}

		File sqliteFile = new File(getDataFolder(), "player-visibility.db");

		try {
			sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
			try (Statement statement = sqliteConnection.createStatement()) {
				statement.executeUpdate("""
						CREATE TABLE IF NOT EXISTS player_visibility (
						  uuid TEXT PRIMARY KEY,
						  visible INTEGER NOT NULL
						)
						""");
			}
		} catch (SQLException exception) {
			getLogger().severe("Could not initialize SQLite database: " + exception.getMessage());
			return;
		}

		databaseExecutor = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "BlueMapPlayerControl-SQLite");
			thread.setDaemon(true);
			return thread;
		});
	}

	private void loadVisibilityPreferencesFromDatabase() {
		if (sqliteConnection == null) {
			return;
		}

		visibilityPreferences.clear();
		try (PreparedStatement statement = sqliteConnection.prepareStatement("SELECT uuid, visible FROM player_visibility");
			 ResultSet resultSet = statement.executeQuery()) {
			while (resultSet.next()) {
				String uuidValue = resultSet.getString("uuid");
				try {
					UUID playerUUID = UUID.fromString(uuidValue);
					visibilityPreferences.put(playerUUID, resultSet.getInt("visible") == 1);
				} catch (IllegalArgumentException exception) {
					getLogger().warning("Invalid UUID in SQLite database: " + uuidValue);
				}
			}
		} catch (SQLException exception) {
			getLogger().warning("Could not load visibility preferences from SQLite: " + exception.getMessage());
		}
	}

	private void saveVisibilityPreferenceAsync(UUID playerUUID, boolean visibility) {
		if (sqliteConnection == null || databaseExecutor == null) {
			return;
		}

		databaseExecutor.execute(() -> upsertVisibilityPreference(playerUUID, visibility));
	}

	private void upsertVisibilityPreference(UUID playerUUID, boolean visibility) {
		try (PreparedStatement statement = sqliteConnection.prepareStatement("""
				INSERT INTO player_visibility (uuid, visible)
				VALUES (?, ?)
				ON CONFLICT(uuid) DO UPDATE SET visible = excluded.visible
				""")) {
			statement.setString(1, playerUUID.toString());
			statement.setInt(2, visibility ? 1 : 0);
			statement.executeUpdate();
		} catch (SQLException exception) {
			getLogger().warning("Could not save visibility preference for " + playerUUID + ": " + exception.getMessage());
		}
	}

	private void shutdownDatabaseExecutor() {
		if (databaseExecutor == null) {
			return;
		}

		databaseExecutor.shutdown();
		try {
			if (!databaseExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
				databaseExecutor.shutdownNow();
			}
		} catch (InterruptedException exception) {
			databaseExecutor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private void closeDatabaseConnection() {
		if (sqliteConnection == null) {
			return;
		}

		try {
			sqliteConnection.close();
		} catch (SQLException exception) {
			getLogger().warning("Could not close SQLite connection: " + exception.getMessage());
		}
	}
}
