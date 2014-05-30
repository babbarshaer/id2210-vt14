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
public class TestScenario extends Scenario{
    
    private static SimulationScenario scenario = new SimulationScenario() {
        {

            SimulationScenario.StochasticProcess peerAdd0 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(5, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(6), constant(6000)
                    );
                }
            };

            SimulationScenario.StochasticProcess peerAdd1 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(5, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(4), constant(4000)
                    );
                }
            };
            
            SimulationScenario.StochasticProcess peerAdd2 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(5, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(8), constant(10000)
                    );
                }
            };
            
            SimulationScenario.StochasticProcess peerAdd3 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(1000));
                    raise(5, Operations.peerJoin(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(10), constant(10000)
                    );
                }
            };
            
            
            SimulationScenario.StochasticProcess process1 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(1, Operations.batchRequest(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(2), constant(3), constant(3000),
                            constant(100 * 6 * 1) // 1 minute
                    );
                }
            };
            
             
            
             SimulationScenario.StochasticProcess process2 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(50));
                    raise(1000, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(3), constant(4000),
                            constant(1000 * 3 * 1) // 1 minute
                    );
                }
            };
             
             SimulationScenario.StochasticProcess process3 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(1200, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(6), constant(5700),
                            constant(1000 * 4 * 1) // 1 minute
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
             
             
             SimulationScenario.StochasticProcess testProcess1 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(1, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(5), constant(850),
                            constant(10000 * 2 * 1) // 1 minute
                    );
                }
            };
             
             SimulationScenario.StochasticProcess testProcess2 = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(1, Operations.requestResources(),
                            uniform(0, Integer.MAX_VALUE),
                            constant(2), constant(1850),
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
            
            SimulationScenario.StochasticProcess bootstrapUtilizationManager = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(1, Operations.bootstrapUtilizationHandler,constant(10));
                }
            };
            
            
            SimulationScenario.StochasticProcess resourceRequestInitiation = new SimulationScenario.StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(1, Operations.resourceRequestInitiation);
                }
            };
            
            
            //Simple Overlay adjustment Test.
            peerAdd0.start();
            peerAdd1.startAfterTerminationOf(1000,peerAdd0);
            peerAdd2.startAfterTerminationOf(1000, peerAdd1);
            bootstrapUtilizationManager.startAtSameTimeWith(peerAdd0);
//            bootstrapUtilizationManager.startAfterTerminationOf(100, peerAdd1);
            process1.startAfterTerminationOf( 3000 , peerAdd1);
            resourceRequestInitiation.startAtSameTimeWith(process1);
//            process2.startAtSameTimeWith(process1);
//            process3.startAfterTerminationOf(1000, peerAdd1);
//            peerAdd2.startAfterTerminationOf( 800, peerAdd1);
//            peerAdd3.startAfterTerminationOf(800, peerAdd2);
            
//           testProcess1.startAfterTerminationOf(100, peerAdd0);
//           testProcess2.startAfterTerminationOf(100, peerAdd0);
            
            //requestSchedulingCompletionProcess.startAtSameTimeWith(process1);
//            process2.startAfterTerminationOf(3000, process0);
//            process3.startAfterTerminationOf(2000, process0);
//            process4.startAfterStartOf(200, process1);
//         terminateProcess.startAfterTerminationOf(1000*1000, process1);
            
        }
    };

    // -------------------------------------------------------------------
    public TestScenario() {
        super(scenario);
    }
    
    
}
