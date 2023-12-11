package com.internaltest.sarahchatbotmvp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.internaltest.sarahchatbotmvp.auth.SignIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Splash : AppCompatActivity() {
    private val uiScope = CoroutineScope(Dispatchers.Main)
    companion object {
        var isCurrentActivity = false
    }

    override fun onResume() {
        super.onResume()
        isCurrentActivity = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash)

        uiScope.launch {
            delay(2000)
            val intent = Intent(applicationContext, SignIn::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        isCurrentActivity = false
        finish()
    }
}
