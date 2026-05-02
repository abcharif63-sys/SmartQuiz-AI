package ma.uca.smartquizai

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.parcelize.Parcelize

/**
 * Activité principale du quiz.
 * Reçoit la liste des questions via Intent et gère le déroulement complet :
 * affichage, validation, feedback, score et navigation vers les résultats.
 */
class QuizActivity : AppCompatActivity() {

    // ── UI ──────────────────────────────────────────
    private lateinit var questionCounter: TextView
    private lateinit var scoreDisplay: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var questionTypeBadge: TextView
    private lateinit var questionText: TextView
    private lateinit var choicesContainer: LinearLayout
    private lateinit var openAnswerInput: EditText
    private lateinit var feedbackBox: View
    private lateinit var feedbackIcon: TextView
    private lateinit var feedbackText: TextView
    private lateinit var btnValidate: Button
    private lateinit var btnNext: Button

    // ── État ─────────────────────────────────────────
    private lateinit var questions: List<Question>
    private var currentIndex  = 0
    private var correctCount  = 0
    private var selectedAnswer = -1
    private var answered       = false
    private val wrongQuestions = mutableListOf<Question>()

    private val letters = listOf("A", "B", "C", "D")

    // ─────────────────────────────────────────────────
    // onCreate
    // ─────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        @Suppress("DEPRECATION")
        questions = intent.getParcelableArrayListExtra<Question>(MainActivity.EXTRA_QUESTIONS)
            ?: emptyList()

        if (questions.isEmpty()) { finish(); return }

        bindViews()
        setupListeners()
        loadQuestion()
    }

    // ─────────────────────────────────────────────────
    // Liaison des vues
    // ─────────────────────────────────────────────────
    private fun bindViews() {
        questionCounter   = findViewById(R.id.questionCounter)
        scoreDisplay      = findViewById(R.id.scoreDisplay)
        progressBar       = findViewById(R.id.progressBar)
        questionTypeBadge = findViewById(R.id.questionTypeBadge)
        questionText      = findViewById(R.id.questionText)
        choicesContainer  = findViewById(R.id.choicesContainer)
        openAnswerInput   = findViewById(R.id.openAnswerInput)
        feedbackBox       = findViewById(R.id.feedbackBox)
        feedbackIcon      = findViewById(R.id.feedbackIcon)
        feedbackText      = findViewById(R.id.feedbackText)
        btnValidate       = findViewById(R.id.btnValidate)
        btnNext           = findViewById(R.id.btnNext)
    }

    // ─────────────────────────────────────────────────
    // Listeners
    // ─────────────────────────────────────────────────
    private fun setupListeners() {
        btnValidate.setOnClickListener { validateAnswer() }
        btnNext.setOnClickListener     { nextQuestion() }
        openAnswerInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!answered) btnValidate.isEnabled = (s?.length ?: 0) >= 5
            }
        })
    }

    // ─────────────────────────────────────────────────
    // Chargement d'une question
    // ─────────────────────────────────────────────────
    private fun loadQuestion() {
        val q     = questions[currentIndex]
        val total = questions.size
        selectedAnswer = -1
        answered       = false

        // Header
        questionCounter.text = "Question ${currentIndex + 1}/$total"
        scoreDisplay.text    = "Score: $correctCount"
        progressBar.progress = ((currentIndex.toFloat() / total) * 100).toInt()

        // Badge type
        when (q.type) {
            "qcm"  -> {
                questionTypeBadge.text = "● QCM"
                questionTypeBadge.setTextColor(Color.parseColor("#A78BFA"))
                questionTypeBadge.setBackgroundResource(R.drawable.badge_qcm_bg)
            }
            "vf"   -> {
                questionTypeBadge.text = "◆ Vrai / Faux"
                questionTypeBadge.setTextColor(Color.parseColor("#38BDF8"))
                questionTypeBadge.setBackgroundResource(R.drawable.badge_vf_bg)
            }
            "open" -> {
                questionTypeBadge.text = "✎ Question ouverte"
                questionTypeBadge.setTextColor(Color.parseColor("#FBBF24"))
                questionTypeBadge.setBackgroundResource(R.drawable.badge_open_bg)
            }
        }

        // Texte
        questionText.text = q.question

        // Reset feedback
        feedbackBox.visibility = View.GONE
        btnValidate.isEnabled = false
        btnValidate.text = "Valider"
        btnNext.visibility = View.GONE

        if (q.type == "open") {
            choicesContainer.visibility = View.GONE
            openAnswerInput.visibility  = View.VISIBLE
            openAnswerInput.setText("")
        } else {
            openAnswerInput.visibility  = View.GONE
            choicesContainer.visibility = View.VISIBLE
            buildChoices(q)
        }
    }

    // ─────────────────────────────────────────────────
    // Construction des choix QCM / Vrai-Faux
    // ─────────────────────────────────────────────────
    private fun buildChoices(q: Question) {
        choicesContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        q.choices.forEachIndexed { idx, choice ->
            val itemView = inflater.inflate(R.layout.item_choice, choicesContainer, false)
            itemView.findViewById<TextView>(R.id.choiceLetter).text =
                if (q.type == "vf") (if (idx == 0) "V" else "F") else letters.getOrElse(idx) { idx.toString() }
            itemView.findViewById<TextView>(R.id.choiceText).text = choice

            itemView.setOnClickListener {
                if (!answered) selectChoice(idx, q.choices.size)
            }
            choicesContainer.addView(itemView)
        }
    }

    private fun selectChoice(idx: Int, total: Int) {
        selectedAnswer = idx
        btnValidate.isEnabled = true

        for (i in 0 until total) {
            val item = choicesContainer.getChildAt(i)
            item?.setBackgroundResource(
                if (i == idx) R.drawable.choice_selected_bg else R.drawable.choice_default_bg
            )
            item?.findViewById<TextView>(R.id.choiceText)
                ?.setTextColor(if (i == idx) Color.parseColor("#A78BFA") else Color.parseColor("#A09AC5"))
        }
    }

    // ─────────────────────────────────────────────────
    // Validation de la réponse
    // ─────────────────────────────────────────────────
    private fun validateAnswer() {
        if (answered) return
        answered = true
        btnValidate.isEnabled = false

        val q          = questions[currentIndex]
        val isLastQ    = currentIndex == questions.size - 1
        val isCorrect: Boolean

        if (q.type == "open") {
            isCorrect = true          // Les questions ouvertes sont toujours comptées correctes
            correctCount++
            showFeedback(true, q.explanation)
        } else {
            isCorrect = selectedAnswer == q.correct
            if (isCorrect) correctCount++ else wrongQuestions.add(q)

            // Colorer les choix
            for (i in 0 until q.choices.size) {
                val item = choicesContainer.getChildAt(i)
                when {
                    i == q.correct         -> {
                        item?.setBackgroundResource(R.drawable.choice_correct_bg)
                        item?.findViewById<TextView>(R.id.choiceText)
                            ?.setTextColor(Color.parseColor("#34D399"))
                        item?.findViewById<TextView>(R.id.choiceLetter)
                            ?.setTextColor(Color.parseColor("#34D399"))
                    }
                    i == selectedAnswer    -> {
                        item?.setBackgroundResource(R.drawable.choice_wrong_bg)
                        item?.findViewById<TextView>(R.id.choiceText)
                            ?.setTextColor(Color.parseColor("#F87171"))
                        item?.findViewById<TextView>(R.id.choiceLetter)
                            ?.setTextColor(Color.parseColor("#F87171"))
                    }
                }
            }
            showFeedback(isCorrect, q.explanation)
        }

        scoreDisplay.text = "Score: $correctCount"
        btnNext.visibility = View.VISIBLE
        btnNext.text = if (isLastQ) "Voir les résultats →" else "Suivant →"
    }

    private fun showFeedback(correct: Boolean, explanation: String) {
        feedbackBox.setBackgroundResource(
            if (correct) R.drawable.feedback_correct_bg else R.drawable.feedback_wrong_bg
        )
        feedbackIcon.text = if (correct) "✓" else "✗"
        feedbackIcon.setTextColor(
            if (correct) Color.parseColor("#34D399") else Color.parseColor("#F87171")
        )
        feedbackText.text = explanation
        feedbackBox.visibility = View.VISIBLE
    }

    // ─────────────────────────────────────────────────
    // Question suivante / Résultats
    // ─────────────────────────────────────────────────
    private fun nextQuestion() {
        currentIndex++
        if (currentIndex >= questions.size) {
            showResults()
        } else {
            loadQuestion()
        }
    }

    private fun showResults() {
        val intent = Intent(this, ResultsActivity::class.java).apply {
            putExtra(EXTRA_CORRECT, correctCount)
            putExtra(EXTRA_TOTAL_Q, questions.size)
            putParcelableArrayListExtra(EXTRA_WRONG_Q, ArrayList(wrongQuestions))
            putParcelableArrayListExtra(EXTRA_ALL_Q,   ArrayList(questions))
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_CORRECT = "extra_correct"
        const val EXTRA_TOTAL_Q = "extra_total_q"
        const val EXTRA_WRONG_Q = "extra_wrong_q"
        const val EXTRA_ALL_Q   = "extra_all_q"
    }
}
