package com.titpea.papersudoku.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Typing in a page (11.0): a page pasted as text, and the draft kept on disk. */
class ImportDraftTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val digits = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"

    @Test
    fun `81 digits with 0 or a dot for blanks fill the page`() {
        val page = parsePage(digits)!!
        assertEquals(81, page.size)
        assertEquals(listOf(5, 3, 0, 0, 7), page.take(5))
        assertEquals(page, parsePage(digits.replace('0', '.')))
    }

    @Test
    fun `a printed grid's spaces, line breaks and rules are skipped`() {
        val grid = digits.chunked(9).joinToString("\n") { row -> row.chunked(3).joinToString(" | ") }
        assertEquals(parsePage(digits), parsePage("$grid\n"))
        assertEquals(parsePage(digits), parsePage(digits.chunked(27).joinToString("\n------+-------+------\n")))
    }

    @Test
    fun `anything else is no page`() {
        assertNull(parsePage(digits.drop(1)))
        assertNull(parsePage(digits + "1"))
        assertNull(parsePage(digits.replaceFirst('5', 'x')))
        assertNull(parsePage(""))
    }

    @Test
    fun `the draft survives on disk and goes when it is blank or cleared`() {
        val file = File(folder.root, "import-draft.txt")
        val draft = ImportDraft(file)
        assertNull(draft.read())

        val page = parsePage(digits)!!
        draft.write(page)
        assertEquals(page, ImportDraft(file).read())

        draft.write(List(81) { 0 })
        assertFalse(file.exists())

        draft.write(page)
        draft.clear()
        assertNull(draft.read())
    }

    @Test
    fun `a damaged draft reads as none`() {
        val file = File(folder.root, "import-draft.txt").apply { writeText("not a page") }
        assertNull(ImportDraft(file).read())
    }
}
