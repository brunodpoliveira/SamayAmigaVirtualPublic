package com.internaltest.sarahchatbotmvp

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.internaltest.sarahchatbotmvp.auth.SignIn
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class LoginAcitivityViewTest {

    private lateinit var activityScenario: ActivityScenario<SignIn>

    @JvmField
    @Rule
    var activityScenarioRule = activityScenarioRule<SignIn>()

    @Before
    fun setUp() {
        Intents.init()
        activityScenario = ActivityScenario.launch(SignIn::class.java)
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun whenPoliticaDePrivacidadeClicked_VerifyBrowserIsOpened() {
        onView(withId(R.id.privacy)).perform(click())
        val url = "https://teqbot.com.br/notificacao-de-privacidade/"
        Intents.intended(
            allOf(
                hasAction(Intent.ACTION_VIEW),
                hasData(url)
            )
        )
    }

    @Test
    fun whenTermosDeUsoClicked_VerifyBrowserIsOpened() {
        onView(withId(R.id.terms_of_use)).perform(click())
        val url = "https://teqbot.com.br/termos-de-servico/"
        Intents.intended(
            allOf(
                hasAction(Intent.ACTION_VIEW),
                hasData(url)
            )
        )
    }


    @Test
    fun whenLoginClicked_VerifySuccess() {
        //TODO: Preciso ver uma maneira de testar o click no dialog do Google Account
    }

}