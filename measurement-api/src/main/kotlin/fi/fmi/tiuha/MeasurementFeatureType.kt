package fi.fmi.tiuha

import org.geotools.data.DataStore
import org.geotools.data.FeatureReader
import org.geotools.data.Query
import org.geotools.data.Transaction
import org.geotools.filter.text.ecql.ECQL
import org.locationtech.geomesa.fs.storage.common.interop.ConfigurationUtils
import org.locationtech.geomesa.utils.interop.SimpleFeatureTypes
import org.locationtech.jts.geom.Point
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType
import java.time.Instant
import java.util.*

private const val FEATURE_TYPE_SPEC = "*geom:Point:srid=4326,dtg:Date,temp:Float,rh:Float,pa:Float,source:String"
const val FEATURE_NAME = "measurement"
val MEASUREMENT_FEATURE_TYPE: SimpleFeatureType = SimpleFeatureTypes.createType(FEATURE_NAME, FEATURE_TYPE_SPEC)

fun setMeasurementFeatureAttributes(feat: SimpleFeature, geom: Point, dtg: Instant, temp: Float?, rh: Float?, pa: Float?) {
    feat.setAttribute("geom", geom)
    feat.setAttribute("dtg", Date.from(dtg))
    feat.setAttribute("temp", temp)
    feat.setAttribute("rh", rh)
    feat.setAttribute("pa", pa)
    feat.setAttribute("source", "Netatmo")
}
