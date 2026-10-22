package sokoban;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Niveau Sokoban lu depuis l'entree standard du jeu, et sa traduction en probleme PDDL.
 * Sur stdin : '#' = mur, '*' = objectif, '.' = sol.
 */
public class Level {
    // U, R, D, L : meme ordre que la chaine "URDL" du Board
    static final int[] DX = {0, 1, 0, -1};
    static final int[] DY = {-1, 0, 1, 0};
    static final String[] DIR = {"up", "right", "down", "left"};

    final int width, height;
    final boolean[][] wall;
    final boolean[][] goal;
    final boolean[][] box;
    final boolean[][] usable;
    final int px, py;

    public Level(int width, int height, String[] grid, int px, int py, int[][] boxes) {
        this.width = width;
        this.height = height;
        this.px = px;
        this.py = py;

        wall = new boolean[width][height];
        goal = new boolean[width][height];
        box = new boolean[width][height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char c = x < grid[y].length() ? grid[y].charAt(x) : '.';
                wall[x][y] = c == '#';
                goal[x][y] = c == '*'; // CORRECTION : '.' = sol, seul '*' est un objectif
            }
        }
        for (int[] b : boxes) box[b[0]][b[1]] = true;
        usable = floodFill();
    }

    /** Cases atteignables depuis le joueur sans traverser de mur. */
    private boolean[][] floodFill() {
        boolean[][] seen = new boolean[width][height];
        Deque<int[]> todo = new ArrayDeque<>();
        todo.add(new int[]{px, py});
        seen[px][py] = true;
        while (!todo.isEmpty()) {
            int[] c = todo.poll();
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + DX[d], ny = c[1] + DY[d];
                if (inside(nx, ny) && !wall[nx][ny] && !seen[nx][ny]) {
                    seen[nx][ny] = true;
                    todo.add(new int[]{nx, ny});
                }
            }
        }
        return seen;
    }

    boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    boolean free(int x, int y) {
        return inside(x, y) && usable[x][y];
    }

    /** Case "morte" : coin non-objectif, une caisse poussee la ne peut plus jamais bouger. */
    boolean dead(int x, int y) {
        if (goal[x][y]) return false;
        boolean vertical = !free(x, y - 1) || !free(x, y + 1);
        boolean horizontal = !free(x - 1, y) || !free(x + 1, y);
        return vertical && horizontal;
    }

    static String cell(int x, int y) {
        return "c_" + x + "_" + y;
    }

    /** Genere le fichier probleme PDDL correspondant au niveau. */
    public String toPddl() {
        StringBuilder sb = new StringBuilder();
        sb.append("(define (problem sokoban-level)\n");
        sb.append("  (:domain sokoban)\n");

        sb.append("  (:objects\n   ");
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (usable[x][y]) sb.append(' ').append(cell(x, y));
        sb.append(" - cell)\n");

        sb.append("  (:init\n");
        sb.append("    (at-player ").append(cell(px, py)).append(")\n");
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!usable[x][y]) continue;
                String c = cell(x, y);

                if (box[x][y]) sb.append("    (at-box ").append(c).append(")\n");
                else if (!(x == px && y == py)) sb.append("    (clear ").append(c).append(")\n");

                if (!dead(x, y)) sb.append("    (safe ").append(c).append(")\n");

                for (int d = 0; d < 4; d++) {
                    int nx = x + DX[d], ny = y + DY[d];
                    if (free(nx, ny))
                        sb.append("    (adjacent ").append(c).append(' ').append(cell(nx, ny))
                                .append(' ').append(DIR[d]).append(")\n");
                }
            }
        }
        sb.append("  )\n");

        List<String> goals = new ArrayList<>();
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (goal[x][y] && usable[x][y]) goals.add("(at-box " + cell(x, y) + ")");
        sb.append("  (:goal (and ").append(String.join(" ", goals)).append("))\n");
        sb.append(")\n");
        return sb.toString();
    }
}
