package com.potero.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.potero.db.PoteroDatabase
import java.io.File

/**
 * Factory for creating SQLite driver on JVM platform
 */
object DriverFactory {
    private var driver: SqlDriver? = null
    private var database: PoteroDatabase? = null

    /**
     * Get or create the database instance
     */
    fun getDatabase(dbPath: String? = null): PoteroDatabase {
        if (database == null) {
            driver = createDriver(dbPath)
            database = PoteroDatabase(driver!!)
        }
        return database!!
    }

    /**
     * Create SQLite driver
     */
    private fun createDriver(dbPath: String?): SqlDriver {
        val path = dbPath ?: getDefaultDbPath()
        val dbFile = File(path)

        // Ensure directory exists
        dbFile.parentFile?.mkdirs()

        // Check if database already exists
        val dbExists = dbFile.exists()

        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")

        // Create schema only if database doesn't exist
        if (!dbExists) {
            PoteroDatabase.Schema.create(driver)
        } else {
            // Run migrations for existing databases
            runMigrations(driver)
        }

        return driver
    }

    /**
     * Run database migrations for schema updates
     */
    private fun runMigrations(driver: SqlDriver) {
        // Migration 1: Add abstract_korean column to Paper table
        try {
            driver.execute(null, "SELECT abstract_korean FROM Paper LIMIT 1", 0)
        } catch (e: Exception) {
            // Column doesn't exist, add it
            println("[DB Migration] Adding abstract_korean column to Paper table")
            driver.execute(null, "ALTER TABLE Paper ADD COLUMN abstract_korean TEXT", 0)
        }
    }

    /**
     * Get default database path
     */
    private fun getDefaultDbPath(): String {
        val userHome = System.getProperty("user.home")
        val poteroDir = File(userHome, ".potero")
        poteroDir.mkdirs()
        return File(poteroDir, "potero.db").absolutePath
    }

    /**
     * Close database connection
     */
    fun close() {
        driver?.close()
        driver = null
        database = null
    }
}
