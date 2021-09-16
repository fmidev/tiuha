package fi.fmi.tiuha

import org.geotools.data.DataStoreFinder

fun main(args: Array<String>) {
    val dataStore = DataStoreFinder.getDataStore(mapOf(
        "keyspaces.keyspaceName" to "measurements",
        "keyspaces.catalog" to "testcatalog",
        "keyspaces.region" to "eu-west-1"
    ))

    dataStore.getSchema("gdelt-quickstart").attributeDescriptors.forEach {
            attr -> println(attr.name)
    }
}
