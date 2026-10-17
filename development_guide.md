# Développer MAKOFF Music

```bash
git clone https://github.com/anderson76389/Music.git
cd Music
```

Ouvrir le dossier dans Android Studio. Installer le JDK 21, le SDK Android 37.0 et les outils de compilation 36.0.0. Renseigner le SDK dans `local.properties` si nécessaire et créer une clé de débogage locale avec Android Studio.

```bash
python3 scripts/audit_independence.py
./gradlew :app:assembleFossDebug :app:assembleGmsDebug :app:testFossDebugUnitTest :innertubex:apiCheck
```

Sous Windows, utiliser `gradlew.bat`. Le moteur local est dans `vendor/innertubex` : il est substitué par Gradle à la coordonnée `fr.makoff.music:innertubex`. Aucun dépôt du projet d’origine n’est nécessaire pour récupérer ces sources.

Le logo d’origine est volontairement conservé. `assets/logo-origine.sha256` enregistre ses empreintes locales ; le contrôle d’indépendance vérifie leur intégrité. Ne pas changer la version de l’application ni le schéma de base de données pour un simple changement graphique.

Les publications et mises à jour utilisent uniquement ce dépôt. Voir `LIRE-MOI-MAKOFF.txt` pour la signature, et `services/listen-together/INSTALLATION-MUSIC.txt` pour l’hébergement de l’écoute partagée. Conserver les licences et les attributions.
