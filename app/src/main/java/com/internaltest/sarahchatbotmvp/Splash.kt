package com.internaltest.sarahchatbotmvp

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.internaltest.sarahchatbotmvp.auth.SignIn

class Splash : AppCompatActivity() {
    private val splashTimeOut: Long = 1500
    private val animationDuration: Long = 600
    private val textAnimationDelay: Long = 400

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash)

        Handler().postDelayed({
            val intent = Intent(this, SignIn::class.java)
            startActivity(intent)
            finish()
        }, splashTimeOut)

        animateLogoAndText()
    }

    private fun animateLogoAndText() {
        val imgLogo = findViewById<View>(R.id.imageViewLogo)
        val textViewDescription = findViewById<View>(R.id.textViewDescription)
        val textView = findViewById<View>(R.id.textView)

        imgLogo.translationY = 100f

        val translateY = ObjectAnimator.ofFloat(imgLogo, View.TRANSLATION_Y, 0f)
        translateY.duration = animationDuration

        translateY.start()

        // Configurar animação para o texto
        val textAnimation = ObjectAnimator.ofFloat(textViewDescription, View.ALPHA, 0f, 1f)
        textAnimation.duration = animationDuration
        textAnimation.startDelay = textAnimationDelay

        val textAnimation2 = ObjectAnimator.ofFloat(textView, View.ALPHA, 0f, 1f)
        textAnimation2.duration = animationDuration
        textAnimation2.startDelay = textAnimationDelay

        // Agrupar as animações usando AnimatorSet
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(textAnimation, textAnimation2)
        animatorSet.start()
    }
}

