# Projet de Planification Automatique - Solveur Sokoban

**Membres du groupe :** Osama Lababidi, Arthur Laumay, Adrien Van-Robays

## Fichiers créés ou modifiés

*   **`sokoban.pddl`** : Modélisation du domaine PDDL. Ce fichier définit les types (`cell`, `direction`), les prédicats de base (`at-player`, `at-box`, `clear`, `safe`, `adjacent`) ainsi que les deux actions principales (`move` et `push`).
*   **`java/sokoban/Planner.java`** *(à lister uniquement si vous l'avez modifié)* : Fichier configurant le planificateur PDDL4J, notamment le choix de la stratégie de recherche (ASTAR) et de l'heuristique (FAST_FORWARD).
*   **`java/sokoban/Level.java`** *(à lister uniquement si vous l'avez modifié)* : Composant traduisant la grille du jeu en un problème PDDL (états initiaux, voisins, buts, et détection des zones mortes `safe`).

## Choix de modélisation

*   **Optimisation des états** : Les murs ne sont pas déclarés comme des objets pour réduire la complexité ; seules les cases praticables sont typées.
*   **Stratégie d'évitement (Dead-ends)** : Le prédicat `safe` est utilisé dans l'action `push` pour empêcher le planificateur de pousser une caisse dans un coin bloqué, optimisant ainsi le temps de recherche.
*   **Respect de STRIPS pur** : L'utilisation du prédicat `clear` plutôt que d'une condition négative sur `at-box` assure la compatibilité maximale et les performances dans PDDL4J.

## Exécution

Le projet utilise des chemins portables pour garantir la compatibilité multi-OS. Pour lancer le banc d'essai sur l'ensemble des niveaux :
`java -cp target/sokoban-1.0-SNAPSHOT-jar-with-dependencies.jar sokoban.Bench run`