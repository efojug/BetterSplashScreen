package moe.efojug.bettersplashscreen

import android.app.Application
import moe.efojug.bettersplashscreen.status.ModuleStatusRepository

class BetterSplashApplication : Application() {
    val moduleStatus: ModuleStatusRepository by lazy { ModuleStatusRepository(this) }
}
