package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // I-co-connect nito ang MainActivity sa activity_main.xml na nakikita sa res/layout mo
        setContentView(R.layout.activity_main)
    }
}