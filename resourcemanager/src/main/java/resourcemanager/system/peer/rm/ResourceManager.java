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
    GradientEnum gradientEnum = GradientEnum.CPU;
    boolean useGradient = true;
    static final double TEMPERATURE = 0.6;

    ArrayList<PeerDescriptor> cpuGradientNeighborsDescriptors = new ArrayList<PeerDescriptor>();
    ArrayList<PeerDescriptor> memoryGradientNeighborsDescriptors = new ArrayList<PeerDescriptor>();

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
        subscribe(handleGradientSample, tmanPort);

        // Intelligent Gradient Ascent Search Handler.
        subscribe(rescheduleJobHandlerUpdated, networkPort);
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

            logger.info("Allocate resources Received By : " + self + ":  cpu: " + event.getNumCpus() + " + memory: " + event.getMemoryInMbs() + " + id: " + event.getId());

            if (useGradient) {

                // Based on the request, check the dominant one.
                //FIXME: Improve the strategy of selecting a dominant neighbor.
                ArrayList<PeerDescriptor> neighborsInfo = getGradientNeighborsBasedOnRequest(event);
                if (neighborsInfo.isEmpty()) {
                    //Tman didn't send the samples yet.
                    logger.info("Buffering the request .... " + event.getId());
                    bufferedRequestsAtScheduler.add(event);
                    return;
                }
                scheduleTheApplicationJobOnGradient(event, neighborsInfo);

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

    /**
     * Based on the request event, fetch the gradient info.
     *
     * @param event
     * @return
     */
    private ArrayList<PeerDescriptor> getGradientNeighborsBasedOnRequest(RequestResource event) {

        if (isDominant(event.getNumCpus(), event.getMemoryInMbs()/1000.0)) {
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
     *
     * FIXME: Change the implementation based on the dominant resource in the
     * request.
     *
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

        List<Address> randomNeighboursSelected;
        List<Integer> randomIndexArray = getRandomIndexArray(partnersDescriptor.size());

        // Check if the available resources have required free resources.
        int entriesFound = 0;
        for (Integer i : randomIndexArray) {

            if (entriesFound == neighborCorrectnessCriteria) {
                break;
            }

            PeerDescriptor currentPeerDescriptor = partnersDescriptor.get(i);
            if (currentPeerDescriptor.getFreeCpu() >= event.getNumCpus() && currentPeerDescriptor.getFreeMemory() >= event.getMemoryInMbs()) {
                entriesFound += 1;
            }
        }

        // Not good entries, start with the gradient ascent search.
        if (entriesFound < neighborCorrectnessCriteria) {

            // As the peer Descriptors will already be sorted.
            PeerDescriptor descriptor = getSoftMaxAddress(partnersDescriptor, TEMPERATURE);
            if (descriptor == null) {
                logger.info("~~ Not able to find the neighbor to reschedule the job ~~");
                return;
            }

//            logger.info("Start the process of Rescheduling at :" + self + " ~~To: " + descriptor.getAddress());
            RescheduleJob rescheduledJob = new RescheduleJob(self, descriptor.getAddress(), event);
            trigger(rescheduledJob, networkPort);
            return;
        }

        randomNeighboursSelected = new ArrayList<Address>();

        for (Integer i : randomIndexArray) {
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
     *
     * Reschedule event to discover better peers.
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

                logger.info(" ~~~ Rescheduling the Job With ID: " + event.getId() + " To:  " + descriptor.getAddress() + "  ~~~ By: " + self);
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
     *
     * Rescheduling using Intelligent Gradient Approach.
     */
    Handler<RescheduleJob> rescheduleJobHandlerUpdated = new Handler<RescheduleJob>() {

        @Override
        public void handle(RescheduleJob rescheduleJobEvent) {

            RequestResource event = rescheduleJobEvent.getResourceRequest();

            // STEP1: Check if current node has available resources to satisfy the request.
            if (availableResources.isAvailable(event.getNumCpus(), event.getMemoryInMbs())) {

//                logger.info("Found the Available Resources to Execute .... ");
//                System.exit(1);
                // Not sure if this is a good mechanism to execute the job on the rescheduled node or not, as the reliability factor reduces because of single node executing the job.
                WorkerJobDetail workerJobDetail = new WorkerJobDetail(event.getNumCpus(), event.getMemoryInMbs(), event.getId(), event.getTimeToHoldResource(), rescheduleJobEvent.getSource(), new ArrayList<Address>());
                workerJobList.add(workerJobDetail);
                executeJob(workerJobDetail);
                return;
            }

            // STEP2: Check if the TTL Has expired, because then no need to move forward.
            if (rescheduleJobEvent.getTTL() == 0) {
                bufferedRequestsAtScheduler.add(event);
                return;
            }

            // STEP3: The current node doesn't have the required resources, check if any neighbors have the correspongding required resources.
//            logger.info("Reschedule Job Requiring  cpu: " + event.getNumCpus() + " + memory: " + event.getMemoryInMbs() + " + id: " + event.getId());
            ArrayList<PeerDescriptor> currentNeighborsInfo = getGradientNeighborsBasedOnRequest(event);
            if (currentNeighborsInfo.isEmpty()) {

                // No samples returned by the TMan yet, so buffering the request.
                logger.info("Buffering the re-scheduled request .... " + event.getId());
                bufferedRequestsAtScheduler.add(event);
                return;
            }

            List<Address> randomNeighboursSelected;
            List<Integer> randomIndexArray = getRandomIndexArray(currentNeighborsInfo.size());

            // Check if the available resources have required free resources.
            int entriesFound = 0;
            for (Integer i : randomIndexArray) {

                if (entriesFound == neighborCorrectnessCriteria) {
                    break;
                }

                // Check if any neighbor can satisfy the request criteria.
                PeerDescriptor currentPeerDescriptor = currentNeighborsInfo.get(i);
                if (currentPeerDescriptor.getFreeCpu() >= event.getNumCpus() && currentPeerDescriptor.getFreeMemory() >= event.getMemoryInMbs()) {
                    entriesFound += 1;
                }
            }

            // STEP4:  If neighbours are not found that can satisfy the request, then we move on to Re-Scheduling.
            if (entriesFound < neighborCorrectnessCriteria) {

                // Now in case, we go for rescheduling, then we need to fetch the neighbor which has better utility in terms of dominant resource or atleast the same utility as the resource requested.
                ResourceEnum dominantResource;
                // Step1: Find the dominant resource, in the two resources, after bringing them onto same scales.
                if (isDominant(event.getNumCpus(), (event.getMemoryInMbs() / 1000.0))) {    
                    dominantResource = ResourceEnum.CPU;
                } 
                else {
                    dominantResource = ResourceEnum.MEMORY;
                }

                // Check for the node to  which the job is rescheduled for the utility of the dominant resource.
                int retries = 0;
                int maximumRetries = rescheduleJobEvent.getDominantResourceRetries();

                PeerDescriptor descriptor = null;
                while (retries < maximumRetries) {

                    descriptor = getSoftMaxAddress(currentNeighborsInfo, TEMPERATURE);
                    logger.info("Node:  " + self.getId() +" Checking: " + descriptor.toString());
//                    if (descriptor == null) {
//                        logger.info(" ~~  Not able to find the neighbor to reschedule the job ~~ ");
//                        return;
//                    }

                    // Never send it to a node with a lower utility for the dominant resource.
                    if (dominantResource == ResourceEnum.CPU) {
                        if (descriptor.getFreeCpu() >= availableResources.getNumFreeCpus() || descriptor.getFreeCpu() >= event.getNumCpus()) {
                            logger.info("Found a Better Descriptor .... ");
                            break;
                        }
                    }

                   else if (dominantResource == ResourceEnum.MEMORY) {
                        if (descriptor.getFreeMemory() >= availableResources.getFreeMemInMbs() || descriptor.getFreeMemory() >= event.getMemoryInMbs()) {
                            break;
                        }
                       
                    }
                    
                     retries += 1;
                }

                if (retries == maximumRetries) {
                    bufferedRequestsAtScheduler.add(event);
                    return;
                }

                // Possible Null pointer exception can be thrown in this one.
//                logger.info(" ~~~ Rescheduling the Job With ID: " + event.getId() + " To:  " + descriptor.getAddress() + "  ~~~ By: " + self);
                //  Again Reschedule the event as TTL != 0
                RescheduleJob job = new RescheduleJob(self, descriptor.getAddress(), event, rescheduleJobEvent.getTTL());
                // Further reschedule the request in the network.
                job.reduceTTL();
                trigger(job, networkPort);
                return;
            }

            // STEP 5: Schedule the request in case peers with the required resources are found.
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
    Handler<TManSample> handleGradientSample = new Handler<TManSample>() {
        @Override
        public void handle(TManSample event) {

            if (event.getSample().isEmpty()) {
                // Code Flaw.
                logger.info("~~~ Received Empty TMan Sample ~~~");
                System.exit(1);
                return;
            }

//            logger.info("Received Gradient Sample with size: " + event.getPartnersDescriptor().size());
            ArrayList<PeerDescriptor> similarPartnersDescriptor = event.getPartnersDescriptor();

            // Based on the gradient of the sample populate the appropriate neighbors.
            if (event.getGradientEnum() == GradientEnum.CPU) {
                
//                logger.info("CPU Gradient Sample Size: " + similarPartnersDescriptor.size());
                cpuGradientNeighborsDescriptors.clear();
                cpuGradientNeighborsDescriptors.addAll(similarPartnersDescriptor);
            } 
            
            else if (event.getGradientEnum() == GradientEnum.MEMORY) {
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
    Handler<JobCompletion> jobCompletionTimeout = new Handler<JobCompletion>() {

        @Override
        public void handle(JobCompletion event) {

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
     * FIXME: Based on the resource just check which gradient to use.
     *
     * Check if any buffered jobs can be scheduled..
     */
    private void checkForBufferedJobsAtScheduler() {

        if (useGradient) {

            ArrayList<PeerDescriptor> partnerDescriptors; 
            ArrayList<RequestResource> eventsTobeRemoved = new ArrayList<RequestResource>();
            
           for(int i =0 ; i < bufferedRequestsAtScheduler.size(); i ++){
               
                partnerDescriptors = getGradientNeighborsBasedOnRequest(bufferedRequestsAtScheduler.get(i));
                if (partnerDescriptors.isEmpty()) {
                    logger.info("Gradient Neighbors Not available.");
                    continue;
                }
                // schedule the request and then remove it from the buffered requests.
                scheduleTheApplicationJobOnGradient(bufferedRequestsAtScheduler.get(i), partnerDescriptors);
                eventsTobeRemoved.add(bufferedRequestsAtScheduler.get(i));
            }
           
           // Remove the requests from the buffered list.
           for(RequestResource r: eventsTobeRemoved)
               bufferedRequestsAtScheduler.remove(r);
           
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

    /**
     * True if utility1 dominant on utility2. 
     * Very Basic Comparison Mechanism to check for the Dominant utility.
     *
     * @param utility1
     * @param utility2
     * @return
     */
    private boolean isDominant(double utility1, double utility2) {
        return (utility1 >= utility2);
    }
}
