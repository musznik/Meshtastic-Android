package com.geeksville.mesh

import android.graphics.Color
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geeksville.mesh.util.GPSFormat
import com.geeksville.mesh.util.bearing
import com.geeksville.mesh.util.latLongToMeter
import com.geeksville.mesh.util.anonymize
import kotlinx.parcelize.Parcelize

/**
 * Room [Embedded], [Entity] and [PrimaryKey] annotations and imports, as well as any protobuf
 * reference [MeshProtos], [TelemetryProtos], [ConfigProtos] can be removed when only using the API.
 * For details check the AIDL interface in [com.geeksville.mesh.IMeshService]
 */

//
// model objects that directly map to the corresponding protobufs
//

@Parcelize
data class MeshUser(
    val id: String,
    val longName: String,
    val shortName: String,
    val hwModel: MeshProtos.HardwareModel,
    val isLicensed: Boolean = false,
    @ColumnInfo(name = "role", defaultValue = "0")
    val role: Int = 0,
) : Parcelable {

    override fun toString(): String {
        return "MeshUser(id=${id.anonymize}, " +
            "longName=${longName.anonymize}, " +
            "shortName=${shortName.anonymize}, " +
            "hwModel=$hwModelString, " +
            "isLicensed=$isLicensed, " +
            "role=$role)"
    }

    /** Create our model object from a protobuf.
     */
    constructor(p: MeshProtos.User) : this(
        p.id,
        p.longName,
        p.shortName,
        p.hwModel,
        p.isLicensed,
        p.roleValue
    )

    fun toProto(): MeshProtos.User =
        MeshProtos.User.newBuilder()
            .setId(id)
            .setLongName(longName)
            .setShortName(shortName)
            .setHwModel(hwModel)
            .setIsLicensed(isLicensed)
            .setRoleValue(role)
            .build()

    /** a string version of the hardware model, converted into pretty lowercase and changing _ to -, and p to dot
     * or null if unset
     * */
    val hwModelString: String?
        get() =
            if (hwModel == MeshProtos.HardwareModel.UNSET) null
            else hwModel.name.replace('_', '-').replace('p', '.').lowercase()
}

@Parcelize
data class Position(
    val latitude: Double,
    val longitude: Double,
    val altitude: Int,
    val time: Int = currentTime(), // default to current time in secs (NOT MILLISECONDS!)
    val satellitesInView: Int = 0,
    val groundSpeed: Int = 0,
    val groundTrack: Int = 0, // "heading"
    val precisionBits: Int = 0,
) : Parcelable {

    companion object {
        /// Convert to a double representation of degrees
        fun degD(i: Int) = i * 1e-7
        fun degI(d: Double) = (d * 1e7).toInt()

        fun currentTime() = (System.currentTimeMillis() / 1000).toInt()
    }

    /** Create our model object from a protobuf.  If time is unspecified in the protobuf, the provided default time will be used.
     */
    constructor(position: MeshProtos.Position, defaultTime: Int = currentTime()) : this(
        // We prefer the int version of lat/lon but if not available use the depreciated legacy version
        degD(position.latitudeI),
        degD(position.longitudeI),
        position.altitude,
        if (position.time != 0) position.time else defaultTime,
        position.satsInView,
        position.groundSpeed,
        position.groundTrack,
        position.precisionBits
    )

    /// @return distance in meters to some other node (or null if unknown)
    fun distance(o: Position) = latLongToMeter(latitude, longitude, o.latitude, o.longitude)

    /// @return bearing to the other position in degrees
    fun bearing(o: Position) = bearing(latitude, longitude, o.latitude, o.longitude)

    // If GPS gives a crap position don't crash our app
    fun isValid(): Boolean {
        return latitude != 0.0 && longitude != 0.0 &&
                (latitude >= -90 && latitude <= 90.0) &&
                (longitude >= -180 && longitude <= 180)
    }

    fun gpsString(gpsFormat: Int): String = when (gpsFormat) {
        ConfigProtos.Config.DisplayConfig.GpsCoordinateFormat.DEC_VALUE -> GPSFormat.DEC(this)
        ConfigProtos.Config.DisplayConfig.GpsCoordinateFormat.DMS_VALUE -> GPSFormat.DMS(this)
        ConfigProtos.Config.DisplayConfig.GpsCoordinateFormat.UTM_VALUE -> GPSFormat.UTM(this)
        ConfigProtos.Config.DisplayConfig.GpsCoordinateFormat.MGRS_VALUE -> GPSFormat.MGRS(this)
        else -> GPSFormat.DEC(this)
    }

    override fun toString(): String {
        return "Position(lat=${latitude.anonymize}, lon=${longitude.anonymize}, alt=${altitude.anonymize}, time=${time})"
    }
}


@Parcelize
data class DeviceMetrics(
    val time: Int = currentTime(), // default to current time in secs (NOT MILLISECONDS!)
    val batteryLevel: Int = 0,
    val voltage: Float,
    val channelUtilization: Float,
    val airUtilTx: Float,
    val uptimeSeconds: Int,
) : Parcelable {
    companion object {
        fun currentTime() = (System.currentTimeMillis() / 1000).toInt()
    }

    /** Create our model object from a protobuf.
     */
    constructor(p: TelemetryProtos.DeviceMetrics, telemetryTime: Int = currentTime()) : this(
        telemetryTime,
        p.batteryLevel,
        p.voltage,
        p.channelUtilization,
        p.airUtilTx,
        p.uptimeSeconds,
    )
}

@Parcelize
data class EnvironmentMetrics(
    val time: Int = currentTime(), // default to current time in secs (NOT MILLISECONDS!)
    val temperature: Float,
    val relativeHumidity: Float,
    val barometricPressure: Float,
    val gasResistance: Float,
    val voltage: Float,
    val current: Float,
    val iaq: Int,
) : Parcelable {
    companion object {
        fun currentTime() = (System.currentTimeMillis() / 1000).toInt()
    }

    /** Create our model object from a protobuf.
     */
    constructor(t: TelemetryProtos.EnvironmentMetrics, telemetryTime: Int = currentTime()) : this(
        telemetryTime,
        t.temperature,
        t.relativeHumidity,
        t.barometricPressure,
        t.gasResistance,
        t.voltage,
        t.current,
        t.iaq,
    )

    fun getDisplayString(inFahrenheit: Boolean = false): String {
        val temp = if (temperature != 0f) {
            if (inFahrenheit) {
                val fahrenheit = temperature * 1.8F + 32
                String.format("%.1f°F", fahrenheit)
            } else {
                String.format("%.1f°C", temperature)
            }
        } else null
        val humidity = if (relativeHumidity != 0f) String.format("%.0f%%", relativeHumidity) else null
        val pressure = if (barometricPressure != 0f) String.format("%.1fhPa", barometricPressure) else null
        val gas = if (gasResistance != 0f) String.format("%.0fMΩ", gasResistance) else null
        val voltage = if (voltage != 0f) String.format("%.2fV", voltage) else null
        val current = if (current != 0f) String.format("%.1fmA", current) else null
        val iaq = if (iaq != 0) "IAQ: $iaq" else null

        return listOfNotNull(
            temp,
            humidity,
            pressure,
            gas,
            voltage,
            current,
            iaq,
        ).joinToString(" ")
    }

}

@Parcelize
@Entity(tableName = "NodeInfo")
data class NodeInfo(
    @PrimaryKey(autoGenerate = false)
    val num: Int, // This is immutable, and used as a key
    @Embedded(prefix = "user_")
    var user: MeshUser? = null,
    @Embedded(prefix = "position_")
    var position: Position? = null,
    var snr: Float = Float.MAX_VALUE,
    var rssi: Int = Int.MAX_VALUE,
    var lastHeard: Int = 0, // the last time we've seen this node in secs since 1970
    @Embedded(prefix = "devMetrics_")
    var deviceMetrics: DeviceMetrics? = null,
    var channel: Int = 0,
    @Embedded(prefix = "envMetrics_")
    var environmentMetrics: EnvironmentMetrics? = null,
    @ColumnInfo(name = "hopsAway", defaultValue = "0")
    var hopsAway: Int = 0,
    @ColumnInfo(name = "sendPackets", defaultValue = "0")
    var sendPackets: Int = 0
) : Parcelable {

    val colors: Pair<Int, Int>
        get() { // returns foreground and background @ColorInt for each 'num'
            val r = (num and 0xFF0000) shr 16
            val g = (num and 0x00FF00) shr 8
            val b = num and 0x0000FF
            val brightness = ((r * 0.299) + (g * 0.587) + (b * 0.114)) / 255
            return (if (brightness > 0.5) Color.BLACK else Color.WHITE) to Color.rgb(r, g, b)
        }

    val batteryLevel get() = deviceMetrics?.batteryLevel
    val voltage get() = deviceMetrics?.voltage
    val batteryStr get() = if (batteryLevel in 1..100) String.format("%d%%", batteryLevel) else ""

    /**
     * true if the device was heard from recently
     */
    val isOnline: Boolean
        get() {
            val now = System.currentTimeMillis() / 1000
            val timeout = 15 * 60
            return (now - lastHeard <= timeout)
        }

    /// return the position if it is valid, else null
    val validPosition: Position?
        get() {
            return position?.takeIf { it.isValid() }
        }

    /// @return distance in meters to some other node (or null if unknown)
    fun distance(o: NodeInfo?): Int? {
        val p = validPosition
        val op = o?.validPosition
        return if (p != null && op != null) p.distance(op).toInt() else null
    }

    /// @return bearing to the other position in degrees
    fun bearing(o: NodeInfo?): Int? {
        val p = validPosition
        val op = o?.validPosition
        return if (p != null && op != null) p.bearing(op).toInt() else null
    }

    /// @return a nice human readable string for the distance, or null for unknown
    fun distanceStr(o: NodeInfo?, prefUnits: Int = 0) = distance(o)?.let { dist ->
        when {
            dist == 0 -> null // same point
            prefUnits == ConfigProtos.Config.DisplayConfig.DisplayUnits.METRIC_VALUE && dist < 1000 -> "%.0f m".format(dist.toDouble())
            prefUnits == ConfigProtos.Config.DisplayConfig.DisplayUnits.METRIC_VALUE && dist >= 1000 -> "%.1f km".format(dist / 1000.0)
            prefUnits == ConfigProtos.Config.DisplayConfig.DisplayUnits.IMPERIAL_VALUE && dist < 1609 -> "%.0f ft".format(dist.toDouble()*3.281)
            prefUnits == ConfigProtos.Config.DisplayConfig.DisplayUnits.IMPERIAL_VALUE && dist >= 1609 -> "%.1f mi".format(dist / 1609.34)
            else -> null
        }
    }
}
