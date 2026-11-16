# Programas — Application Android Native (Kotlin)

Application Android réelle développée en **Kotlin** (sans PWA), connectée directement à :
`https://programas-upgc.metroone110.workers.dev/`

## Fonctionnalités incluses

- **Package ID** : `ci.upgc.programas` (Version `1.0.0`, code `1`)
- **WebView native optimisée** : barre d'état colorée `#EA580C`, navigation Retour native, écran hors-ligne avec bouton Réessayer
- **Géolocalisation GPS** : autorisation runtime pour le Radar 65 m
- **Google Auth / Firebase** : prise en charge multi-fenêtres + User-Agent natif (pas de blocage `disallowed_useragent`)
- **Paiements & liens externes** : ouverture native de Wave, WhatsApp, appels et intent `https://`

---

## Option 1 — Compiler et signer l'APK automatiquement via GitHub (recommandé, sans PC puissant)

1. Crée un **nouveau dépôt GitHub dédié** (ne modifie pas ton dépôt web en ligne).
2. Envoie tout le contenu de ce dossier `.zip` dans ce nouveau dépôt.
3. Ouvre l'onglet **Actions** sur GitHub : le workflow `Build Signed Android APK` génère une clé de signature, compile `assembleRelease`, vérifie la signature avec `apksigner` et publie **`programas-android-signed-apk`** prêt à installer sur Android.

> Astuce : pour garder exactement la même clé de signature entre deux mises à jour, génère ton propre fichier `.jks` une fois avec `scripts/generate-keystore.sh` et enregistre `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` dans les Secrets du dépôt GitHub.

---

## Option 2 — Compiler avec Android Studio

1. Ouvre ce dossier dans **Android Studio**.
2. Exécute `./scripts/generate-keystore.sh` puis copie `keystore.properties.example` vers `keystore.properties`.
3. Dans Android Studio : **Build → Generate Signed Bundle / APK → APK** (ou lance `gradle assembleRelease`).
4. Récupère l'APK signé dans `app/build/outputs/apk/release/app-release.apk`.
