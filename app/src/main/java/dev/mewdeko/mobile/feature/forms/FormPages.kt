package dev.mewdeko.mobile.feature.forms

/**
 * Operations on the pages a form is split into.
 *
 * A page is stored as the section break that heads it followed by its questions, which is the flat
 * list the API and the public form both use. Everything that has to reason about that shape lives
 * here so the builder cannot disagree with itself about where a page begins. Ported from the
 * dashboard's `formPages.ts` so the two cannot drift apart in behaviour.
 */

/** One page of a form: the section break heading it (or null for a headingless first page), and the questions on it. */
data class FormPage(
    val heading: FormQuestion?,
    val headingIndex: Int,
    val questions: List<FormQuestion>,
    val questionIndices: List<Int>,
)

/** Renumbers questions so display order matches the order they sit in the list. */
private fun resequence(questions: List<FormQuestion>): List<FormQuestion> =
    questions.mapIndexed { index, question -> question.copy(displayOrder = index) }

/**
 * Splits questions into the pages a submitter fills in one at a time.
 *
 * A break at the very top heads the first page rather than creating an empty one above it. The
 * break itself is never counted among a page's questions, because it asks nothing.
 */
fun formPages(questions: List<FormQuestion>): List<FormPage> {
    val pages = mutableListOf<FormPage>()
    var heading: FormQuestion? = null
    var headingIndex = -1
    var pageQuestions = mutableListOf<FormQuestion>()
    var pageIndices = mutableListOf<Int>()

    questions.forEachIndexed { index, question ->
        if (question.type == FormQuestionType.SECTION_BREAK) {
            if (pageQuestions.isNotEmpty() || heading != null) {
                pages += FormPage(heading, headingIndex, pageQuestions, pageIndices)
            }
            heading = question
            headingIndex = index
            pageQuestions = mutableListOf()
            pageIndices = mutableListOf()
        } else {
            pageQuestions += question
            pageIndices += index
        }
    }
    pages += FormPage(heading, headingIndex, pageQuestions, pageIndices)
    return pages
}

/** Names a page for its tab, falling back to its number when it has no heading. */
fun formPageLabel(pages: List<FormPage>, index: Int): String {
    val heading = pages.getOrNull(index)?.heading?.questionText?.trim()
    return heading?.takeIf { it.isNotEmpty() } ?: "Page ${index + 1}"
}

/** Where a page begins in the flat list, counting its heading. */
private fun startOfPage(pages: List<FormPage>, pageIndex: Int): Int {
    val page = pages.getOrNull(pageIndex) ?: return 0
    if (page.headingIndex >= 0) return page.headingIndex
    return page.questionIndices.firstOrNull() ?: 0
}

/** Where a page ends in the flat list, which is where the next one begins. */
private fun endOfPage(pages: List<FormPage>, pageIndex: Int, total: Int): Int {
    val next = pages.getOrNull(pageIndex + 1) ?: return total
    return if (next.headingIndex >= 0) next.headingIndex else total
}

/** Adds a question to the end of the given page rather than the end of the whole form. */
fun formPageInsertQuestion(
    questions: List<FormQuestion>,
    question: FormQuestion,
    pageIndex: Int,
): Pair<List<FormQuestion>, Int> {
    val pages = formPages(questions)
    val insertAt = endOfPage(pages, pageIndex, questions.size)
    val next = questions.toMutableList().apply { add(insertAt, question) }
    return resequence(next) to insertAt
}

/** Starts a new page after the given one. Everything added afterwards lands on the new page. */
fun formPageAdd(
    questions: List<FormQuestion>,
    breakQuestion: FormQuestion,
    afterPage: Int,
): Pair<List<FormQuestion>, Int> {
    val pages = formPages(questions)
    val insertAt = endOfPage(pages, afterPage, questions.size)
    val next = questions.toMutableList().apply { add(insertAt, breakQuestion) }
    return resequence(next) to (afterPage + 1)
}

/** Gives the first page a heading, which it does not have until a break is put above it. */
fun formPageAddHeading(questions: List<FormQuestion>, breakQuestion: FormQuestion): List<FormQuestion> =
    resequence(listOf(breakQuestion) + questions)

/**
 * Swaps a page with its neighbour, moving its heading and every question on it together.
 *
 * Returns null when the move is not possible: a first page with no heading cannot move, because
 * being at the top is what defines it.
 */
fun formPageMove(questions: List<FormQuestion>, fromPage: Int, forward: Boolean): Pair<List<FormQuestion>, Int>? {
    val pages = formPages(questions)
    val target = if (forward) fromPage + 1 else fromPage - 1
    if (target < 0 || target >= pages.size) return null

    val first = minOf(fromPage, target)
    val second = maxOf(fromPage, target)
    if (first == 0 && pages[0].headingIndex < 0) return null

    val total = questions.size
    val before = questions.subList(0, startOfPage(pages, first))
    val firstBlock = questions.subList(startOfPage(pages, first), endOfPage(pages, first, total))
    val secondBlock = questions.subList(startOfPage(pages, second), endOfPage(pages, second, total))
    val after = questions.subList(endOfPage(pages, second, total), total)

    return resequence(before + secondBlock + firstBlock + after) to target
}

/**
 * Removes a page break, merging that page's questions into the one before it. The questions are
 * kept; only the break that separated them goes away.
 */
fun formPageRemove(questions: List<FormQuestion>, pageIndex: Int): Pair<List<FormQuestion>, Int>? {
    val pages = formPages(questions)
    val page = pages.getOrNull(pageIndex) ?: return null
    if (page.headingIndex < 0) return null

    val next = questions.filterIndexed { index, _ -> index != page.headingIndex }
    return resequence(next) to maxOf(0, pageIndex - 1)
}

/**
 * Moves a question one place within its own page. A move stops at a page boundary rather than
 * carrying the question onto the next page.
 */
fun formQuestionMoveInPage(questions: List<FormQuestion>, index: Int, forward: Boolean): List<FormQuestion>? {
    val target = if (forward) index + 1 else index - 1
    if (target < 0 || target >= questions.size) return null
    if (questions[target].type == FormQuestionType.SECTION_BREAK) return null

    val next = questions.toMutableList()
    val moved = next[index]
    next[index] = next[target]
    next[target] = moved
    return resequence(next)
}

/** Places a copy of a question directly below the original, where somebody duplicating it looks. */
fun formQuestionDuplicateInList(questions: List<FormQuestion>, index: Int, copy: FormQuestion): List<FormQuestion> {
    val next = questions.toMutableList().apply { add(index + 1, copy) }
    return resequence(next)
}
