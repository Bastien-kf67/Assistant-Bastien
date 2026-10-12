# Assistant Bastien

Application Android qui capture en quelques secondes une chose à ne pas oublier.
Capture d'abord, organisation automatique ensuite.

Le cahier des charges complet (V2) est tenu à part ; les règles de développement sont dans `CLAUDE.md`.

## Où en est-on

- **Jalon 1a — capture locale.** Widget « Écrire / Parler », écran de saisie, base sur le téléphone,
  historique avec case à cocher.
- **Jalon 1b — compléments.** Toucher une tâche pour la renommer ou la supprimer, corbeille de
  30 jours, recherche, verrouillage de l'historique par empreinte, raccourci « Noter » (touche
  latérale, tuile des réglages rapides, écran verrouillé).
- **Jalon 2, en avance — Microsoft To Do.** Code prêt et testé, synchro dans les deux sens avec
  la liste « Assistant (test) ». Inactif tant que l'inscription Microsoft n'est pas faite
  (`TodoConfig.CLIENT_ID` et `TENANT_ID` vides).

## Noter sans passer par le widget

- **Touche latérale** : Paramètres → Fonctions avancées → Touche latérale → Appuyer deux fois →
  Ouvrir l'application → **Assistant**. Lancée ainsi, l'appli ouvre directement l'écran de saisie
  (porte d'entrée par défaut, catégorie INFO) ; l'icône Assistant ouvre l'historique.
- **Tuile des réglages rapides** : dérouler les réglages rapides → crayon (modifier) → faire glisser
  la tuile **Noter** dans la liste.
- **Écran verrouillé** : l'écran de saisie s'affiche par-dessus l'écran verrouillé quand c'est
  possible ; il ne montre jamais les captures existantes. La dictée, elle, peut demander de déverrouiller.

## Verrouillage

L'historique demande l'empreinte (ou le code du téléphone) à l'ouverture, puis après 1 minute
en arrière-plan. Noter ne demande jamais rien. Sans verrouillage d'écran configuré sur le
téléphone, l'appli ne peut pas se verrouiller.

La base n'a pas de chiffrement en plus de celui d'Android : le téléphone chiffre déjà les
données de chaque appli, inaccessibles tant qu'il n'a pas été déverrouillé.

## Installer sur le Galaxy Z Fold

1. Sur le téléphone, ouvrir la page **Releases** du dépôt GitHub (connecté au compte GitHub).
2. Dans la version **test**, toucher le fichier `assistant-test-XX.apk` pour le télécharger.
3. Ouvrir le fichier téléchargé. La première fois, Android demande d'autoriser l'installation
   depuis le navigateur : accepter, revenir en arrière, toucher **Installer**.
4. Ouvrir l'appli **Assistant**, toucher **Ajouter le widget** (ou appui long sur l'écran
   d'accueil → Widgets → Assistant).

Mise à jour : même manipulation avec le nouvel APK, par-dessus l'ancien. Les captures sont conservées.

**Ne pas désinstaller l'appli** : tant qu'il n'y a pas de serveur (Jalon 3), les captures
n'existent que sur le téléphone, une désinstallation les efface.

## Dictée (bouton Parler)

Le bouton Parler utilise la dictée Google du téléphone, en mode hors ligne.
Si elle répond « hors ligne indisponible », installer le français hors ligne :
Paramètres → Google → Paramètres des applications Google → Recherche, Assistant et voix →
Voix → Reconnaissance vocale hors connexion → Français.
L'intitulé exact peut varier selon la version d'Android.

## Batterie Samsung

Sans To Do, l'appli ne fait rien en arrière-plan : aucun réglage n'est nécessaire.
Une fois To Do connecté, exclure Assistant de l'optimisation pour que la synchro passe :
Paramètres → Batterie → Limites d'utilisation en arrière-plan → Applis jamais en veille → Assistant.

## Développement

- Kotlin, Jetpack Compose, Glance (widget), Room (base), WorkManager (synchro en arrière-plan),
  AppAuth (connexion Microsoft), OkHttp (Microsoft Graph), Biometric (empreinte).
- Branche `dev` pour le travail, `main` pour les versions stables.
- La compilation se fait sur GitHub (`.github/workflows/build.yml`) : tests, puis APK signé,
  publié dans les Releases (`test` pour dev, `stable` pour main).
- La clé de signature n'est jamais dans le dépôt. Elle est fournie par deux secrets GitHub :
  `ASSISTANT_KEYSTORE_BASE64` et `ASSISTANT_KEYSTORE_PASSWORD` (alias fixe `assistant`).
  Sans elle, aucun APK n'est publié : une autre signature obligerait à désinstaller l'appli,
  donc à perdre les captures.
- Le schéma de la base est exporté à chaque compilation (artefact `schema-base`) : à verser
  dans `app/schemas/` avant toute évolution du modèle de données.
