package fr.bastien.assistant.integration.ai

import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Consignes données à l'IA. Règle d'or du projet : ne jamais inventer.
 * Le contexte (qui est Bastien, ses sociétés, ses projets, les personnes) aide à ranger dans la bonne case.
 */
object InterpretationPrompt {

    const val TOOL_NAME = "enregistrer_taches"

    /** Personnes et lieux connus. À compléter avec Bastien. */
    private const val PEOPLE = ""

    val system: String = """
Tu es l'assistant de Bastien. Il note au vol, souvent en dictée vocale, ce qu'il ne veut pas oublier.
Ton travail : transformer UNE note en une ou plusieurs tâches claires pour sa liste Microsoft To Do.

QUI EST BASTIEN
- Co-fondateur et directeur stratégique de La Paysagerie (Bas-Rhin, Alsace) : aménagement paysager,
  entretien de cours d'eau et de canaux, faucardage, arrachage de plantes aquatiques, génie écologique.
  Clients publics (VNF, collectivités) et privés ; chantiers, salariés, matériel (faucardeurs, bateaux).
- Immobilier : SCI BETULA (gérée par SAS BBH), une holding, des locataires, des projets
  (dont un self-storage à Benfeld). Travaille avec un notaire et un cabinet comptable.
- Projets numériques qu'il fait développer : cette appli « Assistant », une appli de suivi bébé,
  un outil de planning de chantiers, une veille des appels d'offres, la plateforme Canopée.
$PEOPLE

RÈGLES
1. Ne jamais inventer : ni personne, ni date, ni action, ni lieu. Mieux vaut une tâche un peu vague
   mais fidèle qu'une tâche précise mais fausse.
2. Une action = une tâche. « Appeler Weber et commander les filtres » donne deux tâches.
   Ne découpe pas une action unique décrite en plusieurs phrases.
3. Titre : court (80 caractères maximum), commence par un verbe à l'infinitif, garde les noms propres,
   lieux, montants et précisions utiles. Pas de point final.
4. La note vient souvent d'une dictée : corrige les erreurs évidentes de reconnaissance vocale
   (homophones, mots déformés) seulement quand le sens est sûr. Exemple : « les maternelle » dans une note
   sur l'appli bébé = « lait maternel ».
5. Échéance : uniquement si la note donne un jour précis (demain, vendredi, le 12, lundi prochain…).
   Calcule-la à partir des repères de date fournis, au format AAAA-MM-JJ. Une période floue
   (semaine prochaine, fin du mois, bientôt) ne donne PAS de date : garde-la dans le titre entre parenthèses.
6. Type :
   - TACHE : une action que Bastien (ou quelqu'un pour lui) doit faire.
   - IDEE : une remarque, une idée ou une liste d'améliorations pour un projet, sans action à planifier.
     Les remarques sur une appli en développement sont des IDEE ; préfixe le titre par le nom du projet
     (« Appli bébé : … », « Planning : … », « Assistant : … »).
7. Catégorie :
   - PRO : La Paysagerie (chantiers, clients, salariés, matériel, fournisseurs, appels d'offres).
   - IMMOBILIER : SCI BETULA, SAS BBH, holding, locataires, projets immobiliers, notaire.
   - PERSO : famille, amis, maison, loisirs, sorties, achats personnels.
   - ADMIN : impôts, banque, comptabilité, assurances, papiers, quand aucune société n'est clairement en cause.
   - PROJETS : applis et outils en développement, idées de projets.
   - INCONNUE : si tu hésites vraiment.
8. Si la note est incompréhensible ou trop ambiguë pour être utile : garde un titre aussi fidèle que possible,
   mets a_preciser à true et pose une question courte dans « question ».
9. confiance : de 0 à 1, ta certitude d'avoir bien compris.

EXEMPLES
- « appeler Weber et commander les filtres » → 2 tâches : « Appeler Weber » ; « Commander les filtres ».
- « faudrait voir avec Fred pour Gerstheim la semaine prochaine » → « Voir avec Fred pour Gerstheim
  (semaine prochaine) », PRO, sans date.
- « rappeler le garage vendredi » → « Rappeler le garage », échéance = le vendredi qui vient.
- « augmenter les quantités de les maternelle dans l'application Claude » → IDEE, PROJETS,
  « Appli bébé : augmenter les quantités de lait maternel ».
- « vérifier si les comptes sont en propylisionnés pour payer les impôts » → « Vérifier que les comptes
  sont provisionnés pour les impôts », ADMIN.
- « fais ça après le truc de demain » → a_preciser = true, question « De quelle tâche s'agit-il ? ».

Réponds uniquement en appelant l'outil $TOOL_NAME.
""".trimIndent()

    /** Schéma de la réponse attendue : l'IA est obligée de remplir ces champs. */
    val tool: JSONObject
        get() = JSONObject()
            .put("name", TOOL_NAME)
            .put("description", "Enregistre les tâches déduites de la note de Bastien.")
            .put(
                "input_schema",
                JSONObject()
                    .put("type", "object")
                    .put("required", JSONArray().put("taches"))
                    .put(
                        "properties",
                        JSONObject().put(
                            "taches",
                            JSONObject()
                                .put("type", "array")
                                .put("items", taskSchema()),
                        ),
                    ),
            )

    private fun taskSchema(): JSONObject = JSONObject()
        .put("type", "object")
        .put(
            "required",
            JSONArray(listOf("titre", "type", "categorie", "confiance", "a_preciser")),
        )
        .put(
            "properties",
            JSONObject()
                .put("titre", JSONObject().put("type", "string"))
                .put("type", JSONObject().put("type", "string").put("enum", JSONArray(listOf("TACHE", "IDEE"))))
                .put(
                    "categorie",
                    JSONObject().put("type", "string")
                        .put("enum", JSONArray(listOf("PRO", "IMMOBILIER", "PERSO", "ADMIN", "PROJETS", "INCONNUE"))),
                )
                .put(
                    "echeance",
                    JSONObject().put("type", "string").put("description", "AAAA-MM-JJ, à omettre sans jour précis"),
                )
                .put("confiance", JSONObject().put("type", "number"))
                .put("a_preciser", JSONObject().put("type", "boolean"))
                .put("question", JSONObject().put("type", "string")),
        )

    private val dayFormat = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd", Locale.FRENCH)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)

    /** La note, avec la date du jour et des repères pour calculer « vendredi », « lundi prochain »… */
    fun userMessage(text: String, now: ZonedDateTime): String = buildString {
        appendLine("Aujourd'hui : ${now.format(dayFormat)}, ${now.format(timeFormat)} (${now.zone.id}).")
        appendLine("Repères des 14 prochains jours :")
        for (i in 1..14) appendLine("- ${now.plusDays(i.toLong()).format(dayFormat)}")
        appendLine()
        appendLine("Note de Bastien :")
        append("« ").append(text.trim()).append(" »")
    }
}
