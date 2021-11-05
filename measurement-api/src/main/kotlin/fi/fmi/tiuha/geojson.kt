package fi.fmi.tiuha

import kotlinx.serialization.Serializable

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
        val result: Double,
        val windAngle: Double? = null,
)
