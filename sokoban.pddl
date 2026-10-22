(define (domain sokoban)
  (:requirements :strips :typing)
  (:types cell direction)
  (:constants up down left right - direction)

  (:predicates
    (at-player ?c - cell)
    (at-box ?c - cell)
    (clear ?c - cell)
    (safe ?c - cell)
    (adjacent ?c1 - cell ?c2 - cell ?d - direction))

  ;; Le gardien se deplace sur une case libre voisine
  (:action move
    :parameters (?d - direction ?from - cell ?to - cell)
    :precondition (and (at-player ?from)
                       (clear ?to)
                       (adjacent ?from ?to ?d))
    :effect (and (not (at-player ?from))
                 (at-player ?to)
                 (not (clear ?to))
                 (clear ?from)))

  ;; Le gardien pousse la caisse situee devant lui
  (:action push
    :parameters (?d - direction ?from - cell ?boxpos - cell ?to - cell)
    :precondition (and (at-player ?from)
                       (at-box ?boxpos)
                       (clear ?to)
                       (safe ?to)
                       (adjacent ?from ?boxpos ?d)
                       (adjacent ?boxpos ?to ?d))
    :effect (and (not (at-player ?from))
                 (at-player ?boxpos)
                 (not (at-box ?boxpos))
                 (at-box ?to)
                 (not (clear ?to))
                 (clear ?from)))
)
