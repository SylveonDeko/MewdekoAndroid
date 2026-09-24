package dev.mewdeko.mobile.feature.xp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bot parity for the rank card model helpers the designer and renderer share. */
class XpTemplateModelsTest {

    private val sample = XpCardData.sample(guildName = "Guild", guildId = "1", userId = "2")

    @Test
    fun orderSanitisationKeepsSavedOrderDropsUnknownAndAppendsMissing() {
        val saved = listOf("user-icon", "club-icon", "user-icon", "awarded", "bogus")
        assertEquals(
            listOf("user-icon", "awarded", "user-text", "guild-level", "progress-bar", "guild-rank", "time-on-level"),
            sanitizeBuiltInOrder(saved),
        )
        assertEquals(DefaultBuiltInOrder, sanitizeBuiltInOrder(emptyList()))
    }

    @Test
    fun timeOnLevelHumanizesToOneUnitWithWeeksAsTheCeiling() {
        assertEquals("2 days ago", humanizeTimeOnLevel(XpTimeOnLevel(days = 2, hours = 8, minutes = 23)))
        assertEquals("2 weeks ago", humanizeTimeOnLevel(XpTimeOnLevel(days = 15)))
        assertEquals("1 hour ago", humanizeTimeOnLevel(XpTimeOnLevel(hours = 1, minutes = 5)))
        assertEquals("23 minutes ago", humanizeTimeOnLevel(XpTimeOnLevel(minutes = 23)))
        assertEquals("no time ago", humanizeTimeOnLevel(XpTimeOnLevel()))
        assertEquals(XpTimeOnLevel(3, 4, 5), XpTimeOnLevel(item1 = 3, item2 = 4, item3 = 5).normalized())
    }

    @Test
    fun awardedTextMatchesTheBot() {
        assertEquals("(+ 150)", awardedXpText(150))
        assertEquals("(-20)", awardedXpText(-20))
    }

    @Test
    fun placeholdersResolveCaseInsensitivelyWithOneDecimalProgress() {
        val text = resolveXpPlaceholders("%XP.PROGRESS% %xp.level.next% %xp.remaining% %xp.user.displayname%", sample)
        assertEquals("67.9% 48 2310 Quantum Viper", text)
    }

    @Test
    fun barPolygonFollowsEachDirection() {
        val bar = XpTemplateBar(barPointAx = 10, barPointAy = 20, barPointBx = 5, barPointBy = 40, barLength = 100)
        assertEquals(XpPoint(60f, 20f), barPolygon(bar.copy(barDirection = 3), 0.5f)[1])
        assertEquals(XpPoint(-40f, 20f), barPolygon(bar.copy(barDirection = 2), 0.5f)[1])
        assertEquals(XpPoint(10f, 70f), barPolygon(bar.copy(barDirection = 1), 0.5f)[1])
        assertEquals(XpPoint(5f, -10f), barPolygon(bar.copy(barDirection = 0), 0.5f)[2])
        assertEquals(XpPoint(110f, 20f), barPolygon(bar.copy(barDirection = 9), 1f)[1])
    }

    @Test
    fun snapRoundsToTheGridOrToIntegers() {
        assertEquals(120, snapCoordinate(116f, snap = true, gridSize = 10))
        assertEquals(115, snapCoordinate(114.6f, snap = false, gridSize = 10))
    }

    @Test
    fun movingTheBarTranslatesPointB() {
        val moved = XpTemplate().withBuiltInPosition("progress-bar", 329, 129)
        assertEquals(329, moved.templateBar.barPointAx)
        assertEquals(129, moved.templateBar.barPointAy)
        assertEquals(294, moved.templateBar.barPointBx)
        assertEquals(260, moved.templateBar.barPointBy)
    }

    @Test
    fun builtInSpecsRoundTrip() {
        val template = XpTemplate()
        val spec = template.textSpec("guild-rank")!!
        val edited = template.withTextSpec("guild-rank", spec.copy(fontSize = 40, color = "FF112233"))
        assertEquals(40, edited.templateGuild.guildRankFontSize)
        assertEquals("FF112233", edited.templateGuild.guildRankColor)
        assertFalse(edited.withBuiltInShown("user-icon", false).templateUser.showIcon)
        assertNull(template.textSpec("user-icon"))
        assertTrue(template.invalidColorFields().isEmpty())
        assertEquals(listOf("Username"), template.copy(templateUser = template.templateUser.copy(textColor = "nope")).invalidColorFields())
    }

    @Test
    fun customColoursUseTheBotsArgbReading() {
        val color = parseElementColor("#FFFFFF30")
        assertEquals(1f, color.alpha, 0.001f)
        assertEquals(0x30 / 255f, color.blue, 0.001f)
        assertEquals(0f, parseElementColor("#00000080").alpha, 0.001f)
        assertEquals(0f, parseElementColor("not a colour").alpha, 0.001f)
    }

    @Test
    fun initialsTakeTwoWords() {
        assertEquals("QV", avatarInitials("Quantum Viper"))
        assertEquals("Q", avatarInitials("QuantumViper42"))
        assertEquals("", avatarInitials("  "))
    }

    @Test
    fun newElementsUseTheWebDefaults() {
        val line = newCustomElement(XpCustomElementType.LINE, zIndex = 3)
        assertEquals(180.0, line.width, 0.0)
        assertEquals(0.0, line.height, 0.0)
        assertEquals(4.0, line.strokeWidth, 0.0)
        assertEquals(3, line.zIndex)
        assertEquals("Custom text", newCustomElement(XpCustomElementType.TEXT, 0).label)
        assertEquals(12.0, newCustomElement(XpCustomElementType.RECTANGLE, 0).cornerRadius, 0.0)
        assertTrue(line.id.startsWith("custom-"))
    }
}
