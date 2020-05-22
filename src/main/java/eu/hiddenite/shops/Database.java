package eu.hiddenite.shops;

import org.bukkit.configuration.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
        if (!connection.isValid(1) && !createConnection()) {
            return null;
        }
        return connection.prepareStatement(statement);
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
