package fi.fmi.tiuha

private const val propertyPrefix = "http://tiuha.fmi.fi/property"

private val identifierRegex = Regex("^[A-Za-z0-9_]+$")

fun generatePropertyURI(source: String, propertyName: String): String {
    assert(source.matches(identifierRegex))
    assert(propertyName.matches(identifierRegex))
    return "$propertyPrefix/$source/$propertyName"
}
