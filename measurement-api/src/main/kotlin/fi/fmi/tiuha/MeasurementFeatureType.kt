package fi.fmi.tiuha

import org.locationtech.geomesa.utils.interop.SimpleFeatureTypes
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType
import java.time.ZonedDateTime
import java.util.*

private const val FEATURE_TYPE_SPEC = "*geom:Point:srid=4326,dtg:Date,property_id:String,qc_passed:Boolean,value:Double,import_id:Long"
const val FEATURE_NAME = "measurement"
val MEASUREMENT_FEATURE_TYPE: SimpleFeatureType = SimpleFeatureTypes.createType(FEATURE_NAME, FEATURE_TYPE_SPEC)

fun setMeasurementFeatureAttributes(feat: SimpleFeature, geometryFactory: GeometryFactory, geoJsonFeature: GeoJsonQCFeature) {
    val (x, y, z) = geoJsonFeature.geometry.coordinates
    val geom = geometryFactory.createPoint(Coordinate(x, y, z))
    val dtg = ZonedDateTime.parse(geoJsonFeature.properties.resultTime).toInstant()
    val propertyId = getPropertyId(geoJsonFeature.properties.observedProperty)
    feat.setAttribute("geom", geom)
    feat.setAttribute("dtg", Date.from(dtg))
    feat.setAttribute("property_id", propertyId)
    feat.setAttribute("qc_passed", geoJsonFeature.properties.qcPassed)
    feat.setAttribute("value", geoJsonFeature.properties.result)
    feat.setAttribute("import_id", 0)
}
