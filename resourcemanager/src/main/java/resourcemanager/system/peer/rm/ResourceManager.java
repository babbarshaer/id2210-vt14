package resourcemanager.system.peer.rm;

import com.sun.jndi.dns.ResourceRecord;
import common.configuration.RmConfiguration;
import common.peer.AvailableResources;
import common.simulation.RequestResource;
import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;
import cyclon.system.peer.cyclon.PeerDescriptor;
import java.rmi.activation.ActivationGroup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.web.Web;
import sun.swing.SwingUtilities2;
import system.peer.RmPort;
import tman.system.peer.tman.ComparatorById;
import tman.system.peer.tman.GradientEnum;
import tman.system.peer.tman.TManSample;
import tman.system.peer.tman.TManSamplePort;

/**
 * Should have some comments here.
 *
 * @author jdowling
 */
public final class ResourceManager extends ComponentDefinition {

    //Testing Purposes.
    int testJobs = 0;
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);
    Positive<RmPort> indexPort = positive(RmPort.class);
    Positive<Network> networkPort = positive(Network.class);
    Positive<Timer> timerPort = positive(Timer.class);
    Negative<Web> webPort = negative(Web.class);
    Positive<CyclonSamplePort> cyclonSamplePort = positive(CyclonSamplePort.class);
    Positive<TManSamplePort> tmanPort = positive(TManSamplePort.class);

    ArrayList<Address> randomNeighbours = new ArrayList<Address>();
    ArrayList<Address> cpuGradientNeighbors = new ArrayList<Address>();
    ArrayList<Address> memoryGradientNeighbors = new ArrayList<Address>();
    ArrayList<Address> combinedGradientNeighbors = new ArrayList<Address>();
    GradientEnum gradientEnum = GradientEnum.CPU;
    boolean useGradient = true;
    static final double TEMPERATURE = 0.8;

    ArrayList<PeerDescriptor> cpuGradientNeighborsDescriptors = new ArrayList<PeerDescriptor>();
    private static final int neighborCorrectnessCriteria = 1;

    private Address self;
    private RmConfiguration configuration;
    Random random;
    private AvailableResources availableResources;
    private List<RequestResource> bufferedRequestsAtScheduler;

    Comparator<PeerDescriptor> peerAgeComparator = new Comparator<PeerDescriptor>() {
        @Override
        public int compare(PeerDescriptor t, PeerDescriptor t1) {
            if (t.getAge() > t1.getAge()) {
                return 1;
            } else {
                return -1;
            }
        }
    };

    private final int probeRatio = 4;
    private LinkedList<ApplicationJobDetail> schedulerJobList;
    private LinkedList<WorkerJobDetail> workerJobList;

    public ResourceManager() {

        subscribe(handleInit, control);
        subscribe(handleCyclonSample, cyclonSamplePort);
        subscribe(handleRequestResource, indexPort);
        subscribe(handleUpdateTimeout, timerPort);
        subscribe(handleResourceAllocationRequest, networkPort);
        subscribe(handleResourceAllocationResponse, networkPort);
        subscribe(handleCpuGradientTManSample, tmanPort);

        // Gradient Ascent Search Handler.
        subscribe(rescheduleJobHandler, networkPort);
        subscribe(jobCompletionTimeout, timerPort);
        subscribe(requestCompletionEvent, networkPort);
        subscribe(jobCancellationHandler, networkPort);

    }

    Handler<RmInit> handleInit = new Handler<RmInit>() {
        @Override
        public void handle(RmInit init) {
            self = init.getSelf();
            configuration = init.getConfiguration();
            random = new Random(init.getConfiguration().getSeed());
            availableResources = init.getAvailableResources();
            long period = configuration.getPeriod();
            schedulerJobList = new LinkedList<ApplicationJobDetail>();
            workerJobList = new LinkedList<WorkerJobDetail>();
            bufferedRequestsAtScheduler = new ArrayList<RequestResource>();
            SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(period, period);
            rst.setTimeoutEvent(new UpdateTimeout(rst));
            trigger(rst, timerPort);

        }
    };

    //TODO: Functionality needs to be implemented here.
    Handler<UpdateTimeout> handleUpdateTimeout = new Handler<UpdateTimeout>() {
        @Override
        public void handle(UpdateTimeout event) {

//            if (neighbours.isEmpty()) {
//                return;
//            }
//
//            // TODO: What data is to be exchanged with the neighbour ?
//            Address dest = neighbours.get(random.nextInt(neighbours.size()));
        }
    };
    
    /**
     * Periodically Cyclon sends this event to the Resource Manager which
     * updates the Random Neighbors.
     */
    Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
        @Override
        public void handle(CyclonSample event) {

//            logger.info("Received Cyclon Samples: " + event.getSample().size());
            // receive a new list of neighbours
            randomNeighbours.clear();
            randomNeighbours.addAll(event.getSample());

            checkForBufferedJobsAtScheduler();
        }
    };

    /**
     * Requesting the scheduler to schedule the job.
     */
    Handler<RequestResource> handleRequestResource = new Handler<RequestResource>() {

        @Override
        public void handle(RequestResource event) {

            logger.info("Allocate resources Received By : " +  self  + ":  cpu: " + event.getNumCpus() + " + memory: " + event.getMemoryInMbs() + " + id: " + event.getId());

            if (useGradient) {
                //Use Gradient Approach
                ArrayList<PeerDescriptor> gradientBasedNeighborInfo = getNeighborsInfoBasedOnGradient();
                if (gradientBasedNeighborInfo.isEmpty()) {
                    //Tman didn't send the samples yet.
                    logger.info("Buffering the request .... " + event.getId());
                    bufferedRequestsAtScheduler.add(event);
                    return;
                }
                scheduleTheApplicationJobOnGradient(event, gradientBasedNeighborInfo);
            } else {
                // Use Random Approach.
                ArrayList<Address> currentNeighbors = getRandomNeighbors();
                if (currentNeighbors.isEmpty()) {
                    logger.info("Buffering the request .... " + event.getId());
                    bufferedRequestsAtScheduler.add(event);
                    return;
                }
                scheduleTheApplicationJob(event, currentNeighbors);
            }
        }
    };
    
    // Handler for the resource request being sent by the neighbour peer.
    Handler<RequestResources.Request> handleResourceAllocationRequest = new Handler<RequestResources.Request>() {
        @Override
        public void handle(RequestResources.Request event) {

            // Step1: Create an instance of job detail as submitted by the peer.
            WorkerJobDetail peerJobDetail = new WorkerJobDetail(event.getNumCpus(), event.getAmountMemInMb(), event.getRequestId(), event.getTimeToHoldResource(), event.getSource(), event.getPeers());
            workerJobList.add(peerJobDetail);

            //Step2: Check for free resources and then allocate them.
            checkResourcesAndExecute();
        }
    };

    /**
     * @deprecated.
     */
    Handler<RequestResources.Response> handleResourceAllocationResponse = new Handler<RequestResources.Response>() {
        @Override
        public void handle(RequestResources.Response event) {
        }
    };
    

    /**
     * Based on the gradient specified fetch the neighbors to talk to for the
     * request.
     *
     * @return
     */
    private ArrayList<PeerDescriptor> getNeighborsInfoBasedOnGradient() {

        // By Default return random neighbours.
        switch (gradientEnum) {
            case CPU:
                return cpuGradientNeighborsDescriptors;
            default:
                return cpuGradientNeighborsDescriptors;

        }
    }

    private ArrayList<Address> getRandomNeighbors() {
        return randomNeighbours;
    }

    /**
     * Simply Schedule the Received Resource Request.
     *
     * @param event
     */
    private void scheduleTheApplicationJob(RequestResource event, List<Address> neighbors) {

        List<Address> randomNeighboursSelected = new ArrayList<Address>();
        List<Integer> randomIndexArray = getRandomIndexArray(neighbors.size());

        for (Integer i : randomIndexArray) {
            randomNeighboursSelected.add(neighbors.get(i));
        }
        if (randomNeighboursSelected.isEmpty()) {
            logger.info("Check the random selection logic .....");
            return;
        }

        for (Address dest : randomNeighboursSelected) {
            RequestResources.Request req = new RequestResources.Request(self, dest, event.getNumCpus(), event.getMemoryInMbs(), event.getId(), event.getTimeToHoldResource(), randomNeighboursSelected);
            trigger(req, networkPort);
        }

        //Schedule the job, if everything looks good.
        ApplicationJobDetail applicationJobDetail = new ApplicationJobDetail(event);
        schedulerJobList.add(applicationJobDetail);
        logger.info("Job Received From Application: " + applicationJobDetail.toString());
    }

    /**
     * Schedule the application job when received for a particular gradient.
     *
     * @param event
     */
    private void scheduleTheApplicationJobOnGradient(RequestResource event, List<PeerDescriptor> partnersDescriptor) {

        List<Address> randomNeighboursSelected = null;
        List<Integer> randomIndexArray = getRandomIndexArray(partnersDescriptor.size());

        // Check if the available resources have required free resources.
        int entriesFound = 0;
        for (Integer i : randomIndexArray) {

            if (entriesFound >= neighborCorrectnessCriteria) {
                break;
            }

            PeerDescriptor currentPeerDescriptor = partnersDescriptor.get(i);
            if (currentPeerDescriptor.getFreeCpu() >= event.getNumCpus() && currentPeerDescriptor.getFreeMemory() >= event.getMemoryInMbs()) {
                entriesFound += 1;
            }
        }

        if (entriesFound < neighborCorrectnessCriteria) {
            // As the peer Descriptors will already be sorted.
            PeerDescriptor descriptor = getSoftMaxAddress(partnersDescriptor, TEMPERATURE);
            if (descriptor == null) {
                logger.info("~~ Not able to find the neighbor to reschedule the job ~~");
                return;
            }

            logger.info("Start the process of Rescheduling at :" + self  + " ~~To: " + descriptor.getAddress());
            RescheduleJob rescheduledJob = new RescheduleJob(self, descriptor.getAddress(), event);
            trigger(rescheduledJob, networkPort);
            return;
        }
        
        randomNeighboursSelected = new ArrayList<Address>();
        
        for(Integer i : randomIndexArray){
            randomNeighboursSelected.add(partnersDescriptor.get(i).getAddress());
        }
        
        for (Address dest : randomNeighboursSelected) {
            RequestResources.Request req = new RequestResources.Request(self, dest, event.getNumCpus(), event.getMemoryInMbs(), event.getId(), event.getTimeToHoldResource(), randomNeighboursSelected);
            trigger(req, networkPort);
        }
        
        ApplicationJobDetail applicationJobDetail = new ApplicationJobDetail(event);
        schedulerJobList.add(applicationJobDetail);
        logger.info("Job Received From Application: " + applicationJobDetail.toString());
    }

    /**
     * Rescheduling Event Received by the
     */
    Handler<RescheduleJob> rescheduleJobHandler = new Handler<RescheduleJob>() {

        @Override
        public void handle(RescheduleJob rescheduleJobEvent) {

            RequestResource event = rescheduleJobEvent.getResourceRequest();

//            logger.info("Reschedule Job Requiring  cpu: " + event.getNumCpus() + " + memory: " + event.getMemoryInMbs() + " + id: " + event.getId());
            ArrayList<PeerDescriptor> currentNeighborsInfo = getNeighborsInfoBasedOnGradient();

            if (currentNeighborsInfo.isEmpty()) {
                // No samples returned by the TMan yet, so buffering the request.
                logger.info("Buffering the re-scheduled request .... " + event.getId());
                bufferedRequestsAtScheduler.add(event);
                return;
            }

            //Place the code of the job scheduling here, copied from the other scheduler.
            List<Address> randomNeighboursSelected;
            //FIXME: Add some selection logic here.
            List<Integer> randomIndexArray = getRandomIndexArray(currentNeighborsInfo.size());

            // Check if the available resources have required free resources.
            int entriesFound = 0;
            for (Integer i : randomIndexArray) {

                if (entriesFound == neighborCorrectnessCriteria) {
                    break;
                }

                PeerDescriptor currentPeerDescriptor = currentNeighborsInfo.get(i);
                if (currentPeerDescriptor.getFreeCpu() >= event.getNumCpus() && currentPeerDescriptor.getFreeMemory() >= event.getMemoryInMbs()) {
                    entriesFound += 1;
                }
            }

            if (entriesFound < neighborCorrectnessCriteria) {
                // As the peer Descriptors will already be sorted.
                PeerDescriptor descriptor = getSoftMaxAddress(currentNeighborsInfo, TEMPERATURE);
                if (descriptor == null) {
                    logger.info(" ~~  Not able to find the neighbor to reschedule the job ~~ ");
                    return;
                }

                // Extra logic to search send further the rescheduled job or buffering the request in case TTL =0;
                if (rescheduleJobEvent.getTTL() == 0) {
                    
//                    logger.info("~~~ TTL Becomes 0 now ... ~~~");
                    // Buffer the request at the current node.
                    bufferedRequestsAtScheduler.add(event);
                    return;
                }

                logger.info(" ~~~ Rescheduling the Job With ID: "+ event.getId() +" To:  "  + descriptor.getAddress() +"  ~~~ By: " + self);
                //  Again Reschedule the event as TTL != 0
                RescheduleJob job = new RescheduleJob(self, descriptor.getAddress(), event, rescheduleJobEvent.getTTL());
                // Further reschedule the request in the network.
                job.reduceTTL();
                trigger(job, networkPort);
                return;
            }
            
            logger.info(" ~~ ... RESCHEDULING SHOULD STOP BECAUSE FOUND THE NEIGHBOR ... ~~ ");
            randomNeighboursSelected = new ArrayList<Address>();
            for (Integer i : randomIndexArray) {
                randomNeighboursSelected.add(currentNeighborsInfo.get(i).getAddress());
            }
            
            for (Address dest : randomNeighboursSelected) {
                RequestResources.Request req = new RequestResources.Request(self, dest, event.getNumCpus(), event.getMemoryInMbs(), event.getId(), event.getTimeToHoldResource(), randomNeighboursSelected);
                trigger(req, networkPort);
            }
            
            ApplicationJobDetail applicationJobDetail = new ApplicationJobDetail(event);
            schedulerJobList.add(applicationJobDetail);
            logger.info("Job Received From Application added to the scheduler list :  " + applicationJobDetail.toString());
        }
    };

    /**
     * FIXME: Make this piece of code generic enough.
     */
    Handler<TManSample> handleCpuGradientTManSample = new Handler<TManSample>() {
        @Override
        public void handle(TManSample event) {

            if (event.getSample().isEmpty()) {
                // Code Flaw.
                logger.info("~~~ Received Empty TMan Sample ~~~");
                System.exit(1);
                return;
            }
            
            logger.info("Received Gradient Sample with size: " + event.getPartnersDescriptor().size());
            
            // As it is gradient sample, so only fetch the peer descriptor.
            ArrayList<PeerDescriptor> similarPartnersDescriptor = event.getPartnersDescriptor();
            cpuGradientNeighborsDescriptors.clear();
            cpuGradientNeighborsDescriptors.addAll(similarPartnersDescriptor);
            checkForBufferedJobsAtScheduler();
        }
    };

    /**
     * Check for the available resources and execute the task if resources
     * found.
     */
    private void checkResourcesAndExecute() {

        for (WorkerJobDetail peerJobDetail : workerJobList) {

            int cpuRequired = peerJobDetail.getCpu();
            int memoryRequired = peerJobDetail.getMemory();

            if (peerJobDetail.getJobStatus() == JobStatusEnum.QUEUED && availableResources.allocate(cpuRequired, memoryRequired)) {
                // Resources are available.

                //Step1: Send cancel messages to the remaining peers.
                List<Address> workers = peerJobDetail.getWorkers();
                for (Address addr : workers) {
                    if (addr != self) {
                        // Send cancel Job Message to every other peer.
                        CancelJob cancelJobMessage = new CancelJob(self, addr, peerJobDetail.getRequestId());
                        trigger(cancelJobMessage, networkPort);
                    }
                }
                //Step2: Change the status of the job, so that it is not picked again.
                peerJobDetail.setJobStatus(JobStatusEnum.PROCESSING);

                //Step3: Execute the job.
                executeJob(peerJobDetail);
            }
        }
    }

    /**
     * Cross Server Job Cancellation Handler.
     */
    Handler<CancelJob> jobCancellationHandler = new Handler<CancelJob>() {

        @Override
        public void handle(CancelJob event) {
            
            WorkerJobDetail requiredJobDetail = null;
            
            for (WorkerJobDetail workerJobDetail : workerJobList) {
                
                // If task is queued, then delete the task from queue.
                if (workerJobDetail.getRequestId() == event.getRequestId() && workerJobDetail.getJobStatus() == JobStatusEnum.QUEUED) {
                    requiredJobDetail = workerJobDetail;
                    logger.info("Job Cancellation Successful  ~~ " + requiredJobDetail.getRequestId());
                    break;
                }
            }

            if (requiredJobDetail != null) {
                workerJobList.remove(requiredJobDetail);
            }
        }
    };

    /**
     * Resource Request has been completed successfully.
     */
    Handler<JobCompletionTimeout> jobCompletionTimeout = new Handler<JobCompletionTimeout>() {

        @Override
        public void handle(JobCompletionTimeout event) {

            //Step1: Free The resources.
            WorkerJobDetail jobDetail = event.getPeerJobDetail();
            availableResources.release(jobDetail.getCpu(), jobDetail.getMemory());
            logger.info("Resources Released: " + "Cpu: " + jobDetail.getCpu() + " Memory: " + jobDetail.getMemory());

            //Step2: Remove the resource from the processed list as it has been completely processed.
            workerJobList.remove(jobDetail);

            //Step3:Send the completion request to the scheduler about the completion of the request.
            JobCompletionEvent requestCompletionEvent = new JobCompletionEvent(self, jobDetail.getSchedulerAddress(), jobDetail.getRequestId());
            trigger(requestCompletionEvent, networkPort);

            //Check the available resources again, to see which jobs can be configured.
            checkResourcesAndExecute();

        }
    };

    /**
     * Request has been processed completely by the worker.
     */
    Handler<JobCompletionEvent> requestCompletionEvent = new Handler<JobCompletionEvent>() {
        @Override
        public void handle(JobCompletionEvent event) {
            // Simply remove the completed job.
            ApplicationJobDetail jobDetail = new ApplicationJobDetail(event.getRequestId());
            schedulerJobList.remove(jobDetail);
            logger.info("Job: " + event.getRequestId() + " completed.");

        }
    };

    // Execute the job successfully.
    private void executeJob(WorkerJobDetail jobDetail) {
        //Schedule a timeout for the resource.
        ScheduleTimeout st = new ScheduleTimeout(jobDetail.getTimeToHoldResource());
        JobCompletionTimeout timeout = new JobCompletionTimeout(st, jobDetail);
        st.setTimeoutEvent(timeout);
        trigger(st, timerPort);
    }

    /**
     * Based on the neighbors it randomly selects the neighbors to be probed
     * randomly.
     *
     * @deprecated
     * @return
     */
    private List<Address> getRandomPeersForProbing(List<Address> neighbours) {

        ArrayList<Address> peers = new ArrayList<Address>();
        if (probeRatio >= neighbours.size()) {
            return neighbours;
        } else {

            List<Integer> indexArray = new ArrayList<Integer>();
            Random random = new Random();
            populateIndexArrayUpdated(indexArray, random, neighbours);

            // It Returns with the random indexes in the list.
            for (Integer i : indexArray) {
                peers.add(neighbours.get(i));
            }

        }
        return peers;
    }

    /**
     * Randomly select the peers.
     *
     * @deprecated
     * @param indexArray
     * @param random
     */
    private void populateIndexArrayUpdated(List<Integer> indexArray, Random random, List<Address> neighbours) {

        while (indexArray.size() < probeRatio) {
            boolean duplicate = false;
            //Iterate over the index array.
            int nextInt = random.nextInt(neighbours.size());
            for (Integer i : indexArray) {
                if (i == nextInt) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                indexArray.add(nextInt);
            }
        }

    }

    /**
     * Simply supply the neighbor size to get the random index array.
     *
     * @param neighboursSize
     * @return
     */
    private List<Integer> getRandomIndexArray(int neighboursSize) {

        List<Integer> randomIndexArray = new ArrayList<Integer>();
        Random randomVariable = new Random();

        if (neighboursSize <= probeRatio) {
            // Add all the values in the random index array.
            for (int i = 0; i < neighboursSize; i++) {
                randomIndexArray.add(i);
            }
        } 
        else {
            while (randomIndexArray.size() < probeRatio) {
                boolean duplicate = false;
                //Iterate over the index array.
                int nextInt = randomVariable.nextInt(neighboursSize);
                for (Integer i : randomIndexArray) {
                    if (i == nextInt) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    randomIndexArray.add(nextInt);
                }
            }
        }
        return randomIndexArray;
    }

    /**
     * 
     * Check if any buffered jobs can be scheduled..
     */
    
    private void checkForBufferedJobsAtScheduler() {
        
        if (useGradient) {
            
            //FIXME: Move on to send the request to per gradient basis.
            ArrayList<PeerDescriptor> partnerDescriptors = getNeighborsInfoBasedOnGradient();
            
            if (!partnerDescriptors.isEmpty()) {
                for (RequestResource requestResource : bufferedRequestsAtScheduler) {
                    scheduleTheApplicationJobOnGradient(requestResource, partnerDescriptors);
                }
                bufferedRequestsAtScheduler.clear();
            }
        } 
        
        // Get neighbours from the random approach and schedule them.
        else {
            List<Address> neighbors = getRandomNeighbors();
            
            if (!neighbors.isEmpty()) {
                for (RequestResource requestResource : bufferedRequestsAtScheduler) {
                    scheduleTheApplicationJob(requestResource, neighbors);
                }
                bufferedRequestsAtScheduler.clear();
            }
        }
    }

    /**
     * Fetch the best entry to forward the request in case of rescheduling.
     *
     * @param entries
     * @param temperature
     * @return
     */
    private PeerDescriptor getSoftMaxAddress(List<PeerDescriptor> entries, double temperature) {

        // FIXME: Create a separate class for sorting of data in the preference order.
        // For now the records are sorted. 
        double rnd = random.nextDouble();
        double total = 0.0d;
        double[] values = new double[entries.size()];
        int j = entries.size() + 1;
        for (int i = 0; i < entries.size(); i++) {
            // get inverse of values - lowest have highest value.
            double val = j;
            j--;
            values[i] = Math.exp(val / temperature);
            total += values[i];
        }

        for (int i = 0; i < values.length; i++) {
            if (i != 0) {
                values[i] += values[i - 1];
            }
            // normalise the probability for this entry
            double normalisedUtility = values[i] / total;
            if (normalisedUtility >= rnd) {
                return entries.get(i);
            }
        }
        return entries.get(entries.size() - 1);
    }
}
