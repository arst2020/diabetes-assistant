package com.arst.diabetesassistant

import android.app.Application
import com.arst.diabetesassistant.alarm.Notifier
import com.arst.diabetesassistant.alarm.Scheduler
import com.arst.diabetesassistant.data.Store

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifier.createChannels(this)
        Scheduler.rescheduleAll(this)
    }
}
