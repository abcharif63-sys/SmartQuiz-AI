package ma.uca.smartquizai

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import ma.uca.smartquizai.BuildConfig

/**
 * Service d'appel à l'API Google Gemini (Solution Gratuite).
 * Utilise la clé API définie dans local.properties via BuildConfig.
 */
object ClaudeApiService {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
    
    // On utilise la clé sécurisée générée dans BuildConfig
    private val API_KEY = BuildConfig.GEMINI_API_KEY

    fun generateQuestions(
        courseText: String,
        count: Int,
        types: List<String>,
        difficulty: String
    ): List<Question> {
        if (courseText.trim().length < 20) {
            Log.e("GeminiAPI", "Texte du cours trop court ou vide.")
            return emptyList()
        }

        if (API_KEY.isEmpty()) {
            Log.e("GeminiAPI", "Clé API manquante. Ajoutez GEMINI_API_KEY=votre_cle dans local.properties")
            return emptyList()
        }

        val prompt = """
            Tu es un générateur de quiz. Analyse ce cours et génère exactement $count questions.
            Difficulté : $difficulty. 
            Types : ${types.joinToString(", ")}.
            
            COURS :
            ${courseText.take(5000)}
            
            REPONDS UNIQUEMENT PAR UN TABLEAU JSON au format suivant :
            [
              {
                "type": "qcm",
                "question": "Question ici ?",
                "choices": ["A", "B", "C", "D"],
                "correct": 0,
                "explanation": "Explication"
              }
            ]
        """.trimIndent()

        val responseText = callGemini(prompt) ?: return emptyList()
        return parseGeminiResponse(responseText)
    }

    private fun callGemini(prompt: String): String? {
        return try {
            val url = URL("$BASE_URL?key=$API_KEY")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            val requestBody = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().apply {
                        put("parts", JSONArray().put(
                            JSONObject().put("text", prompt)
                        ))
                    }
                ))
            }

            OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()) }

            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e("GeminiAPI", "Erreur HTTP ${conn.responseCode}: $error")
                null
            }
        } catch (e: Exception) {
            Log.e("GeminiAPI", "Erreur réseau : ${e.message}")
            null
        }
    }

    private fun parseGeminiResponse(response: String): List<Question> {
        return try {
            val root = JSONObject(response)
            val candidates = root.optJSONArray("candidates") ?: return emptyList()
            if (candidates.length() == 0) return emptyList()
            
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return emptyList()
            val textResponse = content.getJSONArray("parts").getJSONObject(0).getString("text")

            val start = textResponse.indexOf("[")
            val end = textResponse.lastIndexOf("]")
            if (start == -1 || end == -1) {
                Log.e("GeminiAPI", "Format JSON non trouvé dans la réponse")
                return emptyList()
            }
            
            val jsonCleaned = textResponse.substring(start, end + 1)
            val jsonArray = JSONArray(jsonCleaned)
            val list = mutableListOf<Question>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val choicesArr = obj.optJSONArray("choices") ?: JSONArray()
                val choices = (0 until choicesArr.length()).map { choicesArr.getString(it) }

                list.add(Question(
                    type = obj.optString("type", "qcm"),
                    question = obj.optString("question", ""),
                    choices = choices,
                    correct = obj.optInt("correct", 0),
                    explanation = obj.optString("explanation", "")
                ))
            }
            list
        } catch (e: Exception) {
            Log.e("GeminiAPI", "Erreur de parsing : ${e.message}")
            emptyList()
        }
    }
}
