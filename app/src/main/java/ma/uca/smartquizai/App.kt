package ma.uca.smartquizai

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialisation de PDFBox pour Android
        PDFBoxResourceLoader.init(applicationContext)
    }
}
