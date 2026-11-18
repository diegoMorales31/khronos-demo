package com.moga.khronosdemo.support;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * Test-only safety net. The application also reads DB_URL from .env, which may point at a real
 * database, and the integration tests empty every table. Before Flyway touches anything, every test
 * context checks that the database name contains "test" and stops otherwise.
 */
@Component
public class TestDatabaseGuard implements FlywayMigrationStrategy {

    @Override
    public void migrate(Flyway flyway) {
        String database = currentDatabase(flyway);
        if (database == null || !database.toLowerCase(Locale.ROOT).contains("test")) {
            throw new IllegalStateException("Refusing to run the tests against database '" + database
                    + "': they delete every row, so the database name must contain 'test'."
                    + " Point DB_URL at a throwaway database (see README, Tests).");
        }
        flyway.migrate();
    }

    private static String currentDatabase(Flyway flyway) {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select current_database()")) {
            return result.next() ? result.getString(1) : null;
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not check which database the tests would use", ex);
        }
    }
}
