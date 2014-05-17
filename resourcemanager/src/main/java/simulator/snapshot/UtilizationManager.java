/*
 * To change this license header, choose License Headers in Project Properties
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package simulator.snapshot;

import common.simulation.BootstrapUtilizationHandler;
import common.simulation.ResourceRequestInitiation;
import common.simulation.SimulatorPort;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import resourcemanager.system.peer.rm.RequestCompletion;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.p2p.simulator.P2pSimulator;

/**
 * Simply get the Utilization from the Peers and then Basically, calculate the
 * final resource usage and average and mean time.
 *
 * @author babbarshaer
 */
public class UtilizationManager extends ComponentDefinition {

    static List<Long> requestIdList = new ArrayList<Long>();
    long numberOfJobsScheduled;
    Positive<SimulatorPort> p2pSimulatorPort = requires(SimulatorPort.class);
    long startTime;
    long finishTime;
    
    private final Logger logger = LoggerFactory.getLogger(UtilizationManager.class);
    
    // Create A UtilizationManager Port to send the data to.
    Negative<UtilizationPort> utilizationManagerPort = provides(UtilizationPort.class);

    public UtilizationManager() {
        subscribe(bootstrapHandler, p2pSimulatorPort);
        subscribe(requestInitiationHandler, p2pSimulatorPort);
        subscribe(requestCompletionHandler, utilizationManagerPort);
        
    }

    /**
     * Inform the utilization manager about the requests to be scheduled.
     */
    Handler<BootstrapUtilizationHandler> bootstrapHandler = new Handler<BootstrapUtilizationHandler>() {
        @Override
        public void handle(BootstrapUtilizationHandler event) {
            //logger.info("Received the bootstrap request .... ");
            numberOfJobsScheduled = event.getRequestsToBeScheduled();
        }
    };
    
    /**
     * Start with the counting the time as started with the request scheduling.
     */
    Handler<ResourceRequestInitiation> requestInitiationHandler = new Handler<ResourceRequestInitiation>() {
        @Override
        public void handle(ResourceRequestInitiation event) {
            //logger.info("Received the Resource Request Initiation Event .... ");
            startTime = System.currentTimeMillis();
        }
    };

    /**
     * Handler for the events received from the scheduler regarding the individual task completion.
     */
    Handler<RequestCompletion> requestCompletionHandler = new Handler<RequestCompletion>() {
        @Override
        public void handle(RequestCompletion event) {
            
            requestIdList.add(event.getId());
            
            if (requestIdList.size() == numberOfJobsScheduled) {
                finishTime = System.currentTimeMillis();
                computeTime();
            }
            else{
                logger.info("Jobs Completed: " + requestIdList.size());
            }
        }
    };

    
    /**
     * Compute the total time for now and send the message to the Data Center Simulator. 
     */
    private void computeTime() {
        long totalTime = finishTime - startTime;
        trigger(new Time(totalTime), utilizationManagerPort);
    }

}
