package de.fapdf.data

import org.json.JSONArray
import org.json.JSONObject

/** JSON-Form der Profildaten (wird ausschließlich verschlüsselt gespeichert). */
fun PersonProfile.toJson(): String = JSONObject().apply {
    put("firstName", firstName); put("lastName", lastName); put("birthDate", birthDate)
    put("birthPlace", birthPlace); put("street", street); put("houseNumber", houseNumber)
    put("zip", zip); put("city", city); put("country", country); put("phone", phone); put("email", email)
    put("custom", JSONArray().apply {
        custom.forEach { put(JSONObject().put("key", it.key).put("value", it.value)) }
    })
}.toString()

fun personProfileFromJson(id: Long, label: String, json: String): PersonProfile {
    val o = JSONObject(json)
    val arr = o.optJSONArray("custom") ?: JSONArray()
    val custom = (0 until arr.length()).map {
        val c = arr.getJSONObject(it)
        CustomValue(c.optString("key"), c.optString("value"))
    }
    return PersonProfile(
        id = id, label = label,
        firstName = o.optString("firstName"), lastName = o.optString("lastName"),
        birthDate = o.optString("birthDate"), birthPlace = o.optString("birthPlace"),
        street = o.optString("street"), houseNumber = o.optString("houseNumber"),
        zip = o.optString("zip"), city = o.optString("city"), country = o.optString("country"),
        phone = o.optString("phone"), email = o.optString("email"), custom = custom,
    )
}
