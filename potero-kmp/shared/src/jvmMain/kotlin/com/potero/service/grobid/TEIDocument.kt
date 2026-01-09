package com.potero.service.grobid

/**
 * TEI (Text Encoding Initiative) document structure extracted from GROBID
 */
data class TEIDocument(
    val header: TEIHeader,
    val body: TEIBody,
    val references: List<TEIReference>,
    val rawXml: String
)

/**
 * TEI document header with metadata
 */
data class TEIHeader(
    val title: String?,
    val authors: List<TEIAuthor>,
    val abstract: String?,
    val keywords: List<String>
)

/**
 * TEI author information
 */
data class TEIAuthor(
    val firstName: String?,
    val middleName: String?,
    val lastName: String?,
    val affiliation: String?
) {
    val fullName: String
        get() = listOfNotNull(firstName, middleName, lastName).joinToString(" ")
}

/**
 * TEI document body with structural elements
 */
data class TEIBody(
    val citationSpans: List<TEICitationSpan>,
    val figures: List<TEIFigure>,
    val formulas: List<TEIFormula>,
    val personMentions: List<TEIPersonMention>
)

/**
 * Citation span with bounding box and linking information
 */
data class TEICitationSpan(
    val xmlId: String?,
    val rawText: String,
    val refType: String,  // 'biblio', 'figure', 'formula'
    val targetXmlId: String?,  // Links to reference xml:id
    val bboxes: List<TEIBoundingBox>
)

/**
 * Figure with caption and bounding boxes
 */
data class TEIFigure(
    val xmlId: String,
    val label: String?,  // "Figure 1"
    val caption: String?,
    val bboxes: List<TEIBoundingBox>
)

/**
 * Formula with LaTeX and bounding boxes
 */
data class TEIFormula(
    val xmlId: String?,
    val label: String?,  // "(1)"
    val latex: String?,
    val bboxes: List<TEIBoundingBox>
)

/**
 * Person mention with name and role
 */
data class TEIPersonMention(
    val personName: String,
    val role: String?,
    val bboxes: List<TEIBoundingBox>
)

/**
 * Reference entry from bibliography
 */
data class TEIReference(
    val xmlId: String,  // e.g., "#b12"
    val rawTei: String,  // <biblStruct> XML
    val authors: String?,
    val title: String?,
    val venue: String?,
    val year: Int?,
    val doi: String?,
    val bboxes: List<TEIBoundingBox>
)

/**
 * Bounding box with page number and coordinates
 * Coordinates are in PDF coordinate system (origin at bottom-left)
 */
data class TEIBoundingBox(
    val pageNum: Int,
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double
) {
    /**
     * Convert from PDF coordinates (origin bottom-left) to viewport coordinates (origin top-left)
     * @param pageHeight Height of the PDF page
     */
    fun toViewportCoords(pageHeight: Double): TEIBoundingBox {
        return TEIBoundingBox(
            pageNum = pageNum,
            x1 = x1,
            y1 = pageHeight - y2,
            x2 = x2,
            y2 = pageHeight - y1
        )
    }
}
