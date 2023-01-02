package org.com.SplitPickupAndDelivery.solver;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import org.apache.xmlbeans.impl.common.LoadSaveUtils;

import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;

import static java.lang.Math.round;

/**
 * The MILPPickupAndDelivery class implements a mixed integer linear programming model
 * for solving the split pickup and delivery problem between multiple hubs. This solver
 * require the Google OR-tools library version 9.5.
 * @Date: 15/12/2022
 */
public class MILPPickupAndDeliveryWithTimeConstraints {

    private ArrayList<Integer> H;       // set of hubs
    private ArrayList<Integer> S1;      // set of departure nodes
    private ArrayList<Integer> S2;      // set of arrival nodes
    private double[][] travel_time;
    private double[] start_working_time;
    private double base_time;
    private int num_nodes;
    private ArrayList<Integer> K;       // set of all trucks
    private double[][] request;         // request[i][j] is the amount of boxes needs to be delivered from i to j

    private MPSolver solver;
    private MPVariable[][][]  x;
    private MPVariable[][] arrival_time;        // arrival_time[k][i] is the time point that trucks k arrives at hub i
    private MPVariable[][] z;
    private MPVariable[][][] p;

    public MILPPickupAndDeliveryWithTimeConstraints() {

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

        travel_time = new double[num_nodes][num_nodes];
        for (int i: H) {
            for (int j: H) {
                travel_time[i][j] = 1e3 * MappedData.travel_time[i][j]; // convert to milisecs
            }
        }

        start_working_time = new double[K.size()];
        base_time = MappedData.startWorkingTime.get(0);
        for (double t: MappedData.startWorkingTime) {
            base_time = Math.min(t, base_time);
        }
        for (int k=0; k<K.size(); k++) {
            start_working_time[k] = MappedData.startWorkingTime.get(k) - base_time;
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

                if (route_k.size() > 1) {
                    double load = ((int) (10000 * z[k][H.size() + k].solutionValue() / MappedData.capacity.get(k))) / 100.0;
                    System.out.print("Truck " + (k + 1) + ": ");
                    for (int hub: route_k) {
                        load = ((int) (10000 * z[k][hub].solutionValue() / MappedData.capacity.get(k))) / 100.0;
                        System.out.print(" -> HUB " + hub + " (" + load + "%) ");

                        long ti = round(arrival_time[k][hub].solutionValue() + base_time);
                        Date date = new Date(ti);
                        System.out.print(date + " ");
                    }
                    System.out.println();

                    for (int i: route_k) {
                        System.out.println("\tOperations at HUB " + i + ":");

                        ArrayList<MappedRequest> pickup_operations = new ArrayList<>();
                        ArrayList<MappedRequest> drop_operations = new ArrayList<>();
                        for (int j: H) {
                            if (p[k][i][j].solutionValue() > 1e-3) {
                                System.out.println("\t\tPick " + round(p[k][i][j].solutionValue()) + " boxes to delivery to HUB " + j);
                                pickup_operations.add(new MappedRequest(i, j, round(p[k][i][j].solutionValue())));
                            }
                        }

                        for (int j: H) {
                            if (p[k][j][i].solutionValue() > 1e-3) {
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
                    route_k.clear();
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
        double M_time = 1e8;

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
        // every truck departs from its location
        for (int k1: K) {
            for (int k2: K) {
                for (int i: H) {
                    int location = MappedData.truck_location.get(k1);
                    MPConstraint c;
                    if (k1 == k2 && i == location) {
                        c = solver.makeConstraint(1, 1);
                    } else {
                        c = solver.makeConstraint(0, 0);
                    }
                    c.setCoefficient(x[k1][H.size() + k2][i], 1);
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

        // sub-tour elimination constraints
        MPVariable[][] t = new MPVariable[K.size()][num_nodes];
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

        // forbidden point constraint
        for (int k: K) {
            if (MappedData.forbiddenPoints.get(k) == null) {
                continue;
            }

            for (int p: MappedData.forbiddenPoints.get(k)) {
                MPConstraint c = solver.makeConstraint(0, 0);
                for (int i: S1H) {
                    c.setCoefficient(x[k][i][p], 1);
                }

                for (int j: HS2) {
                    c.setCoefficient(x[k][p][j], 1);
                }
            }
        }

        // forbidden route constraint
        for (int i: H) {
            for (int j: H) {
                if (travel_time[i][j] < 0) {
                    for (int k: K) {
                        MPConstraint c = solver.makeConstraint(0, 0);
                        c.setCoefficient(x[k][i][j], 1);
                    }
                }
            }
        }

        // time constraints
        arrival_time = new MPVariable[K.size()][num_nodes];
        for (int k: K) {
            for (int i=0; i<arrival_time[0].length; i++) {
                arrival_time[k][i] = solver.makeNumVar(start_working_time[k], M_time, "");
            }
        }

        for (int k: K) {
            for (int i: S1H) {
                for (int j: HS2) {
                    MPConstraint c = solver.makeConstraint(travel_time[i][j]-M_time, M_time);
                    c.setCoefficient(arrival_time[k][j], 1);
                    c.setCoefficient(arrival_time[k][i], -1);
                    c.setCoefficient(x[k][i][j], -M_time);
                }
            }
        }

        for (MappedRequest req: MappedData.requests) {
            double pickup_time = req.pickupTime - base_time;
            double delivery_time = req.deliveryTime - base_time;

            for (int k: K) {
                MPVariable u = solver.makeIntVar(0, 1, "");
                MPConstraint c = solver.makeConstraint(-M, 0);
                c.setCoefficient(p[k][req.from_hub][req.to_hub], 1);
                c.setCoefficient(u, -M);

                // pickup time constraint
                MPConstraint pickup_constraint = solver.makeConstraint(-M_time, pickup_time + M_time);
                pickup_constraint.setCoefficient(arrival_time[k][req.from_hub], 1);
                pickup_constraint.setCoefficient(u, M_time);

                // delivery time constraint
                MPConstraint delivery_constraint = solver.makeConstraint(-M_time, delivery_time + M_time);
                delivery_constraint.setCoefficient(arrival_time[k][req.to_hub], 1);
                delivery_constraint.setCoefficient(u, M_time);
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
