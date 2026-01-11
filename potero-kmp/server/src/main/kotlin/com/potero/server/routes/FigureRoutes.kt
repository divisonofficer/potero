package com.potero.server.routes

import com.potero.server.di.ServiceLocator
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

/**
 * Routes for serving figure images extracted from PDFs
 */
fun Route.figureRoutes() {
    val database = ServiceLocator.database

    route("/figures") {
        // GET /api/figures/{figureId}/image - Serve figure image
        get("/{figureId}/image") {
            val figureId = call.parameters["figureId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing figureId")

            // Get figure from database
            val figure = database.figureQueries.getFigureById(figureId).executeAsOneOrNull()
                ?: return@get call.respond(HttpStatusCode.NotFound, "Figure not found")

            // Check if image path exists
            val imagePath = figure.image_path
            if (imagePath.isNullOrBlank()) {
                return@get call.respond(HttpStatusCode.NotFound, "Figure image not available")
            }

            // Check if file exists
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                return@get call.respond(HttpStatusCode.NotFound, "Figure image file not found")
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
