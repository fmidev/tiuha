package fi.fmi.tiuha

import fi.fmi.tiuha.netatmo.netatmoPropertyNameTitleMap
import fi.fmi.tiuha.netatmo.netatmoPropertyNameUnitMap
import org.geotools.data.DataStore
import org.geotools.data.Query
import org.geotools.data.Transaction
import org.geotools.filter.text.ecql.ECQL
import org.locationtech.jts.geom.Point
import org.opengis.feature.simple.SimpleFeature
import java.lang.Double.max
import java.lang.Double.min
import java.util.*

class Geomesa(private val ds: DataStore) {
    fun query(ecqlPredicate: String): GeoJson<MeasurementProperties> = Log.time("GeoMesa query") {
        Log.info("Executing query: $ecqlPredicate")
        val query = Query(FEATURE_NAME, ECQL.toFilter(ecqlPredicate))
        val reader = ds.getFeatureReader(query, Transaction.AUTO_COMMIT)
        var i = 0
        var maxX = -1000.0
        var maxY = -1000.0
        var minX = 1000.0
        var minY = 1000.0
        Log.info("Starting read")
        val features = mutableListOf<SimpleFeature>()
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
        toGeoJsonFeatureCollection(features)
    }

    fun toGeoJsonFeatureCollection(features: List<SimpleFeature>): GeoJson<MeasurementProperties> {
        return GeoJson(
                type = "FeatureCollection",
                features = features.map(::featureToGeoJson),
        )
    }

    fun featureToGeoJson(f: SimpleFeature): GeoJsonFeature<MeasurementProperties> {
        val geom = f.getAttributeAsType<Point>("geom")
        val dtg = f.getAttributeAsType<Date>("dtg")
        val (sourceId, propertyName) = f.getAttributeAsType<String>("property_id").split("/")
        val value = f.getAttributeAsType<Double>("value")

        return GeoJsonFeature(
                type = "Feature",
                geometry = Geometry(type = "Point", coordinates = listOf(geom.coordinate.x, geom.coordinate.y)),
                properties = MeasurementProperties(
                        sourceId = sourceId,
                        _id = "",
                        featureType = "MeasureObservation",
                        resultTime = timeFormatter.format(dtg.toInstant()),
                        observedPropertyTitle = netatmoPropertyNameTitleMap.get(propertyName)!!,
                        observedProperty = generatePropertyURI(sourceId, propertyName),
                        unitOfMeasureTitle = netatmoPropertyNameUnitMap.get(propertyName)!!,
                        unitOfMeasure = "",
                        result = value
                )
        )
    }

    inline fun <reified T> SimpleFeature.getAttributeAsType(name: String): T {
        val value = getAttribute(name)
        if (value !is T) {
            throw RuntimeException("Expected '$name' attribute to be ${T::class.simpleName}")
        }
        return value
    }
}