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

    // Initialization of the Resource Manager.
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

            // pick a random neighbour to ask for index updates from. 
            // You can change this policy if you want to.
            // Maybe a gradient neighbour who is closer to the leader?
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

            //logger.info("Received resource request from: " + event.getSource().toString());
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
     * Periodically Cyclon sends this event to the Resource Manager which updates the Random Neighbors.
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
                return  memoryGradientNeighbors;
                
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

        ApplicationJobDetail applicationJobDetail = new ApplicationJobDetail(event);
        schedulerJobList.add(applicationJobDetail);
//            logger.info("Job Received From Application: " + applicationJobDetail.toString());

        List<Address> randomNeighboursSelected = getRandomPeersForProbing(neighbors);
        if (randomNeighboursSelected == null) {
            logger.info(" No Scheduling for the task ...  " + applicationJobDetail.getRequestId());
        }

        for (Address dest : randomNeighboursSelected) {
            RequestResources.Request req = new RequestResources.Request(self, dest, event.getNumCpus(), event.getMemoryInMbs(), event.getId(), event.getTimeToHoldResource(), randomNeighboursSelected);
            trigger(req, networkPort);
        }
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

            for (WorkerJobDetail peerJobDetail : workerJobList) {
                if (peerJobDetail.getRequestId() == event.getRequestId() && peerJobDetail.getJobStatus() == JobStatusEnum.QUEUED) {
                    // Job Detail Found.
                    requiredJobDetail = peerJobDetail;
                    logger.info("*************** Successfully Canceled the Job  *************" + requiredJobDetail.getRequestId());
                    break;
                }
            }
            // Remove the found job detail.
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
     *
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
     * Check for any buffered jobs to be scheduled.
    */
    private void checkForBufferedJobsAtScheduler(){
        
        List<Address> neighbors = getNeighborsBasedOnGradient();
        if(!neighbors.isEmpty()){
            
            for(RequestResource requestResource : bufferedRequestsAtScheduler){
                scheduleTheApplicationJob(requestResource, neighbors);
            }
            bufferedRequestsAtScheduler.clear();
        }
    }

}
