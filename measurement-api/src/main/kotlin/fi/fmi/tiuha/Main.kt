package fi.fmi.tiuha

import fi.fmi.tiuha.netatmo.importMeasurementsFromS3Bucket
import org.geotools.data.Query
import org.geotools.data.Transaction
import org.geotools.filter.text.ecql.ECQL
import org.locationtech.jts.geom.Point
import java.lang.Double.max
import java.lang.Double.min

fun main(args: Array<String>) {
    if (args.contains("--import")) {
        val keysToImport = args.dropWhile { it != "--import" }.drop(1)
        importMeasurementsFromS3Bucket(keysToImport)
    } else {
        val ds = S3DataStore()

        println("Getting reader")
        val start = System.currentTimeMillis()
        val query = Query(
            FEATURE_NAME, ECQL.toFilter("dtg BETWEEN 2021-07-17T07:00:00Z AND 2021-07-17T08:00:00Z")
        )
        val reader = ds.dataStore.getFeatureReader(query, Transaction.AUTO_COMMIT)
        var i = 0
        var maxX = -1000.0
        var maxY = -1000.0
        var minX = 1000.0
        var minY = 1000.0
        println("Starting read")
        while (reader.hasNext()) {
            i++
            val feat = reader.next()
            if (i == 1) println("first in")
            val p: Point = feat.defaultGeometry as Point
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
            minX = min(minX, p.x)
            minY = min(minY, p.y)
        }
        println("$i results")
        val s = (System.currentTimeMillis() - start) / 1000.0
        println("Took $s s")
        println("($minX, $minY, $maxX, $maxY)")

    }
}
