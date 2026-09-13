# Architecture des tests — journal évolutif

Ce document complète [archi_test_ini.md](archi_test_ini.md), qui décrit un **état figé** (la photo initiale du projet). Celui-ci est fait pour être **enrichi au fil du développement des tests** : nouvelles décisions, nouveaux outils, nouveaux chiffres de couverture/mutation, au fur et à mesure qu'on avance.

## Pourquoi ajouter le mutation testing (PIT) en plus de JaCoCo ?

JaCoCo répond à la question *"quelles lignes mon code exécute-t-il pendant les tests ?"*. Il ne répond pas à *"si ce code était cassé, mes tests s'en rendraient-ils compte ?"*. Une ligne peut être exécutée à 100 % par un test qui ne vérifie rien de pertinent sur son résultat — JaCoCo la comptera quand même comme couverte.

### L'exemple qui a motivé ce choix

Dans [`UserServiceTest.test_create_user`](../src/test/java/com/openclassrooms/etudiant/service/UserServiceTest.java), la version originale du test ressemblait à ceci :

```java
when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD); // stub qui renvoie la même valeur que l'entrée
...
ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
verify(userRepository).save(userCaptor.capture());
assertThat(userCaptor.getValue()).isEqualTo(user); // compare l'objet à lui-même
```

`UserService.register()` ne crée pas de nouvel objet : il mute `user` sur place (`user.setPassword(...)`) puis sauvegarde cette même instance. Donc `userCaptor.getValue()` **est** `user` (même référence mémoire). L'assertion `isEqualTo(user)` compare l'objet à lui-même — elle est vraie quoi qu'il arrive, y compris si on supprime complètement l'appel à `passwordEncoder.encode(...)` dans le code de production. JaCoCo affichait pourtant cette ligne comme couverte à 100 %.

Version corrigée, effectivement testée :

```java
when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD); // valeur différente de l'entrée, donc observable
...
verify(userRepository).save(user); // register() mute `user` en place : c'est bien la même instance qui est sauvegardée
assertThat(user.getPassword()).isEqualTo(ENCODED_PASSWORD); // prouve que l'encodage a réellement eu lieu
```

Le changement clé : le stub renvoie désormais une valeur **distincte** de l'entrée, et l'assertion porte sur cette valeur précise plutôt que sur l'identité de l'objet. Si quelqu'un supprime l'appel à `encode(...)` demain, ce test échoue — l'ancien ne l'aurait jamais détecté.

C'est exactement ce que **PIT** automatise et généralise à toute la base de code : il modifie le bytecode compilé pour introduire des **mutants** (ex. supprimer un appel de méthode, inverser une condition, changer une valeur de retour), puis rejoue la suite de tests sur chaque mutant.

- Mutant **tué** (*killed*) : au moins un test échoue → le test détecte bien ce changement de comportement.
- Mutant **survivant** (*survived*) : tous les tests passent malgré le bug injecté → zone testée en apparence, mais pas réellement vérifiée.

Le "test strength" de PIT (score de mutation) est donc un indicateur plus exigeant que le pourcentage de couverture de JaCoCo, sur le même code.

## Configuration retenue

Ajout dans [`pom.xml`](../pom.xml) du plugin `org.pitest:pitest-maven` (1.30.0) avec le sous-plugin `pitest-junit5-plugin` (1.2.3, nécessaire car le projet utilise JUnit 5) :

```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.30.0</version>
    <dependencies>
        <dependency>
            <groupId>org.pitest</groupId>
            <artifactId>pitest-junit5-plugin</artifactId>
            <version>1.2.3</version>
        </dependency>
    </dependencies>
    <configuration>
        <targetClasses>
            <param>com.openclassrooms.etudiant.service.*</param>
        </targetClasses>
        <targetTests>
            <param>com.openclassrooms.etudiant.service.*</param>
        </targetTests>
    </configuration>
</plugin>
```

Le périmètre (`targetClasses`/`targetTests`) est volontairement limité au package `service` pour l'instant — c'est le seul qui a des tests unitaires à ce jour (voir [Ce qui n'est pas couvert](archi_test_ini.md#ce-qui-nest-pas-couvert) dans l'état initial). À élargir au fur et à mesure que `StudentService`, `JwtService`, etc. seront couverts par des tests.

**Lancer une analyse** :

```bash
mvn org.pitest:pitest-maven:mutationCoverage
```

Rapport HTML généré dans `target/pit-reports/<horodatage>/index.html`.

Contrairement à JaCoCo (branché sur `mvn test`, donc systématique), PIT n'est **pas** exécuté automatiquement à chaque build — il est nettement plus lent (il rejoue les tests une fois par mutant). À lancer ponctuellement, pas en continu.

## Premier résultat (baseline, 2026-09-12)

| Indicateur | Valeur |
| --- | --- |
| Classes analysées | `com.openclassrooms.etudiant.service.*` |
| Couverture de lignes (classes mutées) | 10/61 (16 %) |
| Mutations générées | 33 |
| Mutations tuées | 3 (9 %) |
| Test strength (sur le code couvert) | 100 % |

Le score global est bas, mais c'est cohérent avec un fait déjà connu : `UserService.login()` n'a aucun test (voir [archi_test_ini.md](archi_test_ini.md#ce-qui-nest-pas-couvert)), donc tous les mutants qui y sont injectés sont `NO_COVERAGE`, pas `SURVIVED` — PIT ne fait ici que confirmer un trou déjà identifié, pas en révéler un nouveau. Le "test strength" de 100 % indique qu'**une fois qu'un mutant est atteint par un test**, il est bien tué — c'est le chiffre à surveiller en priorité au fur et à mesure qu'on ajoute des tests sur `login()`.

## Journal des évolutions

- **2026-09-12** — Ajout du plugin PIT (mutation testing) et de JaCoCo (couverture) dans `pom.xml`. Correction de `UserServiceTest.test_create_user` : l'assertion `isEqualTo(user)` (auto-comparaison, ne vérifiait rien) remplacée par une vérification explicite du mot de passe encodé. Configuration VS Code (`.vscode/settings.json`) pour lancer la couverture directement depuis le Test Explorer. SonarLint activé en local pour l'analyse statique continue.
- **2026-09-13** — Implémentation du [plan de tests](plan_de_tests.md) côté back-end : `JwtServiceTest` et `StudentServiceTest` (nouveaux, unitaires), complément de `UserServiceTest` (`login()`) et de `UserControllerTest` (`/api/login`), et nouveau `StudentControllerTest` (11 cas, CRUD + sécurité, jeton obtenu via un vrai aller-retour `/api/login` plutôt qu'un token construit à la main). Activation du seuil de couverture JaCoCo à 80 % (`check` bound à la phase `verify`, jusque-là seul le reporting était en place) — couverture de lignes mesurée à 90,2 %. Effet de bord notable : PIT (dont le périmètre est `com.openclassrooms.etudiant.service.*`) mutation-teste désormais aussi `JwtService` et `StudentService` sans configuration supplémentaire. Correction en cours de route d'un environnement cassé : Lombok 1.18.32 est incompatible avec le build JDK 25 résolu par défaut sur la machine (`NoSuchFieldException: TypeTag :: UNKNOWN`) — `~/.mavenrc` épingle désormais Maven sur le JDK 21 déjà installé, cohérent avec le `<java.version>21</java.version>` du projet.
- **2026-09-12** — Ajout d'une CI GitHub Actions ([`.github/workflows/ci.yml`](../.github/workflows/ci.yml)) : exécute la suite de tests à chaque push/PR sur `main`, génère un badge de couverture, et publie le rapport JaCoCo sur GitHub Pages — visible publiquement sans dépendre de rapports committés dans le repo (donc toujours à jour avec le dernier commit poussé, contrairement à un rapport généré localement puis committé à la main). Ajout d'un hook `pre-commit` versionné dans `.githooks/` (à activer via `git config core.hooksPath .githooks`) qui lance uniquement les tests unitaires (exclut `*ControllerTest`, basé sur Testcontainers/Docker, trop lent pour un hook local) — garde-fou rapide côté développeur, complémentaire à la CI qui, elle, rejoue la suite complète et fait foi pour un tiers (mentor/examinateur).
