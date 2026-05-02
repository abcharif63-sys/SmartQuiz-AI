package ma.uca.smartquizai

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.apache.poi.xslf.usermodel.XMLSlideShow
import java.io.InputStream

/**
 * Utilitaire d'extraction de texte depuis des fichiers PDF et PowerPoint (PPTX).
 *
 * Dépendances requises dans build.gradle (module app) :
 *   implementation 'org.apache.poi:poi-ooxml:5.2.3'
 *
 * Pour les PDFs, Android PdfRenderer ne supporte pas l'extraction de texte natif.
 * On utilise ici une approche simplifiée via InputStream (texte encodé).
 * Pour une extraction complète, ajouter PdfBox-Android :
 *   implementation 'com.tom_roush:pdfbox-android:2.0.27.0'
 */
object FileExtractorUtil {

    /**
     * Extrait le texte d'un fichier selon son extension.
     *
     * @param context  Contexte Android
     * @param uri      URI du fichier sélectionné
     * @param fileName Nom du fichier (pour détecter l'extension)
     * @return Texte extrait, ou message d'erreur
     */
    fun extractText(context: Context, uri: Uri, fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf"        -> extractFromPdf(context, uri)
            "ppt", "pptx" -> extractFromPptx(context, uri)
            else          -> "Format non supporté : .$ext\nFormats acceptés : PDF, PPT, PPTX"
        }
    }

    // ─────────────────────────────────────────────
    // Extraction PDF (lecture brute du flux)
    // ─────────────────────────────────────────────
    private fun extractFromPdf(context: Context, uri: Uri): String {
        return try {
            // Méthode basique : lecture du flux pour récupérer le texte visible
            // Pour une meilleure extraction, utiliser PdfBox-Android
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return "Impossible d'ouvrir le fichier PDF."

            val content = inputStream.bufferedReader(Charsets.ISO_8859_1).use { it.readText() }

            // Extraire les chaînes lisibles entre parenthèses (format PDF interne)
            val sb = StringBuilder()
            val regex = Regex("""\(([^\)]{3,})\)""")
            regex.findAll(content).forEach { match ->
                val text = match.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\r", " ")
                    .replace("\\t", " ")
                    .trim()
                if (text.isNotBlank() && text.length > 3) {
                    sb.append(text).append(" ")
                }
            }

            val result = sb.toString().trim()
            if (result.length < 50) {
                "Texte extrait du PDF (${result.length} car.).\n" +
                "Pour de meilleurs résultats, copie-colle le texte de ton cours directement."
            } else {
                result
            }
        } catch (e: Exception) {
            "Erreur lecture PDF : ${e.message}"
        }
    }

    // ─────────────────────────────────────────────
    // Extraction PPTX via Apache POI
    // ─────────────────────────────────────────────
    private fun extractFromPptx(context: Context, uri: Uri): String {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return "Impossible d'ouvrir le fichier PowerPoint."

            val sb = StringBuilder()
            inputStream.use { stream ->
                val slideShow = XMLSlideShow(stream)
                slideShow.slides.forEachIndexed { index, slide ->
                    sb.append("=== Slide ${index + 1} : ${slide.slideName ?: ""} ===\n")
                    slide.shapes.forEach { shape ->
                        if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) {
                            shape.textParagraphs.forEach { para ->
                                val text = para.text.trim()
                                if (text.isNotBlank()) sb.append(text).append("\n")
                            }
                        }
                    }
                    sb.append("\n")
                }
            }

            val result = sb.toString().trim()
            if (result.isBlank()) "Aucun texte trouvé dans la présentation." else result
        } catch (e: Exception) {
            "Erreur lecture PPTX : ${e.message}"
        }
    }
}
