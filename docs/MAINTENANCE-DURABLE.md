# Music — vérifications de durabilité (15 novembre 2026)

Ce document distingue les garanties testées des dépendances qui ne peuvent pas être supprimées ou garanties à vie.

## Synchronisation YouTube Music

- Les likes, favoris et playlists utilisent les API YouTube Music. Les données ne sont donc **pas** hébergées par Music.
- Les instantanés servant à interpréter des suppressions distantes sont **isolés par compte** (empreinte SHA-256 de la session) et remplacés de façon atomique. Les anciens instantanés non isolés ne sont volontairement plus utilisés : une resynchronisation complète recrée les états de référence sans supprimer de favoris sur la seule base d'un ancien compte.
- Le délai d'auto-synchronisation n'est enregistré **qu'après un passage sans échec réseau détecté** et uniquement si le compte connecté n'a pas changé pendant le passage. Un échec est donc réessayable.
- Le suivi de lecture communique les événements de lecture et de progression à YouTube lorsque le compte et les flux l'autorisent. **Le comportement de l'historique et de la reprise sur les autres clients dépend de YouTube** et doit être testé avec deux appareils connectés au même compte ; un test unitaire du rapporteur ne prouve pas que YouTube accepte les rapports réels.

## SoundProof

- Fonction facultative, désactivée par défaut.
- Référentiel communautaire externe : NuclearBlox/SoundProof-Dumps. Des votes ne constituent pas une preuve d'usage de l'IA.
- Index local mis en cache, rafraîchissement manuel, recontrôle après chargement différé des artistes ; aucun nom d'artiste ni historique envoyé au serveur SoundProof.
- Si le dépôt externe disparaît, le cache local peut encore filtrer les entrées déjà connues, mais les mises à jour du référentiel deviennent indisponibles. **SoundProof n'est pas indépendant de sa source de données.**

## Sauvegardes et restauration

- La sauvegarde personnelle contient les **préférences**, la **base de données locale** et les **alias d'artistes**, pas les fichiers de musique hors ligne ; sauvegarder ces fichiers séparément.
- Si connecté, les préférences sauvegardées peuvent contenir les **cookies de connexion YouTube**. Conserver le fichier dans un endroit privé et chiffré ; ne jamais l'envoyer dans un ticket GitHub.
- Les imports et l'aperçu des sauvegardes bornent les octets réellement décompressés (jusqu'à 1 Gio au total lors de l'import) pour empêcher une archive corrompue de saturer le stockage ; la base SQLite et sa migration restent validées avant remplacement des données actives.
- Pour valider une restauration complète, utiliser une copie de test, puis vérifier les playlists, préférences et liens de compte après redémarrage.

## Publication et maintenance

- Les APK publiques sont signées avec les secrets GitHub Actions du projet. Les APK de test utilisent une autre signature ; elles ne remplacent pas une version publique.
- Conserver **hors GitHub**, dans une sauvegarde chiffrée, la clé privée originale (keystore, mot de passe et alias). Les secrets GitHub Actions ne peuvent pas être relus en clair ; perdre la clé empêcherait les mises à jour signées installables par-dessus l'existant.
- Le workflow vérifie les signatures, les APK par architecture, la compatibilité de mise à jour et les sommes SHA-256, puis publie les fichiers après les tests Android.
- L'indépendance de l'infrastructure **Metrolist** a des audits automatisés ; l'application reste dépendante de YouTube pour le catalogue/compte, de GitHub pour les publications et de SoundProof pour rafraîchir ses votes.

## Reste à confirmer sur un vrai HONOR Magic V6

L'installation de la dernière APK signée sans perte de données, une sauvegarde/restauration complète, l'historique et la progression sur deux clients YouTube Music, ainsi que le filtrage SoundProof sur les titres réellement écoutés. Une compilation verte et des émulateurs ne remplacent pas ces vérifications physiques.
