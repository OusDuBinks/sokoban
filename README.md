# Projet de Planification Automatique - Solveur Sokoban

**Membres du groupe :** Osama Lababidi, Arthur Laumay, Adrien Van-Robays

## Description du projet
Ce projet implémente un agent autonome capable de résoudre des niveaux du jeu Sokoban en modélisant le problème en PDDL (Planning Domain Definition Language) et en utilisant le planificateur heuristique PDDL4J.

## Fichiers créés ou modifiés

Conformément aux consignes, voici la liste exhaustive des fichiers que nous avons créés ou modifiés pour mener à bien ce projet :

### 1. Modélisation PDDL

*   **`pddl/sokoban.pddl`** : Fichier de domaine PDDL définissant les types (`cell`, `direction`), les prédicats (`at-player`, `at-box`, `clear`, `safe`, `adjacent`), et les deux actions possibles (`move` et `push`). La modélisation utilise le fragment STRIPS pur pour optimiser les performances de recherche[cite: 2].
*   **`pddl/test1.pddl` à `test30.pddl`** : Fichiers de problèmes générés automatiquement représentant les 30 niveaux de test sous forme d'états initiaux et de buts PDDL.

### 2. Code Java (Agent et Planificateur)

*   **`src/main/java/sokoban/Level.java`** : Classe responsable de la traduction de la grille d'entrée standard (caractères `#`, `*`, `.`, etc.) en code PDDL compréhensible par le solveur. Implémentation d'un algorithme de type *flood fill* pour identifier les cases mortes (`dead-ends`) et définir le prédicat `safe`, réduisant drastiquement l'espace de recherche.
*   **`src/main/java/sokoban/Planner.java`** : Interface avec PDDL4J, exécutée dans un processus séparé pour éviter les conflits de *classloader*. Configuration de l'algorithme A* (`ASTAR`) et de l'heuristique `FAST_FORWARD`.
*   **`src/main/java/sokoban/Agent.java`** : Boucle principale du joueur, responsable de la lecture de l'état initial, de l'appel au planificateur, de la récupération du plan de résolution (U, D, L, R) et de son exécution tour par tour.
*   **`src/main/java/sokoban/Bench.java`** : Outil de banc d'essai permettant de générer les 30 problèmes PDDL (`gen`) et de tester le planificateur sur l'ensemble des niveaux (`run`) afin de produire les statistiques de réussite.

*(Note : Les fichiers composant le moteur du jeu de base tels que `Board.java`, `Box.java`, `Cell.java`, `Pusher.java` et `SokobanMain.java` ont été conservés dans leur état d'origine pour garantir l'intégrité de l'environnement graphique).*

## Compilation et Exécution

Avant de lancer les tests ou l'interface du jeu, il est nécessaire de compiler le projet et de générer l'archive `.jar` avec Maven :
```bash
mvn clean package
```

### 1. Lancement du banc d'essai (Statistiques globales)

Pour exécuter le banc d'essai complet (sans interface) et générer le tableau des résultats :

```bash
java -cp target/sokoban-1.0-SNAPSHOT-jar-with-dependencies.jar sokoban.Bench run

```

### 2. Lancement du jeu avec interface graphique (Test individuel)

Pour lancer un niveau spécifique avec le moteur graphique local, exécutez la commande suivante (qui gère l'ouverture des modules Java nécessaires à la réflexion) :

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED -server -Xms2048m -Xmx2048m -cp target\sokoban-1.0-SNAPSHOT-jar-with-dependencies.jar sokoban.SokobanMain

```

**Pour changer le niveau testé visuellement :**

1. Ouvrez le fichier `src/main/java/sokoban/SokobanMain.java`.
2. Modifiez le nom du fichier JSON dans l'instruction `gameRunner.setTestCase("test25.json");` par le niveau que vous souhaitez tester (par exemple `"test10.json"`).
3. Recompilez impérativement le projet avec `mvn clean package`.
4. Relancez la commande d'exécution `java --add-opens...`.