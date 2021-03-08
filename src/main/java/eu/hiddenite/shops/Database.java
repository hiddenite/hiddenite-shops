package eu.hiddenite.shops;

import org.bukkit.configuration.Configuration;

import java.sql.*;
import java.util.logging.Logger;

public class Database {
    private final Logger logger;
    private final Configuration config;
    private Connection connection = null;

    public Database(Configuration config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    public boolean open() {
        return createConnection();
    }

    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public PreparedStatement prepareStatement(String statement) throws SQLException {
        return prepareStatement(statement, Statement.NO_GENERATED_KEYS);
    }

    public PreparedStatement prepareStatement(String statement, int options) throws SQLException {
        if (!connection.isValid(1) && !createConnection()) {
            return null;
        }
        return connection.prepareStatement(statement, options);
    }

    public static int getGeneratedId(PreparedStatement statement) throws SQLException {
        try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return 0;
    }

    private boolean createConnection() {
        String sqlHost = config.getString("mysql.host");
        String sqlUser = config.getString("mysql.user");
        String sqlPassword = config.getString("mysql.password");
        String sqlDatabase = config.getString("mysql.database");

        try {
            DriverManager.setLoginTimeout(2);
            connection = DriverManager.getConnection("jdbc:mysql://" + sqlHost + "/" + sqlDatabase, sqlUser, sqlPassword);
            logger.info("Successfully connected to " + sqlUser + "@" + sqlHost + "/" + sqlDatabase);
            return true;
        } catch (SQLException e) {
            logger.warning("Could not connect to " + sqlUser + "@" + sqlHost + "/" + sqlDatabase);
            e.printStackTrace();
            return false;
        }
    }
}
