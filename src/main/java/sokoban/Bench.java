package sokoban;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Banc d'essai hors interface : lit config/testN.json, genere le probleme PDDL, lance le
 * planificateur (processus separe), REJOUE le plan sur une simulation du jeu pour verifier
 * qu'il gagne, et produit le tableau de resultats du rapport.
 *
 * Usage (depuis la racine du projet, apres mvn clean package) :
 *   java -cp target/sokoban-1.0-SNAPSHOT-jar-with-dependencies.jar sokoban.Bench gen [niveaux]
 *       -> ecrit pddl/testN.pddl pour chaque niveau (exemples de problemes generes)
 *   java -cp target/sokoban-1.0-SNAPSHOT-jar-with-dependencies.jar sokoban.Bench run [niveaux] [options]
 *       -> resout, verifie et affiche le tableau (aussi ecrit dans resultats.md)
 *
 * niveaux : "1-10", "1,3,8" ou "5" (defaut : 1-30)
 * options : --timeout=30  --weight=1.2  --heuristic=FAST_FORWARD  --out=resultats.md
 *           (heuristiques : FAST_FORWARD, MAX, SUM, AJUSTED_SUM, ...)
 */
public class Bench {

    /** Niveau au format de l'entree standard du jeu. */
    static class Parsed {
        int width, height, px, py;
        String[] grid;     // '#' mur, '*' objectif, '.' sol
        int[][] boxes;
    }

    // ------------------------------------------------------------------ lecture du niveau

    /** Extrait la valeur de "testIn" d'un fichier testN.json (chaine JSON avec \n echappes). */
    static String readTestIn(Path json) throws Exception {
        String s = new String(Files.readAllBytes(json), StandardCharsets.UTF_8);
        int k = s.indexOf("\"testIn\"");
        if (k < 0) throw new IllegalArgumentException("pas de champ testIn dans " + json);
        int i = s.indexOf('"', s.indexOf(':', k) + 1) + 1;
        StringBuilder sb = new StringBuilder();
        while (i < s.length() && s.charAt(i) != '"') {
            char c = s.charAt(i++);
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': break;
                    case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: sb.append(e); // \" \\ \/
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Convertit le format du fichier JSON vers le format de l'entree standard (cf. Cell.getMapChar). */
    static Parsed parse(String testIn) {
        String[] lines = testIn.split("\n", -1);
        Parsed p = new Parsed();
        p.height = lines.length;
        for (String l : lines) p.width = Math.max(p.width, l.length());
        p.grid = new String[p.height];
        List<int[]> boxes = new ArrayList<>();
        p.px = -1;
        for (int y = 0; y < p.height; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < p.width; x++) {
                char c = x < lines[y].length() ? lines[y].charAt(x) : ' ';
                if (c == '#') row.append('#');
                else if (c == '.' || c == '*' || c == '+') row.append('*');
                else row.append('.');
                if (c == '$' || c == '*') boxes.add(new int[]{x, y});
                if (c == '@' || c == '+') { p.px = x; p.py = y; }
            }
            p.grid[y] = row.toString();
        }
        if (p.px < 0) throw new IllegalArgumentException("pas de gardien dans le niveau");
        p.boxes = boxes.toArray(new int[0][]);
        return p;
    }

    static Level toLevel(Parsed p) {
        return new Level(p.width, p.height, p.grid, p.px, p.py, p.boxes);
    }

    // ------------------------------------------------------------------ verification du plan

    /** Rejoue le plan comme le jeu (Pusher.move). Renvoie null si le plan gagne, sinon la raison. */
    static String replay(Parsed p, String plan) {
        boolean[][] box = new boolean[p.width][p.height];
        for (int[] b : p.boxes) box[b[0]][b[1]] = true;
        int x = p.px, y = p.py;
        if (allSolved(p, box)) return null;
        for (int i = 0; i < plan.length(); i++) {
            int d = "URDL".indexOf(plan.charAt(i));
            if (d < 0) return "coup inconnu '" + plan.charAt(i) + "' (coup " + (i + 1) + ")";
            int nx = x + Level.DX[d], ny = y + Level.DY[d];
            if (!in(p, nx, ny) || p.grid[ny].charAt(nx) == '#') return "mur au coup " + (i + 1);
            if (box[nx][ny]) {
                int bx = nx + Level.DX[d], by = ny + Level.DY[d];
                if (!in(p, bx, by) || p.grid[by].charAt(bx) == '#' || box[bx][by])
                    return "caisse bloquee au coup " + (i + 1);
                box[nx][ny] = false;
                box[bx][by] = true;
            }
            x = nx;
            y = ny;
            if (allSolved(p, box)) return null; // le jeu s'arrete des la victoire
        }
        return "caisses non rangees a la fin du plan";
    }

    static boolean in(Parsed p, int x, int y) {
        return x >= 0 && y >= 0 && x < p.width && y < p.height;
    }

    static boolean allSolved(Parsed p, boolean[][] box) {
        for (int y = 0; y < p.height; y++)
            for (int x = 0; x < p.width; x++)
                if (box[x][y] && p.grid[y].charAt(x) != '*') return false;
        return true;
    }

    // ------------------------------------------------------------------ planificateur

    /** Lance Planner dans un processus separe. Renvoie le plan, "NOPLAN" ou "TIMEOUT"/"ERREUR: ...". */
    static String runPlanner(Path domain, Path problem, int timeout, String weight, String heuristic) throws Exception {
        String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        List<String> entries = new ArrayList<>();
        for (String e : System.getProperty("java.class.path").split(File.pathSeparator))
            if (!e.isEmpty()) entries.add(Paths.get(e).toAbsolutePath().toString());
        String cp = String.join(File.pathSeparator, entries);

        Path out = Files.createTempFile("sokoban-bench-", ".out");
        ProcessBuilder pb = new ProcessBuilder(javaBin, "-Xmx2g", "-cp", cp, "sokoban.Planner",
                domain.toString(), problem.toString(), String.valueOf(timeout), weight, heuristic);
        pb.redirectErrorStream(true);
        pb.redirectOutput(out.toFile());
        Process proc = pb.start();
        if (!proc.waitFor(timeout + 20L, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            return "TIMEOUT";
        }
        List<String> lines = Files.readAllLines(out, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.startsWith("PLAN:")) return line.substring(5).trim();
            if (line.startsWith("NOPLAN")) return "NOPLAN";
        }
        return "ERREUR: " + (lines.isEmpty() ? "pas de sortie" : lines.get(Math.max(0, lines.size() - 1)));
    }

    // ------------------------------------------------------------------ programme principal

    static List<Integer> parseLevels(String spec) {
        List<Integer> res = new ArrayList<>();
        for (String part : spec.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            if (part.contains("-")) {
                String[] ab = part.split("-");
                for (int n = Integer.parseInt(ab[0]); n <= Integer.parseInt(ab[1]); n++) res.add(n);
            } else {
                res.add(Integer.parseInt(part));
            }
        }
        return res;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
            System.out.println("Usage : Bench gen|run [niveaux] [--timeout=30] [--weight=1.2] "
                    + "[--heuristic=FAST_FORWARD] [--out=resultats.md]");
            return;
        }
        String mode = args[0];
        String levelSpec = "1-30";
        int timeout = 30;
        String weight = "1.2", heuristic = "FAST_FORWARD", outFile = "resultats.md";
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--timeout=")) timeout = Integer.parseInt(a.substring(10));
            else if (a.startsWith("--weight=")) weight = a.substring(9);
            else if (a.startsWith("--heuristic=")) heuristic = a.substring(12);
            else if (a.startsWith("--out=")) outFile = a.substring(6);
            else levelSpec = a;
        }
        List<Integer> levels = parseLevels(levelSpec);

        Path pddlDir = Paths.get("pddl");
        Files.createDirectories(pddlDir);

        if (mode.equals("gen")) {
            for (int n : levels) {
                Path json = Paths.get("config", "test" + n + ".json");
                if (!Files.exists(json)) continue;
                Parsed p = parse(readTestIn(json));
                Level lvl = toLevel(p);
                Path pddl = pddlDir.resolve("test" + n + ".pddl");
                Files.write(pddl, lvl.toPddl().getBytes(StandardCharsets.UTF_8));
                System.out.println("ecrit " + pddl + " (" + p.width + "x" + p.height + ", " + p.boxes.length + " caisses)");
            }
            return;
        }

        // mode "run"
        Path domainFile = Files.createTempFile("sokoban-domain-", ".pddl");
        Files.write(domainFile, Agent.domainText().getBytes(StandardCharsets.UTF_8));

        StringBuilder md = new StringBuilder();
        md.append("Heuristique : ").append(heuristic).append(", poids : ").append(weight)
                .append(", timeout : ").append(timeout).append(" s\n\n");
        md.append("| Niveau | Caisses | Resolu ? | Longueur du plan | Temps de planif (ms) | Rejoue sur le jeu |\n");
        md.append("|---|---|---|---|---|---|\n");
        System.out.print(md);

        int solved = 0;
        for (int n : levels) {
            Path json = Paths.get("config", "test" + n + ".json");
            if (!Files.exists(json)) continue;
            String row;
            try {
                Parsed p = parse(readTestIn(json));
                Level lvl = toLevel(p);
                Path problem = Files.createTempFile("sokoban-test" + n + "-", ".pddl");
                Files.write(problem, lvl.toPddl().getBytes(StandardCharsets.UTF_8));

                long t0 = System.currentTimeMillis();
                String plan = runPlanner(domainFile, problem, timeout, weight, heuristic);
                long ms = System.currentTimeMillis() - t0;

                String ok, len = "-", rep = "-";
                if (plan.equals("NOPLAN") || plan.equals("TIMEOUT") || plan.startsWith("ERREUR")) {
                    ok = plan.equals("NOPLAN") ? "NON (pas de plan / timeout planif.)" : "NON (" + plan + ")";
                } else {
                    len = String.valueOf(plan.length());
                    String err = replay(p, plan);
                    if (err != null) {
                        ok = "NON"; rep = "ECHEC : " + err;
                    } else if (plan.length() > 400) {
                        ok = "NON"; rep = "gagne mais > 400 coups (perdu au Referee)";
                    } else {
                        ok = "OUI"; rep = "OK"; solved++;
                    }
                }
                row = "| test" + n + " | " + p.boxes.length + " | " + ok + " | " + len + " | " + ms + " | " + rep + " |";
            } catch (Exception e) {
                row = "| test" + n + " | - | NON (erreur : " + e + ") | - | - | - |";
            }
            System.out.println(row);
            md.append(row).append("\n");
        }
        md.append("\nNiveaux resolus : ").append(solved).append(" / ").append(levels.size()).append("\n");
        System.out.println("\nNiveaux resolus : " + solved + " / " + levels.size());
        Files.write(Paths.get(outFile), md.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Tableau ecrit dans " + outFile);
    }
}
