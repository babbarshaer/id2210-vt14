package main;

import simulator.core.DataCenterSimulationMain;
import common.configuration.Configuration;
import common.simulation.scenarios.BasicCPUBasedGradientScenario;
import common.simulation.scenarios.BasicMemoryBasedGradientScenario;
import common.simulation.scenarios.BasicScenario;
import common.simulation.scenarios.BatchRequestScenario;
import common.simulation.scenarios.CPUResourceGradientScenario;
import common.simulation.scenarios.GradientScenario;
import common.simulation.scenarios.Scenario;
import common.simulation.scenarios.Scenario1;
import common.simulation.scenarios.TestScenario;

public class Main {

    public static void main(String[] args) throws Throwable {
        // TODO - change the random seed, have the user pass it in.
        long seed = System.currentTimeMillis();
        Configuration configuration = new Configuration(seed);

        Scenario scenario = new BatchRequestScenario();
        scenario.setSeed(seed);
        scenario.getScenario().simulate(DataCenterSimulationMain.class);
    }
}
