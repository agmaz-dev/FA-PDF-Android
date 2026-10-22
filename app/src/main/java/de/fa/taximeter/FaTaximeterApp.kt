package de.fa.taximeter

import android.app.Application
import de.fa.taximeter.data.MeterRepository

class FaTaximeterApp : Application() {
    lateinit var repository: MeterRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = MeterRepository(this)
    }
}
