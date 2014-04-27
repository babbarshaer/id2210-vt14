package resourcemanager.system.peer.rm;

import common.configuration.RmConfiguration;
import common.peer.AvailableResources;
import common.simulation.RequestResource;
import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;
import cyclon.system.peer.cyclon.PeerDescriptor;
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
    ArrayList<Address> neighbours = new ArrayList<Address>();
    private Address self;
    private RmConfiguration configuration;
    Random random;
    private AvailableResources availableResources;
    // When you partition the index you need to find new nodes.
    // This is a routing table maintaining a list of pairs in each partition.
    private Map<Integer, List<PeerDescriptor>> routingTable;
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
        subscribe(handleTManSample, tmanPort);

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
            routingTable = new HashMap<Integer, List<PeerDescriptor>>(configuration.getNumPartitions());
            random = new Random(init.getConfiguration().getSeed());
            availableResources = init.getAvailableResources();
            long period = configuration.getPeriod();
            schedulerJobList = new LinkedList<ApplicationJobDetail>();
            workerJobList = new LinkedList<WorkerJobDetail>();
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
            if (neighbours.isEmpty()) {
                return;
            }

            // TODO: What data is to be exchanged with the neighbour ?
            Address dest = neighbours.get(random.nextInt(neighbours.size()));

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

    // Periodically Cyclon sends this event to the Resource Manager which contains the random sample.
    Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
        @Override
        public void handle(CyclonSample event) {
            logger.info("Received samples: " + event.getSample().size());

            // receive a new list of neighbours
            neighbours.clear();
            neighbours.addAll(event.getSample());
            
            // update routing tables
            // TODO: Need for the routing tables ?
            for (Address p : neighbours) {
                int partition = p.getId() % configuration.getNumPartitions();
                List<PeerDescriptor> nodes = routingTable.get(partition);
                if (nodes == null) {
                    nodes = new ArrayList<PeerDescriptor>();
                    routingTable.put(partition, nodes);
                }
                // Note - this might replace an existing entry in Lucene
                //TODO:  For all the addresses why new entries are pushed in the tables ?

                nodes.add(new PeerDescriptor(p));
                // keep the freshest descriptors in this partition
                Collections.sort(nodes, peerAgeComparator);
                List<PeerDescriptor> nodesToRemove = new ArrayList<PeerDescriptor>();
                for (int i = nodes.size(); i > configuration.getMaxNumRoutingEntries(); i--) {
                    nodesToRemove.add(nodes.get(i - 1));
                }
                // Removing the extra nodes in the view to bring back the view size to a constant. 
                // The node selected is based on the policy used in Cyclon.
                nodes.removeAll(nodesToRemove);
            }
        }
    };

    
    /**
     * Requesting the scheduler to schedule the job.
     */
    Handler<RequestResource> handleRequestResource = new Handler<RequestResource>() {
        @Override
        public void handle(RequestResource event) {

            logger.info("Allocate resources:  cpu: " + event.getNumCpus() + " + memory: " + event.getMemoryInMbs() + " + id: " + event.getId());

            if (neighbours.isEmpty()) {
                return;
            }

            ApplicationJobDetail applicationJobDetail = new ApplicationJobDetail(event);
            schedulerJobList.add(applicationJobDetail);
            logger.info("Job Received From Application: " + applicationJobDetail.toString());

            List<Address> randomNeighbours = getRandomPeersForProbing();
            if(randomNeighbours == null){
                logger.info(" No Scheduling for the task ...  " + applicationJobDetail.getRequestId());
            }
            
            for (Address dest : randomNeighbours) {    
                RequestResources.Request req = new RequestResources.Request(self, dest, event.getNumCpus(), event.getMemoryInMbs(), event.getId(), event.getTimeToHoldResource(), randomNeighbours);
                trigger(req, networkPort);
            }
        }
    };

    Handler<TManSample> handleTManSample = new Handler<TManSample>() {
        @Override
        public void handle(TManSample event) {
            // TODO: 
        }
    };

    /**
     * Check for the available resources and execute the task if resources found.
     */
    private void checkResourcesAndExecute() {

        for (WorkerJobDetail peerJobDetail : workerJobList) {
            
            int cpuRequired = peerJobDetail.getCpu();
            int memoryRequired = peerJobDetail.getMemory();
            
            if(peerJobDetail.getJobStatus() == JobStatusEnum.QUEUED && availableResources.allocate(cpuRequired, memoryRequired)){
                // Resources are available.
                
                //Step1: Send cancel messages to the remaining peers.
                List<Address> workers = peerJobDetail.getWorkers();
                for(Address addr : workers){
                    if(addr !=  self){
                        // Send cancel Job Message to every other peer.
                        CancelJob cancelJobMessage = new CancelJob(self, addr, peerJobDetail.getRequestId());
                        trigger(cancelJobMessage,networkPort);
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
            
            for(WorkerJobDetail peerJobDetail : workerJobList){
                if(peerJobDetail.getRequestId() == event.getRequestId() && peerJobDetail.getJobStatus() == JobStatusEnum.QUEUED){
                    // Job Detail Found.
                    requiredJobDetail = peerJobDetail;
                    logger.info("*************** Successfully Canceled the Job  *************" + requiredJobDetail.getRequestId());
                    break;
                }
            }
            // Remove the found job detail.
            if(requiredJobDetail != null)
                workerJobList.remove(requiredJobDetail);
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
    private List<Address> getRandomPeersForProbing() {

        ArrayList<Address> peers = new ArrayList<Address>();
        if (probeRatio >= neighbours.size()) {
            peers = (ArrayList<Address>) neighbours.clone();
        } else {

            List<Integer> indexArray = new ArrayList<Integer>();
            Random random = new Random();
            populateIndexArrayUpdated(indexArray, random);

            // It Returns with the random indexes in the list.
            for (Integer i : indexArray) {
                peers.add(neighbours.get(i));
            }

        }
        return peers;
    }

    /**
     * @deprecated 
     * @param indexArray
     * @param random 
     */
    private void populateIndexArray(List<Integer> indexArray, Random random) {

        if (indexArray.size() == probeRatio) {
            return;
        }
        boolean duplicateFound = false;
        int currentRandomNumber = random.nextInt(neighbours.size());
        //Check if the random number is present in the array.
        for (Integer i : indexArray) {
            if (i == currentRandomNumber) {
                // Again search for the random index.
                duplicateFound = true;
                break;
            }
        }
        
        if(duplicateFound){
            populateIndexArray(indexArray, random);
        }
        
        if (!duplicateFound) {
            indexArray.add(currentRandomNumber);
            populateIndexArray(indexArray, random);

        }
    }
    
    /**
     * Randomly select the peers.
     * @param indexArray
     * @param random 
     */
    private void populateIndexArrayUpdated(List<Integer> indexArray , Random random){
        
        while(indexArray.size() < probeRatio){
            boolean duplicate = false;
            //Iterate over the index array.
            int nextInt = random.nextInt(neighbours.size());
            for(Integer i : indexArray){
                if(i == nextInt){
                    duplicate = true;
                    break;
                }
            }
            if(!duplicate){
                indexArray.add(nextInt);
            }
        }
        
    }

}
