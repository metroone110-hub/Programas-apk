#!/usr/bin/env bash
set -euo pipefail

KEYSTORE_PATH="programas-release.jks"
ALIAS="programas"

echo "Création de la clé de signature Android ($KEYSTORE_PATH)..."
keytool -genkeypair -v \
  -keystore "$KEYSTORE_PATH" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

echo ""
echo "Clé générée avec succès !"
echo "Copiez keystore.properties.example vers keystore.properties et renseignez votre mot de passe."
echo "Ensuite exécutez : ./gradlew assembleRelease"
