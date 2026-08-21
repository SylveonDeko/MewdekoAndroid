package dev.mewdeko.mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point; installs the Hilt dependency graph. */
@HiltAndroidApp
class MewdekoApplication : Application()
