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
import system.peer.RmPort;
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

            logger.info("Allocate resources:  cpu: " + event.getNumCpus() + " + memory: " + event.getMemoryInMbs() + " + id: " + event.getId());
            List<Address> currentNeighbors = getNeighborsBasedOnGradient();

            //FIXME :  Check for any buffered request in case the sample is received, based on the gradient using.
            //FIXME: A generic way to allocate the requests at this end.
            if (currentNeighbors.isEmpty()) {
                // FIXME : But this will create a skew in case of request distribution in case we start handling the requests like quickly.
                // Verify by simulations that the case is valid ....
                logger.info("Buffering the request .... " + event.getId());
                bufferedRequestsAtScheduler.add(event);
                return;
            }
            
            scheduleTheApplicationJob(event, currentNeighbors);
        }
    };

    /**
     * Based on the gradient specified fetch the neighbors to talk to for the
     * request.
     *
     * @return
     */
    private List<Address> getNeighborsBasedOnGradient() {

        // By Default return random neighbours.
        switch (gradientEnum) {

            case RANDOM:
                return randomNeighbours;

            case CPU:
                return cpuGradientNeighbors;

            case MEMORY:
                return memoryGradientNeighbors;

            case BOTH:
                return combinedGradientNeighbors;

            default:
                return randomNeighbours;
        }
    }

    /**
     * Simply Schedule the Received Resource Request.
     *
     * @param event
     */
    private void scheduleTheApplicationJob(RequestResource event, List<Address> neighbors) {

        List<Address> randomNeighboursSelected = new ArrayList<Address>();
        List<Integer> randomIndexArray = getRandomIndexArray(neighbors.size());
        
        for(Integer i : randomIndexArray){
            randomNeighboursSelected.add(neighbors.get(i));
        }
        if(randomNeighboursSelected.isEmpty()){
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
     * Simply Schedule the Received Resource Request.
     *
     * @param event
     */
    private void scheduleTheApplicationJobOnGradient( RequestResource event, List<PeerDescriptor> partnersDescriptor ) {

        List<Address> randomNeighboursSelected = null;
        List<Integer> randomIndexArray = getRandomIndexArray(partnersDescriptor.size());
        
        // Check if the available resources have required free resources.
        int entriesFound = 0;
        for(Integer i : randomIndexArray){
            
            if(entriesFound >= neighborCorrectnessCriteria){
                break;
            }
            
            PeerDescriptor currentPeerDescriptor = partnersDescriptor.get(i);
            if(currentPeerDescriptor.getFreeCpu() >= event.getNumCpus() && currentPeerDescriptor.getFreeMemory() >= event.getMemoryInMbs()){
                entriesFound +=1;
            }
        }
        
        if(entriesFound < neighborCorrectnessCriteria){
            // FIXME: Reschedule the request to the approximate best neighbor as selected by the softmax approach.
            // As the peer Descriptors will already be sorted.
        }
        
        randomNeighboursSelected = new ArrayList<Address>();
        
        for(PeerDescriptor pd : partnersDescriptor){
            randomNeighboursSelected.add(pd.getAddress());
        }
        for (Address dest : randomNeighboursSelected) {
            RequestResources.Request req = new RequestResources.Request(self, dest, event.getNumCpus(), event.getMemoryInMbs(), event.getId(), event.getTimeToHoldResource(), randomNeighboursSelected);
            trigger(req, networkPort);
        }

        ApplicationJobDetail applicationJobDetail = new ApplicationJobDetail(event);
        schedulerJobList.add(applicationJobDetail);
        logger.info("Job Received From Application: " + applicationJobDetail.toString());
    }

    Handler<TManSample> handleCpuGradientTManSample = new Handler<TManSample>() {
        @Override
        public void handle(TManSample event) {

            if (event.getSample().isEmpty()) {
                logger.info("Received Empty TMan Sample ********** ");
                return;
            }

//            logger.info("Received TMan Sample ..." + event.getSample().size());
            //Simply add the neighbours received from the Tman to the neighbours.
            List<Address> similarGradientNeighbours = event.getSample();
            cpuGradientNeighbors.clear();
            cpuGradientNeighbors.addAll(similarGradientNeighbours);

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
            //TODO: Better way to execute the tasks when the capacity becomes available.
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
     * Check for any buffered jobs to be scheduled.
     */
    private void checkForBufferedJobsAtScheduler() {

        List<Address> neighbors = getNeighborsBasedOnGradient();
        if (!neighbors.isEmpty()) {

            for (RequestResource requestResource : bufferedRequestsAtScheduler) {
                scheduleTheApplicationJob(requestResource, neighbors);
            }
            bufferedRequestsAtScheduler.clear();
        }
    }

}
