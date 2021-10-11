package fi.fmi.tiuha

import kotlinx.serialization.Serializable

object NetatmoUnit {
    val length = "m"
    val wind = "kph"
    val pressure = "mbar"
    val temperature = "C"
    val CO3 = "ppm"
    val humidity = "%"
    val noise = "dB"
}

@Serializable
data class GeoJson(
        val type: String,
        val features: List<GeoJsonFeature>,
)

@Serializable
data class GeoJsonFeature(
        val type: String,
        val geometry: Geometry,
        val properties: FeatureProperties,
)

@Serializable
data class Geometry(
        val type: String,
        val coordinates: List<Double>,
)

@Serializable
data class FeatureProperties(
        val _id: String,
        val featureType: String,
        val resultTime: String,
        val observedPropertyTitle: String,
        val observedProperty: String,
        val unitOfMeasureTitle: String,
        val unitOfMeasure: String,
        val result: Double
)

fun mkTemperatureFeature(
        id: String,
        geometry: Geometry,
        ts: String,
        tempC: Double,
): GeoJsonFeature =
        GeoJsonFeature(
                type = "Feature",
                geometry = geometry,
                properties = FeatureProperties(
                        _id = id,
                        featureType = "MeasureObservation",
                        resultTime = ts,
                        observedPropertyTitle = "Air temperature",
                        observedProperty = "http://vocab.nerc.ac.uk/collection/P07/current/CFSN0023/",
                        unitOfMeasureTitle = NetatmoUnit.temperature,
                        unitOfMeasure = "http://www.opengis.net/def/uom/UCUM/degC",
                        result = tempC,
                )
        )

fun mkHumidityFeature(
        id: String,
        geometry: Geometry,
        ts: String,
        humidity: Double,
): GeoJsonFeature =
        GeoJsonFeature(
                type = "Feature",
                geometry = geometry,
                properties = FeatureProperties(
                        _id = id,
                        featureType = "MeasureObservation",
                        resultTime = ts,
                        observedPropertyTitle = "Relative Humidity",
                        observedProperty = "http://vocab.nerc.ac.uk/collection/P07/current/CFSN0413/",
                        unitOfMeasureTitle = NetatmoUnit.humidity,
                        unitOfMeasure = "",
                        result = humidity,
                )
        )

fun mkPressureFeature(
        id: String,
        geometry: Geometry,
        ts: String,
        pressure: Double,
): GeoJsonFeature =
        GeoJsonFeature(
                type = "Feature",
                geometry = geometry,
                properties = FeatureProperties(
                        _id = id,
                        featureType = "MeasureObservation",
                        resultTime = ts,
                        observedPropertyTitle = "Air Pressure",
                        observedProperty = "http://vocab.nerc.ac.uk/collection/P07/current/CFSN0015/",
                        unitOfMeasureTitle = NetatmoUnit.pressure,
                        unitOfMeasure = "",
                        result = pressure,
                )
        )