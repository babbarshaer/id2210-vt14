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
public class CPUResourceGradientScenario extends Scenario {

    private static SimulationScenario scenario = new SimulationScenario() {
        {

            SimulationScenario.StochasticProcess peerAdd0 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(5, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(2), constant(10000)
                    );
                }
            };

            SimulationScenario.StochasticProcess peerAdd1 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(5, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(4), constant(10000)
                    );
                }
            };

            
            
            SimulationScenario.StochasticProcess peerAdd2 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(5, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(6), constant(10000)
                    );
                }
            };

            SimulationScenario.StochasticProcess peerAdd3 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(5, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(8), constant(10000)
                    );
                }
            };

            SimulationScenario.StochasticProcess peerAdd4 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(5, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(10), constant(10000)
                    );
                }
            };

            SimulationScenario.StochasticProcess peerAdd5 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(5, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(12), constant(10000)
                    );
                }
            };

            SimulationScenario.StochasticProcess peerAdd6 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(5, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(14), constant(10000)
                    );
                }
            };
            
            
            SimulationScenario.StochasticProcess peerAdd7 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(5, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(3), constant(10000)
                    );
                }
            };
            
            
            SimulationScenario.StochasticProcess peerAdd8 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(5, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(5), constant(10000)
                    );
                }
            };
            
            SimulationScenario.StochasticProcess peerAdd9 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(5, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(1), constant(10000)
                    );
                }
            };
            
            

            SimulationScenario.StochasticProcess process1 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(500, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(2), constant(1000),
                            constant(10 * 2 * 1) // 1 minute
                    );
                }
            };

            SimulationScenario.StochasticProcess process2 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(50));
                    raise(500, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(3), constant(1000),
                            constant(10 * 3 * 1) // 1 minute
                    );
                }
            };

            SimulationScenario.StochasticProcess process3 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(500, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(5), constant(1000),
                            constant(10 * 4 * 1) // 1 minute
                    );
                }
            };

            SimulationScenario.StochasticProcess process4 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(500, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(7), constant(1000),
                            constant(10 * 3 * 1) // 1 minute
                    );
                }
            };

            SimulationScenario.StochasticProcess process5 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(500, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(9), constant(1000),
                            constant(10 * 4 * 1) // 1 minute
                    );
                }
            };

            SimulationScenario.StochasticProcess process6 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(500, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(11), constant(1000),
                            constant(10 * 4 * 1) // 1 minute
                    );
                }
            };
            
            SimulationScenario.StochasticProcess process7 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(500, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(14), constant(1000),
                            constant(10 * 4 * 1) // 1 minute
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

            // Simply state the number of requests to be scheduled in the system.
            SimulationScenario.StochasticProcess bootstrapUtilizationManager = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(1, Operations.bootstrapUtilizationHandler, constant(1000));
                }
            };

            SimulationScenario.StochasticProcess resourceRequestInitiation = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(1, Operations.resourceRequestInitiation);
                }
            };

            // CPU Gradient Test.
            peerAdd0.start();
            peerAdd1.startAfterTerminationOf(5000, peerAdd0);
            peerAdd2.startAfterTerminationOf(5000, peerAdd1);
            peerAdd3.startAfterTerminationOf(100, peerAdd2);
            peerAdd4.startAfterTerminationOf(100, peerAdd3);
            peerAdd5.startAfterTerminationOf(100, peerAdd4);
            peerAdd6.startAfterTerminationOf(100, peerAdd5);
            peerAdd7.startAfterTerminationOf(100, peerAdd6);
            peerAdd8.startAfterTerminationOf(100, peerAdd7);
            peerAdd8.startAfterTerminationOf(100, peerAdd8);

            bootstrapUtilizationManager.startAfterTerminationOf(100, peerAdd1);

            // Peer Initialization and bootstrapping of the utilization manager complete.
            // Schedule the resources now.
//            process1.startAfterTerminationOf(1000, peerAdd5);
            
//            process2.startAfterTerminationOf(1000, process1);
//            process3.startAfterTerminationOf(1000, process2);
//
//            process4.startAfterTerminationOf(1000, process3);
//            process5.startAfterTerminationOf(1000, process4);
            process6.startAfterTerminationOf(1000, peerAdd0);
            resourceRequestInitiation.startAtSameTimeWith(process6);
            process7.startAfterTerminationOf(1000, process6);
            
            //requestSchedulingCompletionProcess.startAtSameTimeWith(process1);
//            process2.startAfterTerminationOf(3000, process0);
//            process3.startAfterTerminationOf(2000, process0);
//            process4.startAfterStartOf(200, process1);
//         terminateProcess.startAfterTerminationOf(1000*1000, process1);
        }
    };

    // -------------------------------------------------------------------
    public CPUResourceGradientScenario() {
        super(scenario);
    }

}
