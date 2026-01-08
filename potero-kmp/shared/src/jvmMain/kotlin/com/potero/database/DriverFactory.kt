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
        }

        return driver
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
