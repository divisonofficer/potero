package com.potero.server.routes

import com.potero.server.di.ServiceLocator
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

/**
 * Routes for serving table images extracted from PDFs
 */
fun Route.tableRoutes() {
    val database = ServiceLocator.database

    route("/tables") {
        // GET /api/tables/{tableId}/image - Serve table image
        get("/{tableId}/image") {
            val tableId = call.parameters["tableId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing tableId")

            // Get table from database
            val table = database.pdfTableQueries.getTableById(tableId).executeAsOneOrNull()
                ?: return@get call.respond(HttpStatusCode.NotFound, "Table not found")

            // Check if image path exists
            val imagePath = table.image_path
            if (imagePath.isNullOrBlank()) {
                return@get call.respond(HttpStatusCode.NotFound, "Table image not available")
            }

            // Check if file exists
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                return@get call.respond(HttpStatusCode.NotFound, "Table image file not found")
            }

            // Set cache headers (1 year - images don't change)
            call.response.cacheControl(CacheControl.MaxAge(maxAgeSeconds = 31536000)) // 1 year

            // Set content type
            call.response.header(HttpHeaders.ContentType, "image/png")

            // Serve the image file
            call.respondFile(imageFile)
        }
    }
}
