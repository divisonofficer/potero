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

        // Migration 3: Create Reference table if not exists
        if (!tableExists("Reference")) {
            println("[DB Migration] Creating Reference table")
            driver.execute(null, """
                CREATE TABLE IF NOT EXISTS Reference (
                    id TEXT NOT NULL PRIMARY KEY,
                    paper_id TEXT NOT NULL REFERENCES Paper(id) ON DELETE CASCADE,
                    number INTEGER NOT NULL,
                    raw_text TEXT NOT NULL,
                    authors TEXT,
                    title TEXT,
                    venue TEXT,
                    year INTEGER,
                    doi TEXT,
                    page_num INTEGER NOT NULL DEFAULT 0,
                    bbox_x1 REAL,
                    bbox_y1 REAL,
                    bbox_x2 REAL,
                    bbox_y2 REAL,
                    confidence REAL DEFAULT 0.5,
                    provenance TEXT DEFAULT 'pattern',
                    created_at INTEGER NOT NULL
                )
            """.trimIndent(), 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS reference_paper_id_idx ON Reference(paper_id)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS reference_number_idx ON Reference(paper_id, number)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS reference_doi_idx ON Reference(doi)", 0)
        }

        // Migration 3.1: Add bbox columns to Reference table (if table already exists without them)
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

        // Migration 6: Create GROBID tables (for advanced PDF structure extraction)

        // 6.1: BoundingBox table
        if (!tableExists("BoundingBox")) {
            println("[DB Migration] Creating BoundingBox table")
            driver.execute(null, """
                CREATE TABLE IF NOT EXISTS BoundingBox (
                    id TEXT NOT NULL PRIMARY KEY,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    page_num INTEGER NOT NULL,
                    x1 REAL NOT NULL,
                    y1 REAL NOT NULL,
                    x2 REAL NOT NULL,
                    y2 REAL NOT NULL,
                    rect_index INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent(), 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_bbox_entity ON BoundingBox(entity_type, entity_id)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_bbox_page ON BoundingBox(entity_type, entity_id, page_num)", 0)
        }

        // 6.2: GrobidCitationSpan table
        if (!tableExists("GrobidCitationSpan")) {
            println("[DB Migration] Creating GrobidCitationSpan table")
            driver.execute(null, """
                CREATE TABLE IF NOT EXISTS GrobidCitationSpan (
                    id TEXT NOT NULL PRIMARY KEY,
                    paper_id TEXT NOT NULL REFERENCES Paper(id) ON DELETE CASCADE,
                    page_num INTEGER NOT NULL,
                    raw_text TEXT NOT NULL,
                    xml_id TEXT,
                    ref_type TEXT NOT NULL,
                    target_xml_id TEXT,
                    confidence REAL NOT NULL DEFAULT 0.95,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent(), 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_grobid_citation_paper ON GrobidCitationSpan(paper_id)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_grobid_citation_page ON GrobidCitationSpan(paper_id, page_num)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_grobid_citation_target ON GrobidCitationSpan(target_xml_id)", 0)
        }

        // 6.3: GrobidReference table
        if (!tableExists("GrobidReference")) {
            println("[DB Migration] Creating GrobidReference table")
            driver.execute(null, """
                CREATE TABLE IF NOT EXISTS GrobidReference (
                    id TEXT NOT NULL PRIMARY KEY,
                    paper_id TEXT NOT NULL REFERENCES Paper(id) ON DELETE CASCADE,
                    xml_id TEXT NOT NULL,
                    raw_tei TEXT,
                    authors TEXT,
                    title TEXT,
                    venue TEXT,
                    year INTEGER,
                    doi TEXT,
                    arxiv_id TEXT,
                    page_num INTEGER,
                    confidence REAL NOT NULL DEFAULT 0.95,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent(), 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_grobid_ref_paper ON GrobidReference(paper_id)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_grobid_ref_xmlid ON GrobidReference(paper_id, xml_id)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_grobid_ref_doi ON GrobidReference(doi)", 0)
        }

        // 6.4: Figure table
        if (!tableExists("Figure")) {
            println("[DB Migration] Creating Figure table")
            driver.execute(null, """
                CREATE TABLE IF NOT EXISTS Figure (
                    id TEXT NOT NULL PRIMARY KEY,
                    paper_id TEXT NOT NULL REFERENCES Paper(id) ON DELETE CASCADE,
                    page_num INTEGER NOT NULL,
                    xml_id TEXT,
                    label TEXT,
                    caption TEXT,
                    image_path TEXT,
                    confidence REAL NOT NULL DEFAULT 0.90,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent(), 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_figure_paper ON Figure(paper_id)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_figure_page ON Figure(paper_id, page_num)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_figure_xmlid ON Figure(paper_id, xml_id)", 0)
        }

        // 6.5: Formula table
        if (!tableExists("Formula")) {
            println("[DB Migration] Creating Formula table")
            driver.execute(null, """
                CREATE TABLE IF NOT EXISTS Formula (
                    id TEXT NOT NULL PRIMARY KEY,
                    paper_id TEXT NOT NULL REFERENCES Paper(id) ON DELETE CASCADE,
                    page_num INTEGER NOT NULL,
                    xml_id TEXT,
                    label TEXT,
                    latex TEXT,
                    confidence REAL NOT NULL DEFAULT 0.80,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent(), 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_formula_paper ON Formula(paper_id)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_formula_page ON Formula(paper_id, page_num)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_formula_xmlid ON Formula(paper_id, xml_id)", 0)
        }

        // 6.6: PersonMention table
        if (!tableExists("PersonMention")) {
            println("[DB Migration] Creating PersonMention table")
            driver.execute(null, """
                CREATE TABLE IF NOT EXISTS PersonMention (
                    id TEXT NOT NULL PRIMARY KEY,
                    paper_id TEXT NOT NULL REFERENCES Paper(id) ON DELETE CASCADE,
                    page_num INTEGER NOT NULL,
                    person_name TEXT NOT NULL,
                    role TEXT,
                    confidence REAL NOT NULL DEFAULT 0.85,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent(), 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_person_mention_paper ON PersonMention(paper_id)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_person_mention_page ON PersonMention(paper_id, page_num)", 0)
            driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_person_mention_name ON PersonMention(person_name)", 0)
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
