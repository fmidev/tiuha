package fi.fmi.tiuha

import org.geotools.data.DataStore
import org.geotools.data.Query
import org.geotools.data.Transaction
import org.geotools.filter.text.ecql.ECQL
import org.locationtech.jts.geom.Point
import org.opengis.feature.simple.SimpleFeature
import java.lang.Double.min
import java.lang.Double.max

class Geomesa(private val ds: DataStore) {
    fun query(ecqlPredicate: String): List<SimpleFeature> = Log.time("GeoMesa query") {
        Log.info("Getting reader")
        val source = ds.getFeatureSource(FEATURE_NAME)
        val featureCollection = source.getFeatures(ECQL.toFilter(ecqlPredicate))
        var i = 0
        var maxX = -1000.0
        var maxY = -1000.0
        var minX = 1000.0
        var minY = 1000.0
        Log.info("Starting read")
        val features = mutableListOf<SimpleFeature>()
        val reader = featureCollection.features()
        while (reader.hasNext()) {
            i++
            val feat = reader.next()
            features.add(feat)
            if (i == 1) Log.info("first in")
            val p: Point = feat.defaultGeometry as Point
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
            minX = min(minX, p.x)
            minY = min(minY, p.y)
        }
        Log.info("$i results")
        Log.info("($minX, $minY, $maxX, $maxY)")
        features
    }
}