package com.potero.service.grobid

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parser for GROBID TEI XML output
 *
 * Converts GROBID's TEI XML format into domain entities (TEIDocument, etc.)
 * Handles coordinate extraction from the `coords` attribute.
 */
object TEIParser {

    /**
     * Parse TEI XML string into TEIDocument
     *
     * @param teiXml TEI XML string from GROBID
     * @return Parsed TEIDocument with all structural elements
     */
    fun parse(teiXml: String): TEIDocument {
        val doc = Jsoup.parse(teiXml, "", org.jsoup.parser.Parser.xmlParser())

        return TEIDocument(
            header = parseHeader(doc),
            body = parseBody(doc),
            references = parseReferences(doc),
            rawXml = teiXml
        )
    }

    /**
     * Parse TEI header (metadata)
     */
    private fun parseHeader(doc: Document): TEIHeader {
        val titleStmt = doc.selectFirst("teiHeader > fileDesc > titleStmt")
        val abstract = doc.selectFirst("teiHeader > profileDesc > abstract")

        return TEIHeader(
            title = titleStmt?.selectFirst("title")?.text(),
            authors = parseAuthors(doc),
            abstract = abstract?.text(),
            keywords = parseKeywords(doc)
        )
    }

    /**
     * Parse authors from TEI header
     */
    private fun parseAuthors(doc: Document): List<TEIAuthor> {
        return doc.select("teiHeader > fileDesc > sourceDesc > biblStruct > analytic > author").map { authorElem ->
            val persName = authorElem.selectFirst("persName")
            TEIAuthor(
                firstName = persName?.selectFirst("forename[type=first]")?.text(),
                middleName = persName?.selectFirst("forename[type=middle]")?.text(),
                lastName = persName?.selectFirst("surname")?.text(),
                affiliation = authorElem.selectFirst("affiliation")?.text()
            )
        }
    }

    /**
     * Parse keywords from TEI header
     */
    private fun parseKeywords(doc: Document): List<String> {
        return doc.select("teiHeader > profileDesc > textClass > keywords > term").map { it.text() }
    }

    /**
     * Parse TEI body (structural elements)
     */
    private fun parseBody(doc: Document): TEIBody {
        val bodyElem = doc.selectFirst("text > body")

        return TEIBody(
            citationSpans = parseCitationSpans(bodyElem),
            figures = parseFigures(bodyElem),
            formulas = parseFormulas(bodyElem),
            personMentions = parsePersonMentions(bodyElem)
        )
    }

    /**
     * Parse citation spans (in-text references)
     */
    private fun parseCitationSpans(bodyElem: Element?): List<TEICitationSpan> {
        if (bodyElem == null) return emptyList()

        return bodyElem.select("ref[type]").mapNotNull { ref ->
            val type = ref.attr("type")
            if (type !in listOf("biblio", "figure", "formula")) return@mapNotNull null

            TEICitationSpan(
                xmlId = ref.attr("xml:id").takeIf { it.isNotBlank() },
                rawText = ref.text(),
                refType = type,
                targetXmlId = ref.attr("target").takeIf { it.isNotBlank() },
                bboxes = parseCoordsAttribute(ref.attr("coords"))
            )
        }
    }

    /**
     * Parse figures with captions
     */
    private fun parseFigures(bodyElem: Element?): List<TEIFigure> {
        if (bodyElem == null) return emptyList()

        return bodyElem.select("figure").map { figElem ->
            TEIFigure(
                xmlId = figElem.attr("xml:id"),
                label = figElem.selectFirst("head")?.text(),
                caption = figElem.selectFirst("figDesc")?.text(),
                bboxes = parseCoordsAttribute(figElem.attr("coords"))
            )
        }
    }

    /**
     * Parse formulas
     */
    private fun parseFormulas(bodyElem: Element?): List<TEIFormula> {
        if (bodyElem == null) return emptyList()

        return bodyElem.select("formula").map { formulaElem ->
            TEIFormula(
                xmlId = formulaElem.attr("xml:id").takeIf { it.isNotBlank() },
                label = formulaElem.attr("n").takeIf { it.isNotBlank() },
                latex = formulaElem.text(),
                bboxes = parseCoordsAttribute(formulaElem.attr("coords"))
            )
        }
    }

    /**
     * Parse person mentions
     */
    private fun parsePersonMentions(bodyElem: Element?): List<TEIPersonMention> {
        if (bodyElem == null) return emptyList()

        return bodyElem.select("persName").map { persName ->
            TEIPersonMention(
                personName = persName.text(),
                role = persName.attr("role").takeIf { it.isNotBlank() },
                bboxes = parseCoordsAttribute(persName.attr("coords"))
            )
        }
    }

    /**
     * Parse references from TEI back matter
     */
    private fun parseReferences(doc: Document): List<TEIReference> {
        return doc.select("text > back > listBibl > biblStruct").map { biblStruct ->
            TEIReference(
                xmlId = biblStruct.attr("xml:id"),
                rawTei = biblStruct.outerHtml(),
                authors = biblStruct.select("author > persName").joinToString(", ") { it.text() },
                title = biblStruct.selectFirst("title[level=a]")?.text()
                    ?: biblStruct.selectFirst("title")?.text(),
                venue = biblStruct.selectFirst("title[level=j]")?.text()
                    ?: biblStruct.selectFirst("title[level=m]")?.text(),
                year = biblStruct.selectFirst("date")?.attr("when")?.toIntOrNull(),
                doi = biblStruct.selectFirst("idno[type=DOI]")?.text(),
                bboxes = parseCoordsAttribute(biblStruct.attr("coords"))
            )
        }
    }

    /**
     * Parse coordinates attribute into list of bounding boxes
     *
     * Format: "page,x1,y1,x2,y2" or "page1,x1,y1,x2,y2;page2,x1,y1,x2,y2"
     * Coordinates are in PDF coordinate system (origin at bottom-left)
     *
     * @param coords Coords attribute value from TEI XML
     * @return List of bounding boxes
     */
    fun parseCoordsAttribute(coords: String): List<TEIBoundingBox> {
        if (coords.isBlank()) return emptyList()

        return coords.split(";").mapNotNull { coordStr ->
            try {
                val parts = coordStr.trim().split(",").map { it.toDouble() }
                if (parts.size != 5) return@mapNotNull null

                TEIBoundingBox(
                    pageNum = parts[0].toInt(),
                    x1 = parts[1],
                    y1 = parts[2],
                    x2 = parts[3],
                    y2 = parts[4]
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
