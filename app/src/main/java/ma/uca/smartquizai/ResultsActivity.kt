package ma.uca.smartquizai

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Affiche les résultats du quiz :
 * - Score en pourcentage (cercle animé)
 * - Tableau correct / incorrect / total
 * - Révision de chaque réponse
 * - Bouton pour reprendre uniquement les questions ratées
 */
class ResultsActivity : AppCompatActivity() {

    // ── UI ──────────────────────────────────────────
    private lateinit var resultEmoji: TextView
    private lateinit var resultMessage: TextView
    private lateinit var resultSub: TextView
    private lateinit var scoreCircle: ProgressBar
    private lateinit var scorePct: TextView
    private lateinit var resCorrect: TextView
    private lateinit var resWrong: TextView
    private lateinit var resTotal: TextView
    private lateinit var reviewList: LinearLayout
    private lateinit var btnRetryWrong: Button
    private lateinit var btnBackHome: Button

    // ── Données ──────────────────────────────────────
    private var correct  = 0
    private var totalQ   = 0
    private lateinit var wrongQuestions: List<Question>
    private lateinit var allQuestions: List<Question>

    // ─────────────────────────────────────────────────
    // onCreate
    // ─────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        correct = intent.getIntExtra(QuizActivity.EXTRA_CORRECT, 0)
        totalQ  = intent.getIntExtra(QuizActivity.EXTRA_TOTAL_Q, 1)

        @Suppress("DEPRECATION")
        wrongQuestions = intent.getParcelableArrayListExtra<Question>(QuizActivity.EXTRA_WRONG_Q) ?: emptyList()
        @Suppress("DEPRECATION")
        allQuestions   = intent.getParcelableArrayListExtra<Question>(QuizActivity.EXTRA_ALL_Q)   ?: emptyList()

        bindViews()
        displayResults()
        buildReviewList()
        setupListeners()
    }

    // ─────────────────────────────────────────────────
    // Liaison des vues
    // ─────────────────────────────────────────────────
    private fun bindViews() {
        resultEmoji   = findViewById(R.id.resultEmoji)
        resultMessage = findViewById(R.id.resultMessage)
        resultSub     = findViewById(R.id.resultSub)
        scoreCircle   = findViewById(R.id.scoreCircle)
        scorePct      = findViewById(R.id.scorePct)
        resCorrect    = findViewById(R.id.resCorrect)
        resWrong      = findViewById(R.id.resWrong)
        resTotal      = findViewById(R.id.resTotal)
        reviewList    = findViewById(R.id.reviewList)
        btnRetryWrong = findViewById(R.id.btnRetryWrong)
        btnBackHome   = findViewById(R.id.btnBackHome)
    }

    // ─────────────────────────────────────────────────
    // Affichage des résultats
    // ─────────────────────────────────────────────────
    private fun displayResults() {
        val pct   = if (totalQ > 0) (correct * 100 / totalQ) else 0
        val wrong = totalQ - correct

        // Score cercle
        scoreCircle.progress = pct
        scorePct.text        = "$pct%"

        // Breakdown
        resCorrect.text = correct.toString()
        resWrong.text   = wrong.toString()
        resTotal.text   = totalQ.toString()

        // Message contextuel
        when {
            pct >= 90 -> {
                resultEmoji.text   = "🏆"
                resultMessage.text = "Parfait ! Bravo !"
                resultSub.text     = "Tu maîtrises totalement ce chapitre."
            }
            pct >= 75 -> {
                resultEmoji.text   = "🎉"
                resultMessage.text = "Excellent travail !"
                resultSub.text     = "Tu maîtrises bien ce chapitre."
            }
            pct >= 55 -> {
                resultEmoji.text   = "👍"
                resultMessage.text = "Bien joué !"
                resultSub.text     = "Encore un peu de révision et tu y es !"
            }
            else       -> {
                resultEmoji.text   = "😕"
                resultMessage.text = "Continue à t'entraîner !"
                resultSub.text     = "Reprends les erreurs pour progresser."
            }
        }

        // Bouton retry
        if (wrongQuestions.isEmpty()) {
            btnRetryWrong.isEnabled = false
            btnRetryWrong.alpha     = 0.4f
            btnRetryWrong.text      = "✓ Aucune erreur à reprendre"
        } else {
            btnRetryWrong.text = "🔁 Reprendre les ${wrongQuestions.size} erreur(s)"
        }
    }

    // ─────────────────────────────────────────────────
    // Liste de révision
    // ─────────────────────────────────────────────────
    private fun buildReviewList() {
        reviewList.removeAllViews()
        val inflater = LayoutInflater.from(this)

        allQuestions.forEach { q ->
            val isWrong = wrongQuestions.any { it.question == q.question }
            val itemView = inflater.inflate(R.layout.item_review, reviewList, false)

            itemView.findViewById<TextView>(R.id.reviewQuestion).text =
                q.question.take(90) + if (q.question.length > 90) "..." else ""

            val statusView = itemView.findViewById<TextView>(R.id.reviewStatus)
            if (isWrong) {
                statusView.text = "✗ Incorrecte — ${q.choices.getOrElse(q.correct) { "—" }}"
                statusView.setTextColor(Color.parseColor("#F87171"))
                itemView.setBackgroundResource(R.drawable.choice_wrong_bg)
            } else {
                statusView.text = "✓ Correcte"
                statusView.setTextColor(Color.parseColor("#34D399"))
                itemView.setBackgroundResource(R.drawable.choice_correct_bg)
            }

            reviewList.addView(itemView)
        }
    }

    // ─────────────────────────────────────────────────
    // Listeners
    // ─────────────────────────────────────────────────
    private fun setupListeners() {
        btnRetryWrong.setOnClickListener {
            if (wrongQuestions.isNotEmpty()) {
                val intent = Intent(this, QuizActivity::class.java).apply {
                    putParcelableArrayListExtra(
                        MainActivity.EXTRA_QUESTIONS,
                        ArrayList(wrongQuestions)
                    )
                }
                startActivity(intent)
                finish()
            }
        }

        btnBackHome.setOnClickListener {
            // Retourne le score à MainActivity
            val resultIntent = Intent().apply {
                putExtra(MainActivity.EXTRA_SCORE, correct)
                putExtra(MainActivity.EXTRA_TOTAL, totalQ)
            }
            setResult(RESULT_OK, resultIntent)
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        }
    }
}
