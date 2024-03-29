package org.com.SplitPickupAndDelivery.solver;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.util.ArrayList;

import static java.lang.Math.round;

/**
 * The MILPPickupAndDelivery class implements a mixed integer linear programming model
 * for solving the split pickup and delivery problem between multiple hubs. This solver
 * require the Google OR-tools library version 9.5.
 * @Date: 15/12/2022
 */
public class MILPPickupAndDelivery {

    private ArrayList<Integer> H;       // set of hubs
    private ArrayList<Integer> S1;      // set of departure nodes
    private ArrayList<Integer> S2;      // set of arrival nodes
    private int num_nodes;
    private ArrayList<Integer> K;       // set of all trucks
    private double[][] request;         // request[i][j] is the amount of boxes needs to be delivered from i to j

    private MPSolver solver;
    private MPVariable[][][]  x;
    private MPVariable[][] t;
    private MPVariable[][] z;
    private MPVariable[][][] p;

    public MILPPickupAndDelivery() {

        H = new ArrayList<>();
        for (int i=0; i<MappedData.N; i++) {
            H.add(i);
        }

        S1 = new ArrayList<>();
        for (int i=MappedData.N; i<MappedData.N + MappedData.K; i++) {
            S1.add(i);
        }

        S2 = new ArrayList<>();
        for (int i=MappedData.N + MappedData.K; i<MappedData.N + 2 * MappedData.K; i++) {
            S2.add(i);
        }

        K = new ArrayList<>();
        for (int k=0; k<MappedData.K; k++) {
            K.add(k);
        }

        num_nodes = H.size() + S1.size() + S2.size();

        request = new double[num_nodes][num_nodes];
        for (MappedRequest r: MappedData.requests) {
            request[r.from_hub][r.to_hub] = r.quantity;
            // System.out.println(r.from_hub + " -> " + r.to_hub + ": " + r.quantity);
        }
    }

    public MappedSolution solve(boolean verbose) {

        Loader.loadNativeLibraries();
        solver = MPSolver.createSolver(String.valueOf(MPSolver.OptimizationProblemType.SCIP_MIXED_INTEGER_PROGRAMMING));

        if (solver == null) {
            System.err.println("Could not create solver SCIP");
            return null;
        }

        build_model();
        create_obj();

        solver.setTimeLimit(1000 * Parameters.TIME_LIMIT_S);
        solver.setNumThreads(Parameters.NUMBER_OF_CPUs);
        if (verbose) {
            solver.enableOutput();
        } else {
            System.out.println("MILP solver is running...");
        }

        final MPSolver.ResultStatus resultStatus = solver.solve();

        if (resultStatus == MPSolver.ResultStatus.OPTIMAL || resultStatus == MPSolver.ResultStatus.FEASIBLE) {

            if (resultStatus == MPSolver.ResultStatus.OPTIMAL) {
                System.out.println("Optimal solution found!");
            } else {
                System.out.println("A good solution found!");
            }

            ArrayList<ArrayList<Integer>> routes = new ArrayList<>();
            ArrayList<ArrayList<ArrayList<MappedRequest>>> route_pickup = new ArrayList<>();
            ArrayList<ArrayList<ArrayList<MappedRequest>>> route_delivery = new ArrayList<>();
            for (int k: K) {
                // extract the route of truck k
                ArrayList<Integer> route_k = new ArrayList<>();
                route_k.add(H.size() + k);

                int pre = route_k.get(0);
                while (!S2.contains(pre)) {
                    for (int i=0; i<num_nodes; i++) {
                        if (x[k][pre][i].solutionValue() > 0) {
                            pre = i;
                            route_k.add(pre);
                            break;
                        }
                    }
                }

                route_k.remove(0);
                route_k.remove(route_k.size()-1);

                ArrayList<ArrayList<MappedRequest>> pickup = new ArrayList<>();
                ArrayList<ArrayList<MappedRequest>> delivery = new ArrayList<>();

                if (route_k.size() > 0) {
                    double load = ((int) (10000 * z[k][H.size() + k].solutionValue() / MappedData.capacity.get(k))) / 100.0;
                    System.out.print("Truck " + (k + 1) + ": DEPARTURE_HUB (" + load + "%)");
                    for (int hub: route_k) {
                        load = ((int) (10000 * z[k][hub].solutionValue() / MappedData.capacity.get(k))) / 100.0;
                        System.out.print(" -> HUB " + hub + " (" + load + "%)");
                    }
                    System.out.println(" -> ARRIVAL HUB.");

                    for (int i: route_k) {
                        System.out.println("\tOperations at HUB " + i + ":");

                        ArrayList<MappedRequest> pickup_operations = new ArrayList<>();
                        ArrayList<MappedRequest> drop_operations = new ArrayList<>();
                        for (int j: H) {
                            if (p[k][i][j].solutionValue() > 1e-6) {
                                System.out.println("\t\tPick " + round(p[k][i][j].solutionValue()) + " boxes to delivery to HUB " + j);
                                pickup_operations.add(new MappedRequest(i, j, round(p[k][i][j].solutionValue())));
                            }
                        }

                        for (int j: H) {
                            if (p[k][j][i].solutionValue() > 0) {
                                System.out.println("\t\tDrop " + round(p[k][j][i].solutionValue()) + " boxes picked from HUB " + j);
                                drop_operations.add(new MappedRequest(j, i, round(p[k][j][i].solutionValue())));
                            }
                        }
                        System.out.println("\tLeave HUB " + i + " with " + round(z[k][i].solutionValue()) + " boxes.");
                        System.out.println("\t--------------------------");

                        pickup.add(pickup_operations);
                        delivery.add(drop_operations);
                    }
                } else {
                    System.out.println("Truck " + (k + 1) + ": NOT USED.");
                }

                routes.add(route_k);
                route_pickup.add(pickup);
                route_delivery.add(delivery);
            }

            MappedSolution output = new MappedSolution();
            output.routes = routes;
            output.pickup = route_pickup;
            output.delivery = route_delivery;

            return output;
        } else {
            System.out.println("No feasible solution found");
        }

        return null;
    }

    private void build_model() {
        double M = 1e6;

        ArrayList<Integer> HS2 = new ArrayList<>();
        HS2.addAll(H);
        HS2.addAll(S2);

        ArrayList<Integer> S1H = new ArrayList<>();
        S1H.addAll(S1);
        S1H.addAll(H);

        // create decision variables
        x = new MPVariable[MappedData.K][num_nodes][num_nodes];
        for (int k: K) {
            for (int i=0; i<num_nodes; i++) {
                for (int j=0; j<num_nodes; j++) {
                    x[k][i][j] = solver.makeIntVar(0,1,"x[" + k + "," + i + "," + j + "]");
                }
            }
        }

        z = new MPVariable[K.size()][num_nodes];
        for (int k=0; k<K.size(); k++) {
            for (int i: S1) {
                z[k][i] = solver.makeNumVar(0, 0, "");
            }

            for (int i: HS2) {
                z[k][i] = solver.makeNumVar(0, MappedData.capacity.get(k), "");
            }
        }

        p = new MPVariable[K.size()][num_nodes][num_nodes];
        for (int k: K) {
            for (int i=0; i<num_nodes; i++) {
                for (int j=0; j<num_nodes; j++) {
                    p[k][i][j] = solver.makeNumVar(0, request[i][j], "");
                }
            }
        }

        /******************************************************************************
         *                      START ROUTE CONSTRAINTS                               *
         * ****************************************************************************/
        // every truck must depart from a (logical) departure node
        for (int k: K) {
            MPConstraint c1 = solver.makeConstraint(1,1);
            for (int j: HS2) {
                c1.setCoefficient(x[k][H.size() + k][j], 1);
            }
        }

        // exact K trucks departing from departure nodes
        MPConstraint depart = solver.makeConstraint(K.size(), K.size());
        for (int k: K) {
            for (int i: K) {
                for (int j: HS2) {
                    depart.setCoefficient(x[k][H.size() + i][j], 1);
                }
            }
        }

        // every truck must return to a (logical) arrival node
        for (int k: K) {
            MPConstraint c = solver.makeConstraint(1,1);

            for (int i: S1H) {
                c.setCoefficient(x[k][i][H.size() + K.size() + k], 1);
            }
        }

        // balance flow constraint of each truck
        for (int k: K) {
            for (int j: H) {
                MPConstraint c1 = solver.makeConstraint(0, 0);
                MPConstraint c2 = solver.makeConstraint(0, 1);

                for (int i: S1H) {
                    c1.setCoefficient(x[k][i][j], 1);
                    c2.setCoefficient(x[k][i][j], 1);
                }

                for (int i: HS2) {
                    c1.setCoefficient(x[k][j][i], -1);
                }
            }
        }

        // balance flow constraint at each node
        for (int j: H) {
            MPConstraint c = solver.makeConstraint(0, 0);
            for (int k: K) {
                for (int i: S1H) {
                    c.setCoefficient(x[k][i][j], 1);
                }

                for (int i: HS2) {
                    c.setCoefficient(x[k][j][i], -1);
                }
            }
        }

        // sub-tour elimination constraint
        t = new MPVariable[K.size()][num_nodes];
        for (int k: K) {
            for (int i=0; i<t[0].length; i++) {
                t[k][i] = solver.makeIntVar(0, num_nodes, "t[" + k + "," + i + "]");
            }
        }
        for (int k: K) {
            for (int i: S1H) {
                for (int j: HS2) {
                    MPConstraint c = solver.makeConstraint(1-num_nodes, num_nodes);
                    c.setCoefficient(t[k][j], 1);
                    c.setCoefficient(t[k][i], -1);
                    c.setCoefficient(x[k][i][j], -num_nodes);
                }
            }
        }
        /******************************************************************************
         *                      END ROUTE CONSTRAINTS                                 *
         *              START PICKUP AND DELIVERY CONSTRAINTS                         *
         * ****************************************************************************/
        // all the requests must be served
        for (int i: H) {
            for (int j: H) {
                MPConstraint c = solver.makeConstraint(request[i][j], request[i][j]);
                for (int k: K) {
                    c.setCoefficient(p[k][i][j], 1);
                }
            }
        }

        // if truck k move from hub i to hub j, then
        // loading when leaving j = loading when leaving i + total picking up at j - total dropping at j
        for (int k: K) {
            for (int i: S1H) {
                for (int j: HS2) {
                    MPConstraint c1 = solver.makeConstraint(-M, M);
                    c1.setCoefficient(z[k][j], 1);
                    c1.setCoefficient(z[k][i], -1);
                    c1.setCoefficient(x[k][i][j], M);
                    for (int v: H) {
                        c1.setCoefficient(p[k][j][v], -1);
                        c1.setCoefficient(p[k][v][j], 1);
                    }

                    MPConstraint c2 = solver.makeConstraint(-M, M);
                    c2.setCoefficient(z[k][j], 1);
                    c2.setCoefficient(z[k][i], -1);
                    c2.setCoefficient(x[k][i][j], -M);
                    for (int v: H) {
                        c2.setCoefficient(p[k][j][v], -1);
                        c2.setCoefficient(p[k][v][j], 1);
                    }
                }
            }
        }

        // if truck k does not visit hub i, then there is no picking up or dropping off at hub i
        for (int k: K) {
            for (int i: H) {
                MPConstraint pc = solver.makeConstraint(-M, 0);
                for (int j: H) {
                    pc.setCoefficient(p[k][i][j], 1);
                }
                for (int j: S1H) {
                    pc.setCoefficient(x[k][j][i], -M);
                }

                MPConstraint dc = solver.makeConstraint(-M, 0);
                for (int j: H) {
                    dc.setCoefficient(p[k][j][i], 1);
                }
                for (int j: S1H) {
                    dc.setCoefficient(x[k][j][i], -M);
                }
            }
        }

        // truck k must visit hub i before hub j to serve a request R(i -> j, q)
        for (int k: K) {
            for (int i: H) {
                for (int j: H) {
                    MPVariable tmp = solver.makeIntVar(0, 1, "");

                    MPConstraint c1 = solver.makeConstraint(-M, M);
                    c1.setCoefficient(t[k][i], 1);
                    c1.setCoefficient(t[k][j], -1);
                    c1.setCoefficient(tmp, M);

                    MPConstraint c2 = solver.makeConstraint(-M, 0);
                    c2.setCoefficient(p[k][i][j], 1);
                    c2.setCoefficient(tmp, -M);
                }
            }
        }
        /******************************************************************************
         *                      END PICKUP AND DELIVERY CONSTRAINTS                   *
         * ****************************************************************************/

        System.out.println("MILP model built successfully.");
    }

    private void create_obj() {
        // Minimizing the number of inter-hub movements
        // this objective function is only temporary
        MPObjective obj = solver.objective();
        for (int k: K) {
            for (int i=0; i<num_nodes; i++) {
                for (int j=0; j<num_nodes; j++) {
                     obj.setCoefficient(x[k][i][j], 1);
                }
            }
        }

        obj.setMinimization();
    }
}
