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
    private LinkedList<PeerJobDetail> peerSubmittedJobList;

    public ResourceManager() {

        subscribe(handleInit, control);
        subscribe(handleCyclonSample, cyclonSamplePort);
        subscribe(handleRequestResource, indexPort);
        subscribe(handleUpdateTimeout, timerPort);
        subscribe(handleResourceAllocationRequest, networkPort);
        subscribe(handleResourceAllocationResponse, networkPort);
        subscribe(handleTManSample, tmanPort);

        subscribe(processExecutionDecision, networkPort);
        subscribe(jobCompletionTimeout, timerPort);
        subscribe(requestCompletionEvent, networkPort);

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
            peerSubmittedJobList = new LinkedList<PeerJobDetail>();
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

            logger.info("Received resource request from: " + event.getSource().toString());

            // Step1: Create an instance of job detail as submitted by the peer.
            PeerJobDetail peerJobDetail = new PeerJobDetail(event.getNumCpus(), event.getAmountMemInMb(), event.getRequestId(), event.getTimeToHoldResource(), event.getSource());
            peerSubmittedJobList.add(peerJobDetail);

            //Step2: Check for free resources and then allocate them.
            checkAvailableResourcesUpdated();

        }
    };

    Handler<RequestResources.Response> handleResourceAllocationResponse = new Handler<RequestResources.Response>() {
        @Override
        public void handle(RequestResources.Response event) {

            //  If status is successfull then we have the request for the resource allocation at the front at the worker.
            if (event.isSuccessful()) {

                ApplicationJobDetail detail = null;

                for (ApplicationJobDetail jobDetail : schedulerJobList) {
                    if (jobDetail.getRequestId() == event.getId()) {
                        detail = jobDetail;
                        break;
                    }
                }
                if (detail == null) {
                    logger.error(" Not able to find job with Id:  " + event.getId());
                    return;
                }

                // Check if the resource has already been requested or not.
                JobStatusEnum status = detail.getJobStatus();
                ProcessRequestResponse processRequestResponse = null;

                if (JobStatusEnum.REQUESTED == status) {

                    // Send Ack Message and update the status in the map.
                    processRequestResponse = new ProcessRequestResponse(self, event.getSource(), event.getId(), true);

                    //  applicationResourceRequest.put(event.getId(), JobStatusEnum.PROCESSING.getStatus());
                    // update the job status of the scheduled job.
                    detail.setJobStatus(JobStatusEnum.PROCESSING);

                } else if (JobStatusEnum.PROCESSING == status) {
                    // Send a Nack Message.
                    processRequestResponse = new ProcessRequestResponse(self, event.getSource(), event.getId(), false);
                }
                trigger(processRequestResponse, networkPort);
            }
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

    // Request the node for probing the neighbours that are received through the cyclon to check for availability of resources.
    Handler<RequestResource> handleRequestResource = new Handler<RequestResource>() {
        @Override
        public void handle(RequestResource event) {

            logger.info("Allocate resources:  cpu: " + event.getNumCpus() + " + memory: " + event.getMemoryInMbs() + " + id: " + event.getId());

            int counter = 0;

            if (neighbours.isEmpty()) {
                return;
            }

            ApplicationJobDetail applicationJobDetail = new ApplicationJobDetail(event);
            schedulerJobList.add(applicationJobDetail);
            logger.info("Job Received From Application: " + applicationJobDetail.toString());

            for (Address dest : neighbours) {
                // Simply send probe to first 4 neighbours for now.
                // FIXME: Add randomization to the selection criteria.
                if (counter < probeRatio) {
                    RequestResources.Request req = new RequestResources.Request(self, dest, event.getNumCpus(), event.getMemoryInMbs(), event.getId(), event.getTimeToHoldResource());
                    trigger(req, networkPort);
                    counter += 1;
                } else {
                    break;
                }
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
     * Check for the available resources updated.
     */
    private void checkAvailableResourcesUpdated() {

        for (PeerJobDetail peerJobDetail : peerSubmittedJobList) {

            //Check for jobs to be retried.
            if (peerJobDetail.getJobStatus() == JobStatusEnum.WORKER_RETRY && availableResources.allocate(peerJobDetail.getCpu(), peerJobDetail.getMemory())) {
                // simply go for execution.
                executeJob(peerJobDetail);
            } 
            // Check only for the queued jobs.
            else if (peerJobDetail.getJobStatus() == JobStatusEnum.QUEUED && availableResources.isAvailable(peerJobDetail.getCpu(), peerJobDetail.getMemory())) {

                // Reply to scheduler stating that it has been scheduled, do you want to continue.
                RequestResources.Response response = new RequestResources.Response(self, peerJobDetail.getSchedulerAddress(), true, peerJobDetail.getRequestId());
                trigger(response, networkPort);
                //Update the status of the job. 
                peerJobDetail.setJobStatus(JobStatusEnum.SCHEDULED);
            }
            // Check in the remaning queue for other jobs with lower capacity. Don't sit idle.
        }
    }

    /**
     * Process the scheduler decision regarding the execution of the task.
     */
    Handler<ProcessRequestResponse> processExecutionDecision = new Handler<ProcessRequestResponse>() {

        @Override
        public void handle(ProcessRequestResponse event) {

            long requestId = event.getRequestId();
            PeerJobDetail jobDetail = null;

            for (PeerJobDetail peerJobDetail : peerSubmittedJobList) {
                if (peerJobDetail.getJobStatus() == JobStatusEnum.SCHEDULED && peerJobDetail.getRequestId() == requestId) {
                    jobDetail = peerJobDetail;
                    break;
                }
            }

            if (jobDetail == null) {
                logger.error("Job not found in the scheduled list ....");
                return;
            }

            if (event.getStatus() == Boolean.TRUE) {
                //Check again for the available resources.
                if (availableResources.allocate(jobDetail.getCpu(), jobDetail.getMemory())) {

                    executeJob(jobDetail);

                } else {
                    // For now just reject the job.
                    logger.warn("The job cannot be executed due to lack of resources .... ");
                    peerSubmittedJobList.remove(jobDetail);         // TODO: Fix this situation.
                }

            } else {
                //Retry the job again.
                jobDetail.setJobStatus(JobStatusEnum.WORKER_RETRY);
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
            PeerJobDetail jobDetail = event.getPeerJobDetail();
            availableResources.release(jobDetail.getCpu(), jobDetail.getMemory());
            logger.info("Resources Released: " + "Cpu: " + jobDetail.getCpu() + " Memory: " + jobDetail.getMemory());

            //Step2: Remove the resource from the processed list as it has been completely processed.
            peerSubmittedJobList.remove(jobDetail);

            //Step3:Send the completion request to the scheduler about the completion of the request.
            JobCompletionEvent requestCompletionEvent = new JobCompletionEvent(self, jobDetail.getSchedulerAddress(), jobDetail.getRequestId());
            trigger(requestCompletionEvent, networkPort);

            //Check the available resources again, to see which jobs can be configured.
            checkAvailableResourcesUpdated();

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
    private void executeJob(PeerJobDetail jobDetail) {
        //Schedule a timeout for the resource.
        ScheduleTimeout st = new ScheduleTimeout(jobDetail.getTimeToHoldResource());
        JobCompletionTimeout timeout = new JobCompletionTimeout(st, jobDetail);
        st.setTimeoutEvent(timeout);
        trigger(st, timerPort);
    }
}
