package sokoban;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Le "joueur" : lit l'etat du jeu sur l'entree standard, calcule un plan avec PDDL4J
 *
 * Premier tour : "largeur hauteur nbCaisses", la grille ('#' mur, '*' objectif, '.' sol),
 * la position du gardien, puis celle de chaque caisse. Tours suivants : gardien + caisses.
 * Sortie : un caractere par tour parmi U, D, L, R
 */
public class Agent {

    /**
     * Copie integree du domaine PDDL, utilisee pour codingame car codingame ne connait pas le répertoire pddl/*
     * (le jeu peut etre lance depuis un autre dossier).
     */
    static final String DOMAIN = String.join("\n",
            "(define (domain sokoban)",
            "  (:requirements :strips :typing)",
            "  (:types cell direction)",
            "  (:constants up down left right - direction)",
            "  (:predicates",
            "    (at-player ?c - cell)",
            "    (at-box ?c - cell)",
            "    (clear ?c - cell)",
            "    (safe ?c - cell)",
            "    (adjacent ?c1 - cell ?c2 - cell ?d - direction))",
            "  (:action move",
            "    :parameters (?d - direction ?from - cell ?to - cell)",
            "    :precondition (and (at-player ?from) (clear ?to) (adjacent ?from ?to ?d))",
            "    :effect (and (not (at-player ?from)) (at-player ?to) (not (clear ?to)) (clear ?from)))",
            "  (:action push",
            "    :parameters (?d - direction ?from - cell ?boxpos - cell ?to - cell)",
            "    :precondition (and (at-player ?from) (at-box ?boxpos) (clear ?to) (safe ?to)",
            "                       (adjacent ?from ?boxpos ?d) (adjacent ?boxpos ?to ?d))",
            "    :effect (and (not (at-player ?from)) (at-player ?boxpos) (not (at-box ?boxpos))",
            "                 (at-box ?to) (not (clear ?to)) (clear ?from)))",
            ")",
            "");

    /** Journal de l'agent */
    static final Path LOG = Paths.get(System.getProperty("java.io.tmpdir"), "sokoban-agent.log");

    static void log(String msg) {
        System.err.println(msg);
        try {
            Files.write(LOG, (msg + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // le journal est facultatif
        }
    }

    /** Texte du domaine : pddl/sokoban.pddl s'il existe, sinon la copie integree. */
    static String domainText() throws Exception {
        Path file = Paths.get("pddl", "sokoban.pddl");
        if (Files.isRegularFile(file)) return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        return DOMAIN;
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            log("ERREUR AGENT : " + sw);
        }
    }

    static void run() throws Exception {
        log("=== Agent demarre (cwd=" + System.getProperty("user.dir") + ", java="
                + System.getProperty("java.version") + ") ===");
        Scanner in = new Scanner(System.in);

        // 1. Lecture du premier tour
        int width = in.nextInt();
        int height = in.nextInt();
        int nbBoxes = in.nextInt();
        String[] grid = new String[height];
        for (int y = 0; y < height; y++) grid[y] = in.next();
        int px = in.nextInt(), py = in.nextInt();
        int[][] boxes = new int[nbBoxes][2];
        for (int i = 0; i < nbBoxes; i++) {
            boxes[i][0] = in.nextInt();
            boxes[i][1] = in.nextInt();
        }
        log("Niveau lu : " + width + "x" + height + ", " + nbBoxes + " caisses");

        // 2. Encodage en probleme PDDL
        Level level = new Level(width, height, grid, px, py, boxes);
        Path problemFile = Files.createTempFile("sokoban-", ".pddl");
        Files.write(problemFile, level.toPddl().getBytes(StandardCharsets.UTF_8));
        Path domainFile = Files.createTempFile("sokoban-domain-", ".pddl");
        Files.write(domainFile, domainText().getBytes(StandardCharsets.UTF_8));
        log("Probleme ecrit dans " + problemFile);

        // 3. Planification
        long t0 = System.currentTimeMillis();
        String solution = solve(domainFile.toString(), problemFile.toString());
        log("Plan (" + solution.length() + " coups, " + (System.currentTimeMillis() - t0) + " ms) : " + solution);

        // 4. Execution : un coup par tour
        for (int i = 0; i < solution.length(); i++) {
            if (i > 0) {
                // aux tours suivants le jeu renvoie gardien + caisses : on les consomme
                for (int k = 0; k < nbBoxes + 1; k++) {
                    in.nextInt();
                    in.nextInt();
                }
            }
            System.out.println(solution.charAt(i));
            System.out.flush();
        }
    }

    /**
     * Lance le planificateur dans un processus Java separe du jeu et renvoie le plan en U/D/L/R.
     */
    static String solve(String domain, String problem) throws Exception {
        String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();

        // classpath courant, rendu absolu (le dossier de travail est la racine du projet)
        List<String> entries = new ArrayList<>();
        for (String e : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (!e.isEmpty()) entries.add(Paths.get(e).toAbsolutePath().toString());
        }
        String cp = String.join(File.pathSeparator, entries);
        log("Classpath planificateur : " + cp);

        Path out = Files.createTempFile("sokoban-planner-", ".out");
        ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", cp, "sokoban.Planner", domain, problem);
        pb.redirectErrorStream(true);
        pb.redirectOutput(out.toFile());
        Process p = pb.start();

        if (!p.waitFor(59500, TimeUnit.MILLISECONDS)) {
            p.destroyForcibly();
            log("Planificateur interrompu (timeout)");
        }

        List<String> lines = Files.readAllLines(out, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.startsWith("PLAN:")) return line.substring(5).trim();
        }
        log("Sortie du planificateur :\n" + String.join("\n", lines));
        throw new IllegalStateException("Aucun plan trouve");
    }
}
