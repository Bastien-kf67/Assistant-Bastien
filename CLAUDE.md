# CLAUDE.md — règles du projet Assistant Bastien

Bastien dirige, Claude code. Bastien n'est pas développeur : expliquer en mots simples,
sans jargon, ce qui change pour lui.

## Principes obligatoires

1. La simplicité pour l'utilisateur est prioritaire.
2. Ne jamais ajouter une fonctionnalité non demandée.
3. Ne jamais modifier profondément l'architecture sans expliquer pourquoi.
4. Une capture ne doit jamais être perdue.
5. La base Assistant est la source de vérité ; Microsoft To Do est une interface externe.
6. Préférer une interprétation prudente à une supposition ; ne jamais inventer une date ou une action.
7. En cas de doute, marquer la capture à clarifier.
8. Toutes les entrées passent par le même `CaptureService`. Jamais de moteur propre au texte,
   à la voix, à Outlook ou à WhatsApp.
9. Toujours compiler et tester avant de déclarer un jalon terminé.
10. Travailler un jalon à la fois et attendre le retour d'usage réel avant le suivant.

Ordre des priorités : aucune capture perdue, rapidité, simplicité, fiabilité, sécurité,
évolutivité, esthétique. Ne jamais sacrifier les cinq premières pour une fonctionnalité.

## Règles techniques

- **Enregistrer avant tout** : enregistrer en local, confirmer, puis seulement traiter ou synchroniser.
  Aucun appel réseau avant l'écriture en base.
- `originalText` n'est jamais modifié, ni par le code ni par l'IA.
- **Jamais de migration destructive** : pas de `fallbackToDestructiveMigration`. Toute évolution du
  schéma Room passe par une migration écrite et testée. Le schéma exporté (`app/schemas/`) sert
  de référence.
- Anti-double appui : l'identifiant de capture est fixé à l'ouverture de l'écran de saisie et
  conservé avec le brouillon ; `CaptureService` ne crée qu'une capture par identifiant et texte.
- Les écritures sont lancées dans `AssistantApp.appScope` : elles vont au bout même si l'écran se ferme.
- Aucun secret dans Git (clés, mots de passe, jetons). La clé de signature passe par les secrets GitHub.
- Ne jamais écrire de jeton, clé, mot de passe ou cookie dans le journal (`action_log`) ni dans les logs.
- Permissions Android minimales : réseau uniquement, pour To Do.
- Données : `userId` présent partout, identifiants UUID générés sur le téléphone.
- Synchro To Do (`integration/microsoft/TodoSyncEngine`) : d'abord ce qui vient de To Do, puis
  ce qui part. Une modification locale en attente (`syncStatus = WAITING`) l'emporte toujours.
  Rien n'est effacé de la base : les suppressions vont dans la corbeille (`deletedAt`).
- Jetons Microsoft chiffrés par le Keystore Android (`SecureStore`), jamais en clair ni dans un log.
- L'écran de saisie peut s'afficher sur l'écran verrouillé : il ne doit jamais montrer les captures existantes.
- IA (`domain/interpreter`, `integration/ai`) : la capture et sa tâche brute sont enregistrées d'abord ;
  la tâche est retenue (`HELD`) jusqu'à l'analyse. Échec définitif ou trop de pannes : la tâche brute part
  telle quelle. Une tâche touchée par l'utilisateur avant l'analyse n'est pas réécrite.
  L'IA ne doit jamais inventer de date, personne ou action ; une date douteuse est ignorée.
- La clé de l'IA vient du secret GitHub `ANTHROPIC_API_KEY` ; ne jamais l'afficher, la journaliser
  ni la mettre dans un message d'erreur.

## Compilation

L'environnement de Claude ne peut pas télécharger les outils Android de Google : la compilation
et les tests tournent sur GitHub Actions. Envoyer sur `dev`, suivre l'exécution, lire les erreurs,
corriger. Un jalon n'est terminé que quand l'exécution est verte et l'APK publié.

## Structure

- `capture/` : `CaptureService`, `CaptureSource` — le moteur unique.
- `data/` : base Room (`local/`), brouillon, opérations sur les tâches.
- `domain/model/` : statuts, types, catégories.
- `ui/quickcapture/` : écran de saisie ouvert par le widget.
- `ui/main/` : historique, recherche, corbeille, verrouillage.
- `widget/` : widget Glance et tuile « Noter » des réglages rapides.
- `integration/microsoft/` : connexion Microsoft, Microsoft Graph, moteur de synchro To Do.
- `domain/interpreter/` : interface `TaskInterpreter` et moteur d'interprétation.
- `integration/ai/` : appel à Claude, consignes de l'IA, choix du modèle.
- `worker/` : analyse IA et synchro en arrière-plan.
