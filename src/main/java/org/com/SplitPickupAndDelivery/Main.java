package org.com.SplitPickupAndDelivery;

import org.com.SplitPickupAndDelivery.models.InputData;
import org.com.SplitPickupAndDelivery.models.Solution;
import org.com.SplitPickupAndDelivery.solver.*;
import org.com.SplitPickupAndDelivery.utils.IOExcelHandling;

public class Main {

    public static void main(String[] args) {
        InputData data = IOExcelHandling.readInputData("data/input-2.xlsx");
        MappedData.parseInput(data);
        MappedData.display();

        Parameters.TIME_LIMIT_S = 60;
        MILPPickupAndDeliveryWithTimeConstraints solver = new MILPPickupAndDeliveryWithTimeConstraints();
        MappedSolution raw_solution = solver.solve(true);

        Solution solution = MappedData.resolveOutput(raw_solution);
        IOExcelHandling.exportSolution("data/output.xlsx", solution);
    }
}
