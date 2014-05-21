package resourcemanager.system.peer.rm;

import common.configuration.RmConfiguration;
import common.peer.AvailableResources;
import common.simulation.RequestResource;
import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;
import cyclon.system.peer.cyclon.PeerDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.web.Web;
import simulator.snapshot.UtilizationPort;
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
    ArrayList<PeerDescriptor> randomNeighborsDescriptors = new ArrayList<PeerDescriptor>();

    boolean useGradient = true;
    static final double TEMPERATURE = 0.68;

    Positive<UtilizationPort> utilizationPort = requires(UtilizationPort.class);

    private ArrayList<PeerDescriptor> cpuGradientFingerList = new ArrayList<PeerDescriptor>();
    private ArrayList<PeerDescriptor> memoryGradientFingerList = new ArrayList<PeerDescriptor>();
    private ArrayList<PeerDescriptor> cpuGradientNeighborsDescriptors = new ArrayList<PeerDescriptor>();
    private ArrayList<PeerDescriptor> memoryGradientNeighborsDescriptors = new ArrayList<PeerDescriptor>();

    private static final int neighborCorrectnessCriteria = 1;

    private Address self;
    private RmConfiguration configuration;
    Random random;
    private AvailableResources availableResources;
    private List<RequestResource> bufferedRequestsAtScheduler;
    long requestTimeout;
    private List<UUID> outstandingRequestsUUID;

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
        subscribe(handleGradientSample, tmanPort);

        // Intelligent Gradient Ascent Search Handler.
        subscribe(rescheduleJobHandler, networkPort);
        subscribe(jobCompletionTimeout, timerPort);
        subscribe(requestCompletionEvent, networkPort);
        subscribe(jobCancellationHandler, networkPort);
        subscribe(removeRescheduleHandler, networkPort);
        subscribe(resourceRequestTimeout, timerPort);
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
            requestTimeout = configuration.getRequestTimeout();
            outstandingRequestsUUID = new ArrayList<UUID>();
            SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(1000 , 10000);
            rst.setTimeoutEvent(new UpdateTimeout(rst));
            trigger(rst, timerPort);

        }
    };

    //TODO: Functionality needs to be implemented here.
    Handler<UpdateTimeout> handleUpdateTimeout = new Handler<UpdateTimeout>() {
        @Override
        public void handle(UpdateTimeout event) {

//          logger.info("Outstanding Buffered Requests Size:  " + bufferedRequestsAtScheduler.size() + " Node With CPU: "  + availableResources.getNumFreeCpus());
//            logger.info("CPU Gradient Length : "+  cpuGradientNeighborsDescriptors.size() + " Node Id: " + self.getId());
//            logger.info("CPU Finger Length : "    +  cpuGradientFingerList.size() + " Node Id: " + self.getId());
        }
    };

    /**
     * Periodically Cyclon sends this event to the Resource Manager which
     * updates the Random Neighbors.
     */
    Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
        @Override
        public void handle(CyclonSample event) {

//            if(event.getPartnersDescriptor().isEmpty()){
//                logger.info("Partner Descriptor : " + event.getPartnersDescriptor().size());
//                logger.info("Random Sample Size : " + event.getSample().size());
//            }
            
            randomNeighbours.clear();
            randomNeighbours.addAll(event.getSample());
            randomNeighborsDescriptors.clear();
            randomNeighborsDescriptors.addAll(event.getPartnersDescriptor());

            checkForBufferedJobsAtScheduler();
        }
    };

    /**
     * Requesting the scheduler to schedule the task.
     */
    Handler<RequestResource> handleRequestResource = new Handler<RequestResource>() {

        @Override
        public void handle(RequestResource event) {

            // Initiate the initial scheduling of the task received at the node.
            initiateTaskScheduling(event);
        }
    };

    /**
     * Based on the request event, fetch the gradient info.
     *
     * @param event
     * @return
     */
    private ArrayList<PeerDescriptor> getGradientNeighborsBasedOnRequest(RequestResource event) {

        if (isDominant(event.getNumCpus(), event.getMemoryInMbs() / 1000.0)) {
            return cpuGradientNeighborsDescriptors;
        } 
        else {
            return memoryGradientNeighborsDescriptors;
        }

    }

    // Handler for the resource request being sent by the neighbour peer.
    Handler<RequestResources.Request> handleResourceAllocationRequest = new Handler<RequestResources.Request>() {
        @Override
        public void handle(RequestResources.Request event) {

            // Step1: Create an instance of job detail as submitted by the peer.
            WorkerJobDetail peerJobDetail = new WorkerJobDetail(event.getNumCpus(), event.getAmountMemInMb(), event.getRequestId(), event.getTimeToHoldResource(), event.getSource(), event.getPeers(), event.getResourceRequestUUID());
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

    private ArrayList<Address> getRandomNeighbors() {
        return randomNeighbours;
    }

    /**
     * Simply return the random neighbors descriptors.
     *
     * @return Descriptors.
     */
    private ArrayList<PeerDescriptor> getRandomNeighborsDescriptors() {
        return this.randomNeighborsDescriptors;
    }

    /**
     * Simply Schedule the Received Resource Request.
     *
     * @param event
     */
    private boolean checkAndScheduleTheApplicationJob(RequestResource event, List<PeerDescriptor> neighbors) {

        List<Address> randomNeighboursSelected = new ArrayList<Address>();
        List<Integer> randomIndexArray = getRandomIndexArray(neighbors.size());

        //  Simply randomly schedule the job, and handle the inefficient placement in the timeout.
        //  Need to measure increasing timeout in the system.
        for (Integer i : randomIndexArray) {
            randomNeighboursSelected.add(neighbors.get(i).getAddress());
        }

        //Schedule the job, if everything looks good.
        UUID resourceRequestUUID = addTaskToSchedulerList(event);

        for (Address dest : randomNeighboursSelected) {
            RequestResources.Request req = new RequestResources.Request(self, dest, event.getNumCpus(), event.getMemoryInMbs(), event.getId(), event.getTimeToHoldResource(), randomNeighboursSelected, resourceRequestUUID);
            trigger(req, networkPort);
        }

        return true;
    }

    /**
     * Schedule the application job when received for a particular gradient.
     *
     * @param event
     */
    private boolean checkAndScheduleTheApplicationJobOnGradient(RequestResource event, List<PeerDescriptor> partnersDescriptor) {

        // Simply start with the start of the random walk.
        PeerDescriptor descriptor = getNeighborForReschedulingUpdated(event, partnersDescriptor);
        if (descriptor == null) {
            logger.info("~~ Not able to find the neighbor to reschedule the job ~~");
            return false;
        }

        // Reschedule The Job.
        UUID resourceRequestUUID = addTaskToSchedulerList(event);
        RescheduleJob rescheduledJob = new RescheduleJob(self, descriptor.getAddress(), event, resourceRequestUUID);
        trigger(rescheduledJob, networkPort);

        return true;
    }

    /**
     * TODO: Modify it to work intelligently with power of 4 by adding the basic
     * approach and then studying the benefits. Keep rescheduling simple by only
     * traversing up the tree and then finding the good node in the system
     * hopefully.
     */
    Handler<RescheduleJob> rescheduleJobHandler = new Handler<RescheduleJob>() {

        @Override
        public void handle(RescheduleJob rescheduleJobEvent) {

            RequestResource event = rescheduleJobEvent.getResourceRequest();

            // STEP1: Check if current node has available resources to satisfy the request.
            if (availableResources.allocate(event.getNumCpus(), event.getMemoryInMbs())) {

                WorkerJobDetail workerJobDetail = new WorkerJobDetail(event.getNumCpus(), event.getMemoryInMbs(), event.getId(), event.getTimeToHoldResource(), rescheduleJobEvent.getSource(), new ArrayList<Address>(), rescheduleJobEvent.getResourceRequestUUID());
                workerJobList.add(workerJobDetail);
                workerJobDetail.setJobStatus(JobStatusEnum.PROCESSING);
                executeJob(workerJobDetail);
                return;
            }

            // STEP2: The current node doesn't have the required resources, check if any neighbors have the correspongding required resources.
//            logger.info("Reschedule Job Requiring  cpu: " + event.getNumCpus() + " + memory: " + event.getMemoryInMbs() + " + id: " + event.getId());
            ArrayList<PeerDescriptor> currentNeighborsInfo = getGradientNeighborsBasedOnRequest(event);
            // FIXME: Incorporate the logic for the finger list in the rescheduling neighbor selection.

            if (currentNeighborsInfo.isEmpty()) {

                // No samples returned by the TMan yet, so buffering the request.
//                logger.info("Buffering the re-scheduled request .... " + event.getId());
                bufferedRequestsAtScheduler.add(event);
                RemoveRescheduleJob removeRescheduleJobEvent = new RemoveRescheduleJob(self, rescheduleJobEvent.getSource(), event, rescheduleJobEvent.getResourceRequestUUID());
                trigger(removeRescheduleJobEvent, networkPort);
                return;
            }

            if (rescheduleJobEvent.getTTL() == 0) {

//                logger.info(" TTL Became Zero, So buffering ..... ");
                // Trigger a cancellation event to the original scheduler, to remove the request from the scheduler list.
                bufferedRequestsAtScheduler.add(event);
                RemoveRescheduleJob removeRescheduleJobEvent = new RemoveRescheduleJob(self, rescheduleJobEvent.getSource(), event, rescheduleJobEvent.getResourceRequestUUID());
                trigger(removeRescheduleJobEvent, networkPort);
                return;
            }

            // Get the gradient neighbors suitable for scheduling, that are closer to this node.
            PeerDescriptor descriptor = getNeighborForReschedulingUpdated(event, currentNeighborsInfo);
            if (descriptor != null) {
                
                //TODO: Potential Problem .... 
//                if(descriptor.getAddress().equals(rescheduleJobEvent.getSource())){
//                    logger.info("Same Address Found .... ");
//                    System.exit(1);
//                }
                // Further reschedule the request in the network, with the original source as it is random walk.
                RescheduleJob job = new RescheduleJob(rescheduleJobEvent.getSource(), descriptor.getAddress(), event, rescheduleJobEvent.getTTL(), rescheduleJobEvent.getResourceRequestUUID());
                job.reduceTTL();
                trigger(job, networkPort);
            } else {
                
//                logger.info(" ............. going to buffer because the neighbor to reschedule is null .......... ");
                bufferedRequestsAtScheduler.add(event);
                RemoveRescheduleJob removeRescheduleJobEvent = new RemoveRescheduleJob(self, rescheduleJobEvent.getSource(), event, rescheduleJobEvent.getResourceRequestUUID());
                trigger(removeRescheduleJobEvent, networkPort);
            }
        }
    };

    /**
     * TODO: Create the Cancel Timeout Task and modify the requests. Perform the
     * tasks required when found neighbor for scheduling and provide the UUID of
     * the request.
     *
     * @param detail
     */
    private UUID addTaskToSchedulerList(RequestResource event) {

        // Add to the scheduler list.
        ApplicationJobDetail applicationJobDetail = new ApplicationJobDetail(event);
        schedulerJobList.add(applicationJobDetail);

        // Create a timeout event to check the progress of the task.
        ScheduleTimeout st = new ScheduleTimeout(requestTimeout);
        ResourceRequestTimeout timeout = new ResourceRequestTimeout(st, event);
        st.setTimeoutEvent(timeout);
        // Add this UUID to the list of outstanding requests.
        UUID rTimeoutId = st.getTimeoutEvent().getTimeoutId();
        outstandingRequestsUUID.add(rTimeoutId);

        // Trigger the timeout if everything looks good.
        trigger(st, timerPort);
        return rTimeoutId;
    }

    /**
     * Handler for the resource request timeout.
     */
    Handler<ResourceRequestTimeout> resourceRequestTimeout = new Handler<ResourceRequestTimeout>() {

        @Override
        public void handle(ResourceRequestTimeout timeoutEvent) {

            RequestResource event = timeoutEvent.getResourceRequest();
            UUID resourceRequestUUID = timeoutEvent.getTimeoutId();
            if(cleanUpTheTaskAtScheduler(event.getId(), resourceRequestUUID))
                initiateTaskScheduling(event);

            // Again schedule the task, based on random or on being gradient.
//            initiateTaskScheduling(event);
        }

    };

    /**
     * Initiate the scheduling of the task received at the node.
     *
     * @param event
     */
    public void initiateTaskScheduling(RequestResource event) {

        if (useGradient) {
            // Based on the request, check the dominant one.
            ArrayList<PeerDescriptor> neighborsInfo = getGradientNeighborsBasedOnRequest(event);
            
            if (neighborsInfo.isEmpty() || !checkAndScheduleTheApplicationJobOnGradient(event, neighborsInfo)) {
//                logger.info("Going to buffer the request .... ");
                bufferedRequestsAtScheduler.add(event);
            }

        } else {

            // Buffer the request in case we donot find the neighbors to talk to or not able to select neighbors to send the request to.
            ArrayList<PeerDescriptor> descriptors = getRandomNeighborsDescriptors();
            if (descriptors.isEmpty() || !checkAndScheduleTheApplicationJob(event, descriptors)) {
                bufferedRequestsAtScheduler.add(event);
            }
        }
    }

    /**
     * In case the job gets added in the buffer list of another node.
     */
    Handler<RemoveRescheduleJob> removeRescheduleHandler = new Handler<RemoveRescheduleJob>() {

        @Override
        public void handle(RemoveRescheduleJob event) {
            cleanUpTheTaskAtScheduler(event.getRequestEvent().getId(), event.getResourceRequestUUID());
        }
    };

    /**
     * Simply clean up the completed task from the list of scheduled task list.
     *
     * @param requestId
     * @param resourceRequestUUID
     */
    private boolean cleanUpTheTaskAtScheduler(long requestId, UUID resourceRequestUUID) {

        ApplicationJobDetail jobDetail = new ApplicationJobDetail(requestId);

        if (schedulerJobList.contains(jobDetail)) {

            //Remove the task from the scheduled list.
            schedulerJobList.remove(jobDetail);

            // Send the cancellation event.
            outstandingRequestsUUID.remove(resourceRequestUUID);
            CancelTimeout cancelTimeout = new CancelTimeout(resourceRequestUUID);
            trigger(cancelTimeout, timerPort);
            return true;
        }
        
        return false;
    }

    /**
     * FIXME: Make this piece of code generic enough.
     */
    Handler<TManSample> handleGradientSample = new Handler<TManSample>() {
        @Override
        public void handle(TManSample event) {

            if (event.getSample().isEmpty()) {
                logger.info("~~~ Received Empty TMan Sample ~~~");
                return;
            }

//            logger.info("Received Gradient Sample with size: " + event.getPartnersDescriptor().size());
            ArrayList<PeerDescriptor> similarPartnersDescriptor = event.getPartnersDescriptor();
            
            if(similarPartnersDescriptor.isEmpty()){
                logger.info(" Empty Partner Descriptors ...... ");
            }
            
            if (event.getGradientEnum() == GradientEnum.CPU) {

                // Update the finger list first.
                cpuGradientFingerList.clear();
                cpuGradientFingerList.addAll(event.getFingerList());

//                for(PeerDescriptor peer : cpuGradientFingerList){
//                    logger.info("CPU Gradient Finger List Node: "+ self.getId() + " with CPU As: " + peer.getFreeCpu());    
//                }
                //update the similar neighbors.
                cpuGradientNeighborsDescriptors.clear();
                cpuGradientNeighborsDescriptors.addAll(similarPartnersDescriptor);

            } else if (event.getGradientEnum() == GradientEnum.MEMORY) {

                //Same process, as for the cpu one.
                memoryGradientFingerList.clear();
                memoryGradientFingerList.addAll(event.getFingerList());

                //TODO: Incoporate the neighbors in the rescheduling logic intelligently.
                memoryGradientNeighborsDescriptors.clear();
                memoryGradientNeighborsDescriptors.addAll(similarPartnersDescriptor);
            }

            // As it is gradient sample, so only fetch the peer descriptor.
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
                        CancelTask cancelTaskMessage = new CancelTask(self, addr, peerJobDetail.getRequestId());
                        trigger(cancelTaskMessage, networkPort);
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
    Handler<CancelTask> jobCancellationHandler = new Handler<CancelTask>() {

        @Override
        public void handle(CancelTask event) {

            WorkerJobDetail requiredJobDetail = null;

            for (WorkerJobDetail workerJobDetail : workerJobList) {

                // If task is queued, then delete the task from queue.
                if (workerJobDetail.getRequestId() == event.getRequestId() && workerJobDetail.getJobStatus() == JobStatusEnum.QUEUED) {
                    requiredJobDetail = workerJobDetail;
//                    logger.info("Job Cancellation Successful  ~~ " + requiredJobDetail.getRequestId());
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
    Handler<JobCompletion> jobCompletionTimeout = new Handler<JobCompletion>() {

        @Override
        public void handle(JobCompletion event) {

            //Step1: Free The resources.
            WorkerJobDetail jobDetail = event.getPeerJobDetail();
            availableResources.release(jobDetail.getCpu(), jobDetail.getMemory());
//            logger.info("Resources Released: " + "Cpu: " + jobDetail.getCpu() + " Memory: " + jobDetail.getMemory());

            //Step2: Remove the resource from the processed list as it has been completely processed.
            if (workerJobList.contains(jobDetail)) {
                workerJobList.remove(jobDetail);
            }

            //Step3:Send the completion request to the scheduler about the completion of the request.
            JobCompletionEvent requestCompletionEvent = new JobCompletionEvent(self, jobDetail.getSchedulerAddress(), jobDetail.getRequestId(), jobDetail.getResourceRequestUUID());
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

            if (cleanUpTheTaskAtScheduler(event.getRequestId(), event.getResourceRequestUUID())) {
                trigger(new RequestCompletion(event.getRequestId()), utilizationPort);
            }
        }
    };

// Execute the job successfully.
    private void executeJob(WorkerJobDetail jobDetail) {
        //Schedule a timeout for the resource.
        ScheduleTimeout st = new ScheduleTimeout(jobDetail.getTimeToHoldResource());
        JobCompletion timeout = new JobCompletion(st, jobDetail);
        st.setTimeoutEvent(timeout);
        trigger(st, timerPort);
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
        } else {
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
     * Simply return the top most neighbors.
     *
     * @param size
     * @return
     */
    public List<Integer> getNeighborsForGradient(int size) {

        List<Integer> indexArray = new ArrayList<Integer>();
        int i = 0;
        while (i < probeRatio) {
            indexArray.add(i);
            i += 1;
        }
        return indexArray;
    }

    /**
     * FIXME: Based on the resource just check which gradient to use. Check if
     * any buffered jobs can be scheduled..
     */
    private void checkForBufferedJobsAtScheduler() {

        ArrayList<PeerDescriptor> partnerDescriptors;
        ArrayList<RequestResource> eventsTobeRemoved = new ArrayList<RequestResource>();

        if (useGradient) {

            for (int i = 0 ; i < bufferedRequestsAtScheduler.size() ; i++) {

                partnerDescriptors = getGradientNeighborsBasedOnRequest(bufferedRequestsAtScheduler.get(i));

                if (!partnerDescriptors.isEmpty()) {
                    // schedule the request and then remove it from the buffered requests.
                    if (checkAndScheduleTheApplicationJobOnGradient(bufferedRequestsAtScheduler.get(i), partnerDescriptors)) {
                        eventsTobeRemoved.add(bufferedRequestsAtScheduler.get(i));
                    }
                }
            }
        } // Get neighbours from the random approach and schedule them.
        else {

            partnerDescriptors = getRandomNeighborsDescriptors();
            if (!partnerDescriptors.isEmpty()) {
                for (RequestResource requestResource : bufferedRequestsAtScheduler) {

                    if (checkAndScheduleTheApplicationJob(requestResource, partnerDescriptors)) {
                        eventsTobeRemoved.add(requestResource);
                    }
                }
            }
        }

        for (RequestResource event : eventsTobeRemoved) {
            bufferedRequestsAtScheduler.remove(event);
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

    /**
     * True if utility1 dominant on utility2. Very Basic Comparison Mechanism to
     * check for the Dominant utility.
     *
     * @param utility1
     * @param utility2
     * @return
     */
    private boolean isDominant(double utility1, double utility2) {
        return (utility1 >= utility2);
    }

    /**
     * Based on the request fetch the peer to reschedule to the job.
     *
     * @param entries
     * @return
     */
    private PeerDescriptor getNeighborForReschedulingUpdated(RequestResource resourceRequest, List<PeerDescriptor> entries) {

        int resourceToCompare;
        ResourceEnum dominantResource;
        List<PeerDescriptor> favourableNeighbors = new ArrayList<PeerDescriptor>();
        dominantResource = (isDominant(resourceRequest.getNumCpus()/1.0, resourceRequest.getMemoryInMbs()/1000.0)) ? (ResourceEnum.CPU) : ResourceEnum.MEMORY;
        PeerDescriptor fingerBasedDescriptor = null;
        PeerDescriptor gradientBasedDescriptor = null;


        if (dominantResource == ResourceEnum.CPU) {
            
            // Change the criteria based on the requirement.
            resourceToCompare = availableResources.getNumFreeCpus();
            for (PeerDescriptor peerDescriptor : entries) {
                // Add the descriptors which have greater free resources for the dominant resource and the 
                if (peerDescriptor.getFreeCpu() > resourceToCompare || peerDescriptor.getFreeCpu() >= resourceRequest.getNumCpus()) {
                    favourableNeighbors.add(peerDescriptor);
                }
            }
            // Check for an alternative rescheduling neighbor using the fingers.
            if (!cpuGradientFingerList.isEmpty()) {
                fingerBasedDescriptor = cpuGradientFingerList.get(random.nextInt(cpuGradientFingerList.size()));
            }
            // Fetch a random neighbor based on the gradient also.
            if (!favourableNeighbors.isEmpty()) {
                gradientBasedDescriptor = favourableNeighbors.get(random.nextInt(favourableNeighbors.size()));
            }

            if (gradientBasedDescriptor == null) {
//                logger.info("Gradient Null So Returning a Finger Based Neighbor.");
                return fingerBasedDescriptor;
            } 
            else if (fingerBasedDescriptor == null) {               
//                logger.info("Gradient Null So Returning a Finger Based Neighbor.");
                return gradientBasedDescriptor;
            } 
            else {
               // Return the closest neighbor of the above two fetched.
                if ((fingerBasedDescriptor.getFreeCpu() > resourceRequest.getNumCpus() && resourceRequest.getNumCpus() > gradientBasedDescriptor.getFreeCpu()) || Math.abs(fingerBasedDescriptor.getFreeCpu() - resourceRequest.getNumCpus()) < Math.abs(gradientBasedDescriptor.getFreeCpu() - resourceRequest.getNumCpus())) {
//                   logger.info("Returning a Finger Based Neighbor ...");
                    return fingerBasedDescriptor;
                } 
                else {
//                    logger.info("Returning Gradient Based Neighbor .... ");
                    return gradientBasedDescriptor;
                }
            }
        } 
        else {

            logger.info("Memory Approach is Used for Rescheduling ");
            resourceToCompare = availableResources.getFreeMemInMbs();
            for (PeerDescriptor peerDescriptor : entries) {
                // TODO: Change the > sign to >= sign .... 
                if (peerDescriptor.getFreeMemory() >= resourceToCompare || peerDescriptor.getFreeMemory() >= resourceRequest.getMemoryInMbs()) {
                    favourableNeighbors.add(peerDescriptor);
                }
            }
            
            // Check for an alternative rescheduling neighbor using the fingers.
            if (!memoryGradientFingerList.isEmpty()) {
                fingerBasedDescriptor = memoryGradientFingerList.get(random.nextInt(cpuGradientFingerList.size()));
            }
            // Fetch a random neighbor based on the gradient also.
            if (!favourableNeighbors.isEmpty()) {
                gradientBasedDescriptor = favourableNeighbors.get(random.nextInt(favourableNeighbors.size()));
            }

            if (gradientBasedDescriptor == null) {
//                logger.info("Returning a Finger Based Neighbor.");
                return fingerBasedDescriptor;
            } 
            else if (fingerBasedDescriptor == null) {
                
                return gradientBasedDescriptor;
            } 
            else {
               // Return the closest neighbor of the above two fetched.
                if ((fingerBasedDescriptor.getFreeMemory() > resourceRequest.getMemoryInMbs() && resourceRequest.getMemoryInMbs() > gradientBasedDescriptor.getFreeMemory()) || Math.abs(fingerBasedDescriptor.getFreeMemory() - resourceRequest.getMemoryInMbs()) < Math.abs(gradientBasedDescriptor.getFreeMemory() - resourceRequest.getMemoryInMbs())) {
//                   logger.info("Returning a Finger Based Neighbor ...");
                    return fingerBasedDescriptor;
                } 
                else {
//                    logger.info("Returning Gradient Based Neighbor .... ");
                    return gradientBasedDescriptor;
                }
            }
        }
    }

    /**
     * Based on the request fetch the peer to reschedule to the job.
     *
     * @param entries
     * @return
     */
    private PeerDescriptor getNeighborForRescheduling(RequestResource resourceRequest, List<PeerDescriptor> entries) {

        int resourceToCompare;
        ResourceEnum dominantResource = null;
        List<PeerDescriptor> favourableNeighbors = new ArrayList<PeerDescriptor>();
        dominantResource = (isDominant(resourceRequest.getNumCpus(), resourceRequest.getMemoryInMbs())) ? (ResourceEnum.CPU) : ResourceEnum.MEMORY;

        if (dominantResource == ResourceEnum.CPU) {
            // Change the criteria based on the requirement.
            resourceToCompare = availableResources.getNumFreeCpus();
            for (PeerDescriptor peerDescriptor : entries) {
                // Add the descriptors which have greater free resources for the dominant resource and the 
                if (peerDescriptor.getFreeCpu() > resourceToCompare || peerDescriptor.getFreeCpu() >= resourceRequest.getNumCpus()) {
                    favourableNeighbors.add(peerDescriptor);
                }
            }
        } else {

            resourceToCompare = availableResources.getFreeMemInMbs();
            for (PeerDescriptor peerDescriptor : entries) {
                // TODO: Change the > sign to >= sign .... 
                if (peerDescriptor.getFreeMemory() > resourceToCompare || peerDescriptor.getFreeMemory() >= resourceRequest.getNumCpus()) {
                    favourableNeighbors.add(peerDescriptor);
                }
            }
        }

        if (!favourableNeighbors.isEmpty()) {
            // Return the peer descriptor to re - schedule to.
            int index = random.nextInt(favourableNeighbors.size());
            return favourableNeighbors.get(index);
        }
        return null;
    }
}
