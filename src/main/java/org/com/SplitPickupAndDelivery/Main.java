package org.com.SplitPickupAndDelivery;

public class Main {

    public static void main(String[] args) {
        if (!Input.read_input_data("data/test01.txt")) {
            System.out.println("Failed to import problem input.");
        } else {
            System.out.println("Problem input loaded successfully.");
            Input.display();

            Parameters.TIME_LIMIT_S = 60;
            // Parameters.NUMBER_OF_CPUs = Runtime.getRuntime().availableProcessors() - 1;

            MILPPickupAndDelivery solver = new MILPPickupAndDelivery();
            solver.solve(false);
        }


    }
}
