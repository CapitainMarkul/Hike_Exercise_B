package ru.palestra.hike_exercise_b.domain.location

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.location.Location
import androidx.annotation.RequiresPermission
import ru.palestra.hike_exercise_b.data.LocationPoint

/** Описание объекта, который отвечает за работу с геолокацией. */
interface NearbyLocationManagerApi {

    /** Метод для получения текущего метоположения устройства. */
    fun getMyDeviceLocationPoint(): LocationPoint?

    /** Метод для получения текущего метоположения устройства и запроса нового. */
    @RequiresPermission(anyOf = [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION])
    fun getLastLastGeolocationAndRequestUpdate(): Location?

    /**
     * Метод для подписки на события необходимости запросить актуальное метоположение устройства.
     *
     * @param onNeedRequestActualGeolocationAction действие, при необходимости обновления геолокации устройства.
     * */
    fun observeRequestGeolocationChanges(onNeedRequestActualGeolocationAction: () -> Unit)

    /** Метод проверяет, включен ли у пользователя в данный момент GPS. */
    fun isGpsLocationEnabled(): Boolean

    /** Метод просит пользователя активировать работу GPS. */
    fun requestToEnableGpsLocation()

    /**
     * Метод пытается расшифровать массив байт в модель [LocationPoint],
     * в случае неудачи - вернет null.
     *
     * @param rawGeolocationData "сырые" данные для анализа.
     * */
    fun tryConvertRawDataToLocation(rawGeolocationData: String): LocationPoint?

    /**
     * Метод пытается зашифровать данные из [Location] в массив байт,
     * в случае неудачи - вернет null.
     *
     * @param location текущее местоположение [Location].
     * */
    fun tryConvertLocationToRawData(location: Location?): ByteArray?

    /**
     * Метод расчитывает расстояние между двумя точками.
     *
     * @param firstPoint первая точка.
     * @param secondPoint вторая точка.
     *
     * @return расстояние между точками.
     * */
    fun calculateDistanceBetweenLocationPoints(firstPoint: LocationPoint, secondPoint: LocationPoint): Float
}