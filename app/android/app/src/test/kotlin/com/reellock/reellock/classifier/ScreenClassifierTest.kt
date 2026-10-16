package com.reellock.reellock.classifier

import com.reellock.reellock.commitment.Confidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenClassifierTest {

    private val instagram = AppSignatureRegistry.signaturesFor(TargetApp.INSTAGRAM)

    @Test
    fun `null root is a transition`() {
        assertEquals(ScreenClassification.Transition, ScreenClassifier.classify(TargetApp.INSTAGRAM, instagram, null))
    }

    @Test
    fun `too few nodes is a transition`() {
        val tiny = node("some_id", children = arrayOf(node("child")))
        val result = ScreenClassifier.classify(TargetApp.INSTAGRAM, instagram, tiny)
        assertEquals(ScreenClassification.Transition, result)
    }

    @Test
    fun `reels viewer resource ids classify as SHORT_VIDEO with high confidence when tab also selected`() {
        val tree = node(
            "root",
            children = arrayOf(
                node("clips_viewer_container"),
                node("clips_tab_fragment"),
                node("clips_player_surface"),
                node("reel_viewer_timestamp"),
            ),
        )
        val result = ScreenClassifier.classify(TargetApp.INSTAGRAM, instagram, tree) as ScreenClassification.Classified
        assertEquals(ScreenType.SHORT_VIDEO, result.screen)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(TargetApp.INSTAGRAM, result.app)
    }

    @Test
    fun `a retained but invisible Reels fragment does not override the visible screen`() {
        // Regression test: Instagram keeps the Reels viewer fragment
        // attached-but-off-screen after navigating away from it, so its
        // resource-ids (e.g. clips_viewer_view_pager) can still be present
        // in the tree while the user is genuinely looking at Feed/DM/
        // Profile/Search. Confirmed from real-device logs. Only visible
        // nodes should count toward classification.
        val tree = node(
            "root",
            children = arrayOf(
                node("direct_inbox"),
                node("direct_thread"),
                node("unrelated"),
                node("clips_viewer_container", visible = false),
                node("clips_player_surface", visible = false),
            ),
        )
        val result = ScreenClassifier.classify(TargetApp.INSTAGRAM, instagram, tree) as ScreenClassification.Classified
        assertEquals(ScreenType.OTHER, result.screen)
    }

    @Test
    fun `an invisible Reels fragment with no other signal is UNKNOWN, not SHORT_VIDEO`() {
        val tree = node(
            "root",
            children = arrayOf(
                node("unrelated_1"),
                node("unrelated_2"),
                node("unrelated_3"),
                node("clips_viewer_container", visible = false),
            ),
        )
        assertEquals(ScreenClassification.Unknown, ScreenClassifier.classify(TargetApp.INSTAGRAM, instagram, tree))
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
        val result = ScreenClassifier.classify(TargetApp.INSTAGRAM, instagram, tree) as ScreenClassification.Classified
        assertEquals(ScreenType.SHORT_VIDEO, result.screen)
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
        val result = ScreenClassifier.classify(TargetApp.INSTAGRAM, instagram, tree) as ScreenClassification.Classified
        assertEquals(ScreenType.OTHER, result.screen)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `feed resource ids classify as OTHER even with an embedded video label`() {
        val tree = node(
            "root",
            children = arrayOf(
                node("feed_timeline"),
                node("main_feed"),
                node(description = "Original audio"), // feed can contain clips with this label
                node("unrelated"),
            ),
        )
        val result = ScreenClassifier.classify(TargetApp.INSTAGRAM, instagram, tree) as ScreenClassification.Classified
        assertEquals(ScreenType.OTHER, result.screen)
    }

    @Test
    fun `a Reels label with no structural signal is UNKNOWN, not SHORT_VIDEO`() {
        // Regression test: Instagram's bottom-nav tab bar carries
        // contentDescription "Reels" on its icon on every main screen (Feed,
        // Profile, Search...), confirmed from real-device logs. A prior
        // version of the classifier treated an unmatched "Reels" label as a
        // low-confidence SHORT_VIDEO signal, which misclassified ordinary feed
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
        assertEquals(ScreenClassification.Unknown, ScreenClassifier.classify(TargetApp.INSTAGRAM, instagram, tree))
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
        assertEquals(ScreenClassification.Unknown, ScreenClassifier.classify(TargetApp.INSTAGRAM, instagram, tree))
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
    fun `search resource ids classify as OTHER`() {
        val tree = node(
            "root",
            children = arrayOf(
                node("action_bar_search"),
                node("search_edit_text"),
                node("unrelated_1"),
                node("unrelated_2"),
            ),
        )
        val result = ScreenClassifier.classify(TargetApp.INSTAGRAM, instagram, tree) as ScreenClassification.Classified
        assertEquals(ScreenType.OTHER, result.screen)
        assertTrue(result.confidence == Confidence.HIGH)
    }

    // --- YouTube: registry wiring only, not a calibration claim. See
    // AppSignatureRegistry's doc comment on its youtube signatures - these
    // ids are unverified guesses, so this only proves the generic classifier
    // correctly dispatches on whatever signatures it's given, not that it
    // will actually recognize real YouTube Shorts screens.

    @Test
    fun `youtube signatures are wired through the same generic classifier`() {
        val youtube = AppSignatureRegistry.signaturesFor(TargetApp.YOUTUBE)
        val tree = node(
            "root",
            children = arrayOf(
                node("reel_player_page"),
                node("pivot_shorts"),
                node("unrelated_1"),
                node("unrelated_2"),
            ),
        )
        val result = ScreenClassifier.classify(TargetApp.YOUTUBE, youtube, tree) as ScreenClassification.Classified
        assertEquals(ScreenType.SHORT_VIDEO, result.screen)
        assertEquals(TargetApp.YOUTUBE, result.app)
    }

    @Test
    fun `forPackage resolves both supported apps and rejects everything else`() {
        assertEquals(TargetApp.INSTAGRAM, AppSignatureRegistry.forPackage("com.instagram.android"))
        assertEquals(TargetApp.YOUTUBE, AppSignatureRegistry.forPackage("com.google.android.youtube"))
        assertEquals(null, AppSignatureRegistry.forPackage("com.some.other.app"))
    }
}
