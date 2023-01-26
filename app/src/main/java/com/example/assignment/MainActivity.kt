package com.example.assignment

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.ConnectivityManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.MediaStore
import android.telephony.TelephonyManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.example.assignment.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_CAMERA_PERMISSION = 100
    private var imageUrl: Uri? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startPeriodicWork()
        getInternetConnection()
        binding.tvTimeStamp.text = gotTimestamp()
        getBatteryStatus()
        takePicture(this)

        val location = findCurrentLocation()
        if (location != null) {
            val lat = location.latitude
            val long = location.longitude
            Log.d("Location", "Latitude: $lat Longitude: $long")
            binding.tvLocation.text = "Latitude: $lat Longitude: $long"
        } else {
            Log.e("Location", "Could not find location")
            binding.tvLocation.text = "Could not find location"
        }

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_PHONE_STATE), 0
            )
        } else {
            getIMEI()
        }

        binding.btnUpload.setOnClickListener {
            updateData(imageUrl)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getIMEI()
        }
    }
    private val locationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private fun findCurrentLocation(): Location? {
        var location: Location? = null
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                location = loc
            }
        }
        return location
    }

    private fun takePicture(activity: Activity) {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        activity.startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION
            )
        }
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as Bitmap
            Glide.with(this).load(imageBitmap).into(binding.ivImage)
        }
    }

    private fun uploadImage(imageUri: Uri) {
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.getReference("images")
        val imageRef = storageRef.child("imageName.jpg")
        val uploadTask = imageRef.putFile(imageUri)

        uploadTask.addOnSuccessListener {
            imageRef.downloadUrl.addOnSuccessListener {
                val imageUrl = it.toString()
                updateData(imageUri)
            }
        }
    }

    private fun updateData(imageUrl: Uri?) {
        val imei = binding.tvIMEI.text.toString()
        val timestamp = binding.tvTimeStamp.text.toString()
        val batteryStatus = binding.tvBatStat.text.toString()
        val internetStatus = binding.tvIntConn.text.toString()
        val imageUrl = binding.ivImage

        val data = HashMap<String, String>()
        data["imei"] = imei
        data["timestamp"] = timestamp
        data["batteryStatus"] = batteryStatus
        data["internetStatus"] = internetStatus
        data["imageUrl"] = imageUrl.toString()

        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("status")
        val newRef = myRef.push()
        newRef.setValue(data)
    }
    private fun startPeriodicWork() {
        val workManager = WorkManager.getInstance(this)
        val data =
            Data.Builder().putString("imei", getIMEI())
                .putString("timestamp", gotTimestamp())
                .putString("batteryStatus", getBatteryStatus())
                .putString("internetConnection", getInternetConnection())
                .build()

        val request = PeriodicWorkRequest.Builder(MyWorker::class.java, 15, TimeUnit.MINUTES)
            .setInputData(data).build()
        workManager.enqueue(request)
    }
    @SuppressLint("HardwareIds")
    fun getIMEI(): String {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val imei = telephonyManager.deviceId
        binding.tvIMEI.text = imei
        return imei?.toString() ?: "Unknown IMEI"
    }
    private fun getInternetConnection(): String {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        if (networkInfo != null && networkInfo.isConnected) {
            binding.tvIntConn.text = networkInfo.toString()
            return networkInfo.toString() ?: ""
        }
        return ""
    }
    @SuppressLint("SimpleDateFormat")
    fun gotTimestamp(): String {
        val currentDate = Date()
        val format = SimpleDateFormat("yyyyMMddHHmm ss")
        return format.format(currentDate)
    }
    @SuppressLint("SetTextI18n")
    private fun getBatteryStatus(): String {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        var chargingStatus = ""
        if (isCharging) chargingStatus = "Charging" else chargingStatus = "Not Charging"
        binding.tvBatStat.text = "Charging status: $chargingStatus"
        binding.tvBatPct.text = "Battery Percentage: ${(batteryPct)}%"
        return "Battery Percentage: ${(batteryPct).toInt()}%  Charging status: $chargingStatus"
    }
}





