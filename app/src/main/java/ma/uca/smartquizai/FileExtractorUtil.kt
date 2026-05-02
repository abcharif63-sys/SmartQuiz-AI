package ma.uca.smartquizai

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.xslf.usermodel.XMLSlideShow
import java.io.InputStream

/**
 * Utilitaire d'extraction de texte depuis des fichiers PDF et PowerPoint (PPTX).
 */
object FileExtractorUtil {

    /**
     * Extrait le texte d'un fichier selon son extension.
     */
    fun extractText(context: Context, uri: Uri, fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf"        -> extractFromPdf(context, uri)
            "ppt", "pptx" -> extractFromPptx(context, uri)
            else          -> "Format non supporté : .$ext\nFormats acceptés : PDF, PPT, PPTX"
        }
    }

    private fun extractFromPdf(context: Context, uri: Uri): String {
        return try {
            PDFBoxResourceLoader.init(context)  // à faire une seule fois dans Application
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return "Impossible d'ouvrir le PDF."
            val doc = PDDocument.load(inputStream)
            val text = PDFTextStripper().getText(doc)
            doc.close()
            if (text.trim().length < 30) "Texte trop court extrait." else text
        } catch (e: Exception) {
            "Erreur PDF : ${e.message}"
        }
    }

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
