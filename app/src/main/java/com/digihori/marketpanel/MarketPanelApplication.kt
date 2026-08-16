package com.digihori.marketpanel

import android.app.Application

class MarketPanelApplication : Application() {
    val container: AppContainer by lazy { AppContainer(applicationContext) }
}
