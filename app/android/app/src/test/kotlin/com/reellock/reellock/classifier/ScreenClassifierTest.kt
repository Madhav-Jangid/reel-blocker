package com.reellock.reellock.classifier

import com.reellock.reellock.commitment.Confidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenClassifierTest {

    @Test
    fun `null root is a transition`() {
        assertEquals(ScreenClassification.Transition, ScreenClassifier.classify(null))
    }

    @Test
    fun `too few nodes is a transition`() {
        val tiny = node("some_id", children = arrayOf(node("child")))
        val result = ScreenClassifier.classify(tiny)
        assertEquals(ScreenClassification.Transition, result)
    }

    @Test
    fun `reels viewer resource ids classify as REELS with high confidence when tab also selected`() {
        val tree = node(
            "root",
            children = arrayOf(
                node("clips_viewer_container"),
                node("clips_tab_fragment"),
                node("clips_player_surface"),
                node("reel_viewer_timestamp"),
            ),
        )
        val result = ScreenClassifier.classify(tree) as ScreenClassification.Classified
        assertEquals(ScreenType.REELS, result.screen)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `reels structural signal without tab selection is medium confidence`() {
        val tree = node(
            "root",
            children = arrayOf(
                node("clips_viewer_container"),
                node("unrelated_1"),
                node("unrelated_2"),
                node("unrelated_3"),
            ),
        )
        val result = ScreenClassifier.classify(tree) as ScreenClassification.Classified
        assertEquals(ScreenType.REELS, result.screen)
        assertEquals(Confidence.MEDIUM, result.confidence)
    }

    @Test
    fun `direct messages resource ids take priority over a stray Reels label`() {
        val tree = node(
            "root",
            children = arrayOf(
                node("direct_inbox"),
                node("direct_thread"),
                node(description = "Reels"), // a DM thread could mention "Reels" in text; must not flip classification
                node("unrelated"),
            ),
        )
        val result = ScreenClassifier.classify(tree) as ScreenClassification.Classified
        assertEquals(ScreenType.DIRECT_MESSAGES, result.screen)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `feed resource ids classify as FEED even with an embedded video label`() {
        val tree = node(
            "root",
            children = arrayOf(
                node("feed_timeline"),
                node("main_feed"),
                node(description = "Original audio"), // feed can contain clips with this label
                node("unrelated"),
            ),
        )
        val result = ScreenClassifier.classify(tree) as ScreenClassification.Classified
        assertEquals(ScreenType.FEED, result.screen)
    }

    @Test
    fun `a Reels label with no structural signal is UNKNOWN, not REELS`() {
        // Regression test: Instagram's bottom-nav tab bar carries
        // contentDescription "Reels" on its icon on every main screen (Feed,
        // Profile, Search...), confirmed from real-device logs. A prior
        // version of the classifier treated an unmatched "Reels" label as a
        // low-confidence REELS signal, which misclassified ordinary feed
        // scrolling as Reels. Labels are never read for classification now.
        val tree = node(
            "root",
            children = arrayOf(
                node(description = "Reels"),
                node("unrelated_1"),
                node("unrelated_2"),
                node("unrelated_3"),
            ),
        )
        assertEquals(ScreenClassification.Unknown, ScreenClassifier.classify(tree))
    }

    @Test
    fun `no recognizable signal is unknown`() {
        val tree = node(
            "root",
            children = arrayOf(
                node("mystery_view_1"),
                node("mystery_view_2"),
                node("mystery_view_3"),
                node("mystery_view_4"),
            ),
        )
        assertEquals(ScreenClassification.Unknown, ScreenClassifier.classify(tree))
    }

    @Test
    fun `dumpResourceIds returns raw deduplicated ids with no text or descriptions`() {
        val tree = node(
            "root_id",
            description = "should not appear",
            text = "should not appear either",
            children = arrayOf(
                node("clips_viewer_container"),
                node("clips_viewer_container"), // duplicate, should be collapsed
                node("unrelated_1"),
            ),
        )
        val ids = ScreenClassifier.dumpResourceIds(tree)
        assertEquals(listOf("root_id", "clips_viewer_container", "unrelated_1"), ids)
    }

    @Test
    fun `dumpResourceIds on null root returns empty list`() {
        assertEquals(emptyList<String>(), ScreenClassifier.dumpResourceIds(null))
    }

    @Test
    fun `search resource ids classify as SEARCH`() {
        val tree = node(
            "root",
            children = arrayOf(
                node("action_bar_search"),
                node("search_edit_text"),
                node("unrelated_1"),
                node("unrelated_2"),
            ),
        )
        val result = ScreenClassifier.classify(tree) as ScreenClassification.Classified
        assertEquals(ScreenType.SEARCH, result.screen)
        assertTrue(result.confidence == Confidence.HIGH)
    }
}
