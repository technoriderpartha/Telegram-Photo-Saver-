package com.example.tgphotosaver

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Td.init(this)
    }
}
