package ma.uca.smartquizai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import org.json.JSONArray
import org.json.JSONObject

object ClaudeApiService {

    // Initialisation du modèle avec la clé API du BuildConfig
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            // Force la réponse en format JSON si supporté par le SDK
            responseMimeType = "application/json"
        }
    )

    /**
     * Génère des questions de quiz en utilisant le SDK Google AI.
     * Cette fonction est suspendue car l'appel au SDK est asynchrone.
     */
    suspend fun generateQuestions(
        courseText: String,
        count: Int,
        types: List<String>,
        difficulty: String
    ): List<Question> {
        Log.d("GeminiAPI", "=== DÉBUT generateQuestions (SDK Official) ===")
        
        if (courseText.trim().length < 20) return emptyList()
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
            Log.e("GeminiAPI", "Clé API manquante dans local.properties")
            return emptyList()
        }

        val prompt = """
            Agis en tant qu'expert en pédagogie. Génère $count questions de quiz basées sur le texte fourni.
            Difficulté : $difficulty
            Types autorisés : ${types.joinToString(", ")}
            
            Texte de référence : ${courseText.take(4000)}

            Réponds UNIQUEMENT avec un tableau JSON valide suivant exactement cette structure :
            [
              {
                "type": "qcm",
                "question": "Texte de la question",
                "choices": ["Option A", "Option B", "Option C", "Option D"],
                "correct": 0,
                "explanation": "Explication de la réponse"
              }
            ]
            Pour le type 'vf' (Vrai/Faux), utilise deux choix ["Vrai", "Faux"].
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: ""
            
            if (responseText.isEmpty()) {
                Log.e("GeminiAPI", "Réponse vide du SDK")
                return emptyList()
            }

            parseGeminiResponse(responseText)
        } catch (e: Exception) {
            Log.e("GeminiAPI", "Erreur SDK Gemini: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseGeminiResponse(responseText: String): List<Question> {
        return try {
            // Nettoyage au cas où le modèle entoure le JSON de balises markdown
            val cleanedJson = responseText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val array = JSONArray(cleanedJson)
            val questions = mutableListOf<Question>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val choicesJson = obj.optJSONArray("choices") ?: JSONArray()
                val choicesList = mutableListOf<String>()
                for (j in 0 until choicesJson.length()) {
                    choicesList.add(choicesJson.getString(j))
                }

                questions.add(
                    Question(
                        type = obj.optString("type", "qcm"),
                        question = obj.optString("question", ""),
                        choices = choicesList,
                        correct = obj.optInt("correct", 0),
                        explanation = obj.optString("explanation", "")
                    )
                )
            }
            Log.d("GeminiAPI", "Succès : ${questions.size} questions générées")
            questions
        } catch (e: Exception) {
            Log.e("GeminiAPI", "Erreur lors du parsing JSON : ${e.message}\nBrut: $responseText")
            emptyList()
        }
    }
}
