package ma.uca.smartquizai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var uploadZone: View
    private lateinit var fileCard: View
    private lateinit var fileIconView: TextView
    private lateinit var fileNameView: TextView
    private lateinit var fileTypeView: TextView
    private lateinit var btnRemoveFile: TextView
    private lateinit var btnImport: Button
    private lateinit var btnGenerate: Button
    private lateinit var btnMinus: Button
    private lateinit var btnPlus: Button
    private lateinit var countDisplay: TextView
    private lateinit var statDocs: TextView
    private lateinit var statQuizzes: TextView
    private lateinit var statScore: TextView

    private lateinit var chipQCM: TextView
    private lateinit var chipVF: TextView
    private lateinit var chipOpen: TextView

    private lateinit var diffFacile: TextView
    private lateinit var diffMoyen: TextView
    private lateinit var diffDifficile: TextView

    private var selectedUri: Uri? = null
    private var selectedFileName: String = ""
    private var questionCount: Int = 5
    private var difficulty: String = "Facile"
    private val activeTypes = mutableSetOf("qcm", "vf")

    private var totalDocs = 0
    private var totalQuizzes = 0
    private val scores = mutableListOf<Int>()

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handleFileSelected(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupListeners()
        updateStats()
    }

    private fun bindViews() {
        uploadZone    = findViewById(R.id.uploadZone)
        fileCard      = findViewById(R.id.fileCard)
        fileIconView  = findViewById(R.id.fileIcon)
        fileNameView  = findViewById(R.id.fileName)
        fileTypeView  = findViewById(R.id.fileType)
        btnRemoveFile = findViewById(R.id.btnRemoveFile)
        btnImport     = findViewById(R.id.btnImport)
        btnGenerate   = findViewById(R.id.btnGenerate)
        btnMinus      = findViewById(R.id.btnMinus)
        btnPlus       = findViewById(R.id.btnPlus)
        countDisplay  = findViewById(R.id.countDisplay)
        statDocs      = findViewById(R.id.statDocs)
        statQuizzes   = findViewById(R.id.statQuizzes)
        statScore     = findViewById(R.id.statScore)
        chipQCM      = findViewById(R.id.chipQCM)
        chipVF       = findViewById(R.id.chipVF)
        chipOpen     = findViewById(R.id.chipOpen)
        diffFacile   = findViewById(R.id.diffFacile)
        diffMoyen    = findViewById(R.id.diffMoyen)
        diffDifficile = findViewById(R.id.diffDifficile)
    }

    private fun setupListeners() {
        uploadZone.setOnClickListener  { pickFile.launch("*/*") }
        btnImport.setOnClickListener   { pickFile.launch("*/*") }
        btnRemoveFile.setOnClickListener { removeFile() }
        btnMinus.setOnClickListener {
            if (questionCount > 3) { questionCount--; countDisplay.text = questionCount.toString() }
        }
        btnPlus.setOnClickListener {
            if (questionCount < 15) { questionCount++; countDisplay.text = questionCount.toString() }
        }
        chipQCM.setOnClickListener  { toggleType("qcm",  chipQCM) }
        chipVF.setOnClickListener   { toggleType("vf",   chipVF) }
        chipOpen.setOnClickListener { toggleType("open", chipOpen) }
        diffFacile.setOnClickListener   { selectDifficulty("Facile",    diffFacile) }
        diffMoyen.setOnClickListener    { selectDifficulty("Moyen",     diffMoyen) }
        diffDifficile.setOnClickListener { selectDifficulty("Difficile", diffDifficile) }
        btnGenerate.setOnClickListener { generateQuiz() }
    }

    private fun handleFileSelected(uri: Uri) {
        val name = getFileName(uri)
        val ext  = name.substringAfterLast('.', "").uppercase()
        if (ext !in listOf("PDF", "PPT", "PPTX")) {
            Toast.makeText(this, "Format non supporté : $ext", Toast.LENGTH_SHORT).show()
            return
        }
        selectedUri = uri
        selectedFileName = name
        fileNameView.text = name
        fileTypeView.text = ext
        fileIconView.text = if (ext == "PDF") "📕" else "📊"
        fileCard.visibility = View.VISIBLE
        uploadZone.visibility = View.GONE
        totalDocs++
        updateStats()
    }

    private fun removeFile() {
        selectedUri = null
        selectedFileName = ""
        fileCard.visibility = View.GONE
        uploadZone.visibility = View.VISIBLE
    }

    private fun getFileName(uri: Uri): String {
        var name = "fichier"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) name = cursor.getString(index)
        }
        return name
    }

    private fun toggleType(type: String, chip: TextView) {
        if (activeTypes.contains(type)) {
            if (activeTypes.size > 1) {
                activeTypes.remove(type)
                setChipState(chip, false)
            }
        } else {
            activeTypes.add(type)
            setChipState(chip, true)
        }
    }

    private fun selectDifficulty(diff: String, chip: TextView) {
        difficulty = diff
        setChipState(diffFacile,   diff == "Facile")
        setChipState(diffMoyen,    diff == "Moyen")
        setChipState(diffDifficile, diff == "Difficile")
    }

    private fun setChipState(chip: TextView, active: Boolean) {
        chip.setBackgroundResource(if (active) R.drawable.chip_active_bg else R.drawable.chip_inactive_bg)
        chip.setTextColor(getColor(if (active) R.color.accent_purple_light else R.color.text_hint))
    }

    private fun generateQuiz() {
        val uri = selectedUri ?: run {
            Toast.makeText(this, getString(R.string.error_no_file), Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = AlertDialog.Builder(this)
            .setMessage("🤖 Analyse en cours...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val courseText = FileExtractorUtil.extractText(this@MainActivity, uri, selectedFileName)
                
                // On vérifie si l'extraction a fonctionné
                if (courseText.startsWith("Erreur")) {
                    throw Exception(courseText)
                }

                val questions = ClaudeApiService.generateQuestions(
                    courseText  = courseText,
                    count       = questionCount,
                    types       = activeTypes.toList(),
                    difficulty  = difficulty
                )

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    if (questions.isEmpty()) {
                        Toast.makeText(this@MainActivity, "L'IA n'a retourné aucune question. Vérifie ta clé API dans le code.", Toast.LENGTH_LONG).show()
                    } else {
                        launchQuiz(questions)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Log.e("QuizAI", "Erreur: ${e.message}")
                    Toast.makeText(this@MainActivity, "Erreur : ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun launchQuiz(questions: List<Question>) {
        val intent = Intent(this, QuizActivity::class.java).apply {
            putParcelableArrayListExtra(EXTRA_QUESTIONS, ArrayList(questions))
        }
        startActivityForResult(intent, REQUEST_QUIZ)
    }

    private fun updateStats() {
        statDocs.text    = totalDocs.toString()
        statQuizzes.text = totalQuizzes.toString()
        statScore.text   = if (scores.isEmpty()) "—" else "${scores.average().toInt()}%"
    }

    companion object {
        const val REQUEST_QUIZ     = 1001
        const val EXTRA_QUESTIONS  = "extra_questions"
        const val EXTRA_SCORE      = "extra_score"
        const val EXTRA_TOTAL      = "extra_total"
    }
}
