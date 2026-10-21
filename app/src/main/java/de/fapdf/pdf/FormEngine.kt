package de.fapdf.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDVariableText

enum class FieldKind { TEXT, MULTILINE, DATE, CHECKBOX, RADIO, CHOICE, SIGNATURE }

data class WidgetInfo(val pageIndex: Int, val viewBox: Box, val onValue: String?)

data class FormFieldInfo(
    val id: String,
    val label: String,
    val kind: FieldKind,
    val value: String,
    val checked: Boolean,
    val options: List<String>,
    val optionLabels: List<String>,
    val widgets: List<WidgetInfo>,
    val readOnly: Boolean,
    val required: Boolean,
    val maxLength: Int,
)

data class FormInfo(val fields: List<FormFieldInfo>, val xfaOnly: Boolean) {
    val hasFields get() = fields.isNotEmpty()
    val signatureFields get() = fields.filter { it.kind == FieldKind.SIGNATURE }
}

sealed class FieldValue {
    data class Text(val value: String) : FieldValue()
    data class Bool(val checked: Boolean) : FieldValue()
    data class Choice(val value: String) : FieldValue()
}

/** Erkennung und Befüllung interaktiver PDF-Formulare (AcroForm). */
object FormEngine {
    private val dateHints = listOf("datum", "date", "tarih", "geboren", "birth")

    fun read(doc: PDDocument): FormInfo {
        val form: PDAcroForm = doc.documentCatalog.acroForm ?: return FormInfo(emptyList(), false)
        val xfa = runCatching { form.hasXFA() }.getOrDefault(false)
        val result = ArrayList<FormFieldInfo>()
        for (field in form.fieldTree) {
            if (field !is PDTerminalField || field is PDPushButton) continue
            val widgets = field.widgets.mapNotNull { w -> widgetInfo(doc, w, field) }
            if (widgets.isEmpty()) continue
            val label = (field.alternateFieldName?.takeIf { it.isNotBlank() } ?: field.partialName ?: field.fullyQualifiedName).trim()
            val base = FormFieldInfo(
                id = field.fullyQualifiedName, label = label, kind = FieldKind.TEXT, value = "", checked = false,
                options = emptyList(), optionLabels = emptyList(), widgets = widgets,
                readOnly = field.isReadOnly, required = field.isRequired, maxLength = -1,
            )
            val info = when (field) {
                is PDTextField -> {
                    val n = FieldMatcher.normalize(label + " " + field.partialName)
                    val kind = when {
                        dateHints.any { it in n } -> FieldKind.DATE
                        field.isMultiline -> FieldKind.MULTILINE
                        else -> FieldKind.TEXT
                    }
                    base.copy(kind = kind, value = field.value ?: "", maxLength = field.maxLen)
                }
                is PDCheckBox -> base.copy(kind = FieldKind.CHECKBOX, checked = field.isChecked)
                is PDRadioButton -> {
                    val opts = runCatching { field.onValues.toList() }.getOrDefault(emptyList())
                    base.copy(kind = FieldKind.RADIO, value = field.value ?: "", options = opts, optionLabels = opts)
                }
                is PDChoice -> {
                    val exportValues = runCatching { field.optionsExportValues }.getOrNull()?.takeIf { it.isNotEmpty() } ?: field.options
                    val display = runCatching { field.optionsDisplayValues }.getOrNull()?.takeIf { it.size == exportValues.size } ?: exportValues
                    base.copy(kind = FieldKind.CHOICE, value = field.value.firstOrNull() ?: "", options = exportValues, optionLabels = display)
                }
                is PDSignatureField -> base.copy(kind = FieldKind.SIGNATURE)
                else -> base.copy(value = runCatching { field.valueAsString }.getOrDefault(""))
            }
            result.add(info)
        }
        return FormInfo(result, xfa && result.isEmpty())
    }

    private fun widgetInfo(doc: PDDocument, w: PDAnnotationWidget, field: PDField): WidgetInfo? {
        val rect = w.rectangle ?: return null
        var pageIndex = w.page?.let { doc.pages.indexOf(it) } ?: -1
        if (pageIndex < 0) {
            // Manche Formulare setzen /P nicht – Seite über die Anmerkungsliste suchen
            for ((i, p) in doc.pages.withIndex()) {
                if (p.annotations.any { it.cosObject === w.cosObject }) { pageIndex = i; break }
            }
        }
        if (pageIndex < 0) return null
        val g = PdfIo.geometry(doc.getPage(pageIndex))
        val view = g.userBoxToView(Box(rect.lowerLeftX, rect.lowerLeftY, rect.upperRightX, rect.upperRightY))
        val onValue = if (field is PDRadioButton || field is PDCheckBox) {
            runCatching {
                w.appearance?.normalAppearance?.subDictionary?.keys?.map { it.name }?.firstOrNull { it != "Off" }
            }.getOrNull()
        } else null
        return WidgetInfo(pageIndex, view, onValue)
    }

    /**
     * Setzt die Werte. Fehlt der Formularschrift ein Zeichen (z. B. ğ, ş in Standardschriften),
     * wird automatisch eine vollständige Unicode-Schrift hinterlegt und erneut versucht.
     * @return Liste mit Fehlermeldungen (leer = alles übernommen).
     */
    fun apply(doc: PDDocument, values: Map<String, FieldValue>, fonts: FontLibrary): List<String> {
        val form = doc.documentCatalog.acroForm ?: return listOf("Dieses Dokument enthält keine Formularfelder.")
        val errors = ArrayList<String>()
        var unicodeFontName: COSName? = null
        for ((id, v) in values) {
            val field = form.getField(id)
            if (field == null) { errors.add("Feld „$id“ nicht gefunden"); continue }
            if (field.isReadOnly) continue
            try {
                when (v) {
                    is FieldValue.Text -> {
                        try {
                            field.setValue(v.value)
                        } catch (e: Exception) {
                            if (field !is PDVariableText) throw e
                            val name = unicodeFontName ?: installUnicodeFont(doc, form, fonts).also { unicodeFontName = it }
                            field.defaultAppearance = "/${name.name} 0 Tf 0 g"
                            field.widgets.forEach { it.cosObject.removeItem(COSName.DA) }
                            field.setValue(v.value)
                        }
                    }
                    is FieldValue.Bool -> when (field) {
                        is PDCheckBox -> if (v.checked) field.check() else field.unCheck()
                        else -> field.setValue(if (v.checked) "Yes" else "Off")
                    }
                    is FieldValue.Choice -> field.setValue(v.value)
                }
            } catch (e: Exception) {
                errors.add("„${field.alternateFieldName ?: field.partialName}“: ${e.message ?: "Wert nicht zulässig"}")
            }
        }
        return errors
    }

    private fun installUnicodeFont(doc: PDDocument, form: PDAcroForm, fonts: FontLibrary): COSName {
        // Formularschriften dürfen nicht als Teilmenge eingebettet werden, da Werte später geändert werden können
        val font = fonts.load(doc, FontFamily.SANS, bold = false, italic = false, subset = false)
        val dr = form.defaultResources ?: PDResources().also { form.defaultResources = it }
        val name = COSName.getPDFName("FaPdfSans")
        dr.put(name, font)
        return name
    }
}
