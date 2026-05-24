package com.parlor.games.whodunit.authority

import com.parlor.core.ids.PlayerId
import com.parlor.games.whodunit.domain.action.StructuredActionPayload
import com.parlor.games.whodunit.domain.action.WhodunitAction
import com.parlor.games.whodunit.domain.authority.AuthorityScope
import com.parlor.games.whodunit.domain.authority.WhodunitActionAuthority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Authority matrix locks the rule that:
 *
 *  - Host-only actions (shared game progression) are rejected from any peer.
 *  - Self-actor actions (Cast/Abstain/Refuse vote, Complete reveal,
 *    Open/Close private review, structured-action payloads) are rejected
 *    when submitted by anyone other than the named actor.
 *  - Host can always submit anything (host-trusted path bypasses policy in
 *    practice, but this test pins the behaviour: `isAllowed` returns true
 *    when sender == host on host-only actions, and true when sender ==
 *    actor on self-actor actions).
 */
class WhodunitActionAuthorityTest {

    private val host = PlayerId("host")
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")

    // ============================================================ Host-only ==

    @Test
    fun host_only_actions_reject_peers() {
        val hostOnly = listOf<WhodunitAction>(
            WhodunitAction.AssignRoles(seed = 42L),
            WhodunitAction.AdvanceFromIntro,
            WhodunitAction.AdvanceBriefingCard(1),
            WhodunitAction.StartCharacterReveal(alice),
            WhodunitAction.RevealNextClue,
            WhodunitAction.StartDiscussionTimer(60),
            WhodunitAction.PauseDiscussionTimer,
            WhodunitAction.ResumeDiscussionTimer,
            WhodunitAction.TimerTicked(30),
            WhodunitAction.TimerExpired,
            WhodunitAction.AdvanceFromDiscussion,
            WhodunitAction.OpenVote,
            WhodunitAction.CloseVote,
            WhodunitAction.AcknowledgeRevealCard,
            WhodunitAction.AcknowledgeReveal,
            WhodunitAction.BeginReplay,
            WhodunitAction.Pause,
            WhodunitAction.Resume,
            WhodunitAction.EndGameEarly(withReveal = true),
            WhodunitAction.RequestReroll,
        )
        for (action in hostOnly) {
            assertEquals(AuthorityScope.HostOnly, WhodunitActionAuthority.classify(action),
                "expected HostOnly for $action")
            assertTrue(WhodunitActionAuthority.isAllowed(action, host, host),
                "host must be allowed to submit $action")
            assertFalse(WhodunitActionAuthority.isAllowed(action, alice, host),
                "peer alice must NOT be allowed to submit $action")
            assertFalse(WhodunitActionAuthority.isAllowed(action, bob, host),
                "peer bob must NOT be allowed to submit $action")
        }
    }

    // ============================================================ Self-actor ==

    @Test
    fun self_actor_reveal_lifecycle_only_named_player() {
        val actions = listOf<WhodunitAction>(
            WhodunitAction.CompleteCharacterReveal(alice),
            WhodunitAction.OpenPrivateReview(alice),
            WhodunitAction.CloseHide(alice),
        )
        for (action in actions) {
            assertEquals(AuthorityScope.SelfActor(alice), WhodunitActionAuthority.classify(action))
            assertTrue(WhodunitActionAuthority.isAllowed(action, alice, host))
            assertFalse(WhodunitActionAuthority.isAllowed(action, bob, host),
                "bob cannot submit alice's $action")
            assertFalse(WhodunitActionAuthority.isAllowed(action, host, host),
                "even the host cannot submit a SelfActor action with a different actor")
        }
    }

    @Test
    fun self_actor_voting_only_named_voter() {
        val actions = listOf<WhodunitAction>(
            WhodunitAction.CastVote(voter = alice, target = bob),
            WhodunitAction.AbstainVote(alice),
            WhodunitAction.RefuseToVote(alice),
        )
        for (action in actions) {
            assertEquals(AuthorityScope.SelfActor(alice), WhodunitActionAuthority.classify(action))
            assertTrue(WhodunitActionAuthority.isAllowed(action, alice, host))
            assertFalse(WhodunitActionAuthority.isAllowed(action, bob, host),
                "bob cannot vote/abstain/refuse as alice")
        }
    }

    @Test
    fun self_actor_structured_payloads() {
        val cases: List<Pair<WhodunitAction, PlayerId>> = listOf(
            WhodunitAction.SubmitStructuredAction(StructuredActionPayload.Alibi(alice, "I was reading")) to alice,
            WhodunitAction.SubmitStructuredAction(StructuredActionPayload.Question(from = alice, to = bob, text = "where?")) to alice,
            WhodunitAction.SubmitStructuredAction(StructuredActionPayload.Accusation(by = alice, target = bob)) to alice,
            WhodunitAction.SubmitStructuredAction(StructuredActionPayload.Monologue(by = alice, text = "...")) to alice,
        )
        for ((action, actor) in cases) {
            assertEquals(AuthorityScope.SelfActor(actor), WhodunitActionAuthority.classify(action))
            assertTrue(WhodunitActionAuthority.isAllowed(action, actor, host))
            assertFalse(WhodunitActionAuthority.isAllowed(action, bob, host),
                "bob cannot submit $action on alice's behalf")
        }
    }

    @Test
    fun structured_no_action_is_host_only() {
        val a = WhodunitAction.SubmitStructuredAction(StructuredActionPayload.NoAction)
        assertEquals(AuthorityScope.HostOnly, WhodunitActionAuthority.classify(a))
        assertTrue(WhodunitActionAuthority.isAllowed(a, host, host))
        assertFalse(WhodunitActionAuthority.isAllowed(a, alice, host))
    }
}
