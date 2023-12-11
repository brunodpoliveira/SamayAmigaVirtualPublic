package com.internaltest.sarahchatbotmvp

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.internaltest.sarahchatbotmvp.auth.SignIn
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

//TODO testar
@RunWith(AndroidJUnit4::class)
class LoginActivityTest {
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var activityScenario: ActivityScenario<SignIn>

    @Before
    fun setUp() {
        // Mock FirebaseAuth instance
        firebaseAuth = mock(FirebaseAuth::class.java)

        // Launch SignInActivity
        activityScenario = ActivityScenario.launch(SignIn::class.java)
    }

    @Test
    fun whenLoginClicked_VerifySuccess() {
        // Given a mocked FirebaseAuth instance
        `when`(firebaseAuth.currentUser).thenReturn(mock(FirebaseUser::class.java))

        // Perform a click on the signInButton
        onView(withId(R.id.sign_in_button)).perform(click())

        // Verify that FirebaseAuth#signInWithCredential was called
        //(you should replace the `any()` with your respective token)
        verify(firebaseAuth).signInWithCredential(any())

        // Verify user navigates to the MainActivity after successful login
        onView(withId(R.layout.activity_main)).check(matches(isDisplayed()))
    }

    @Test
    fun whenLoginClicked_VerifyFailure() {
        // Given a mocked FirebaseAuth instance
        `when`(firebaseAuth.currentUser).thenReturn(null)

        // Perform a click on the signInButton
        onView(withId(R.id.sign_in_button)).perform(click())

        // Verify that FirebaseAuth#signInWithCredential was called
        //(you should replace the `any()` with your respective token)
        verify(firebaseAuth).signInWithCredential(any())

        // Verify that user remains on the SignInActivity when login fails
        onView(withId(R.layout.activity_sign_in)).check(matches(isDisplayed()))
    }

    @After
    fun tearDown() {
        activityScenario.close()
    }
}
