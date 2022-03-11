package fi.fmi.tiuha

import org.locationtech.geomesa.utils.interop.SimpleFeatureTypes
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType
import java.time.ZonedDateTime
import java.util.*

private const val FEATURE_TYPE_SPEC = "*geom:Point:srid=4326,dtg:Date,property_id:String,value:Double,import_id:Long,altitude:Double"
const val FEATURE_NAME = "measurement_v2"
val MEASUREMENT_FEATURE_TYPE: SimpleFeatureType = SimpleFeatureTypes.createType(FEATURE_NAME, FEATURE_TYPE_SPEC)

fun setMeasurementFeatureAttributes(feat: SimpleFeature, geometryFactory: GeometryFactory, geoJsonFeature: GeoJsonQCFeature, importId: Long) {
    val coordinates = geoJsonFeature.geometry.coordinates
    val (x, y: Double) = coordinates
    val geom = geometryFactory.createPoint(Coordinate(x, y))
    val z = coordinates.getOrNull(2)

    val dtg = ZonedDateTime.parse(geoJsonFeature.properties.resultTime).toInstant()
    val propertyId = getPropertyId(geoJsonFeature.properties.observedProperty)
    feat.setAttribute("geom", geom)
    feat.setAttribute("altitude", z)
    feat.setAttribute("dtg", Date.from(dtg))
    feat.setAttribute("property_id", propertyId)
    feat.setAttribute("value", geoJsonFeature.properties.result)
    feat.setAttribute("import_id", importId)
}
