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
- **Code du calcul GPU retiré** : sans les shaders, ce code ne pouvait plus s'exécuter. Ont été supprimés les backends de calcul OpenGL et Vulkan (`client/core/shader/**`), le skinning et la simulation de tissu sur GPU (`client/core/physics/cloth/**`, `ClothModelComponent`), les traînées (`TrailRenderer`, `AkytheraTrailParticle`, `AkytheraTrailParticleGroup`, `TrailParticleBlueprint` et le type de particule `akythera:trail`), le `CameraComponent` vide de `client/api/camera` et le mixin `MixinPreparedFrame`. `PatchModelRenderer` ne garde que la collecte des rendus. `ClientComponentsFactory` journalise l'API graphique et répond toujours « pas de calcul GPU » (`supportComputeShader()` et `supportClothSimulation()` à `false`). Les maillages animés passent par le processeur (`SkeletalMesh#drawPosed`) ; un maillage porteur de tissu n'est pas dessiné (ses données sont toujours lues). Le mod Solo Leveling n'utilise aucune de ces fonctions (joueur animé par Player Animation Library et BendableCuboids, traînées d'arme propres au mod). Le `CameraComponent` du visualiseur (`client/api/gui/viewer`) est conservé : il sert.
- **Module NeoForge retiré** : projet `:neoforge`, ModDevGradle, NeoForm, dépôt NeoForged, `accesstransformer.cfg` et `version.json` (26.1-snapshot-7, lu par aucun script). `:common` compile avec Fabric Loom (même jar Minecraft et même access widener que `:fabric`) : plus de décompilation NeoForm à chaque `includeBuild` du mod.
- **Dépendances déclarées** (`fabric/src/main/resources/fabric.mod.json`) : `forgeconfigapiport` (version du catalogue, appelé sans condition par `AkytheraFabricClient`) et `player_animation_library` (>= 1.2.6) en `depends`. BendableCuboids est un mod client qu'Akythera n'appelle pas : il est en `suggests`, pour qu'un serveur dédié démarre sans lui.
- **Versions alignées sur le mod** : Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Loom 1.18.4 (épinglé, plus de SNAPSHOT), Gradle 9.7.1 (exigé par Loom 1.18), Mixin et ASM aux versions livrées par Fabric Loader.
- **Accès fragiles isolés** : `ArmorRendererRegistryImpl` (interne à Fabric API, sans équivalent public en 0.161.0) n'est appelé que depuis `AkytheraFabricClient#armorRendererOf` ; `PlayerAnimBone.bend` (déprécié pour suppression dans PAL) n'est écrit que depuis `PlayerAnimationLibraryPlugin#setBend`. À revoir à chaque mise à jour de Fabric API ou de PAL.
- **Langue réécrite** : `assets/akythera/lang/en_us.json` d'origine remplacé par nos propres textes, en anglais et en français (`fr_fr.json`). Mêmes clés.
- **Version** : `0.2.0-SNAPSHOT.4-sl.2` (version amont suivie de `-sl.<n>`). Le paquet Maven porte, comme l'amont, la version Minecraft devant : `26.2-0.2.0-SNAPSHOT.4-sl.2`.
- **Couche joueur (Player Animation Library)** : `PALPoseBinder` règle le poids de la couche (toujours plein, ou fondu des montages), un masque par partie mélangé avec la pose vanilla (`PartWeights`, pose d'avant la couche lisible par `AkytheraAnimationLayer.vanillaPose`), l'orientation du corps (`BodyYaw` : aucune, regard, valeur fournie), des corrections par partie (`PartCorrection`) et les parties qui suivent la flexion d'un os (`bend_carries`, tête et bras avec le buste) ; la flexion est aussi donnée aux os de bras sans partie de modèle (objet tenu de BendableCuboids). Vitesse par montage (`Animator#playAnimMontage(clé, section, vitesse)`, champ `playRate` du paquet de lecture) et échelle de temps par patch (`EntityPatch#setAnimationTimeScale`, pause à l'impact).
- **Capacités et ressources** (API étendue pour le mod, rétrocompatible) :
  - une capacité construite en Java (sans clé de plan) n'est plus forcée `SERVER_ONLY` : son emplacement la nomme sur le réseau, et les clients que sa portée atteint y tiennent une `MirroredAbility` qui suit le cycle de vie et les recharges (`AbilitySystemComponent#getCooldown(AbilityInstance)`), jamais l'état. Le client n'écrit toujours rien côté serveur sans `serverBoundCodec` ;
  - `AbilityState` : méthodes de synchro par défaut « sans synchro », un état purement serveur ne déclare que ses champs ;
  - `Ability#tick`, appelé chaque tick serveur pour les capacités actives ou à état ;
  - `AbilityResource` : `max`, régénération (`regeneration()`, délai après dépense, montant par tick, suspension) et envoi au propriétaire (`syncThreshold()`, paquet `akythera:ability_resource_sync`, lu par `AbilitySystemComponent#getResourceValue` et `getResourceMax`) ;
  - le joueur qui rejoint un niveau (changement de dimension) reçoit de nouveau ses propres capacités, recharges et ressources ;
  - format modifié de `akythera:ability_equipment_sync` (champ `held`) et `akythera:ability_effect_sync` (type, durée, cumul) : client et serveur doivent avoir le même jar.
- **Liens** (`gradle.properties`) : sources et suivi des problèmes pointent vers ce fork.
- **Publication** : dépôt Maven `GitHubPackages` dans `fabric/build.gradle.kts` et workflow `.github/workflows/publish.yml`.

Le code reste sous la licence du dépôt (`LICENSE`). `LICENSE-ASSETS` est conservé tel quel : il ne couvre plus aucun fichier de cette branche, nos traductions mises à part, qui sont les nôtres.

## Paquet publié

- Dépôt Maven : `https://maven.pkg.github.com/SoloLevelingMC/akythera`
- Coordonnées : `io.github.yesssssman.akythera:akythera-fabric:26.2-0.2.0-SNAPSHOT.4-sl.2`

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

Après la fusion, vérifier que `assets/akythera/shaders/` et la langue d'origine n'ont pas été réintroduits (le workflow échoue si un shader arrive dans le jar), et retraduire les clés de langue nouvelles. Les fichiers retirés par le fork (module `neoforge`, code du calcul GPU, du tissu et des traînées) reviendront en conflit « modifié chez eux, supprimé chez nous » : garder la suppression, et reporter dans le chemin processeur ce qui a changé ailleurs. Un appel nouveau à `getAnimationComputeShader`, `ComputeBackends` ou `TrailRenderer` ne compilera plus : le retirer ou le remplacer par le chemin processeur.

Ne jamais pousser vers Antikythera-Studios depuis ce fork.
