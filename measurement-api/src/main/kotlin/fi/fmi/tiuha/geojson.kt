package fi.fmi.tiuha

import kotlinx.serialization.Serializable

@Serializable
data class GeoJson<FeatureProperties>(
        val type: String,
        val features: List<GeoJsonFeature<FeatureProperties>>,
)

@Serializable
data class GeoJsonFeature<FeatureProperties>(
        val type: String,
        val geometry: Geometry,
        val properties: FeatureProperties,
)

typealias GeoJsonQCFeature = GeoJsonFeature<QCMeasurementProperties>

@Serializable
data class Geometry(
        val type: String,
        val coordinates: List<Double>,
)

@Serializable
data class MeasurementProperties(
        val sourceId: String,
        val _id: String,
        val featureType: String,
        val resultTime: String,
        val observedPropertyTitle: String,
        val observedProperty: String,
        val unitOfMeasureTitle: String,
        val unitOfMeasure: String,
        val result: Double,
)

@Serializable
class QCMeasurementProperties (
        val sourceId: String,
        val _id: String,
        val featureType: String,
        val resultTime: String,
        val observedPropertyTitle: String,
        val observedProperty: String,
        val unitOfMeasureTitle: String,
        val unitOfMeasure: String,
        val result: Double,
        val qcPassed: Boolean,
        val qcDetails: QCDetails,
) {
    companion object {
        fun from(input: MeasurementProperties, qcPassed: Boolean, qcDetails: QCDetails) = QCMeasurementProperties(
            sourceId = input.sourceId,
            _id = input.sourceId,
            featureType = input.featureType,
            resultTime = input.resultTime,
            observedPropertyTitle = input.observedPropertyTitle,
            observedProperty = input.observedProperty,
            unitOfMeasureTitle = input.unitOfMeasureTitle,
            unitOfMeasure = input.unitOfMeasure,
            result = input.result,
            qcPassed = qcPassed,
            qcDetails = qcDetails,
        )
    }
}

@Serializable
data class QCDetails(
        val method: String,
        val version: String,
        val flags: List<QCCheckResult>,
)

@Serializable
data class QCCheckResult(
        val check: String,
        val passed: Boolean,
        val result: Int,
)