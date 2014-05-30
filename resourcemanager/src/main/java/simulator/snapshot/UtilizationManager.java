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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import resourcemanager.system.peer.rm.RequestCompletion;
import resourcemanager.system.peer.rm.UpdateTimeout;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.p2p.simulator.P2pSimulator;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timer;

/**
 * Simply get the Utilization from the Peers and then Basically, calculate the
 * final resource usage and average and mean time.
 *
 * @author babbarshaer
 */
public class UtilizationManager extends ComponentDefinition {

    private List<Long> requestTimeTakenList = new ArrayList<Long>();
    long numberOfJobsScheduled;
    Positive<SimulatorPort> p2pSimulatorPort = requires(SimulatorPort.class);
    long startTime;
    long finishTime;
    long ninetyNinthIndex;
    long ninetyNinthIndexTime;
    boolean modified = true;
    int number = 0;

    private final Logger logger = LoggerFactory.getLogger(UtilizationManager.class);

    // Create A UtilizationManager Port to send the data to.
    Negative<UtilizationPort> utilizationManagerPort = provides(UtilizationPort.class);
    Positive<Timer> timerport = requires(Timer.class);

    public UtilizationManager() {

        subscribe(bootstrapHandler, p2pSimulatorPort);
        subscribe(requestInitiationHandler, p2pSimulatorPort);
        subscribe(requestCompletionHandler, utilizationManagerPort);
//        subscribe(updateTimeoutHandler, timerport);

    }

    Handler<UtilizationManagerInit> init = new Handler<UtilizationManagerInit>() {

        @Override
        public void handle(UtilizationManagerInit event) {

        }

    };

    Handler<UtilizationCalculationTimeout> updateTimeoutHandler = new Handler<UtilizationCalculationTimeout>() {

        @Override
        public void handle(UtilizationCalculationTimeout event) {
            logger.info(" Number of Completed Requests So Far ..... " + requestTimeTakenList.size());
        }

    };

    /**
     * Inform the utilization manager about the requests to be scheduled.
     */
    Handler<BootstrapUtilizationHandler> bootstrapHandler = new Handler<BootstrapUtilizationHandler>() {
        @Override
        public void handle(BootstrapUtilizationHandler event) {
            //logger.info("Received the bootstrap request .... ");
            numberOfJobsScheduled = event.getRequestsToBeScheduled();
            ninetyNinthIndex = (99 * numberOfJobsScheduled) / 100;
            logger.info(" 99% index for: " + numberOfJobsScheduled + " Jobs is: " + ninetyNinthIndex);

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
     * Handler for the events received from the scheduler regarding the
     * individual task completion.
     */
    Handler<RequestCompletion> requestCompletionHandler = new Handler<RequestCompletion>() {
        @Override
        public void handle(RequestCompletion event) {

//            requestIdList.add(event.getId());
            number += 1;

            if (number == ninetyNinthIndex) {
                ninetyNinthIndexTime = System.currentTimeMillis();
            }

            if (number == numberOfJobsScheduled) {
                finishTime = System.currentTimeMillis();
                computeTime();
            }

            requestTimeTakenList.add(event.getTimeTaken());

            logger.info("Jobs Completed: " + number);
        }
    };

    /**
     * Compute the total time for now and send the message to the Data Center
     * Simulator.
     */
    private void computeTime() {
        long totalTime = finishTime - startTime;
        
        long ninetyNinthTime;
       long averageTime;
       
        if(modified){
            
            long totalRequestTime =0;
            
            if(ninetyNinthIndex == 0){
                System.out.println("Cannot calculate 99th time for a single request ... ");
                System.exit(1);
            }
            
            Collections.sort(requestTimeTakenList, comparatorByTimeTaken);
            ninetyNinthTime = requestTimeTakenList.get((int) (ninetyNinthIndex-1));
            
            for(Long time : requestTimeTakenList){
                totalRequestTime += time;
            }
            
            averageTime = totalRequestTime/numberOfJobsScheduled;
        }
        
        else{
            ninetyNinthTime = ninetyNinthIndexTime - startTime;
            averageTime =0;
        }
        
        trigger(new Time(totalTime, ninetyNinthTime, averageTime), utilizationManagerPort);
    }

    private final Comparator<Long> comparatorByTimeTaken = new Comparator<Long>() {

        @Override
        public int compare(Long l1, Long l2) {

            if (l1 - l2 < 0) {
                return -1;
            } else if (l2 - l1 < 0) {
                return 1;
            } else {
                return 0;
            }

        }
    };

}
