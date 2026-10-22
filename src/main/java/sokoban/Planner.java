package sokoban;

import fr.uga.pddl4j.heuristics.state.StateHeuristic;
import fr.uga.pddl4j.plan.Plan;
import fr.uga.pddl4j.planners.LogLevel;
import fr.uga.pddl4j.planners.PlannerConfiguration;
import fr.uga.pddl4j.planners.statespace.AbstractStateSpacePlanner;
import fr.uga.pddl4j.planners.statespace.HSP;
import fr.uga.pddl4j.planners.statespace.StateSpacePlanner;
import fr.uga.pddl4j.problem.Problem;
import fr.uga.pddl4j.problem.operator.Action;

/**
 * Programme lance dans un processus Java separe (par l'Agent ou par le banc d'essai Bench).
 *
 * Pourquoi un processus separe ? Le jeu execute l'Agent dans un chargeur de classes
 * (javassist.Loader) incompatible avec Log4j, que PDDL4J initialise des qu'on cree un
 * planificateur : IllegalAccessError. Dans un processus a part, le probleme disparait.
 *
 * Usage : Planner domaine.pddl probleme.pddl [timeoutSecondes] [poids] [heuristique]
 *   defauts : 8 s, poids 1.2, FAST_FORWARD
 * Sortie standard : une ligne "PLAN:UDLR..." ou "NOPLAN".
 */
public class Planner {
    public static void main(String[] args) throws Exception {
        int timeout = args.length > 2 ? Integer.parseInt(args[2]) : 60;
        double weight = args.length > 3 ? Double.parseDouble(args[3]) : 1.2;
        StateHeuristic.Name heuristic = args.length > 4
                ? StateHeuristic.Name.valueOf(args[4]) : StateHeuristic.Name.FAST_FORWARD;

        // Contournement d'un bug de PDDL4J v4.0.0.0 : la configuration par defaut ecrit
        // "[ASTAR]" (avec crochets) puis n'arrive plus a le relire (IllegalArgumentException
        // dans toSearchStrategies). On fournit "ASTAR" nous-memes.
        PlannerConfiguration config = AbstractStateSpacePlanner.getDefaultConfiguration();
        config.setProperty(StateSpacePlanner.SEARCH_STRATEGIES_SETTING, "ASTAR");

        HSP planner = new HSP(config);
        planner.setDomain(args[0]);
        planner.setProblem(args[1]);
        planner.setTimeout(timeout);
        planner.setLogLevel(LogLevel.OFF); // stdout est lu par l'appelant
        planner.setHeuristic(heuristic);
        planner.setHeuristicWeight(weight);

        Problem pb = planner.instantiate(planner.parse());
        Plan plan = planner.solve(pb);
        if (plan == null) {
            System.out.println("NOPLAN");
            return;
        }

        // Le 1er parametre de move/push est la direction : up/down/left/right -> U/D/L/R
        StringBuilder sb = new StringBuilder();
        for (Action a : plan.actions()) {
            String dir = pb.getConstantSymbols().get(a.getInstantiations()[0]);
            sb.append(Character.toUpperCase(dir.charAt(0)));
        }
        System.out.println("PLAN:" + sb);
    }
}
