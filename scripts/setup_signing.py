#!/usr/bin/env python3
"""Configure the private release key from the owner's authenticated terminal."""
import argparse
import base64
import getpass
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

REPO = 'anderson76389/Music'
NAMES = {'MUSIC_KEYSTORE_BASE64', 'MUSIC_STORE_PASSWORD', 'MUSIC_KEY_ALIAS', 'MUSIC_KEY_PASSWORD'}


def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description='Configurer la signature permanente de Metrolist dans les secrets GitHub.')
    parser.add_argument('--resume-upload', action='store_true', help='Reprendre un envoi interrompu avec la même clé locale ; ne jamais utiliser pour changer de clé.')
    args = parser.parse_args()
    for tool in ('keytool', 'gh'):
        if not shutil.which(tool):
            raise RuntimeError(f'{tool} manque. Utilisez un Codespace avec Java et GitHub CLI.')
    run(['gh', 'auth', 'status'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    result = run(['gh', 'secret', 'list', '--repo', REPO, '--json', 'name'], capture_output=True, text=True)
    existing = {item['name'] for item in json.loads(result.stdout)} & NAMES
    directory = Path.home() / '.local' / 'share' / 'metrolist-signing'
    keystore = directory / 'metrolist-release.p12'
    if existing and not args.resume_upload:
        raise RuntimeError('Des secrets de signature existent déjà. Aucune clé remplacée. Si cet outil a été interrompu pendant son premier envoi, relancez-le avec --resume-upload depuis le même Codespace.')
    if args.resume_upload and not keystore.is_file():
        raise RuntimeError('Reprise impossible : la clé locale originale est absente. Ne créez pas une nouvelle clé à sa place.')
    os.umask(0o077)
    directory.mkdir(parents=True, exist_ok=True)
    directory.chmod(0o700)
    print('La clé sera conservée dans :', keystore)
    print('Choisissez un mot de passe unique à conserver dans votre gestionnaire de mots de passe.')
    password = getpass.getpass('Mot de passe de la clé (12 caractères minimum) : ')
    if len(password) < 12 or '\n' in password or '\r' in password:
        raise RuntimeError('Mot de passe invalide ; aucun secret envoyé.')
    if password != getpass.getpass('Confirmez le mot de passe : '):
        raise RuntimeError('Les mots de passe diffèrent ; aucun secret envoyé.')
    env = dict(os.environ, METROLIST_SIGNING_PASSWORD=password)
    if not keystore.exists():
        temporary = directory / 'creation.p12'
        if temporary.exists():
            raise RuntimeError('Une création interrompue existe : conservez ce fichier et vérifiez-le avant de recommencer.')
        run(['keytool', '-genkeypair', '-noprompt', '-storetype', 'PKCS12',
             '-keystore', str(temporary), '-storepass:env', 'METROLIST_SIGNING_PASSWORD',
             '-keypass:env', 'METROLIST_SIGNING_PASSWORD', '-alias', 'metrolist',
             '-keyalg', 'RSA', '-keysize', '4096', '-validity', '10000',
             '-dname', 'CN=Metrolist,O=anderson76389'], env=env)
        temporary.rename(keystore)
    keystore.chmod(0o600)
    # Validate the password and alias before changing any GitHub secret.
    run(['keytool', '-list', '-keystore', str(keystore), '-storepass:env',
         'METROLIST_SIGNING_PASSWORD', '-alias', 'metrolist'], env=env,
        stdout=subprocess.DEVNULL)
    certificate = run(['keytool', '-exportcert', '-rfc', '-keystore', str(keystore),
                       '-storepass:env', 'METROLIST_SIGNING_PASSWORD', '-alias', 'metrolist'],
                      env=env, capture_output=True).stdout
    (directory / 'metrolist-certificate.pem').write_bytes(certificate)
    values = {'MUSIC_KEYSTORE_BASE64': base64.b64encode(keystore.read_bytes()),
              'MUSIC_STORE_PASSWORD': password.encode(), 'MUSIC_KEY_ALIAS': b'metrolist',
              'MUSIC_KEY_PASSWORD': password.encode()}
    for name, value in values.items():
        # Send via stdin: no password/key in command arguments, shell history or output.
        run(['gh', 'secret', 'set', name, '--repo', REPO], input=value,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print(name + ' : enregistré')
    print('\nSignature permanente configurée. Ne régénérez pas cette clé.')
    print('Téléchargez une sauvegarde privée de', keystore)
    print('Gardez son mot de passe séparément. GitHub ne permet pas de relire les secrets.')
    print('Ne placez jamais cette clé dans le dépôt, une Release ou un message.')
    print('Pour lancer la publication signée :')
    print('gh workflow run music-release.yml --repo ' + REPO + ' --ref main -f tag=v13.7.0')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, OSError, KeyboardInterrupt) as error:
        if isinstance(error, subprocess.CalledProcessError):
            print('Échec de la commande. Aucun secret affiché. Vérifiez les droits GitHub sur ce dépôt. Si l’envoi a commencé, conservez la clé et utilisez --resume-upload.', file=sys.stderr)
        else:
            print(str(error), file=sys.stderr)
        sys.exit(1)
