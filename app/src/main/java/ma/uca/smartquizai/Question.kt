package ma.uca.smartquizai

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Modèle représentant une question de quiz générée par l'IA.
 *
 * @param type       "qcm" | "vf" | "open"
 * @param question   Texte de la question
 * @param choices    Liste des choix (vide pour les questions ouvertes)
 * @param correct    Index de la bonne réponse (-1 pour les questions ouvertes)
 * @param explanation Explication ou réponse de référence
 */
@Parcelize
data class Question(
    val type: String,
    val question: String,
    val choices: List<String>,
    val correct: Int,
    val explanation: String
) : Parcelable
