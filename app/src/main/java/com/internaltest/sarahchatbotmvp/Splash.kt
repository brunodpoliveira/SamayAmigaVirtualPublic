package com.internaltest.sarahchatbotmvp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import com.internaltest.sarahchatbotmvp.login.SignIn

class Splash : AppCompatActivity() {
    override fun onCreate(savedInstanceStare: Bundle?) {
        super.onCreate(savedInstanceStare)
        setContentView(R.layout.splash)
        val handler = Handler()
        handler.postDelayed({
            val intent = Intent(applicationContext, SignIn::class.java)
            startActivity(intent)
            finish()
        }, 2000)
    }

    override fun onPause() {
        super.onPause()
        finish()
    }
}