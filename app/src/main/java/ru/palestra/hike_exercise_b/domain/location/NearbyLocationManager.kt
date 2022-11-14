package ru.palestra.hike_exercise_b.domain.location

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import ru.palestra.hike_exercise_b.data.LocationPoint
import ru.palestra.hike_exercise_b.data.LocationPoint.Companion.END_WORD_MARKER
import ru.palestra.hike_exercise_b.data.LocationPoint.Companion.START_WORD_MARKER
import ru.palestra.hike_exercise_b.domain.utils.disposeIfNeeded
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Объект, который отвечает за работу с геолокацией. */
internal class NearbyLocationManager(
    private val context: Context,
    private var lifecycleOwner: LifecycleOwner? = (context as? LifecycleOwner)
) : NearbyLocationManagerApi, LifecycleEventObserver {

    private companion object {
        /* Запрашиваем новое местоположение каждые 5 секунд. */
        private const val INTERVAL_REQUEST_NEW_GEOLOCATION = 5000L
    }

    init {
        lifecycleOwner?.lifecycle?.addObserver(this)
    }

    private val serializableManager: Gson by lazy { Gson() }

    private var observableActualGeolocation: Disposable? = null

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            observableActualGeolocation?.disposeIfNeeded()

            lifecycleOwner?.lifecycle?.removeObserver(this)
            lifecycleOwner = null
        }
    }

    private val locationManager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var lastKnownDevicePosition: Location? = null

    private val locationListener: LocationListener = LocationListener { location ->
        lastKnownDevicePosition = location
    }

    override fun getMyDeviceLocationPoint(): LocationPoint? =
        lastKnownDevicePosition?.let { LocationPoint(it.latitude, it.longitude) }

    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    override fun getLastLastGeolocationAndRequestUpdate(): Location? {
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0F, locationListener)
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0F, locationListener)

        return lastKnownDevicePosition
    }

    override fun observeRequestGeolocationChanges(onNeedRequestActualGeolocationAction: () -> Unit) {
        observableActualGeolocation = Observable
            .interval(INTERVAL_REQUEST_NEW_GEOLOCATION, TimeUnit.MILLISECONDS)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { onNeedRequestActualGeolocationAction() }
    }

    override fun isGpsLocationEnabled(): Boolean {
        val mLocationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return mLocationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
    }

    override fun requestToEnableGpsLocation() =
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    override fun tryConvertRawDataToLocation(rawGeolocationData: String): LocationPoint? =
        try {
            serializableManager.fromJson(rawGeolocationData, LocationPoint::class.java)
        } catch (e: Exception) {
            /* Если не смогли распарсить, то считаем это сторонней информацией. */
            null
        }

    override fun tryConvertLocationToRawData(location: Location?): ByteArray? =
        location?.let {
            "$START_WORD_MARKER${serializableManager.toJson(LocationPoint(it.latitude, it.longitude))}$END_WORD_MARKER"
                .toByteArray()
        }

    override fun calculateDistanceBetweenLocationPoints(firstPoint: LocationPoint, secondPoint: LocationPoint): Float =
        computeDistance(firstPoint.latitude, firstPoint.longitude, secondPoint.latitude, secondPoint.longitude)

    private fun computeDistance(
        firstPointLat: Double,
        firstPointLon: Double,
        secondPointLat: Double,
        secondPointLon: Double
    ): Float {
        // Based on http://www.ngs.noaa.gov/PUBS_LIB/inverse.pdf
        // using the "Inverse Formula" (section 4)

        // Convert lat/long to radians
        val lat1 = firstPointLat * Math.PI / 180.0
        val lon1 = firstPointLon * Math.PI / 180.0
        val lat2 = secondPointLat * Math.PI / 180.0
        val lon2 = secondPointLon * Math.PI / 180.0
        val a = 6378137.0 // WGS84 major axis
        val b = 6356752.3142 // WGS84 semi-major axis
        val f = (a - b) / a
        val aSqMinusBSqOverBSq = (a * a - b * b) / (b * b)
        val l = lon2 - lon1
        var aA = 0.0
        val u1 = atan((1.0 - f) * tan(lat1))
        val u2 = atan((1.0 - f) * tan(lat2))
        val cosU1 = cos(u1)
        val cosU2 = cos(u2)
        val sinU1 = sin(u1)
        val sinU2 = sin(u2)
        val cosU1cosU2 = cosU1 * cosU2
        val sinU1sinU2 = sinU1 * sinU2
        var sigma = 0.0
        var deltaSigma = 0.0
        var cosSqAlpha: Double
        var cos2SM: Double
        var cosSigma: Double
        var sinSigma: Double
        var cosLambda: Double
        var sinLambda: Double
        var lambda = l // initial guess
        for (iter in 0..19) {
            val lambdaOrig = lambda
            cosLambda = cos(lambda)
            sinLambda = sin(lambda)
            val t1 = cosU2 * sinLambda
            val t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda
            val sinSqSigma = t1 * t1 + t2 * t2
            sinSigma = sqrt(sinSqSigma)
            cosSigma = sinU1sinU2 + cosU1cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            val sinAlpha = if (sinSigma == 0.0) 0.0 else cosU1cosU2 * sinLambda / sinSigma
            cosSqAlpha = 1.0 - sinAlpha * sinAlpha
            cos2SM = if (cosSqAlpha == 0.0) 0.0 else cosSigma - 2.0 * sinU1sinU2 / cosSqAlpha
            val uSquared = cosSqAlpha * aSqMinusBSqOverBSq
            aA = 1 + uSquared / 16384.0 * (4096.0 + uSquared * (-768 + uSquared * (320.0 - 175.0 * uSquared)))
            val bB = uSquared / 1024.0 * (256.0 + uSquared * (-128.0 + uSquared * (74.0 - 47.0 * uSquared)))
            val cC = f / 16.0 * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha))
            val cos2SMSq = cos2SM * cos2SM
            deltaSigma = bB * sinSigma * (cos2SM + bB / 4.0 * (cosSigma * (-1.0 + 2.0 * cos2SMSq)
                    - bB / 6.0 * cos2SM * (-3.0 + 4.0 * sinSigma * sinSigma) * (-3.0
                    + 4.0 * cos2SMSq)))
            lambda = l + (1.0 - cC) * f * sinAlpha * (sigma + cC * sinSigma * (cos2SM
                    + cC * cosSigma * (-1.0 + 2.0 * cos2SM * cos2SM)))
            val delta = (lambda - lambdaOrig) / lambda
            if (abs(delta) < 1.0e-12) {
                break
            }
        }
        return (b * aA * (sigma - deltaSigma)).toFloat()
    }
}