package de.fapdf

import de.fapdf.data.CustomValue
import de.fapdf.data.PersonProfile
import de.fapdf.pdf.FieldMatcher
import de.fapdf.pdf.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldMatcherTest {
    @Test fun germanLabels() {
        assertEquals(ProfileKey.FIRST_NAME, FieldMatcher.match("Vorname"))
        assertEquals(ProfileKey.LAST_NAME, FieldMatcher.match("Nachname"))
        assertEquals(ProfileKey.LAST_NAME, FieldMatcher.match("Familienname"))
        assertEquals(ProfileKey.FULL_NAME, FieldMatcher.match("Name, Vorname"))
        assertEquals(ProfileKey.FULL_NAME, FieldMatcher.match("Vor- und Nachname"))
        assertEquals(ProfileKey.BIRTH_DATE, FieldMatcher.match("Geburtsdatum"))
        assertEquals(ProfileKey.BIRTH_PLACE, FieldMatcher.match("Geburtsort"))
        assertEquals(ProfileKey.STREET_LINE, FieldMatcher.match("Straße, Hausnummer"))
        assertEquals(ProfileKey.STREET_LINE, FieldMatcher.match("Straße"))
        assertEquals(ProfileKey.HOUSE_NUMBER, FieldMatcher.match("Hausnr."))
        assertEquals(ProfileKey.ZIP_CITY, FieldMatcher.match("PLZ, Ort"))
        assertEquals(ProfileKey.ZIP, FieldMatcher.match("Postleitzahl"))
        assertEquals(ProfileKey.CITY, FieldMatcher.match("Wohnort"))
        assertEquals(ProfileKey.PHONE, FieldMatcher.match("Telefon (tagsüber)"))
        assertEquals(ProfileKey.EMAIL, FieldMatcher.match("E-Mail-Adresse"))
        assertEquals(ProfileKey.ADDRESS, FieldMatcher.match("Anschrift"))
    }

    @Test fun englishAndTurkishLabels() {
        assertEquals(ProfileKey.FIRST_NAME, FieldMatcher.match("First name"))
        assertEquals(ProfileKey.LAST_NAME, FieldMatcher.match("Surname"))
        assertEquals(ProfileKey.BIRTH_DATE, FieldMatcher.match("Date of birth"))
        assertEquals(ProfileKey.FIRST_NAME, FieldMatcher.match("Adı"))
        assertEquals(ProfileKey.LAST_NAME, FieldMatcher.match("Soyadı"))
        assertEquals(ProfileKey.FULL_NAME, FieldMatcher.match("Ad Soyad"))
        assertEquals(ProfileKey.BIRTH_DATE, FieldMatcher.match("Doğum Tarihi"))
        assertEquals(ProfileKey.EMAIL, FieldMatcher.match("E-posta"))
    }

    @Test fun technicalFieldNamesAndExclusions() {
        assertEquals(ProfileKey.FIRST_NAME, FieldMatcher.match("form1[0].seite1[0].Vorname[0]"))
        assertNull(FieldMatcher.match("Geburtsname"))
        assertNull(FieldMatcher.match("Name der Krankenkasse"))
        assertNull(FieldMatcher.match("Unterschrift"))
        assertNull(FieldMatcher.match("Bundesland"))
    }

    private val profile = PersonProfile(
        label = "Ich", firstName = "Emine", lastName = "Muster", birthDate = "01.02.1980",
        street = "Hauptstraße", houseNumber = "12", zip = "12529", city = "Schönefeld",
        phone = "030 123", email = "a@b.de", custom = listOf(CustomValue("Versichertennummer", "A123456789")),
    )

    @Test fun planUsesContext() {
        val plan = FieldMatcher.plan(
            listOf("f1" to "Name", "f2" to "Vorname", "f3" to "Straße", "f4" to "Hausnummer", "f5" to "PLZ", "f6" to "Ort", "f7" to "Versichertennummer"),
            profile,
        ).associate { it.fieldId to it.value }
        assertEquals("Muster", plan["f1"])           // „Name“ neben „Vorname“ = Nachname
        assertEquals("Emine", plan["f2"])
        assertEquals("Hauptstraße", plan["f3"])      // ohne Nummer, da eigenes Feld vorhanden
        assertEquals("12", plan["f4"])
        assertEquals("12529", plan["f5"])
        assertEquals("Schönefeld", plan["f6"])
        assertEquals("A123456789", plan["f7"])
    }

    @Test fun planFullNameAndStreetLine() {
        val plan = FieldMatcher.plan(listOf("a" to "Name", "b" to "Straße"), profile).associate { it.fieldId to it.value }
        assertEquals("Emine Muster", plan["a"])
        assertEquals("Hauptstraße 12", plan["b"])
    }

    @Test fun findsLabelsInFlatForms() {
        val hits = FieldMatcher.findLabels(listOf("Antrag auf Leistungen", "Vorname: ____________", "Nachname:", "Geburtsdatum: 01.01.2000"))
        assertEquals(listOf(ProfileKey.FIRST_NAME, ProfileKey.LAST_NAME), hits.map { it.key })
    }
}
