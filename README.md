# Assistant Bastien

Application Android qui capture en quelques secondes une chose à ne pas oublier.
Capture d'abord, organisation automatique ensuite.

Le cahier des charges complet (V2) est tenu à part ; les règles de développement sont dans `CLAUDE.md`.

## Où en est-on

**Jalon 1a — capture locale.** Widget « Écrire / Parler », écran de saisie, base sur le téléphone,
historique avec case à cocher. Aucune connexion Internet, aucune donnée ne quitte le téléphone.

## Installer sur le Galaxy Z Fold

1. Sur le téléphone, ouvrir la page **Releases** du dépôt GitHub (connecté au compte GitHub).
2. Dans la version **test**, toucher le fichier `assistant-test-XX.apk` pour le télécharger.
3. Ouvrir le fichier téléchargé. La première fois, Android demande d'autoriser l'installation
   depuis le navigateur : accepter, revenir en arrière, toucher **Installer**.
4. Ouvrir l'appli **Assistant**, toucher **Ajouter le widget** (ou appui long sur l'écran
   d'accueil → Widgets → Assistant).

Mise à jour : même manipulation avec le nouvel APK, par-dessus l'ancien. Les captures sont conservées.

**Ne pas désinstaller l'appli** : au Jalon 1, les captures n'existent que sur le téléphone,
une désinstallation les efface.

## Dictée (bouton Parler)

Le bouton Parler utilise la dictée Google du téléphone, en mode hors ligne.
Si elle répond « hors ligne indisponible », installer le français hors ligne :
Paramètres → Google → Paramètres des applications Google → Recherche, Assistant et voix →
Voix → Reconnaissance vocale hors connexion → Français.
L'intitulé exact peut varier selon la version d'Android.

## Batterie Samsung

Au Jalon 1, l'appli ne fait rien en arrière-plan : aucun réglage n'est nécessaire.
À partir du Jalon 2 (synchro To Do), il faudra exclure Assistant de l'optimisation :
Paramètres → Batterie → Limites d'utilisation en arrière-plan → Applis jamais en veille → Assistant.

## Développement

- Kotlin, Jetpack Compose, Glance (widget), Room (base).
- Branche `dev` pour le travail, `main` pour les versions stables.
- La compilation se fait sur GitHub (`.github/workflows/build.yml`) : tests, puis APK signé,
  publié dans les Releases (`test` pour dev, `stable` pour main).
- La clé de signature n'est jamais dans le dépôt. Elle est fournie par quatre secrets GitHub :
  `ASSISTANT_KEYSTORE_BASE64`, `ASSISTANT_KEYSTORE_PASSWORD`, `ASSISTANT_KEY_ALIAS`,
  `ASSISTANT_KEY_PASSWORD`. Sans elle, chaque version aurait une signature différente et
  Android refuserait la mise à jour.
- Le schéma de la base est exporté à chaque compilation (artefact `schema-base`) : à verser
  dans `app/schemas/` avant toute évolution du modèle de données.
