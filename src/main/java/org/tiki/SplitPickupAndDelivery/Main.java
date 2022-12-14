package org.tiki.SplitPickupAndDelivery;

public class Main {

    public static void main(String[] args) {
        if (!Input.read_input_data("data/tiki-planning-test.txt")) {
            System.out.println("Failed to import problem input.");
        } else {
            System.out.println("Problem input loaded successfully...");
            Input.display();

            MILPSolver solver = new MILPSolver();
            solver.solve();
        }


    }
}
