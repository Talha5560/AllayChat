package net.voxelarc.allaychat.database;

public enum Queries {

    CREATE_USER_TABLE("CREATE TABLE IF NOT EXISTS %s (" +
            "uniqueId VARCHAR(36) PRIMARY KEY," +
            "msgEnabled BOOLEAN NOT NULL DEFAULT TRUE," +
            "spyEnabled BOOLEAN NOT NULL DEFAULT FALSE," +
            "staffEnabled BOOLEAN NOT NULL DEFAULT FALSE," +
            "mentionsEnabled BOOLEAN NOT NULL DEFAULT TRUE" +
            ");"),

    CREATE_IGNORE_TABLE("CREATE TABLE IF NOT EXISTS %s (" +
            "userId VARCHAR(36)," +
            "ignoredPlayerName VARCHAR(32)," +
            "FOREIGN KEY (userId) REFERENCES %s(uniqueId)," +
            "PRIMARY KEY (userId, ignoredPlayerName)" +
            ");"),

    GET_USER("SELECT * FROM %s WHERE uniqueId = ?"),
    GET_ALL_IGNORED("SELECT * FROM %s WHERE userId = ?"),

    DELETE_USER("DELETE FROM %s WHERE uniqueId = ?"),
    DELETE_IGNORED("DELETE FROM %s WHERE userId = ? AND ignoredPlayerName = ?"),
    DELETE_ALL_IGNORED("DELETE FROM %s WHERE userId = ?"),
    DELETE_ALL("DELETE FROM %s"),

    SAVE_USER("REPLACE INTO %s (uniqueId, msgEnabled, spyEnabled, staffEnabled, mentionsEnabled) VALUES (?, ?, ?, ?, ?);"),

    ADD_IGNORED("INSERT INTO %s (userId, ignoredPlayerName) VALUES (?, ?);"),

    ;

    private final String query;

    Queries(String query) {
        this.query = query;
    }

    public String getQuery(Object... variables) {
        return this.query.formatted(variables);
    }

}
