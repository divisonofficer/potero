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
     * Run database migrations for schema updates.
     * Safely checks table/column existence before attempting changes.
     */
    private fun runMigrations(driver: SqlDriver) {
        // Helper to check if a table exists
        fun tableExists(tableName: String): Boolean {
            return try {
                driver.execute(null, "SELECT 1 FROM $tableName LIMIT 1", 0)
                true
            } catch (e: Exception) {
                false
            }
        }

        // Helper to check if a column exists in a table
        fun columnExists(tableName: String, columnName: String): Boolean {
            return try {
                driver.execute(null, "SELECT $columnName FROM $tableName LIMIT 1", 0)
                true
            } catch (e: Exception) {
                false
            }
        }

        // Migration 1: Add abstract_korean column to Paper table
        if (tableExists("Paper") && !columnExists("Paper", "abstract_korean")) {
            println("[DB Migration] Adding abstract_korean column to Paper table")
            driver.execute(null, "ALTER TABLE Paper ADD COLUMN abstract_korean TEXT", 0)
        }

        // Migration 2: Add google_scholar_id and semantic_scholar_id columns to Author table
        if (tableExists("Author")) {
            if (!columnExists("Author", "google_scholar_id")) {
                println("[DB Migration] Adding google_scholar_id column to Author table")
                driver.execute(null, "ALTER TABLE Author ADD COLUMN google_scholar_id TEXT", 0)
            }
            if (!columnExists("Author", "semantic_scholar_id")) {
                println("[DB Migration] Adding semantic_scholar_id column to Author table")
                driver.execute(null, "ALTER TABLE Author ADD COLUMN semantic_scholar_id TEXT", 0)
            }
        }

        // Migration 3: Add bbox columns to Reference table
        if (tableExists("Reference") && !columnExists("Reference", "bbox_x1")) {
            println("[DB Migration] Adding bbox columns to Reference table")
            driver.execute(null, "ALTER TABLE Reference ADD COLUMN bbox_x1 REAL", 0)
            driver.execute(null, "ALTER TABLE Reference ADD COLUMN bbox_y1 REAL", 0)
            driver.execute(null, "ALTER TABLE Reference ADD COLUMN bbox_x2 REAL", 0)
            driver.execute(null, "ALTER TABLE Reference ADD COLUMN bbox_y2 REAL", 0)
            driver.execute(null, "ALTER TABLE Reference ADD COLUMN confidence REAL DEFAULT 0.5", 0)
            driver.execute(null, "ALTER TABLE Reference ADD COLUMN provenance TEXT DEFAULT 'pattern'", 0)
        }

        // Migration 4: Create CitationSpan table if not exists
        if (!tableExists("CitationSpan")) {
            println("[DB Migration] Creating CitationSpan table")
            driver.execute(null, """
                CREATE TABLE IF NOT EXISTS CitationSpan (
                    id TEXT NOT NULL PRIMARY KEY,
                    paper_id TEXT NOT NULL REFERENCES Paper(id) ON DELETE CASCADE,
                    page_num INTEGER NOT NULL,
                    bbox_x1 REAL NOT NULL,
                    bbox_y1 REAL NOT NULL,
                    bbox_x2 REAL NOT NULL,
                    bbox_y2 REAL NOT NULL,
                    raw_text TEXT NOT NULL,
                    style TEXT NOT NULL DEFAULT 'numeric',
                    provenance TEXT NOT NULL DEFAULT 'pattern',
                    confidence REAL NOT NULL DEFAULT 0.5,
                    dest_page INTEGER,
                    dest_y REAL,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent(), 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS citation_span_paper_idx ON CitationSpan(paper_id)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS citation_span_page_idx ON CitationSpan(paper_id, page_num)", 0)
        }

        // Migration 5: Create CitationLink table if not exists
        if (!tableExists("CitationLink")) {
            println("[DB Migration] Creating CitationLink table")
            driver.execute(null, """
                CREATE TABLE IF NOT EXISTS CitationLink (
                    id TEXT NOT NULL PRIMARY KEY,
                    citation_span_id TEXT NOT NULL REFERENCES CitationSpan(id) ON DELETE CASCADE,
                    reference_id TEXT NOT NULL REFERENCES Reference(id) ON DELETE CASCADE,
                    link_method TEXT NOT NULL DEFAULT 'numeric',
                    confidence REAL NOT NULL DEFAULT 0.5,
                    created_at INTEGER NOT NULL,
                    UNIQUE(citation_span_id, reference_id)
                )
            """.trimIndent(), 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS citation_link_span_idx ON CitationLink(citation_span_id)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS citation_link_ref_idx ON CitationLink(reference_id)", 0)
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
