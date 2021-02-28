package eu.hiddenite.shops;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

public class Economy {
    public enum ResultType {
        SUCCESS,
        INVALID_AMOUNT,
        NOT_ENOUGH_MONEY,
        UNEXPECTED_ERROR,
    }

    private final Database database;
    private final Logger logger;
    private final DecimalFormat formatter;

    public Economy(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;

        this.formatter = (DecimalFormat) NumberFormat.getInstance(Locale.US);
        DecimalFormatSymbols symbols = formatter.getDecimalFormatSymbols();
        symbols.setGroupingSeparator('\'');
        formatter.setDecimalFormatSymbols(symbols);
    }

    public long getMoney(UUID playerId) {
        long currentMoney = 0;
        try (PreparedStatement ps = database.prepareStatement("SELECT amount FROM currency WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    currentMoney = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            logger.warning("Could not retrieve the money of " + playerId);
            e.printStackTrace();
            return -1;
        }
        return currentMoney;
    }

    public ResultType addMoney(UUID playerId, long amount) {
        if (amount <= 0) {
            return ResultType.INVALID_AMOUNT;
        }

        try (PreparedStatement ps = database.prepareStatement("INSERT INTO currency" +
                " (player_id, amount)" +
                " VALUES (?, ?)" +
                " ON DUPLICATE KEY UPDATE amount = amount + ?"
        )) {
            ps.setString(1, playerId.toString());
            ps.setLong(2, amount);
            ps.setLong(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Could not add $" + amount + " to " + playerId);
            e.printStackTrace();
            return ResultType.UNEXPECTED_ERROR;
        }

        logger.info("Added $" + amount + " to " + playerId);

        return ResultType.SUCCESS;
    }

    public ResultType removeMoney(UUID playerId, long amount) {
        if (amount <= 0) {
            return ResultType.INVALID_AMOUNT;
        }

        boolean wasUpdated;
        try (PreparedStatement ps = database.prepareStatement("UPDATE currency" +
                " SET amount = amount - ?" +
                " WHERE player_id = ? AND amount >= ?")) {
            ps.setLong(1, amount);
            ps.setString(2, playerId.toString());
            ps.setLong(3, amount);
            wasUpdated = ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Could not take $" + amount + " from " + playerId);
            e.printStackTrace();
            return ResultType.UNEXPECTED_ERROR;
        }

        if (!wasUpdated) {
            logger.info("Not enough money to remove $" + amount + " from " + playerId);
            return ResultType.NOT_ENOUGH_MONEY;
        }

        logger.info("Removed $" + amount + " from " + playerId);
        return ResultType.SUCCESS;
    }

    public String format(long amount) {
        return this.formatter.format(amount);
    }
}
