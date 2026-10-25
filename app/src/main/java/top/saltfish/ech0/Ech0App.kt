package top.saltfish.ech0

import android.app.Application
import top.saltfish.ech0.data.Ech0Store

class Ech0App : Application() {
    override fun onCreate() {
        super.onCreate()
        Ech0Store.init(this)
    }
}
