package com.example.kinetizk.demo

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.kinetizk.demo.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySplashBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set status bar color
        window.statusBarColor = getColor(R.color.background_dark)
        
        // Handle Get Started button click
        binding.btnGetStarted.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        
        // Handle Learn More button click
        binding.btnLearnMore.setOnClickListener {
            // Could open a web page or show more info
            // For now, just show a simple message
        }
    }
}
