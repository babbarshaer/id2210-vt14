package resourcemanager.system.peer.rm;

import common.configuration.RmConfiguration;
import common.peer.AvailableResources;
import common.simulation.BatchRequest;
import common.simulation.BatchRequestResource;
import common.simulation.RequestResource;
import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;
import cyclon.system.peer.cyclon.PeerDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
    static final double TEMPERATURE = 0.7;

    Positive<UtilizationPort> utilizationPort = requires(UtilizationPort.class);

    private ArrayList<PeerDescriptor> cpuGradientFingerList = new ArrayList<PeerDescriptor>();
    private ArrayList<PeerDescriptor> memoryGradientFingerList = new ArrayList<PeerDescriptor>();
    private ArrayList<PeerDescriptor> cpuGradientNeighborsDescriptors = new ArrayList<PeerDescriptor>();
    private ArrayList<PeerDescriptor> memoryGradientNeighborsDescriptors = new ArrayList<PeerDescriptor>();

    // Batch Request Changes.
    private Address self;
    private RmConfiguration configuration;
    Random random;
    private AvailableResources availableResources;
    private List<RequestResource> bufferedRequestsAtScheduler;
    private List<RescheduleJob> bufferedRescheduledJobs;
    long requestTimeout;

    private Map<Long, UUID> requestIdToTimeoutIdMap;

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
    private final int batchProbeRatio = 2;

    private Set<ApplicationJobDetail> schedulerJobList;
    private LinkedList<WorkerJobDetail> workerJobList;

    private Map<BatchRequest, List<Long>> schedulerBatchRequestMap = new HashMap<BatchRequest, List<Long>>();
    private Set<BatchRequestResource> batchResourceRequestSet = new HashSet<BatchRequestResource>();

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
        subscribe(batchRequestHandler, indexPort);
    }

    Handler<RmInit> handleInit = new Handler<RmInit>() {
        @Override
        public void handle(RmInit init) {
            self = init.getSelf();
            configuration = init.getConfiguration();
            random = new Random(init.getConfiguration().getSeed());
            availableResources = init.getAvailableResources();
            long period = configuration.getPeriod();
            schedulerJobList = new HashSet<ApplicationJobDetail>();
            workerJobList = new LinkedList<WorkerJobDetail>();
            bufferedRequestsAtScheduler = new ArrayList<RequestResource>();
            bufferedRescheduledJobs = new ArrayList<RescheduleJob>();
            requestTimeout = configuration.getRequestTimeout();
            requestIdToTimeoutIdMap = new HashMap<Long, UUID>();
//            schedulerBatchJobSet = new HashSet<ApplicationBatchRequestDetail>();

            SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(100, 40);
            rst.setTimeoutEvent(new UpdateTimeout(rst));
            trigger(rst, timerPort);

        }
    };

    /**
     * Batch Request Functionality Addition.
     */
    Handler<BatchRequest> batchRequestHandler = new Handler<BatchRequest>() {

        @Override
        public void handle(BatchRequest event) {
            // Handler for the batch request.
            logger.info(" Batch Request Received With Id : " + event.getBatchRequestId());
            int numberOfMachines = (int) event.getNumberOfMachines();

            for (int withinBatchRequestId = 0; withinBatchRequestId < numberOfMachines; withinBatchRequestId++) {
                BatchRequestResource batchRequestResource = new BatchRequestResource(event.getBatchRequestId(), event.getFreeCpu(), event.getFreeMemory(), event.getTimeToHoldResource(), withinBatchRequestId);
                if (!handleWithinBatchRequestScheduling(batchRequestResource)) {
                    batchResourceRequestSet.add(batchRequestResource);
                } else {
                    logger.info(" Scheduled request number : " + withinBatchRequestId + " within a batch request with id: " + event.getBatchRequestId());
                }
            }

            // Now save an entry for the batch request in the set, with empty list.
            List<Long> withinBatchRequestIdResponse = new ArrayList<Long>();
            schedulerBatchRequestMap.put(event, withinBatchRequestIdResponse);
        }
    };

    /**
     * Handle the batch request scheduling.
     *
     * @param event
     * @return
     */
    public boolean handleWithinBatchRequestScheduling(BatchRequestResource event) {

        List<PeerDescriptor> randomNeighborDescriptors = getRandomNeighborsDescriptors();
        List<Integer> randomIndexArray = getRandomIndexArray(randomNeighborDescriptors.size(), batchProbeRatio);

        if (randomIndexArray.isEmpty()) {
            return false;
        }

        List<Address> randomNeighborSelected = new ArrayList<Address>();
        for (Integer j : randomIndexArray) {
            randomNeighborSelected.add(randomNeighborDescriptors.get(j).getAddress());
        }

        for (Integer index : randomIndexArray) {
            RequestResources.Request workerBatchRequest = new RequestResources.Request(self, randomNeighborDescriptors.get(index).getAddress(), (int) event.getFreeCpu(), (int) event.getFreeMemory(), event.getBatchRequestId(), (int) event.getTimeToHoldResource(), randomNeighborSelected, null, true, (int) event.getWithInBatchRequestId());
            trigger(workerBatchRequest, networkPort);
        }

        return true;
    }

    //TODO: Functionality needs to be implemented here.
    Handler<UpdateTimeout> handleUpdateTimeout = new Handler<UpdateTimeout>() {
        @Override
        public void handle(UpdateTimeout event) {

            checkForBufferedJobsAtScheduler();
            checkForBufferedReschduledJobs();
            checkForBufferedBatchJobs();

        }
    };

    /**
     * Retry allocating the buffered jobs at the scheduler.
     */
    private void checkForBufferedBatchJobs() {

        Set<BatchRequestResource> requestsToBeRemoved = new HashSet<BatchRequestResource>();
        for (BatchRequestResource event : batchResourceRequestSet) {
            if (handleWithinBatchRequestScheduling(event)) {
                requestsToBeRemoved.add(event);
            }
        }

        for (BatchRequestResource requestEvent : requestsToBeRemoved) {
            batchResourceRequestSet.remove(requestEvent);
        }

    }

    /**
     * Simply check for the buffered jobs at the scheduler.
     */
    private void checkForBufferedReschduledJobs() {

        ArrayList<RescheduleJob> jobsToBeRemoved = new ArrayList<RescheduleJob>();

        for (RescheduleJob job : bufferedRescheduledJobs) {

//            logger.info("Found a buffered rescheduled job ... ");
            RequestResource event = job.getResourceRequest();
            ArrayList<PeerDescriptor> neighborsInfo = getGradientNeighborsBasedOnRequest(event);
            PeerDescriptor peer = getNeighborForReschedulingUpdated(event, neighborsInfo);

            if (peer != null) {

                // Reschedule Forward in the network.
                job.setDestination(peer.getAddress());
                job.resetTTL();
                trigger(job, networkPort);
                jobsToBeRemoved.add(job);

            }
        }

        // Remove the Rescheduled Jobs.
        for (RescheduleJob rescheduleJob : jobsToBeRemoved) {
            bufferedRescheduledJobs.remove(rescheduleJob);
        }
    }

    /**
     * Periodically Cyclon sends this event to the Resource Manager which
     * updates the Random Neighbors.
     */
    Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
        @Override
        public void handle(CyclonSample event) {

            randomNeighbours.clear();
            randomNeighbours.addAll(event.getSample());
            randomNeighborsDescriptors.clear();
            randomNeighborsDescriptors.addAll(event.getPartnersDescriptor());

//            checkForBufferedJobsAtScheduler();
        }
    };

    /**
     * Requesting the scheduler to schedule the task.
     */
    Handler<RequestResource> handleRequestResource = new Handler<RequestResource>() {

        @Override
        public void handle(RequestResource event) {
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
        } else {
            return memoryGradientNeighborsDescriptors;
        }

    }

    // Handler for the resource request being sent by the neighbour peer.
    Handler<RequestResources.Request> handleResourceAllocationRequest = new Handler<RequestResources.Request>() {
        @Override
        public void handle(RequestResources.Request event) {

//            logger.info(" New request Received with id : " +  event.getRequestId() +" and within BatchRequest Id : " + event.withinBatchRequestId() + " at worker with id: " + self.getId());
            WorkerJobDetail peerJobDetail = new WorkerJobDetail(event.getNumCpus(), event.getAmountMemInMb(), event.getRequestId(), event.getTimeToHoldResource(), event.getSource(), event.getPeers(), event.getResourceRequestUUID(), event.isBatchRequest(), event.withinBatchRequestId());
            if (workerJobList.contains(peerJobDetail)) {
                return;
            };
            workerJobList.add(peerJobDetail);
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
        List<Integer> randomIndexArray = getRandomIndexArray(neighbors.size(), probeRatio);

        for (Integer i : randomIndexArray) {
            randomNeighboursSelected.add(neighbors.get(i).getAddress());
        }

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
//            logger.info("~~ Not able to find the neighbor to reschedule the job ~~");
            return false;
        }

        // Reschedule The Job.
        UUID resourceRequestUUID = addTaskToSchedulerList(event);
        RescheduleJob rescheduledJob = new RescheduleJob(self, descriptor.getAddress(), event, null);
        trigger(rescheduledJob, networkPort);

        return true;
    }

    /**
     * Keep rescheduling simple by only traversing up the tree and then finding
     * the good node in the system hopefully.
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
            ArrayList<PeerDescriptor> currentNeighborsInfo = getGradientNeighborsBasedOnRequest(event);

            if (currentNeighborsInfo.isEmpty() || rescheduleJobEvent.getTTL() == 0) {
                //Simply buffer it to the reschedule job event.
                bufferedRescheduledJobs.add(rescheduleJobEvent);
                return;
            }

            // Get the gradient neighbors suitable for scheduling, that are closer to this node.
            PeerDescriptor descriptor = getNeighborForReschedulingUpdated(event, currentNeighborsInfo);
            if (descriptor != null) {

                rescheduleJobEvent.setDestination(descriptor.getAddress());
                rescheduleJobEvent.reduceTTL();
                trigger(rescheduleJobEvent, networkPort);

            } else {
                bufferedRescheduledJobs.add(rescheduleJobEvent);
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

        // Add to the scheduler list, if job not present.
        boolean jobPresent = false;
        for (ApplicationJobDetail jobDetail : schedulerJobList) {
            if (jobDetail.getRequestId() == event.getId()) {
                jobPresent = true;
            }
        }

        if (!jobPresent) {
            // Start Time gets distorted if not this.
            ApplicationJobDetail applicationJobDetail = new ApplicationJobDetail(event);
            schedulerJobList.add(applicationJobDetail);
        }

//        // Create a timeout event to check the progress of the task.
        ScheduleTimeout st = generateRequestResourceTimeout(event);
        UUID timeoutId = st.getTimeoutEvent().getTimeoutId();

        requestIdToTimeoutIdMap.put(event.getId(), timeoutId);

        // Trigger the timeout if everything looks good.
        trigger(st, timerPort);
        return timeoutId;
    }

    /**
     * Handler for the resource request timeout.
     */
    Handler<ResourceRequestTimeout> resourceRequestTimeout = new Handler<ResourceRequestTimeout>() {

        @Override
        public void handle(ResourceRequestTimeout timeoutEvent) {

            RequestResource event = timeoutEvent.getResourceRequest();
            ScheduleTimeout st;
            UUID timeoutId;

            if (useGradient) {
//                logger.info(" Gradient Timeout ... ");
                // Based on the request, check the dominant one.
                ArrayList<PeerDescriptor> neighborsInfo = getGradientNeighborsBasedOnRequest(event);
                PeerDescriptor peer = getNeighborForReschedulingUpdated(event, neighborsInfo);

                if (peer == null) {
                    // Go for timeout again.
                    st = generateRequestResourceTimeout(event);
                    timeoutId = st.getTimeoutEvent().getTimeoutId();
                    requestIdToTimeoutIdMap.put(event.getId(), timeoutId);
                    trigger(st, timerPort);
                    return;
                }
                initiateTaskScheduling(event);
            } // Buffer the request in case we donot find the neighbors to talk to or not able to select neighbors to send the request to.
            else {
                ArrayList<PeerDescriptor> descriptors = getRandomNeighborsDescriptors();
                if (descriptors == null) {

                    // FIXME: Modularize the code, move this to a function.
                    st = generateRequestResourceTimeout(event);
                    timeoutId = st.getTimeoutEvent().getTimeoutId();
                    requestIdToTimeoutIdMap.put(event.getId(), timeoutId);
                    trigger(st, timerPort);
                    return;
                }
                initiateTaskScheduling(event);
            }
        }
    };

    /**
     * Simply schedule a resource request timeout.
     *
     * @param event
     * @return timeoutId.
     */
    public ScheduleTimeout generateRequestResourceTimeout(RequestResource event) {

        ScheduleTimeout st = new ScheduleTimeout(requestTimeout);
        ResourceRequestTimeout timeout = new ResourceRequestTimeout(st, event);
        st.setTimeoutEvent(timeout);
        return st;

    }

    /**
     * Simply schedule a batch request resource timeout.
     *
     * @param event
     * @return
     */
    public ScheduleTimeout generateBatchRequestResourceTimeout(BatchRequestResource event) {

        // FIXME: To add timeout handler.
        ScheduleTimeout st = new ScheduleTimeout(requestTimeout);
        BatchResourceRequestTimeout timeout = new BatchResourceRequestTimeout(st, event);
        st.setTimeoutEvent(timeout);
        return st;

    }

    // FIXME: Complete the remaining functionality.
    Handler<BatchResourceRequestTimeout> batchResourceRequestTimeout = new Handler<BatchResourceRequestTimeout>() {
        @Override
        public void handle(BatchResourceRequestTimeout event) {
            logger.info(" Batch Resource Timeout Received .... ");
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
            logger.info(" Remove Job From The Rescheduler Should Not Be Called .... ");
            System.exit(1);
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

//        ApplicationJobDetail jobDetail = new ApplicationJobDetail(requestId);
        ApplicationJobDetail jobDetail = null;
        boolean jobPresent = false;

        for (ApplicationJobDetail job : schedulerJobList) {
            if (job.getRequestId() == requestId) {
                jobPresent = true;
                jobDetail = job;
                break;
            }
        }

        if (jobPresent) {
            schedulerJobList.remove(jobDetail);
            UUID timeoutId = requestIdToTimeoutIdMap.get(requestId);
            CancelTimeout cancelTimeout = new CancelTimeout(timeoutId);

            trigger(cancelTimeout, timerPort);
            return true;
        }
        return false;
    }

    /**
     * Update the gradient neighbors.
     */
    Handler<TManSample> handleGradientSample = new Handler<TManSample>() {
        @Override
        public void handle(TManSample event) {

            if (event.getSample().isEmpty()) {
                logger.info("~~~ Received Empty TMan Sample ~~~");
                return;
            }

            ArrayList<PeerDescriptor> similarPartnersDescriptor = event.getPartnersDescriptor();

            if (similarPartnersDescriptor.isEmpty()) {
                logger.info(" Empty Partner Descriptors ...... ");
            }

            if (event.getGradientEnum() == GradientEnum.CPU) {

                // Update the finger list first.
                cpuGradientFingerList.clear();
                cpuGradientFingerList.addAll(event.getFingerList());

                //update the similar neighbors.
                cpuGradientNeighborsDescriptors.clear();
                cpuGradientNeighborsDescriptors.addAll(similarPartnersDescriptor);

            } else if (event.getGradientEnum() == GradientEnum.MEMORY) {

                //Same process, as for the cpu one.
                memoryGradientFingerList.clear();
                memoryGradientFingerList.addAll(event.getFingerList());

                memoryGradientNeighborsDescriptors.clear();
                memoryGradientNeighborsDescriptors.addAll(similarPartnersDescriptor);
            }

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
                        CancelTask cancelTaskMessage = new CancelTask(self, addr, peerJobDetail.getRequestId(), peerJobDetail.isBatchRequest(), peerJobDetail.withinBatchRequestId());
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
                // Added support for the batch requests also.
                if (workerJobDetail.getRequestId() == event.getRequestId() && workerJobDetail.getJobStatus() == JobStatusEnum.QUEUED && workerJobDetail.withinBatchRequestId() == event.withinBatchRequestId()) {
                    requiredJobDetail = workerJobDetail;
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

            //Step2: Remove the resource from the processed list as it has been completely processed.
            if (workerJobList.contains(jobDetail)) {
                workerJobList.remove(jobDetail);
            }

//            logger.info(" Job Completed with request id: " + jobDetail.getRequestId() + " and within batch request id:  " + jobDetail.withinBatchRequestId()  + " at node with id: " + self.getId());
            //Step3:Send the completion request to the scheduler about the completion of the request.
            JobCompletionEvent requestCompletionEvent = new JobCompletionEvent(self, jobDetail.getSchedulerAddress(), jobDetail.getRequestId(), jobDetail.getResourceRequestUUID(), jobDetail.isBatchRequest(), jobDetail.withinBatchRequestId());
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

            // Handle the completion of batch request separately.
            if (event.isBatchRequest()) {
//                logger.info(" Completed Batch Request Received .... ");
                cleanUpBatchRequestAtScheduler(event);
                return;
            }

            long timeTaken = 0;
            for (ApplicationJobDetail job : schedulerJobList) {
                if (job.getRequestId() == event.getRequestId()) {
                    timeTaken = job.calculateTime(System.currentTimeMillis());
                }
            }

            if (cleanUpTheTaskAtScheduler(event.getRequestId(), event.getResourceRequestUUID())) {
//                logger.info("Request Completed: " + event.getRequestId() + " at: " + self.getId());
                trigger(new RequestCompletion(event.getRequestId(), timeTaken), utilizationPort);
            }
        }
    };

    private boolean cleanUpBatchRequestAtScheduler(JobCompletionEvent event) {

        boolean isBatchRequestComplete = false;
        BatchRequest batchRequest = null;

        for (Map.Entry<BatchRequest, List<Long>> entry : schedulerBatchRequestMap.entrySet()) {

            // Check if we have an entry in the map or not, for the request id.
            if (entry.getKey().getBatchRequestId() == event.getRequestId()) {
                //Check if a new request with batchh request has completed.
                if (!entry.getValue().contains(event.withinbatchRequestId())) {
                    // Update the completed requests.
                    entry.getValue().add(event.withinbatchRequestId());

                    //Check for the completion of the batch request.
                    if (entry.getValue().size() == entry.getKey().getNumberOfMachines()) {
                        System.out.println("Batch Request Complete..... ");
                        isBatchRequestComplete = true;
                        batchRequest = entry.getKey();
                    }
                }
            }
        }

        if (isBatchRequestComplete) {
            // remove the entry from the scheduler, if request completed.
            logger.info(" Batch Request removed from the system .... ");
            schedulerBatchRequestMap.remove(batchRequest);
            return true;
        }
        return false;
    }

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
    private List<Integer> getRandomIndexArray(int neighboursSize, int probeValue) {

        List<Integer> randomIndexArray = new ArrayList<Integer>();

        if (neighboursSize <= probeValue) {
            // Add all the values in the random index array.
            for (int i = 0; i < neighboursSize; i++) {
                randomIndexArray.add(i);
            }
        } else {
            while (randomIndexArray.size() < probeValue) {
                boolean duplicate = false;
                //Iterate over the index array.
                int nextInt = random.nextInt(neighboursSize);
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

//        logger.info("Check Buffered Called ..... ");
        ArrayList<PeerDescriptor> partnerDescriptors;
        ArrayList<RequestResource> eventsTobeRemoved = new ArrayList<RequestResource>();

        if (useGradient) {

            for (int i = 0; i < bufferedRequestsAtScheduler.size(); i++) {

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
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
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
        dominantResource = (isDominant(resourceRequest.getNumCpus() / 1.0, resourceRequest.getMemoryInMbs() / 1000.0)) ? (ResourceEnum.CPU) : ResourceEnum.MEMORY;
        PeerDescriptor fingerBasedDescriptor = null;
        PeerDescriptor gradientBasedDescriptor = null;

        if (dominantResource == ResourceEnum.CPU) {

            // Change the criteria based on the requirement.
            resourceToCompare = availableResources.getNumFreeCpus();
            for (PeerDescriptor peerDescriptor : entries) {

                //Purely Do Gradient Ascend Search.
                // Add the descriptors which have greater free resources for the dominant resource and the 
                if (peerDescriptor.getFreeCpu() >= resourceToCompare || peerDescriptor.getFreeCpu() >= resourceRequest.getNumCpus()) {
                    favourableNeighbors.add(peerDescriptor);
                }
            }

            // Fetch a random neighbor based on the gradient also.
            if (!favourableNeighbors.isEmpty()) {
                gradientBasedDescriptor = favourableNeighbors.get(random.nextInt(favourableNeighbors.size()));
            }

            // Check for an alternative rescheduling neighbor using the fingers.
            if (!cpuGradientFingerList.isEmpty()) {

                ArrayList<PeerDescriptor> favourableFingerBasedDescriptor = new ArrayList<PeerDescriptor>();

                for (PeerDescriptor peer : cpuGradientFingerList) {
                    if (peer.getFreeCpu() > resourceToCompare || peer.getFreeCpu() >= resourceRequest.getNumCpus()) {
                        favourableFingerBasedDescriptor.add(peer);
                    }

                    if (!favourableFingerBasedDescriptor.isEmpty()) {
                        fingerBasedDescriptor = favourableFingerBasedDescriptor.get(random.nextInt(favourableFingerBasedDescriptor.size()));
                    }
                }
            }

            if (gradientBasedDescriptor == null) {
                return fingerBasedDescriptor;

            } else if (fingerBasedDescriptor == null) {
                return gradientBasedDescriptor;
            } else {
                // Return the closest neighbor of the above two fetched.
                if ((fingerBasedDescriptor.getFreeCpu() > resourceRequest.getNumCpus() && resourceRequest.getNumCpus() > gradientBasedDescriptor.getFreeCpu()) || Math.abs(fingerBasedDescriptor.getFreeCpu() - resourceRequest.getNumCpus()) < Math.abs(gradientBasedDescriptor.getFreeCpu() - resourceRequest.getNumCpus())) {
                    return fingerBasedDescriptor;
                } else {
                    return gradientBasedDescriptor;
                }
            }
        } else {

            resourceToCompare = availableResources.getFreeMemInMbs();
            for (PeerDescriptor peerDescriptor : entries) {
                // TODO: Change the > sign to >= sign .... 
                if (peerDescriptor.getFreeMemory() >= resourceToCompare || peerDescriptor.getFreeMemory() >= resourceRequest.getMemoryInMbs()) {
                    favourableNeighbors.add(peerDescriptor);
                }
            }

            if (!favourableNeighbors.isEmpty()) {
                gradientBasedDescriptor = favourableNeighbors.get(random.nextInt(favourableNeighbors.size()));
            }

            // Check for an alternative rescheduling neighbor using the fingers.
            if (!memoryGradientFingerList.isEmpty()) {
                ArrayList<PeerDescriptor> favourableFingerBasedDescriptor = new ArrayList<PeerDescriptor>();

                for (PeerDescriptor peer : memoryGradientFingerList) {
                    if (peer.getFreeMemory() > resourceToCompare || peer.getFreeMemory() >= resourceRequest.getMemoryInMbs()) {
                        favourableFingerBasedDescriptor.add(peer);
                    }

                    if (!favourableFingerBasedDescriptor.isEmpty()) {
                        fingerBasedDescriptor = favourableFingerBasedDescriptor.get(random.nextInt(favourableFingerBasedDescriptor.size()));
                    }
                }
            }

            // Fetch a random neighbor based on the gradient also.
            if (gradientBasedDescriptor == null) {
                return fingerBasedDescriptor;
            } else if (fingerBasedDescriptor == null) {
                return gradientBasedDescriptor;
            } else {
                // Return the closest neighbor of the above two fetched.
                if ((fingerBasedDescriptor.getFreeMemory() > resourceRequest.getMemoryInMbs() && resourceRequest.getMemoryInMbs() > gradientBasedDescriptor.getFreeMemory()) || Math.abs(fingerBasedDescriptor.getFreeMemory() - resourceRequest.getMemoryInMbs()) < Math.abs(gradientBasedDescriptor.getFreeMemory() - resourceRequest.getMemoryInMbs())) {
                    return fingerBasedDescriptor;
                } else {
                    return gradientBasedDescriptor;
                }
            }
        }
    }

    /**
     * Simplify the complexity of the method.
     *
     * @param resourceRequest
     * @param entries
     * @return
     */
    private PeerDescriptor getNeighborForRescheduling(RequestResource resourceRequest, List<PeerDescriptor> entries) {

        int personalResource;
        ResourceEnum dominantResource;
        List<PeerDescriptor> favourableNeighbors = new ArrayList<PeerDescriptor>();
        dominantResource = (isDominant(resourceRequest.getNumCpus() / 1.0, resourceRequest.getMemoryInMbs() / 1000.0)) ? (ResourceEnum.CPU) : ResourceEnum.MEMORY;
        PeerDescriptor descriptorToBeReturned = null;

        if (dominantResource == ResourceEnum.CPU) {
            personalResource = availableResources.getNumFreeCpus();

            if (personalResource < resourceRequest.getNumCpus()) {
                // Check only for better entries in the similar neighborhood, as similar ones are not capable enough to satisfy the request.
                for (PeerDescriptor peerDescriptor : entries) {
                    if (peerDescriptor.getFreeCpu() >= personalResource || peerDescriptor.getFreeCpu() >= resourceRequest.getNumCpus()) {
                        favourableNeighbors.add(peerDescriptor);
                    }
                }
            } else {
                //Check for general entries.
                for (PeerDescriptor peerDescriptor : entries) {
                    if (peerDescriptor.getFreeCpu() > personalResource || peerDescriptor.getFreeCpu() >= resourceRequest.getNumCpus()) {
                        favourableNeighbors.add(peerDescriptor);
                    }
                }
            }

            // Switch over to finger table entries in case no entry found from the similar neighborhood.
            if (favourableNeighbors.isEmpty()) {

                for (PeerDescriptor desc : cpuGradientFingerList) {
                    if (desc.getFreeCpu() >= personalResource || desc.getFreeCpu() >= resourceRequest.getNumCpus()) {
                        favourableNeighbors.add(desc);
                    }
                }
            }

            if (!favourableNeighbors.isEmpty()) {
                descriptorToBeReturned = favourableNeighbors.get(random.nextInt(favourableNeighbors.size()));
            }

            //Finally return the selected neighbor.
//            descriptorToBeReturned = getSoftMaxAddress(favourableNeighbors, TEMPERATURE);
        } else if (dominantResource == ResourceEnum.MEMORY) {

//            logger.info(" Memory is dominant resource ... ");
            personalResource = availableResources.getFreeMemInMbs();

            if (personalResource < resourceRequest.getMemoryInMbs()) {
                // Check only for better entries in the similar neighborhood, as similar ones are not capable enough to satisfy the request.
                for (PeerDescriptor peerDescriptor : entries) {
                    if (peerDescriptor.getFreeMemory() > personalResource || peerDescriptor.getFreeMemory() >= resourceRequest.getMemoryInMbs()) {
                        favourableNeighbors.add(peerDescriptor);
                    }
                }
            } else {
                //Check for general entries.
                for (PeerDescriptor peerDescriptor : entries) {
                    if (peerDescriptor.getFreeMemory() >= personalResource || peerDescriptor.getFreeMemory() >= resourceRequest.getMemoryInMbs()) {
                        favourableNeighbors.add(peerDescriptor);
                    }
                }
            }

            // Switch over to finger table entries in case no entry found from the similar neighborhood.
            if (favourableNeighbors.isEmpty()) {

                for (PeerDescriptor desc : memoryGradientFingerList) {
                    if (desc.getFreeMemory() >= personalResource || desc.getFreeMemory() >= resourceRequest.getMemoryInMbs()) {
                        favourableNeighbors.add(desc);
                    }
                }
            }

            if (!favourableNeighbors.isEmpty()) {
                descriptorToBeReturned = favourableNeighbors.get(random.nextInt(favourableNeighbors.size()));
            }
        }

        return descriptorToBeReturned;
    }

}
