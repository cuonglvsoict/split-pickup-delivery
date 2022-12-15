package org.tiki.SplitPickupAndDelivery;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.util.ArrayList;

public class MILPSolver {

    private ArrayList<Integer> H;       // set of hubs
    private ArrayList<Integer> S1;      // set of departure nodes
    private ArrayList<Integer> S2;      // set of arrival nodes
    private ArrayList<Integer> K;       // set of all trucks
    private double[][] request;
    private int num_nodes;
    private MPSolver solver;
    private MPVariable[][][]  x;
    private MPVariable t[][];
    private MPVariable[][] z;
    private MPVariable[][][] p;

    public Solution solve() {
        H = new ArrayList<>();
        for (int i=0; i<Input.N; i++) {
            H.add(i);
        }

        S1 = new ArrayList<>();
        for (int i=Input.N; i<Input.N + Input.K; i++) {
            S1.add(i);
        }

        S2 = new ArrayList<>();
        for (int i=Input.N + Input.K; i<Input.N + 2 * Input.K; i++) {
            S2.add(i);
        }

        K = new ArrayList<>();
        for (int k=0; k<Input.K; k++) {
            K.add(k);
        }

        num_nodes = H.size() + S1.size() + S2.size();

        request = new double[num_nodes][num_nodes];
        for (Request r: Input.requests) {
            request[r.from_hub][r.to_hub] = r.quantity;
            System.out.println(r.from_hub + " -> " + r.to_hub + ": " + r.quantity);
        }

        Loader.loadNativeLibraries();
        solver = MPSolver.createSolver(String.valueOf(MPSolver.OptimizationProblemType.SCIP_MIXED_INTEGER_PROGRAMMING));

        if (solver == null) {
            System.err.println("Could not create solver SCIP");
            return null;
        }

        build_model();
        create_obj();

        final MPSolver.ResultStatus resultStatus = solver.solve();

        if (resultStatus == MPSolver.ResultStatus.OPTIMAL || resultStatus == MPSolver.ResultStatus.FEASIBLE) {
            Solution output = new Solution();
            System.out.println("Solution found");

            for (int k: K) {
                for (int i=0; i<num_nodes; i++) {
                    for (int j=0; j<num_nodes; j++) {
                        if (x[k][i][j].solutionValue() > 0) {


                            double pick = 0;
                            double drop = 0;
                            for (int v: H) {
                                pick += p[k][j][v].solutionValue();
                                drop += p[k][v][j].solutionValue();
                            }

                            System.out.println(k + ": " + i + " -> " + j + ", " + t[k][j].solutionValue() + ", " + pick + ", " + drop + ", " + z[k][j].solutionValue());
                        }
                    }
                }
            }

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
        x = new MPVariable[Input.K][num_nodes][num_nodes];
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
                z[k][i] = solver.makeNumVar(0, Input.capacity.get(k), "");
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
        // constraint 1
        for (int k: K) {
            MPConstraint c1 = solver.makeConstraint(1,1);
            for (int j: HS2) {
                c1.setCoefficient(x[k][H.size() + k][j], 1);
            }
        }

        // constraint 1.1
        MPConstraint depart = solver.makeConstraint(K.size(), K.size());
        for (int k: K) {
            for (int i: K) {
                for (int j: HS2) {
                    depart.setCoefficient(x[k][H.size() + i][j], 1);
                }
            }
        }

        // constraint 2
        for (int k: K) {
            MPConstraint c = solver.makeConstraint(1,1);

            for (int i: S1H) {
                c.setCoefficient(x[k][i][H.size() + K.size() + k], 1);
            }
        }

        // constraint 3
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

        // constraint 4
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

        // constraint 5
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
        // constraint 6, 7: in the definition of z

        // constraint 9
        for (int i: H) {
            for (int j: H) {
                MPConstraint c = solver.makeConstraint(request[i][j], request[i][j]);
                for (int k: K) {
                    c.setCoefficient(p[k][i][j], 1);
                }
            }
        }

        // constraint 11
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

        // constraint 12
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

        System.out.println("Model built successfully...");
    }

    private void create_obj() {
//        MPObjective obj = solver.objective();
//        for (int k: K) {
//            for (int i=0; i<num_nodes; i++) {
//                for (int j=0; j<num_nodes; j++) {
//                     obj.setCoefficient(x[k][i][j], 1);
//                }
//            }
//        }
//
//        obj.setMinimization();
    }
}
