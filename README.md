<p align="center"><img src="assets/makoff-banner.svg" alt="MAKOFF Music — Votre musique. Votre application." width="100%"></p>

<p align="center">
  <strong>Un client YouTube Music libre pour Android, avec votre bibliothèque, vos téléchargements et votre son.</strong>
</p>

<p align="center">
  <a href="https://github.com/anderson76389/Music/releases">📦 Télécharger l’APK</a> ·
  <a href="https://github.com/anderson76389/Music/actions/workflows/makoff-android.yml">🧪 Vérifications</a> ·
  <a href="https://github.com/anderson76389/Music/issues">💬 Signaler un problème</a>
</p>

## Une application pour écouter à votre façon

- **Votre musique** : recherche, albums, artistes, listes de lecture et bibliothèque ; connexion au compte YouTube Music facultative.
- **Lecture au quotidien** : arrière-plan, écran verrouillé, file d’attente, lecture aléatoire, répétition et minuterie.
- **Écoute hors connexion** : téléchargements, cache et gestion des morceaux enregistrés.
- **Fichiers audio accessibles** : mode yt-dlp 2026.08.19 embarqué pour enregistrer un morceau dans un dossier Android choisi, avec progression et annulation.
- **Son personnalisable** : qualité élevée par défaut, égaliseur et réglages de lecture.
- **Paroles et découverte** : paroles synchronisées, traduction et reconnaissance musicale selon les fournisseurs disponibles.
- **Sur vos appareils** : widgets, Android Auto et Google Cast intégré à l’APK universel.
- **Écoute partagée** : service inclus dans le dépôt, à héberger puis à configurer dans l’application.
- **Interface personnalisable** : thèmes, couleurs et préférences de présentation.

## Télécharger et installer

> **[Préversion test 21 vérifiée](https://github.com/anderson76389/Music/releases/tag/test-21-96f05ce) — un seul APK pour Android :** MAKOFF Music universel, avec Google Cast. Le logo d’origine, le lecteur YouTube Music et les deux modes de téléchargement sont conservés. [Rapport des réglages vérifiés](VERIFICATION-PARAMETRES.txt) · [Vérification de la qualité audio](QUALITE-AUDIO.txt).

| Application | Téléchargement |
| --- | --- |
| MAKOFF Music universel, avec Google Cast | [Télécharger l’APK universel — test 21](https://github.com/anderson76389/Music/releases/download/test-21-96f05ce/MAKOFF-Music-universal.apk) |

Installez directement **`MAKOFF-Music-universal.apk`** ; aucune décompression n’est nécessaire. L’archive de code source et le fichier de sommes de contrôle ne sont pas des versions supplémentaires de l’application.

**Android 8.0 minimum.** Un seul APK prend en charge ARM 32/64 bits et x86 32/64 bits.

**[Ouvrir toutes les versions et leur APK →](https://github.com/anderson76389/Music/releases)**

Dans chaque publication, ouvrez **« Assets » (fichiers joints)** et téléchargez l’unique fichier `.apk`. Autorisez son installation depuis votre navigateur ou votre gestionnaire de fichiers si Android le demande. Les anciennes publications restent disponibles dans l’historique.

### Versions de test

Chaque modification du code sur la branche principale lance les contrôles et publie automatiquement une préversion si tous réussissent. La compilation conserve les deux variantes en interne pour les tests de compatibilité, mais **un seul APK universel avec Google Cast est publié**, accompagné des sources et des sommes de contrôle SHA-256. Le numéro d’exécution et l’identifiant de révision distinguent chaque publication. Les notes décrivent les changements de l’APK et sont consultables dans le journal de l’application.

Les APK de test utilisent `fr.makoff.music.debug` et une signature temporaire propre à la construction. Ils peuvent cohabiter avec une version stable, mais ne garantissent pas une mise à jour directe entre deux tests. **Sauvegardez vos données avant de remplacer une installation de test.** Les mises à jour automatiques sont désactivées dans ces APK.

### Versions stables et mises à jour

Le circuit de publication stable produit l’APK universel signé, les sources et leurs sommes de contrôle dans les versions publiées de ce dépôt. Il nécessite la configuration de la clé privée du projet. Les futures versions doivent conserver cette clé et augmenter leur numéro de version pour permettre la mise à jour Android.

L’application stable recherche ses mises à jour exclusivement dans **ce dépôt**. Une préversion de test n’est pas présentée comme une version stable. Aucune publication stable n’est garantie tant que la signature n’est pas configurée.

## Enregistrer de vrais fichiers audio

Dans le menu d’un morceau, d’un résultat YouTube, du lecteur ou de la file d’attente, choisissez **« Enregistrer un fichier audio (yt-dlp) »**. Android vous demande un dossier au premier usage : créez ou choisissez par exemple `Téléchargements/MAKOFF Music`, puis autorisez son utilisation.

Si YouTube exige une connexion, connectez-vous à votre compte YouTube dans l’application puis relancez le téléchargement. Les restrictions du compte et du service restent applicables.

Le dossier est mémorisé. **Paramètres → Stockage → Fichiers audio — yt-dlp** permet de le changer, de suivre les tâches, de les annuler et d’ouvrir les fichiers terminés. Le mode hors connexion existant continue à utiliser son cache privé séparé.

Les fichiers gardent le meilleur flux audio disponible dans son format d’origine, généralement WebM/Opus ou M4A, sans réencodage. Il n’est pas nécessaire d’installer YTDLnis ou une autre application pour lancer le téléchargement. [Lire le guide et les limites](TELECHARGEMENTS-FICHIERS.txt).

## La qualité sonore, sans promesse artificielle

Le mode **Élevée** est sélectionné par défaut pour la lecture et le téléchargement hors connexion. Il choisit le débit le plus élevé parmi les flux audio disponibles pour la piste d’origine. Le mode **Automatique** peut utiliser une qualité inférieure sur un réseau facturé ; le mode **Basse** privilégie l’économie de données.

- **M4A** est un conteneur ; `mp4a.40.2` désigne généralement le codec **AAC-LC**.
- **WebM/Opus** est un autre format audio compressé disponible selon le flux.
- **FLAC** est un format sans perte. Convertir un flux AAC ou Opus en FLAC ne restitue pas les informations perdues : l’application ne fait pas cette conversion.
- YouTube Music documente une qualité élevée pouvant atteindre **256 kbit/s en AAC ou Opus**. Cela dépend des flux auxquels le compte donne réellement accès ; l’application ne garantit pas ce débit pour chaque morceau. [Documentation YouTube Music](https://support.google.com/youtubemusic/answer/9076559?hl=fr).

L’export en dossier utilise `bestaudio` de yt-dlp, sans réencodage. Son classement des formats peut différer du lecteur : le débit seul ne permet pas de comparer exactement deux codecs. Un débit variable n’est pas une sélection aléatoire.

Un morceau déjà téléchargé conserve son format : pour obtenir une nouvelle qualité, supprimez son téléchargement puis téléchargez-le à nouveau. Le changement de réglage ne doit pas effacer automatiquement votre copie hors connexion. Les détails du morceau indiquent le type MIME, le codec, le débit annoncé et la fréquence d’échantillonnage lorsque ces informations sont disponibles. Le casque, la sortie Android et le codec Bluetooth influencent aussi le résultat.

Les essais Android sur fichiers synthétiques AAC/M4A et Opus/WebM ont réussi : codec, 48 kHz stéréo et identité des octets après téléchargement et export. L’essai YouTube anonyme depuis le réseau GitHub a échoué ; l’accès aux flux de votre compte reste à vérifier sur votre téléphone. [Résultats des tests](https://github.com/anderson76389/Music/actions/runs/39087866925) · [Contrôles audio et limites](QUALITE-AUDIO.txt).

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

## Contributeur du projet MAKOFF Music

**[anderson76389](https://github.com/anderson76389)** — maintenance et développement de MAKOFF Music.

Pour signaler un problème, indiquez la version de l’application, votre version Android et les étapes permettant de le reproduire. Retirez toute information privée des journaux partagés.

## Licence

Projet libre sous **GPL-3.0**. La licence, les auteurs et les notices des composants d’origine sont conservés dans [LICENSE](LICENSE), [PROVENANCE.txt](PROVENANCE.txt) et les fichiers légaux embarqués. La présentation et les téléchargements de cette page concernent uniquement MAKOFF Music.
