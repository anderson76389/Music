<p align="center"><img src="assets/makoff-banner.svg" alt="MAKOFF Music — Votre musique. Votre application." width="100%"></p>

<p align="center">
  <strong>Un client YouTube Music libre pour Android, avec votre bibliothèque, vos téléchargements et votre son.</strong>
</p>

<p align="center">
  <a href="https://github.com/anderson76389/Music/releases">📦 Télécharger les APK</a> ·
  <a href="https://github.com/anderson76389/Music/actions/workflows/makoff-android.yml">🧪 Vérifications</a> ·
  <a href="https://github.com/anderson76389/Music/issues">💬 Signaler un problème</a>
</p>

## Une application pour écouter à votre façon

- **Votre musique** : recherche, albums, artistes, playlists et bibliothèque ; connexion au compte YouTube Music facultative.
- **Lecture au quotidien** : arrière-plan, écran verrouillé, file d’attente, lecture aléatoire, répétition et minuterie.
- **Écoute hors connexion** : téléchargements, cache et gestion des morceaux enregistrés.
- **Son personnalisable** : qualité élevée par défaut, égaliseur et réglages de lecture.
- **Paroles et découverte** : paroles synchronisées, traduction et reconnaissance musicale selon les fournisseurs disponibles.
- **Sur vos appareils** : widgets, Android Auto et Google Cast avec la variante compatible.
- **Écoute partagée** : service inclus dans le dépôt, à héberger puis à configurer dans l’application.
- **Interface personnalisable** : thèmes, couleurs et préférences de présentation.

## Télécharger et installer

> **Nouvelle préversion disponible :** [MAKOFF Music — test 9](https://github.com/anderson76389/Music/releases/tag/test-9-111f10e), avec le logo d’origine conservé. Les deux APK ont été compilés, installés et ouverts sur émulateur Android 11. Les tests unitaires, le contrôle d’API du moteur et les tests du serveur ont réussi.

| APK de test vérifié | Téléchargement direct |
| --- | --- |
| Standard, sans Google Cast | [Télécharger l’APK standard](https://github.com/anderson76389/Music/releases/download/test-9-111f10e/MAKOFF-Music-test.apk) |
| Avec Google Cast | [Télécharger l’APK Google Cast](https://github.com/anderson76389/Music/releases/download/test-9-111f10e/MAKOFF-Music-Google-Cast-test.apk) |

Installez directement le fichier `.apk` ; aucune décompression n’est nécessaire pour les téléchargements ci-dessus. [Toutes les versions](https://github.com/anderson76389/Music/releases) · [Résultats des tests](https://github.com/anderson76389/Music/actions/runs/39065526320) · [Captures et journaux du démarrage](https://github.com/anderson76389/Music/actions/runs/39065526320/artifacts/12113555838).

**Android 8.0 minimum.** Les APK sont universels : ARM 32/64 bits et x86 32/64 bits.

| Variante | Fichier dans les versions stables | Pour qui ? |
| --- | --- | --- |
| Standard, sans Google Cast | `MAKOFF-Music.apk` | Écouter et télécharger sans intégrer Google Cast |
| Avec Google Cast | `MAKOFF-Music-Google-Cast.apk` | Diffuser aussi vers un appareil Google Cast compatible |

**[Ouvrir toutes les versions et leurs APK →](https://github.com/anderson76389/Music/releases)**

Dans chaque version, ouvrez **« Assets » (fichiers joints)** et téléchargez le fichier `.apk` voulu. Autorisez son installation depuis votre navigateur ou votre gestionnaire de fichiers si Android le demande. Les deux variantes utilisent le même identifiant : choisissez celle qui vous convient.

### Versions de test

Chaque envoi sur la branche principale déclenche la compilation des deux variantes, les tests unitaires, les contrôles du serveur et un test d’installation et de démarrage sur émulateur. **La publication de la préversion exige la réussite de ces contrôles et les droits GitHub nécessaires.** Le circuit a publié la préversion test 9 après réussite des contrôles. Son nom contient le numéro d’exécution et le commit ; elle fournit les deux APK, les sources et les sommes de contrôle SHA-256.

Les APK de test utilisent `fr.makoff.music.debug` et une signature temporaire propre à la construction. Ils peuvent cohabiter avec une version stable, mais ne garantissent pas une mise à jour directe entre deux tests. **Sauvegardez vos données avant de remplacer une installation de test.** Les mises à jour automatiques sont désactivées dans ces APK.

### Versions stables et mises à jour

Le circuit de publication stable produit les deux APK signés, les sources et leurs sommes de contrôle dans les Releases de ce dépôt. Il nécessite la configuration de la clé privée du projet. Les futures versions doivent conserver cette clé et augmenter leur numéro de version pour permettre la mise à jour Android.

L’application stable recherche ses mises à jour exclusivement dans **ce dépôt**. Une préversion de test n’est pas présentée comme une version stable. Aucune publication stable n’est garantie tant que la signature n’est pas configurée.

## La qualité sonore, sans promesse artificielle

Le mode **Élevée** est sélectionné par défaut pour la lecture et les téléchargements. Les modes Automatique et Basse restent disponibles. L’application choisit parmi les flux réellement fournis par YouTube Music ; elle ne transforme pas un fichier compressé en audio sans perte. La disponibilité des flux dépend du morceau, du compte et des droits associés. Le casque, la sortie Android et le codec Bluetooth influencent aussi le résultat.

## Un projet géré dans ce dépôt

Le code du moteur de lecture et celui du serveur d’écoute partagée sont inclus dans les sources. L’identité Android, les liens de publication et le canal de mises à jour appartiennent à MAKOFF Music. Aucun serveur d’écoute partagée n’est imposé. L’intégration Discord a été retirée.

L’application reste dépendante des services YouTube Music et des fournisseurs tiers activés. Un changement de ces services peut demander une correction du moteur. Les références techniques héritées et les notices légales sont détaillées dans le [rapport d’indépendance](docs/AUDIT-INDEPENDANCE.md).

## Construire depuis les sources

Prérequis : Android Studio, JDK 21, SDK Android 37.0 et outils de compilation 36.0.0. Configurez le SDK dans Android Studio et créez une clé de débogage locale si elle n’existe pas.

```bash
./gradlew :app:assembleFossDebug :app:assembleGmsDebug :app:testFossDebugUnitTest
python3 scripts/audit_independence.py
```

Sous Windows, utilisez `gradlew.bat`. Les APK sont générés dans `app/build/outputs/apk/foss/debug/` et `app/build/outputs/apk/gms/debug/`.

| Dossier ou guide | Contenu |
| --- | --- |
| `app/` | Application Android |
| `vendor/innertubex/` | Moteur de lecture embarqué |
| `services/listen-together/` | Serveur d’écoute partagée et instructions d’hébergement |
| [Guide de démarrage](DEMARRER-ICI.txt) | Installation et premiers repères |
| [Guide technique](LIRE-MOI-MAKOFF.txt) | Construction, signature et essais sur téléphone |
| [Audit d’indépendance](docs/AUDIT-INDEPENDANCE.md) | Références restantes et limites vérifiées |

## Contribuer

Pour signaler un problème, indiquez la version, la variante de l’APK, votre version Android et les étapes permettant de le reproduire. Retirez toute information privée des journaux partagés.

## Licence

Projet libre sous **GPL-3.0**. La licence, les auteurs et les notices des composants d’origine sont conservés dans [LICENSE](LICENSE), [PROVENANCE.txt](PROVENANCE.txt) et les fichiers légaux embarqués. La présentation et les téléchargements de cette page concernent uniquement MAKOFF Music.
