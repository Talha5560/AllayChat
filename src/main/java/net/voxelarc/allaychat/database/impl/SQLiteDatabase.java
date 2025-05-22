package net.voxelarc.allaychat.database.impl;

import lombok.RequiredArgsConstructor;
import net.voxelarc.allaychat.AllayChatPlugin;
import net.voxelarc.allaychat.api.database.Database;
import net.voxelarc.allaychat.api.user.ChatUser;
import net.voxelarc.allaychat.database.Queries;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;

@RequiredArgsConstructor
public class SQLiteDatabase implements Database {

    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

    protected final AllayChatPlugin plugin;

    private File file;

    protected String usersTable;
    protected String ignoredTable;

    @Override
    public void onEnable() {
        plugin.getDataFolder().mkdirs();

        file = new File(plugin.getDataFolder(), "database.db");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to create database file: " + e.getMessage());
            }
        }

        usersTable = plugin.getConfig().getString("database.users-table", "allaychat_users");
        ignoredTable = plugin.getConfig().getString("database.ignored-table", "allaychat_ignored");

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(Queries.CREATE_USER_TABLE.getQuery(usersTable));
            statement.executeUpdate(Queries.CREATE_IGNORE_TABLE.getQuery(ignoredTable, usersTable));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database thrown an exception!", e);
        }
    }

    @Override
    public void onDisable() {
        saveAllPlayers();
    }

    @Override
    public Connection getConnection() {
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + file);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database thrown an exception!", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<ChatUser> loadPlayerAsync(UUID uniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(Queries.GET_USER.getQuery(usersTable))) {
                statement.setString(1, uniqueId.toString());
                var resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    ChatUser user = new ChatUser(uniqueId);
                    user.setMsgEnabled(resultSet.getBoolean("msgEnabled"));
                    user.setSpyEnabled(resultSet.getBoolean("spyEnabled"));
                    user.setStaffEnabled(resultSet.getBoolean("staffEnabled"));
                    user.setMentionsEnabled(resultSet.getBoolean("mentionsEnabled"));

                    try (PreparedStatement ignoredStatement = connection.prepareStatement(Queries.GET_ALL_IGNORED.getQuery(ignoredTable))) {
                        ignoredStatement.setString(1, uniqueId.toString());
                        var ignoredResultSet = ignoredStatement.executeQuery();
                        while (ignoredResultSet.next()) {
                            user.getIgnoredPlayers().add(ignoredResultSet.getString("ignoredPlayerName"));
                        }
                    }

                    return user;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Database thrown an exception!", e);
                return null;
            }

            // If the user is not found in the database, create a new ChatUser with default values
            return new ChatUser(uniqueId);
        }, EXECUTOR);
    }

    @Override
    public CompletableFuture<Boolean> savePlayerAsync(ChatUser user) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(Queries.SAVE_USER.getQuery(usersTable))) {
                statement.setString(1, user.getUniqueId().toString());
                statement.setBoolean(2, user.isMsgEnabled());
                statement.setBoolean(3, user.isSpyEnabled());
                statement.setBoolean(4, user.isStaffEnabled());
                statement.setBoolean(5, user.isMentionsEnabled());
                statement.executeUpdate();

                // Save ignored players
                try (PreparedStatement deleteStatement = connection.prepareStatement(Queries.DELETE_ALL_IGNORED.getQuery(ignoredTable))) {
                    deleteStatement.setString(1, user.getUniqueId().toString());
                    deleteStatement.executeUpdate();
                }

                for (String ignoredPlayer : user.getIgnoredPlayers()) {
                    try (PreparedStatement addIgnoredStatement = connection.prepareStatement(Queries.ADD_IGNORED.getQuery(ignoredTable))) {
                        addIgnoredStatement.setString(1, user.getUniqueId().toString());
                        addIgnoredStatement.setString(2, ignoredPlayer);
                        addIgnoredStatement.executeUpdate();
                    }
                }

                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Database thrown an exception!", e);
                return false;
            }
        }, EXECUTOR);
    }

    @Override
    public void saveAllPlayers() {
        if (plugin.getUserManager().getAllUsers().isEmpty()) return;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(Queries.SAVE_USER.getQuery(usersTable))) {

            connection.setAutoCommit(false);

            for (ChatUser user : plugin.getUserManager().getAllUsers()) {
                statement.setString(1, user.getUniqueId().toString());
                statement.setBoolean(2, user.isMsgEnabled());
                statement.setBoolean(3, user.isSpyEnabled());
                statement.setBoolean(4, user.isStaffEnabled());
                statement.setBoolean(5, user.isMentionsEnabled());
                statement.addBatch();
            }

            statement.executeBatch();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database thrown an exception!", e);
        }

        // Delete all ignored players
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(Queries.DELETE_ALL.getQuery(ignoredTable))) {
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database thrown an exception!", e);
        }

        // Save all ignored players with batch
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(Queries.ADD_IGNORED.getQuery(ignoredTable))) {
            connection.setAutoCommit(false);

            for (ChatUser user : plugin.getUserManager().getAllUsers()) {
                for (String ignoredPlayer : user.getIgnoredPlayers()) {
                    statement.setString(1, user.getUniqueId().toString());
                    statement.setString(2, ignoredPlayer);
                    statement.addBatch();
                }
            }

            statement.executeBatch();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Database thrown an exception!", e);
        }
    }

}
