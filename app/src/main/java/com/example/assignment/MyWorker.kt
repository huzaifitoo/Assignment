package com.example.assignment

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.database.FirebaseDatabase

class MyWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val imei = inputData.getString("imei") ?: ""
        val timestamp = inputData.getString("timestamp") ?: ""
        val batteryStatus = inputData.getString("batteryStatus") ?: ""
        val internetConnection = inputData.getString("internetConnection") ?: ""
        val image = inputData.getString("image") ?: ""

        val data = HashMap<String, String>()
        data["imei"] = imei
        data["timestamp"] = timestamp
        data["batteryStatus"] = batteryStatus
        data["internetConnection"] = internetConnection
        data["image"] = image

        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("status")
        myRef.push().setValue(data)

        return Result.success()
    }
}





