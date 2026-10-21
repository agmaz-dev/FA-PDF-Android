package de.fapdf.pdf

import de.fapdf.data.PersonProfile

enum class ProfileKey(val title: String) {
    FIRST_NAME("Vorname"), LAST_NAME("Nachname"), FULL_NAME("Name"), BIRTH_DATE("Geburtsdatum"),
    BIRTH_PLACE("Geburtsort"), STREET_LINE("Straße und Hausnummer"), STREET("Straße"), HOUSE_NUMBER("Hausnummer"),
    ZIP("PLZ"), CITY("Ort"), ZIP_CITY("PLZ und Ort"), ADDRESS("Anschrift"), COUNTRY("Land"), PHONE("Telefon"), EMAIL("E-Mail"),
}

data class AutofillSuggestion(val fieldId: String, val fieldLabel: String, val source: String, val value: String)

/**
 * Ordnet Formularfeldbeschriftungen (Deutsch, Englisch, Türkisch) den Profildaten zu.
 * Reine Logik ohne Android – durch Unit-Tests abgesichert.
 */
object FieldMatcher {
    fun normalize(s: String): String {
        // Interne Feldnamen wie "form1[0].seite1[0].Vorname[0]" → "vorname"
        val last = s.split('.').lastOrNull { it.isNotBlank() }.orEmpty().replace(Regex("\\[\\d+]"), "")
        return last.lowercase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace("ç", "c").replace("ğ", "g").replace("ı", "i").replace("ş", "s").replace("i̇", "i")
            .replace(Regex("[^a-z0-9]"), "")
    }

    private fun any(n: String, vararg keys: String) = keys.any { it in n }

    fun match(label: String): ProfileKey? {
        val n = normalize(label)
        if (n.isEmpty()) return null
        if (any(n, "geburtsname", "maidenname", "kontoinhaber", "arbeitgeber", "firma", "kasse", "bank", "unterschrift", "signature", "imza")) return null
        return when {
            any(n, "email", "mail", "eposta") -> ProfileKey.EMAIL
            any(n, "telefon", "phone", "mobil", "handy", "telnr", "cepno", "ceptel") || n == "tel" || n.startsWith("tel") && n.length <= 6 -> ProfileKey.PHONE
            any(n, "geburtsdatum", "gebdatum", "gebdat", "geborenam", "birthdate", "dateofbirth", "birthday", "dogumtarihi", "geburtstag") || n == "dob" -> ProfileKey.BIRTH_DATE
            any(n, "geburtsort", "placeofbirth", "birthplace", "dogumyeri") -> ProfileKey.BIRTH_PLACE
            any(n, "plz", "postleitzahl", "zip", "postcode", "postalcode", "postakodu") &&
                (any(n, "ort", "city", "stadt", "town", "sehir")) -> ProfileKey.ZIP_CITY
            any(n, "plz", "postleitzahl", "zip", "postcode", "postalcode", "postakodu") -> ProfileKey.ZIP
            any(n, "strasse", "street", "sokak", "cadde") || n == "str" -> ProfileKey.STREET_LINE
            any(n, "hausnummer", "hausnr", "housenumber", "housenr", "kapino") || n == "nr" || n == "no" -> ProfileKey.HOUSE_NUMBER
            any(n, "anschrift", "adresse", "address", "adres", "wohnanschrift") -> ProfileKey.ADDRESS
            any(n, "wohnort", "stadt", "city", "town", "sehir") || n == "ort" || n.startsWith("ort") -> ProfileKey.CITY
            any(n, "land", "country", "staat", "ulke") && !any(n, "bundesland", "landkreis", "ausland") -> ProfileKey.COUNTRY
            any(n, "vollername", "fullname", "adsoyad", "namevorname", "vornamename", "vorundnachname",
                "vornameundnachname", "nameundvorname", "nachnamevorname", "vornamenachname") || n == "name" || n == "namen" -> ProfileKey.FULL_NAME
            any(n, "vorname", "firstname", "givenname", "forename", "rufname") || n == "ad" || n == "adi" || n == "isim" -> ProfileKey.FIRST_NAME
            any(n, "nachname", "familienname", "zuname", "lastname", "surname", "familyname", "soyad") -> ProfileKey.LAST_NAME
            else -> null
        }
    }

    fun valueFor(p: PersonProfile, key: ProfileKey): String = when (key) {
        ProfileKey.FIRST_NAME -> p.firstName
        ProfileKey.LAST_NAME -> p.lastName
        ProfileKey.FULL_NAME -> p.fullName
        ProfileKey.BIRTH_DATE -> p.birthDate
        ProfileKey.BIRTH_PLACE -> p.birthPlace
        ProfileKey.STREET_LINE -> p.streetLine
        ProfileKey.STREET -> p.street
        ProfileKey.HOUSE_NUMBER -> p.houseNumber
        ProfileKey.ZIP -> p.zip
        ProfileKey.CITY -> p.city
        ProfileKey.ZIP_CITY -> p.zipCity
        ProfileKey.ADDRESS -> p.fullAddress
        ProfileKey.COUNTRY -> p.country
        ProfileKey.PHONE -> p.phone
        ProfileKey.EMAIL -> p.email
    }

    /**
     * Erstellt Vorschläge für alle Felder. Berücksichtigt Zusammenhänge, z. B.:
     * gibt es ein eigenes Feld „Hausnummer“, wird „Straße“ ohne Nummer befüllt;
     * gibt es „Vorname“, ist ein Feld „Name“ der Nachname.
     */
    fun plan(fields: List<Pair<String, String>>, profile: PersonProfile): List<AutofillSuggestion> {
        val keys = fields.map { (id, label) -> Triple(id, label, match(label)) }
        val present = keys.mapNotNull { it.third }.toSet()
        val out = ArrayList<AutofillSuggestion>()
        for ((id, label, key0) in keys) {
            var key = key0
            if (key == null) {
                // Freie Profilwerte (z. B. „Versichertennummer“) über den Namen zuordnen
                val n = normalize(label)
                val custom = profile.custom.firstOrNull { c -> val k = normalize(c.key); k.isNotEmpty() && (k == n || (k.length >= 5 && k in n)) }
                if (custom != null && custom.value.isNotBlank()) out.add(AutofillSuggestion(id, label, custom.key, custom.value))
                continue
            }
            if (key == ProfileKey.STREET_LINE && ProfileKey.HOUSE_NUMBER in present) key = ProfileKey.STREET
            if (key == ProfileKey.FULL_NAME && ProfileKey.FIRST_NAME in present && normalize(label) in setOf("name", "namen")) key = ProfileKey.LAST_NAME
            if (key == ProfileKey.CITY && ProfileKey.ZIP !in present && ProfileKey.ZIP_CITY !in present && normalize(label).contains("plz")) key = ProfileKey.ZIP_CITY
            val v = valueFor(profile, key)
            if (v.isNotBlank()) out.add(AutofillSuggestion(id, label, key.title, v))
        }
        return out
    }

    /** Für Formulare ohne Felder: Erkennt Beschriftungen in Textzeilen (z. B. „Vorname: ____“). */
    data class LabelHit(val lineIndex: Int, val label: String, val key: ProfileKey, val startFraction: Float)

    fun findLabels(lines: List<String>): List<LabelHit> {
        val hits = ArrayList<LabelHit>()
        lines.forEachIndexed { i, text ->
            val colon = text.indexOf(':')
            val labelPart = if (colon > 0) text.substring(0, colon) else text
            val rest = if (colon > 0) text.substring(colon + 1) else ""
            val restEmpty = rest.isBlank() || rest.all { it == '_' || it == '.' || it == ' ' || it == '…' }
            if (labelPart.length > 40 || !restEmpty) return@forEachIndexed
            val key = match(labelPart) ?: return@forEachIndexed
            val frac = if (colon > 0) (colon + 1).toFloat() / text.length else 1f
            hits.add(LabelHit(i, labelPart.trim(), key, frac))
        }
        return hits
    }
}
