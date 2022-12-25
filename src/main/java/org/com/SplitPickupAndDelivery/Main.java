package org.com.SplitPickupAndDelivery;

import org.com.SplitPickupAndDelivery.models.InputData;
import org.com.SplitPickupAndDelivery.models.Solution;
import org.com.SplitPickupAndDelivery.solver.MILPPickupAndDelivery;
import org.com.SplitPickupAndDelivery.solver.MappedData;
import org.com.SplitPickupAndDelivery.solver.MappedSolution;
import org.com.SplitPickupAndDelivery.utils.IOExcelHandling;

public class Main {

    public static void main(String[] args) {
        InputData data = IOExcelHandling.readInputData("data/input.xlsx");
        MappedData.parseInput(data);
        MappedData.display();

        MILPPickupAndDelivery solver = new MILPPickupAndDelivery();
        MappedSolution raw_solution = solver.solve(true);

        Solution solution = MappedData.resolveOutput(raw_solution);
        IOExcelHandling.exportSolution("data/output.xlsx", solution);
    }
}
