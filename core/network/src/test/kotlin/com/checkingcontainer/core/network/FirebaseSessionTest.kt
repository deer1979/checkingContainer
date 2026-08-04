package com.checkingcontainer.core.network

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseSessionTest {

    private val auth = mockk<FirebaseAuth>()

    @Test
    fun `identidad no anonima se considera confiable`() {
        val user = mockUser(uid = "uid-123", isAnonymous = false)
        every { auth.currentUser } returns user

        val identity = FirebaseSession(auth).currentIdentity()

        assertEquals("uid-123", identity?.uid)
        assertTrue(identity?.isTrusted == true)
        assertFalse(identity?.isAnonymous == true)
    }

    @Test
    fun `ensureSignedIn conserva una identidad confiable existente`() = runTest {
        every { auth.currentUser } returns mockUser(uid = "uid-stable", isAnonymous = false)

        val result = FirebaseSession(auth).ensureSignedIn()

        assertTrue(result)
        verify(exactly = 0) { auth.signInAnonymously() }
    }

    @Test
    fun `reset limpia identidad confiable y crea sesion compatible`() {
        val trustedUser = mockUser(uid = "uid-stable", isAnonymous = false)
        val anonymousTask = mockk<Task<AuthResult>>(relaxed = true)

        every { auth.currentUser } returnsMany listOf(trustedUser, null)
        every { auth.signOut() } just Runs
        every { auth.signInAnonymously() } returns anonymousTask

        FirebaseSession(auth).resetToCompatibilitySessionAsync()

        verify(exactly = 1) { auth.signOut() }
        verify(exactly = 1) { auth.signInAnonymously() }
        verify(exactly = 1) { anonymousTask.addOnFailureListener(any()) }
    }

    @Test
    fun `custom token vacio se rechaza sin llamar Firebase`() = runTest {
        val result = FirebaseSession(auth).signInWithCustomToken("   ")

        assertTrue(result.isFailure)
        verify(exactly = 0) { auth.signInWithCustomToken(any()) }
    }

    @Test
    fun `custom token valido eleva la sesion a identidad confiable`() = runTest {
        val user = mockUser(uid = "uid-custom", isAnonymous = false)
        val authResult = mockk<AuthResult>()
        every { authResult.user } returns user
        every { auth.signInWithCustomToken("token-seguro") } returns Tasks.forResult(authResult)

        val result = FirebaseSession(auth).signInWithCustomToken("token-seguro")

        assertTrue(result.isSuccess)
        assertEquals("uid-custom", result.getOrThrow().uid)
        assertTrue(result.getOrThrow().isTrusted)
    }

    @Test
    fun `custom token que produce identidad anonima se rechaza`() = runTest {
        val user = mockUser(uid = "uid-anon", isAnonymous = true)
        val authResult = mockk<AuthResult>()
        every { authResult.user } returns user
        every { auth.signInWithCustomToken("token-invalido") } returns Tasks.forResult(authResult)

        val result = FirebaseSession(auth).signInWithCustomToken("token-invalido")

        assertTrue(result.isFailure)
    }

    private fun mockUser(uid: String, isAnonymous: Boolean): FirebaseUser =
        mockk<FirebaseUser>().also { user ->
            every { user.uid } returns uid
            every { user.isAnonymous } returns isAnonymous
        }
}
