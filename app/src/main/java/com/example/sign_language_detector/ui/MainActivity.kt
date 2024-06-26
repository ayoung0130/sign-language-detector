package com.example.sign_language_detector.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.sign_language_detector.R
import com.example.sign_language_detector.databinding.ActivityMainBinding
import com.example.sign_language_detector.util.LandmarkProcessor
import dagger.hilt.android.AndroidEntryPoint

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

}