# Fork SoloLevelingMC d'Akythera

Ce dépôt est un fork d'[Antikythera-Studios/akythera](https://github.com/Antikythera-Studios/akythera), tenu par l'équipe du mod [Solo Leveling](https://github.com/SoloLevelingMC/sololeveling). Il n'est pas maintenu par Antikythera Studios : signalez ici les problèmes propres au fork, pas sur le dépôt d'origine.

## Pourquoi ce fork

Akythera n'est publié nulle part (ni Maven, ni Modrinth, ni CurseForge) et une partie de ses fichiers ne peut pas être redistribuée. Le mod Solo Leveling doit pourtant fournir aux joueurs un jar installable sans compiler Akythera. Ce fork :

1. part d'un commit d'Akythera validé par le mod ;
2. retire les fichiers « tous droits réservés » ;
3. publie le jar Fabric sur GitHub Packages.

## Branche `sololeveling`

Branche par défaut du fork. Elle part du commit amont `2a42a97` (« Skinned armor layer and item-in-slot renderer », version `0.2.0-SNAPSHOT.4`). La branche `main` reste une copie de l'amont.

Différences avec l'amont :

- **Shaders de calcul retirés** (`common/src/main/resources/assets/akythera/shaders/compute/*.comp`, couverts par `LICENSE-ASSETS`). `fabric/build.gradle.kts` les exclut aussi du jar, au cas où une fusion de l'amont les ramènerait.
- **Garde-fou** : `ClientComponentsFactory.inspectDeviceSpec` désactive le chemin « compute » quand ces sources sont absentes. Akythera se comporte alors comme sur une carte graphique sans compute shaders : maillages animés sur le processeur, pas de simulation de tissu, pas de traînées `TrailRenderer`. Le mod Solo Leveling n'utilise aucune de ces fonctions (joueur animé par Player Animation Library et BendableCuboids, traînées d'arme propres au mod).
- **Langue réécrite** : `assets/akythera/lang/en_us.json` d'origine remplacé par nos propres textes, en anglais et en français (`fr_fr.json`). Mêmes clés.
- **Version** : `0.2.0-SNAPSHOT.4-sl.1` (version amont suivie de `-sl.<n>`). Le paquet Maven porte, comme l'amont, la version Minecraft devant : `26.2-0.2.0-SNAPSHOT.4-sl.1`.
- **Liens** (`gradle.properties`) : sources et suivi des problèmes pointent vers ce fork.
- **Publication** : dépôt Maven `GitHubPackages` dans `fabric/build.gradle.kts` et workflow `.github/workflows/publish.yml`.

Le code reste sous la licence du dépôt (`LICENSE`). `LICENSE-ASSETS` est conservé tel quel : il ne couvre plus aucun fichier de cette branche, nos traductions mises à part, qui sont les nôtres.

## Paquet publié

- Dépôt Maven : `https://maven.pkg.github.com/SoloLevelingMC/akythera`
- Coordonnées : `io.github.yesssssman.akythera:akythera-fabric:26.2-0.2.0-SNAPSHOT.4-sl.1`

GitHub Packages exige un jeton même en lecture (jeton personnel avec `read:packages`, ou `GITHUB_TOKEN` d'un dépôt autorisé dans les réglages du paquet) :

```groovy
repositories {
    maven {
        url = 'https://maven.pkg.github.com/SoloLevelingMC/akythera'
        credentials {
            username = providers.gradleProperty('gpr.user').orNull ?: System.getenv('GITHUB_ACTOR')
            password = providers.gradleProperty('gpr.key').orNull ?: System.getenv('GITHUB_TOKEN')
        }
    }
}
```

Le jar est aussi joint à chaque exécution du workflow (onglet Actions, artefact `akythera-fabric-<version>`).

## Publier une nouvelle version

1. Monter la version dans `gradle/libs.versions.toml` (`mod = "0.2.0-SNAPSHOT.4-sl.2"`, ou `<nouvelle version amont>-sl.1` après une reprise de l'amont).
2. Pousser sur `sololeveling`. Le workflow `publier` construit `:fabric`, vérifie que le jar ne contient aucun shader, puis publie si cette version n'existe pas encore (GitHub Packages refuse de republier une version).
3. Côté mod : mettre à jour le sous-module `external/akythera` et `akythera_version` dans `gradle.properties`.

En local : `./gradlew :fabric:publishAllPublicationsToGitHubPackagesRepository -PGitHubPackagesUsername=<compte> -PGitHubPackagesPassword=<jeton write:packages>`.

## Reprendre l'amont

```bash
git remote add amont https://github.com/Antikythera-Studios/akythera.git
git fetch amont
git merge <commit amont>   # sur la branche sololeveling
```

Après la fusion, vérifier que `assets/akythera/shaders/` et la langue d'origine n'ont pas été réintroduits (le workflow échoue si un shader arrive dans le jar), que le garde-fou de `ClientComponentsFactory` est toujours là, et retraduire les clés de langue nouvelles.

Ne jamais pousser vers Antikythera-Studios depuis ce fork.
