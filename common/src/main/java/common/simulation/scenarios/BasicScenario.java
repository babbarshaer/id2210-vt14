/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package common.simulation.scenarios;

import se.sics.kompics.p2p.experiment.dsl.SimulationScenario;

/**
 *
 * @author babbarshaer
 */
public class BasicScenario extends Scenario {

    private static SimulationScenario scenario = new SimulationScenario() {
        {

            SimulationScenario.StochasticProcess process0 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(10, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(8), constant(12000)
                    );
                }
            };

            SimulationScenario.StochasticProcess process1 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(50, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(2), constant(2000),
                            constant(1000 * 6 * 1) // 1 minute
                    );
                }
            };
            
             
            
             SimulationScenario.StochasticProcess process2 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(50));
                    raise(100, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(4), constant(3000),
                            constant(1000 * 3 * 1) // 1 minute
                    );
                }
            };
             
             SimulationScenario.StochasticProcess process3 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(120, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(5), constant(6700),
                            constant(1000 * 10 * 1) // 1 minute
                    );
                }
            };
             
             SimulationScenario.StochasticProcess process4 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(240, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(1), constant(850),
                            constant(1000 * 2 * 1) // 1 minute
                    );
                }
            };
             
            // TODO - not used yet
            SimulationScenario.StochasticProcess failPeersProcess = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(1, Operations.peerFail,
                            uniform(0, Integer.MAX_VALUE));
                }
            };

            SimulationScenario.StochasticProcess terminateProcess = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(1, Operations.terminate);
                }
            };
            process0.start();
            process1.startAfterTerminationOf(2000, process0);
            process2.startAfterTerminationOf(3000, process0);
            process3.startAfterTerminationOf(2000, process0);
            process4.startAfterStartOf(200, process1);
         //terminateProcess.startAfterTerminationOf(100*1000, process1);
        }
    };

    // -------------------------------------------------------------------
    public BasicScenario() {
        super(scenario);
    }

}
