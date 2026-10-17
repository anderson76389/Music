# Audit d’indépendance de MAKOFF Music

## Ce qui appartient au projet

- Identifiant Android `fr.makoff.music` et nom propre. Logo d’origine volontairement conservé localement à la demande du propriétaire.
- Téléchargements, mises à jour et publications sur `anderson76389/Music`.
- Sources InnerTubeX embarquées dans `vendor/innertubex`, sans téléchargement du moteur depuis le dépôt d’origine.
- Sources et protocole du serveur d’écoute partagée inclus dans `services/listen-together` ; aucun serveur public présélectionné.
- Discord retiré du code, des écrans et des ressources de l’application.
- Configuration distante du moteur désactivée : `enabled = false`, adresse vide et résolution locale EJS conservée.

## Références à Metrolist encore présentes

| Emplacement | Nature | Effet actuel |
| --- | --- | --- |
| `LICENSE`, `PROVENANCE.txt`, notices et en-têtes | Origine, auteurs et licences | Attribution conservée ; ne constitue pas une dépendance de fonctionnement |
| Espaces de noms `com.metrolist` et noms internes | Organisation historique du code | Code local ; aucun appel réseau déclenché par ces noms |
| `gradle/libs.versions.toml`, `settings.gradle.kts` | Coordonnées propres `fr.makoff.music:innertubex` et substitution locale | Ancien groupe Maven remplacé ; la construction utilise les sources embarquées |
| Métadonnées de publication de `vendor/innertubex` | Groupe et liens de publication de Music ; auteurs d’origine conservés | Ne publie pas le composant automatiquement et ne sert pas de canal de mise à jour Android |
| `GitHubPlayerConfigClient.kt`, `RemotePlayerConfigStore.kt` dans le moteur embarqué | Anciennes adresses Faraday et règles de compatibilité | Chemin distant désactivé par la configuration de l’application ; chaînes encore présentes dans les sources |
| Tests du moteur | Adresses de données de test | Aucun service de production imposé |
| Fichiers `.github/workflows/*.yml.disabled` | Anciennes procédures archivées | Non exécutées par GitHub Actions |
| Guides et documents historiques | Références à l’origine | Ne servent ni de téléchargement ni de configuration à l’application |

Il ne s’agit donc pas d’un effacement de toute occurrence du nom dans les sources. Le README et les liens de distribution utilisent l’identité propre du projet ; les attributions sont conservées et les anciennes chaînes techniques sont identifiées ici. Réactiver la configuration distante du moteur demanderait un nouvel audit.

## Dépendances externes qui restent nécessaires

YouTube Music reste la source des flux et des données. Google Cast, les paroles, la traduction, la reconnaissance ou Last.fm dépendent des services et clés correspondants. Les outils de construction téléchargent aussi leurs dépendances Android habituelles. Aucun fork ne peut garantir qu’un changement de YouTube n’exigera jamais de maintenance.

## Publication et essais

Les préversions de test ne sont publiées qu’après compilation des deux APK, tests unitaires, tests du serveur et installation/démarrage sur émulateur. Ce contrôle de démarrage ne prouve pas la lecture réelle, le téléchargement hors connexion, la connexion au compte, Google Cast ou Android Auto : ces scénarios nécessitent des essais complémentaires sur appareils et comptes appropriés.

Une publication stable requiert les secrets de signature `MUSIC_KEYSTORE_BASE64`, `MUSIC_STORE_PASSWORD`, `MUSIC_KEY_ALIAS`, `MUSIC_KEY_PASSWORD`. La clé doit rester privée et être sauvegardée durablement. L’écoute partagée requiert l’hébergement du serveur inclus et la même adresse configurée chez les participants.

Le contrôle reproductible des principaux points est disponible avec :

```bash
python3 scripts/audit_independence.py
```

## Résultats constatés le 17 octobre 2026 (heure française)

Exécution [38977059046](https://github.com/anderson76389/Music/actions/runs/38977059046), sources `b69f7e39fb291d2ee2e0634b482ade992650e2fb` :

- Compilation des APK FOSS et Google Cast et tests unitaires : **réussis**.
- Tests et compilation du serveur : **réussis**.
- Installation et ouverture des deux APK sur émulateur Android 11 : **réussies**, activité principale au premier plan, processus vivant, aucun plantage du processus détecté pendant l’observation.
- Captures et journaux : conservés dans le fichier de construction `MAKOFF-tests-demarrage`.
- Publication dans Releases : **échec**, GitHub renvoie `HTTP 403: Resource not accessible by integration` sur la création de la version, malgré la permission `contents: write` visible dans le job. Aucune Release n’a été créée. Il reste à résoudre les droits ou restrictions de publication du dépôt ; changer le YAML pour déclarer cette même permission ne suffit pas à établir la cause.
- Les secrets de signature stable ne sont pas configurés dans le cadre de ces essais. Les APK validés sont des versions de test à signature temporaire.

La vérification automatisée ne valide pas la lecture sonore réelle, le téléchargement hors connexion, la connexion au compte ou Cast. Le contrôle d’installation/démarrage des futures versions stables est configuré, mais n’a pas encore été exécuté sur des APK de publication signés.

## Nouvel audit et logo conservé

Le [rapport du 17 octobre 2026](../AUDIT-METRO-2026-10-17.txt) détaille les corrections supplémentaires, le logo restauré, les adresses Faraday encore inactives, les noms internes et les dépendances tierces. Le contrôle local vérifie désormais aussi les 31 empreintes du logo et les coordonnées propres du moteur.

## Résultats du nouvel audit avec logo restauré

Exécution [39065526320](https://github.com/anderson76389/Music/actions/runs/39065526320), sources `111f10e159e0d67f638485b12d50ac9ef96b91e2` : compilation FOSS/GMS, tests unitaires Android, `apiCheck` du moteur, tests du serveur et installation/démarrage des deux APK sur Android 11 **réussis**. La [préversion test 9](https://github.com/anderson76389/Music/releases/tag/test-9-111f10e) a été publiée avec les deux APK, les sources et les sommes de contrôle. Le refus 403 de l’ancien essai ne s’est pas reproduit ; sa cause n’est pas établie ici. Les résultats de l’ancien essai ci-dessus restent un historique, pas l’état actuel de la publication.

Ces APK gardent une signature de test temporaire. Les mises à jour stables signées, la lecture sonore réelle, Cast et les salons entre appareils ne sont pas validés par ces essais.
