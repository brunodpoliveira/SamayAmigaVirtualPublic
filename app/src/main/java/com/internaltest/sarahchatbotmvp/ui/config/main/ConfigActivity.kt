package com.internaltest.sarahchatbotmvp.ui.config.main

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import com.internaltest.sarahchatbotmvp.R
import com.internaltest.sarahchatbotmvp.base.BaseActivity
import com.internaltest.sarahchatbotmvp.databinding.ActivityConfigBinding
import com.internaltest.sarahchatbotmvp.ui.config.main.ConfigFragment
import com.internaltest.sarahchatbotmvp.ui.main.MainActivity

class ConfigActivity() : BaseActivity() {

    private lateinit var binding: ActivityConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}