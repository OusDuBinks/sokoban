;; =============================================================================
;; Domaine PDDL du Sokoban (STRIPS + typage)
;;
;; Modelisation :
;;  - une constante "cell" par case praticable (les murs ne sont pas des objets :
;;    une case qui n'existe pas ne peut pas etre traversee) ;
;;  - 4 directions, declarees dans le domaine. La direction est le 1er parametre
;;    de chaque action, ce qui permet de lire directement U/D/L/R dans le plan.
;;
;; Les deux actions correspondent chacune a exactement UN coup du jeu.
;; On utilise le predicat (clear ?c) plutot qu'une negation de (at-box ?c) pour
;; rester en STRIPS pur (pas de :negative-preconditions), mieux supporte et plus
;; rapide dans PDDL4J.
;; =============================================================================
(define (domain sokoban)
  (:requirements :strips :typing)

  (:types cell direction)

  (:constants up down left right - direction)

  (:predicates
    (at-player ?c - cell)                              ; le gardien est sur ?c
    (at-box ?c - cell)                                 ; une caisse est sur ?c
    (clear ?c - cell)                                  ; ?c est libre (ni gardien, ni caisse)
    (safe ?c - cell)                                   ; on peut pousser une caisse sur ?c
                                                       ; (pas un coin "mort", cf. Level.dead)
    (adjacent ?c1 - cell ?c2 - cell ?d - direction))   ; ?c2 est voisine de ?c1 dans la direction ?d

  ;; Le gardien se deplace sur une case libre voisine.
  (:action move
    :parameters (?d - direction ?from - cell ?to - cell)
    :precondition (and (at-player ?from)
                       (clear ?to)
                       (adjacent ?from ?to ?d))
    :effect (and (not (at-player ?from))
                 (at-player ?to)
                 (not (clear ?to))
                 (clear ?from)))

  ;; Le gardien pousse la caisse situee devant lui, d'une case, dans la meme direction.
  ;; Apres le coup, le gardien occupe l'ancienne case de la caisse (?boxpos) :
  ;; celle-ci n'est donc pas "clear", alors que ?from le devient.
  (:action push
    :parameters (?d - direction ?from - cell ?boxpos - cell ?to - cell)
    :precondition (and (at-player ?from)
                       (at-box ?boxpos)
                       (clear ?to)
                       (safe ?to)                      ; optimisation : jamais dans un coin mort
                       (adjacent ?from ?boxpos ?d)
                       (adjacent ?boxpos ?to ?d))
    :effect (and (not (at-player ?from))
                 (at-player ?boxpos)
                 (not (at-box ?boxpos))
                 (at-box ?to)
                 (not (clear ?to))
                 (clear ?from)))
)
