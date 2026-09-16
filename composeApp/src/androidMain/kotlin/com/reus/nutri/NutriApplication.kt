package com.reus.nutri

import android.app.Application

class NutriApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        androidContext = this
    }
}
