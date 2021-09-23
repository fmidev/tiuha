package fi.fmi.tiuha

import org.geotools.data.DataStore
import org.geotools.data.FeatureReader
import org.geotools.data.Query
import org.geotools.data.Transaction
import org.geotools.filter.text.ecql.ECQL
import org.locationtech.geomesa.utils.interop.SimpleFeatureTypes
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType

private const val FEATURE_TYPE_SPEC = "*geom:Point:srid=4326,dtg:Date,temp:Float,rh:Float,pa:Float,source:String"
const val FEATURE_NAME = "measurement"

fun createMeasurementFeatureType(): SimpleFeatureType = SimpleFeatureTypes.createType(FEATURE_NAME, FEATURE_TYPE_SPEC)

val jyvaskylaQuery = Query(
    FEATURE_NAME, ECQL.toFilter("bbox(geom,23.38,61.39,27.99,62.99)")
)

fun getMeasurementReader(store: DataStore): FeatureReader<SimpleFeatureType, SimpleFeature> {
    return store.getFeatureReader(jyvaskylaQuery, Transaction.AUTO_COMMIT)
}
