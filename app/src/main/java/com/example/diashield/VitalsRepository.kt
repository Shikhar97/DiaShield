package com.example.diashield

import androidx.annotation.WorkerThread

class VitalsRepository(private val vitalsDao: VitalsDb.VitalsDao) {
    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insert(vitalsUser: VitalsDb.VitalsUser) {
        vitalsDao.insert(vitalsUser)
    }
}
