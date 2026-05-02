package ma.uca.smartquizai

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object ClaudeApiService {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    private val API_KEY = BuildConfig.GEMINI_API_KEY

    fun generateQuestions(
        courseText: String,
        count: Int,
        types: List<String>,
        difficulty: String
    ): List<Question> {

        Log.d("GeminiAPI", "=== DÉBUT generateQuestions ===")
        Log.d("GeminiAPI", "Longueur texte: ${courseText.length}")
        Log.d("GeminiAPI", "Clé API présente: ${API_KEY.isNotEmpty()}")
        Log.d("GeminiAPI", "Clé (5 premiers chars): ${API_KEY.take(5)}")

        if (courseText.trim().length < 20) {
            Log.e("GeminiAPI", "ÉCHEC: texte trop court")
            return emptyList()
        }

        if (API_KEY.isEmpty()) {
            Log.e("GeminiAPI", "ÉCHEC: clé API vide")
            return emptyList()
        }

        // Prompt simplifié pour maximiser les chances
        val prompt = """Génère $count questions de quiz en JSON sur ce texte.
Difficulté: $difficulty.
Texte: ${courseText.take(3000)}

Réponds UNIQUEMENT avec ce JSON (sans markdown) :
[{"type":"qcm","question":"?","choices":["A","B","C","D"],"correct":0,"explanation":"..."}]"""

        Log.d("GeminiAPI", "Appel API en cours...")
        val responseText = callGemini(prompt)

        if (responseText == null) {
            Log.e("GeminiAPI", "ÉCHEC: réponse null de l'API")
            return emptyList()
        }

        Log.d("GeminiAPI", "Réponse reçue (200 chars): ${responseText.take(200)}")
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
            conn.readTimeout   = 30000

            val body = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().apply {
                        put("parts", JSONArray().put(
                            JSONObject().put("text", prompt)
                        ))
                    }
                ))
            }.toString()

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = conn.responseCode
            Log.d("GeminiAPI", "HTTP response code: $code")

            if (code == 200) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val error = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                Log.e("GeminiAPI", "Erreur HTTP $code: $error")
                null
            }
        } catch (e: Exception) {
            Log.e("GeminiAPI", "Exception réseau: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun parseGeminiResponse(response: String): List<Question> {
        return try {
            val root       = JSONObject(response)
            val candidates = root.optJSONArray("candidates") ?: run {
                Log.e("GeminiAPI", "Pas de 'candidates' dans la réponse")
                return emptyList()
            }

            val content = candidates.getJSONObject(0).optJSONObject("content") ?: run {
                Log.e("GeminiAPI", "Pas de 'content' dans candidates[0]")
                return emptyList()
            }

            val text = content.getJSONArray("parts").getJSONObject(0).getString("text")
            Log.d("GeminiAPI", "Texte brut Gemini: ${text.take(300)}")

            // Cherche [ ... ] n'importe où dans la réponse
            val start = text.indexOf("[")
            val end   = text.lastIndexOf("]")

            if (start == -1 || end == -1 || end <= start) {
                Log.e("GeminiAPI", "JSON array introuvable dans: $text")
                return emptyList()
            }

            val json  = text.substring(start, end + 1)
            val array = JSONArray(json)
            Log.d("GeminiAPI", "Questions parsées: ${array.length()}")

            val list = mutableListOf<Question>()
            for (i in 0 until array.length()) {
                val obj     = array.getJSONObject(i)
                val choices = obj.optJSONArray("choices") ?: JSONArray()
                list.add(Question(
                    type        = obj.optString("type", "qcm"),
                    question    = obj.optString("question", ""),
                    choices     = (0 until choices.length()).map { choices.getString(it) },
                    correct     = obj.optInt("correct", 0),
                    explanation = obj.optString("explanation", "")
                ))
            }
            list
        } catch (e: Exception) {
            Log.e("GeminiAPI", "Erreur parsing JSON: ${e.message}")
            emptyList()
        }
    }
}
