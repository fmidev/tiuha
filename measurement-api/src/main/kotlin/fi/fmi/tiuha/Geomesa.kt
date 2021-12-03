package fi.fmi.tiuha

import fi.fmi.tiuha.netatmo.netatmoPropertyNameTitleMap
import fi.fmi.tiuha.netatmo.netatmoPropertyNameUnitMap
import org.geotools.data.DataStore
import org.geotools.data.Query
import org.geotools.data.Transaction
import org.geotools.filter.text.ecql.ECQL
import org.locationtech.jts.geom.Point
import org.opengis.feature.simple.SimpleFeature
import java.util.*

class Geomesa(private val ds: DataStore) {
    fun query(ecqlPredicate: String): GeoJson<MeasurementProperties> = Log.time("GeoMesa query") {
        Log.info("Executing query: $ecqlPredicate")
        val query = Query(FEATURE_NAME, ECQL.toFilter(ecqlPredicate))
        val reader = ds.getFeatureReader(query, Transaction.AUTO_COMMIT)
        val features = mutableListOf<SimpleFeature>()
        while (reader.hasNext()) {
            val feat = reader.next()
            features.add(feat)
            val p: Point = feat.defaultGeometry as Point
        }
        Log.info("${features.size} results")
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
        val altitude = f.getAttributeAsType<Double?>("altitude")
        val (sourceId, propertyName) = f.getAttributeAsType<String>("property_id").split("/")
        val value = f.getAttributeAsType<Double>("value")

        return GeoJsonFeature(
                type = "Feature",
                geometry = Geometry(
                        type = "Point",
                        coordinates = if (altitude == null) {
                            listOf(geom.coordinate.x, geom.coordinate.y)
                        } else {
                            listOf(geom.coordinate.x, geom.coordinate.y, altitude)
                        },
                ),
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