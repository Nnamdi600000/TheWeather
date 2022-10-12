package com.codennamdi.theweather.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.codennamdi.theweather.Constants
import com.codennamdi.theweather.R
import com.codennamdi.theweather.databinding.ActivityMainBinding
import com.codennamdi.theweather.models.WeatherResponse
import com.codennamdi.theweather.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var binding: ActivityMainBinding
    private lateinit var customProgressDialog: Dialog


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        userPermissions()
        binding.swipeToRefresh.setOnRefreshListener {
            requestLocationData()
            updated()
        }
    }

    private fun updated() {
        binding.swipeToRefresh.isRefreshing = false
    }

    private fun userPermissions() {
        if (!isLocationEnabled()) {
            Snackbar.make(
                findViewById(android.R.id.content),
                "Please enable your location provider, it is turned off.",
                Snackbar.LENGTH_LONG
            ).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withContext(this@MainActivity)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ).withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "Please grant all permissions, if not the app won't work.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>,
                        token: PermissionToken
                    ) {
                        showRationalDialog()
                    }
                }).onSameThread().check()
        }
    }

    private fun showRationalDialog() {
        AlertDialog.Builder(this@MainActivity)
            .setMessage("You did not enable the permissions for this feature, you can do that by heading to settings of the app")
            .setPositiveButton("GOTO SETTINGS") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun isLocationEnabled(): Boolean {
        //This provide access to the system location service.
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    //Here we are getting the users current location.
    private val mLocationCallback = object : LocationCallback() {
        @RequiresApi(Build.VERSION_CODES.M)
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation

            val latitude = mLastLocation?.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation?.longitude
            Log.i("Current Longitude", "$longitude")

            getMyLocationWeatherDetails(latitude!!, longitude!!)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority =
            com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getMyLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this@MainActivity)) {
            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()
            val service: WeatherService = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> =
                service.getWeather(latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID)
            customProgressDialog()
            listCall.enqueue(object : Callback<WeatherResponse> {
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful) {
                        stopCustomProgressDialog()
                        val weatherList: WeatherResponse? = response.body()
                        if (weatherList != null) {
                            setUpUI(weatherList)
                        }
                        Log.i("Response result", "$weatherList")
                    } else {
                        stopCustomProgressDialog()
                        val rc = response.code()
                        when (rc) {
                            400 -> {
                                Log.e("Error 400", "bad connection")
                            }
                            404 -> {
                                Log.e("Error 404", "not found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    stopCustomProgressDialog()
                    Log.e("Error", t.message.toString())
                }
            })
        } else {
            Snackbar.make(
                findViewById(android.R.id.content),
                "Internet network is not available, please enable your internet connection.",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun customProgressDialog() {
        customProgressDialog = Dialog(this)
        customProgressDialog.setContentView(R.layout.custom_dialog)
        customProgressDialog.show()
    }

    private fun stopCustomProgressDialog() {
        customProgressDialog.dismiss()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setUpUI(weatherList: WeatherResponse) {
        for (i in weatherList.weather.indices) {
            Log.i("current weather", weatherList.weather.toString())
            binding.weatherMainDescriptionId.text = weatherList.weather[i].main
            binding.fullDescriptionId.text = weatherList.weather[i].description
            binding.temperatureId.text =
                weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
            binding.sunriseId.text = unixTime(weatherList.sys.sunrise)
            binding.sunsetId.text = unixTime(weatherList.sys.sunset)
            binding.location.text = weatherList.name + ","
            binding.country.text = weatherList.sys.country
            binding.humidityPercent.text = weatherList.main.humidity.toString() + "%"
            binding.windId.text = weatherList.wind.speed.toString() + "km/h"
            binding.feelsId.text = weatherList.main.feels_like.toString() + " Feels like"

            when (weatherList.weather[i].icon) {
                "01d" -> binding.ivWeatherCondition.setImageResource(R.drawable.sunny)
                "01n" -> binding.ivWeatherCondition.setImageResource(R.drawable.clear_night)
                "02d" -> binding.ivWeatherCondition.setImageResource(R.drawable.cloudy_img)
                "02n" -> binding.ivWeatherCondition.setImageResource(R.drawable.cloudy_night)
                "03d" -> binding.ivWeatherCondition.setImageResource(R.drawable.scattered_clouds)
                "03n" -> binding.ivWeatherCondition.setImageResource(R.drawable.scattered_clouds)
                "04d" -> binding.ivWeatherCondition.setImageResource(R.drawable.broken_clouds)
                "04n" -> binding.ivWeatherCondition.setImageResource(R.drawable.broken_clouds)
                "09d" -> binding.ivWeatherCondition.setImageResource(R.drawable.shower_rain)
                "09n" -> binding.ivWeatherCondition.setImageResource(R.drawable.shower_rain)
                "10d" -> binding.ivWeatherCondition.setImageResource(R.drawable.rain)
                "10n" -> binding.ivWeatherCondition.setImageResource(R.drawable.rain)
                "11d" -> binding.ivWeatherCondition.setImageResource(R.drawable.thunderstorm_img)
                "11n" -> binding.ivWeatherCondition.setImageResource(R.drawable.thunderstorm_img)
                "13d" -> binding.ivWeatherCondition.setImageResource(R.drawable.snow)
                "13n" -> binding.ivWeatherCondition.setImageResource(R.drawable.snow)
                "50d" -> binding.ivWeatherCondition.setImageResource(R.drawable.mist)
                "50n" -> binding.ivWeatherCondition.setImageResource(R.drawable.mist)
            }
        }
    }

    private fun getUnit(value: String): String? {
        var value = "\u2103"

        if ("US" == value || "LR" == value || "MM" == value) {
            value = "\u2109"
        }
        return value
    }

    private fun unixTime(timeX: Long): String? {
        val date = Date(timeX * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}