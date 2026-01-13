package com.immichframe.immichframe

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.preference.PreferenceManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView
import com.immichframe.immichframe.moderntls.ModernTlsOkHttpClient
import com.immichframe.immichframe.sensors.ActivitySensor
import com.immichframe.immichframe.sensors.SensorServiceCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.security.Security
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var imageView1: ImageView
    private lateinit var imageView2: ImageView
    private lateinit var cardPhotoInfoLeft: MaterialCardView
    private lateinit var txtPhotoInfoLeft: TextView
    private lateinit var cardPhotoInfoRight: MaterialCardView
    private lateinit var txtPhotoInfoRight: TextView
    private lateinit var txtDateTime: TextView
    private lateinit var btnPrevious: Button
    private lateinit var btnPause: Button
    private lateinit var btnNext: Button
    private lateinit var dimOverlay: View
    private lateinit var swipeRefreshLayout: View
    private lateinit var serverSettings: Helpers.ServerSettings
    private var retrofit: Retrofit? = null
    private lateinit var apiService: Helpers.ApiService
    private lateinit var rcpServer: RpcHttpServer
    private var keepScreenOn = true
    private var blurredBackground = true
    private var showCurrentDate = true
    private var currentWeather = ""
    private var isImageTimerRunning = false
    private var isScreenTurnedOffByUser = false
    private val handler = Handler(Looper.getMainLooper())
    private var previousImage: Helpers.ImageResponse? = null
    private var currentImage: Helpers.ImageResponse? = null
    private var portraitCache: Helpers.ImageResponse? = null
    private val imageRunnable = object : Runnable {
        override fun run() {
            if (isImageTimerRunning) {
                handler.postDelayed(this, (serverSettings.interval * 1000).toLong())
                getNextImage()
            }
        }
    }

    private val sensorServiceCallback = object : SensorServiceCallback {
        override fun sleep() {
            runOnUiThread {
                turnScreenOff()
            }
        }

        override fun wakeUp() {
            runOnUiThread {
                if(!isDuringDimHours()) {
                    turnScreenOn()
                }
            }
        }
    }

    private val sensorServiceRunnable = object : Runnable {
        override fun run() {
            activitySensor?.checkSensors(sensorServiceCallback)

            handler.postDelayed(this, 1000L)
        }
    }

    private var activitySensor: ActivitySensor? = null
    private var isShowingFirst = true
    private var zoomAnimator: ObjectAnimator? = null

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                loadSettings()
            }
        }

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("alarm", "Received intent in activity")

            turnScreenOff()
            scheduleNextTurnOff()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        //force dark mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Security.insertProviderAt(ModernTlsOkHttpClient.conscrypt(), 1)
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.main_view)

        imageView1 = findViewById(R.id.imageView1)
        imageView2 = findViewById(R.id.imageView2)
        cardPhotoInfoLeft = findViewById(R.id.cardPhotoInfoLeft)
        txtPhotoInfoLeft = findViewById(R.id.txtPhotoInfoLeft)
        cardPhotoInfoRight = findViewById(R.id.cardPhotoInfoRight)
        txtPhotoInfoRight = findViewById(R.id.txtPhotoInfoRight)
        txtDateTime = findViewById(R.id.txtDateTime)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnPause = findViewById(R.id.btnPause)
        btnNext = findViewById(R.id.btnNext)
        dimOverlay = findViewById(R.id.dimOverlay)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            swipeRefreshLayout.isRefreshing = false
            settingsAction()
        }

        btnPrevious.setOnClickListener {
            val toast = Toast.makeText(this, "Previous", Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.CENTER_VERTICAL or Gravity.START, 0, 0)
            toast.show()
            previousAction()
        }

        btnPause.setOnClickListener {
            val toast = Toast.makeText(this, "Pause", Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
            if (isPaused()) {
                resumeAction()
            } else {
                pauseAction()
            }
        }

        btnNext.setOnClickListener {
            val toast = Toast.makeText(this, "Next", Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.CENTER_VERTICAL or Gravity.END, 0, 0)
            toast.show()
            nextAction()
        }

        rcpServer = RpcHttpServer(
            onNextCommand = { runOnUiThread { nextAction() } },
            onPreviousCommand = { runOnUiThread { previousAction() } },
            onPauseCommand = { runOnUiThread { pauseAction() } },
            onSettingsCommand = { runOnUiThread { settingsAction() } },
            onBrightnessCommand = { brightness -> runOnUiThread { screenBrightnessAction(brightness) } },
            onScreenOffCommand = { runOnUiThread { turnScreenOff(true); } },
            onScreenOnCommand = { runOnUiThread { turnScreenOn(true); } },
        )
        rcpServer.start()

        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val savedUrl = prefs.getString("webview_url", "") ?: ""

        if (savedUrl.isBlank()) {
            val intent = Intent(this@MainActivity, SettingsActivity::class.java)
            settingsLauncher.launch(intent)
        } else {
            loadSettings()
        }

        registerReceiver(
            alarmReceiver,
            IntentFilter("TURN_OFF_ALARM")
        )
    }

    private fun showImage(imageResponse: Helpers.ImageResponse) {
        CoroutineScope(Dispatchers.IO).launch {
            //get the window size
            val decorView = window.decorView
            val width = decorView.width
            val height = decorView.height
            val maxSize = maxOf(width, height)

            var randomBitmap = Helpers.decodeBitmapFromBytes(imageResponse.randomImageBase64)
            val thumbHashBitmap = Helpers.decodeBitmapFromBytes(imageResponse.thumbHashImageBase64)
            var isMerged = false

            val isPortrait = randomBitmap.height > randomBitmap.width
            if (isPortrait && serverSettings.layout == "splitview") {
                if (portraitCache != null) {
                    var decodedPortraitImageBitmap =
                        Helpers.decodeBitmapFromBytes(portraitCache!!.randomImageBase64)
                    decodedPortraitImageBitmap =
                        Helpers.reduceBitmapQuality(decodedPortraitImageBitmap, maxSize)
                    randomBitmap = Helpers.reduceBitmapQuality(randomBitmap, maxSize)

                    val colorString =
                        serverSettings.primaryColor?.takeIf { it.isNotBlank() } ?: "#FFFFFF"
                    val parsedColor = colorString.toColorInt()

                    randomBitmap =
                        Helpers.mergeImages(decodedPortraitImageBitmap, randomBitmap, parsedColor)
                    isMerged = true

                    decodedPortraitImageBitmap.recycle()
                } else {
                    portraitCache = imageResponse
                    getNextImage()
                    return@launch
                }
            } else {
                randomBitmap = Helpers.reduceBitmapQuality(randomBitmap, maxSize * 2)
            }

            withContext(Dispatchers.Main) {
                updateUI(randomBitmap, thumbHashBitmap, isMerged, imageResponse)
            }
        }
    }

    private fun updateUI(
        finalImage: Bitmap,
        thumbHashBitmap: Bitmap,
        isMerged: Boolean,
        imageResponse: Helpers.ImageResponse
    ) {
        val imageViewOld = if (isShowingFirst) imageView1 else imageView2
        val imageViewNew = if (isShowingFirst) imageView2 else imageView1

        zoomAnimator?.cancel()
        imageViewNew.alpha = 0f
        imageViewNew.scaleX = 1f
        imageViewNew.scaleY = 1f
        imageViewNew.setImageBitmap(finalImage)
        imageViewNew.visibility = View.VISIBLE

        if (blurredBackground) {
            imageViewNew.background = thumbHashBitmap.toDrawable(resources)
        } else {
            imageViewNew.background = null
        }

        imageViewNew.animate()
            .alpha(1f)
            .setDuration((serverSettings.transitionDuration * 1000).toLong())
            .withEndAction {
                if (serverSettings.imageZoom) {
                    startZoomAnimation(imageViewNew)
                }
            }
            .start()

        imageViewOld.animate()
            .alpha(0f)
            .setDuration((serverSettings.transitionDuration * 1000).toLong())
            .withEndAction {
                imageViewOld.visibility = View.GONE
            }
            .start()

        // Toggle active ImageView
        isShowingFirst = !isShowingFirst

        if (isMerged) {
            updatePhotoInfo(
                portraitCache?.photoDate ?: "",
                portraitCache?.imageLocation ?: "",
                imageResponse.photoDate,
                imageResponse.imageLocation
            )
            portraitCache = null
        } else {
            updatePhotoInfo(
                "",
                "",
                imageResponse.photoDate,
                imageResponse.imageLocation
            )
        }

        updateDateTimeWeather()
    }

    private fun updatePhotoInfo(
        leftPhotoDate: String,
        leftPhotoLocation: String,
        rightPhotoDate: String,
        rightPhotoLocation: String
    ) {
        if (serverSettings.showPhotoDate || serverSettings.showImageLocation) {
            txtPhotoInfoLeft.textSize =
                Helpers.cssFontSizeToSp(serverSettings.baseFontSize, this)
            txtPhotoInfoRight.textSize =
                Helpers.cssFontSizeToSp(serverSettings.baseFontSize, this)
            if (serverSettings.primaryColor != null) {
                txtPhotoInfoLeft.setTextColor(
                    runCatching { serverSettings.primaryColor!!.toColorInt() }
                        .getOrDefault(Color.WHITE)
                )
                txtPhotoInfoRight.setTextColor(
                    runCatching { serverSettings.primaryColor!!.toColorInt() }
                        .getOrDefault(Color.WHITE)
                )
            } else {
                txtPhotoInfoLeft.setTextColor(Color.WHITE)
                txtPhotoInfoRight.setTextColor(Color.WHITE)
            }


            val leftPhotoInfo = buildString {
                if (serverSettings.showPhotoDate && leftPhotoDate.isNotEmpty()) {
                    append(leftPhotoDate)
                }
                if (serverSettings.showImageLocation && leftPhotoLocation.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append(leftPhotoLocation)
                }
            }
            txtPhotoInfoLeft.text = leftPhotoInfo
            cardPhotoInfoLeft.visibility =
                if (leftPhotoInfo.isNotEmpty()) View.VISIBLE else View.GONE

            val rightPhotoInfo = buildString {
                if (serverSettings.showPhotoDate && rightPhotoDate.isNotEmpty()) {
                    append(rightPhotoDate)
                }
                if (serverSettings.showImageLocation && rightPhotoLocation.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append(rightPhotoLocation)
                }
            }
            txtPhotoInfoRight.text = rightPhotoInfo
            cardPhotoInfoRight.visibility =
                if (rightPhotoInfo.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun updateDateTimeWeather() {
        if (serverSettings.showClock) {
            val currentDateTime = Calendar.getInstance().time

            val formattedDate = try {
                SimpleDateFormat(serverSettings.photoDateFormat, Locale.getDefault()).format(
                    currentDateTime
                )
            } catch (_: Exception) {
                ""
            }

            val formattedTime = try {
                SimpleDateFormat(serverSettings.clockFormat, Locale.getDefault()).format(
                    currentDateTime
                )
            } catch (_: Exception) {
                ""
            }

            val dt = if (showCurrentDate && formattedDate.isNotEmpty()) {
                "$formattedDate\n$formattedTime"
            } else {
                formattedTime
            }

            txtDateTime.text = SpannableString(dt).apply {
                val start =
                    if (showCurrentDate && formattedDate.isNotEmpty()) formattedDate.length + 1 else 0
                setSpan(RelativeSizeSpan(2f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        if (serverSettings.showWeatherDescription) {
            txtDateTime.append(currentWeather)
        }
    }

    private fun getNextImage() {
        apiService.getImageData().enqueue(object : Callback<Helpers.ImageResponse> {
            override fun onResponse(
                call: Call<Helpers.ImageResponse>,
                response: Response<Helpers.ImageResponse>
            ) {
                if (response.isSuccessful) {
                    val imageResponse = response.body()
                    if (imageResponse != null) {
                        previousImage = currentImage
                        currentImage = imageResponse
                        showImage(imageResponse)
                    }
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to load image (HTTP ${response.code()})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<Helpers.ImageResponse>, t: Throwable) {
                t.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    "Failed to load image: ${t.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun startImageTimer() {
        if (!isImageTimerRunning) {
            isImageTimerRunning = true
            handler.postDelayed(imageRunnable, (serverSettings.interval * 1000).toLong())
        }
    }

    private fun stopImageTimer() {
        isImageTimerRunning = false
        handler.removeCallbacks(imageRunnable)
    }

    private fun startZoomAnimation(imageView: ImageView) {
        zoomAnimator?.cancel()
        zoomAnimator = ObjectAnimator.ofPropertyValuesHolder(
            imageView,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.2f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.2f)
        )
        zoomAnimator?.duration = (serverSettings.interval * 1000).toLong()
        zoomAnimator?.start()
    }

    private fun getServerSettings(
        onSuccess: (Helpers.ServerSettings) -> Unit,
        onFailure: (Throwable) -> Unit,
        maxRetries: Int = 36,
        retryDelayMillis: Long = 5000
    ) {
        var retryCount = 0

        fun attemptFetch() {
            apiService.getServerSettings().enqueue(object : Callback<Helpers.ServerSettings> {
                override fun onResponse(
                    call: Call<Helpers.ServerSettings>,
                    response: Response<Helpers.ServerSettings>
                ) {
                    if (response.isSuccessful) {
                        val serverSettingsResponse = response.body()
                        if (serverSettingsResponse != null) {
                            onSuccess(serverSettingsResponse)
                        } else {
                            handleFailure(Exception("Empty response body"))
                        }
                    } else {
                        handleFailure(Exception("HTTP ${response.code()}: ${response.message()}"))
                    }
                }

                override fun onFailure(call: Call<Helpers.ServerSettings>, t: Throwable) {
                    handleFailure(t)
                }

                private fun handleFailure(t: Throwable) {
                    Log.e("Settings", "Error when fetching server settings", t)
                    if (retryCount < maxRetries) {
                        retryCount++
                        Toast.makeText(
                            this@MainActivity,
                            "Retrying to fetch server settings... Attempt $retryCount of $maxRetries",
                            Toast.LENGTH_SHORT
                        ).show()
                        Handler(Looper.getMainLooper()).postDelayed({
                            attemptFetch()
                        }, retryDelayMillis)
                    } else {
                        onFailure(t)
                    }
                }
            })
        }

        attemptFetch()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        blurredBackground = prefs.getBoolean("blurredBackground", true)
        showCurrentDate = prefs.getBoolean("showCurrentDate", true)
        val savedUrl = prefs.getString("webview_url", "") ?: ""

        keepScreenOn = prefs.getBoolean("keepScreenOn", true)
        val authSecret = prefs.getString("authSecret", "") ?: ""
        val headers = prefs.getString("headers", "") ?: ""
        //val screenDim = prefs.getBoolean("screenDim", false)
        val settingsLock = prefs.getBoolean("settingsLock", false)

        swipeRefreshLayout.isEnabled = !settingsLock
        cardPhotoInfoLeft.visibility =
            View.GONE //enabled in onSettingsLoaded based on server settings
        cardPhotoInfoRight.visibility =
            View.GONE //enabled in onSettingsLoaded based on server settings
        txtDateTime.visibility = View.GONE //enabled in onSettingsLoaded based on server settings
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val wakeLockMinutes = prefs.getString("wakeLockMinutes", "15")
        activitySensor = ActivitySensor(this, wakeLockMinutes!!.toInt())

        retrofit = Helpers.createRetrofit(savedUrl, authSecret, headers)
        apiService = retrofit!!.create(Helpers.ApiService::class.java)
        getServerSettings(
            onSuccess = { settings ->
                serverSettings = settings
                onSettingsLoaded()
            },
            onFailure = { error ->
                Toast.makeText(
                    this,
                    "Failed to load server settings: ${error.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    private fun onSettingsLoaded() {
        if (serverSettings.imageFill) {
            imageView1.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView2.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            imageView1.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView2.scaleType = ImageView.ScaleType.FIT_CENTER
        }

        if (serverSettings.showClock) {
            txtDateTime.visibility = View.VISIBLE
            txtDateTime.textSize = Helpers.cssFontSizeToSp(serverSettings.baseFontSize, this)
            if (serverSettings.primaryColor != null) {
                txtDateTime.setTextColor(
                    runCatching { serverSettings.primaryColor!!.toColorInt() }
                        .getOrDefault(Color.WHITE)
                )
            } else {
                txtDateTime.setTextColor(Color.WHITE)
            }
        } else {
            txtDateTime.visibility = View.GONE
        }

        getNextImage()
        startImageTimer()
        this.handler.post(sensorServiceRunnable)
    }

    private fun previousAction() {
        val safePreviousImage = previousImage
        if (safePreviousImage != null) {
            stopImageTimer()
            showImage(safePreviousImage)
            startImageTimer()
        }
    }

    private fun nextAction() {
        stopImageTimer()
        getNextImage()
        startImageTimer()
    }

    private fun isPaused(): Boolean {
        return !isImageTimerRunning
    }

    private fun pauseAction() {
        zoomAnimator?.cancel()
        stopImageTimer()
    }

    private fun resumeAction() {
        getNextImage()
        startImageTimer()
    }

    private fun settingsAction() {
        val intent = Intent(this, SettingsActivity::class.java)
        stopImageTimer()
        settingsLauncher.launch(intent)
    }

    private fun screenBrightnessAction(brightness: Float) {
        val lp = window.attributes
        lp.screenBrightness = brightness
        window.attributes = lp
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_F4 -> { // Settings button
                    settingsAction()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    val text = if (isImageTimerRunning) "|| Pause" else "▶ Play"
                    val toast = Toast.makeText(this, text, Toast.LENGTH_SHORT)
                    toast.setGravity(Gravity.CENTER, 0, 0)
                    toast.show()
                    if (isPaused()) {
                        resumeAction()
                    } else {
                        pauseAction()
                    }
                    return true
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val toast = Toast.makeText(this, "← Previous", Toast.LENGTH_SHORT)
                    toast.setGravity(Gravity.CENTER_VERTICAL or Gravity.START, 0, 0)
                    toast.show()
                    previousAction()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val toast = Toast.makeText(this, "Next →", Toast.LENGTH_SHORT)
                    toast.setGravity(Gravity.CENTER_VERTICAL or Gravity.END, 0, 0)
                    toast.show()
                    nextAction()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    // available
                    return true
                }


                KeyEvent.KEYCODE_F1 -> { // Power button
                    if (isScreenOn()) {
                        turnScreenOff(true)
                    } else {
                        turnScreenOn(true)
                    }
                    return true
                }

                KeyEvent.KEYCODE_F2 -> { // Album button
                    // available
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isScreenOn(): Boolean {
        val powerManager = this.getSystemService(POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        return powerManager.isScreenOn
    }

    private fun turnScreenOff(isUserInitiated: Boolean = false) {
        val isOn = isScreenOn()
        if (isOn) {
            isScreenTurnedOffByUser = isUserInitiated

            Log.i("immichframe", "Turns screen off")
            pauseAction()

            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(this, MyDeviceAdminReceiver::class.java)

            if (dpm.isAdminActive(admin)) {
                dpm.lockNow()
            }
        }
    }

    private fun turnScreenOn(isUserInitiated: Boolean = false) {
        val isOff = !isScreenOn()
        val shouldProceed = isUserInitiated || !isScreenTurnedOffByUser
        if (isOff && shouldProceed) {
            isScreenTurnedOffByUser = false

            Log.i("immichframe", "Turns screen on")
            resumeAction()

            val pm = getSystemService(POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION") val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "immichframe:wake"
            )
            wl.acquire(3000)
        }
    }

    override fun onResume() {
        super.onResume()
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        scheduleNextTurnOff()
    }

    override fun onDestroy() {
        super.onDestroy()
        rcpServer.stop()
        handler.removeCallbacksAndMessages(null)
    }

    fun isDuringDimHours(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        val startHour = prefs.getInt("dimStartHour", 22)
        val startMinute = prefs.getInt("dimStartMinute", 0)
        val endHour = prefs.getInt("dimEndHour", 7)
        val endMinute = prefs.getInt("dimEndMinute", 0)

        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return if (startMinutes < endMinutes) {
            // Same-day range (e.g. 09:00 → 18:00)
            nowMinutes in startMinutes until endMinutes
        } else {
            // Overnight range (e.g. 22:00 → 07:00)
            nowMinutes !in endMinutes..<startMinutes
        }
    }

    fun scheduleNextTurnOff() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val hour = prefs.getInt("dimStartHour", 22)
        val minute = prefs.getInt("dimStartMinute", 0)

        val intent = Intent(this, AlarmReceiver::class.java);

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            // If time already passed today → schedule tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        Log.d("alarm", "schedule alarm at: ${calendar.timeInMillis}")

        alarmManager.setExact(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }
}
