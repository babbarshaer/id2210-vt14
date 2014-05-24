package tman.system.peer.tman;

import common.configuration.TManConfiguration;
import common.peer.AvailableResources;
import java.util.ArrayList;

import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;
import cyclon.system.peer.cyclon.DescriptorBuffer;
import cyclon.system.peer.cyclon.PeerDescriptor;
import cyclon.system.peer.cyclon.ShuffleTimeout;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;

import tman.simulator.snapshot.Snapshot;

public final class TManUpdated extends ComponentDefinition {

    private static final Logger logger = LoggerFactory.getLogger(TManUpdated.class);

    Negative<TManSamplePort> tmanPort = negative(TManSamplePort.class);
    Positive<CyclonSamplePort> cyclonSamplePort = positive(CyclonSamplePort.class);
    Positive<Network> networkPort = positive(Network.class);
    Positive<Timer> timerPort = positive(Timer.class);
    private long period;
    private Address self;
    private TManConfiguration tmanConfiguration;
    private Random r;
    private AvailableResources availableResources;

    // TODO: Gradient Change.
    private int shuffleLength;
    private int similarViewSize;
    private long shuffleTimeout;
    private GradientCache gradientCache;
    GradientEnum gradientEnum;
    private ArrayList<PeerDescriptor> fingerList;
    boolean joining = false;
    private HashMap<UUID, Address> outstandingRandomShuffles;

    public class TManSchedule extends Timeout {

        public TManSchedule(SchedulePeriodicTimeout request) {
            super(request);
        }

        public TManSchedule(ScheduleTimeout request) {
            super(request);
        }
    }

    public TManUpdated() {

        subscribe(handleInit, control);
        subscribe(handleRound, timerPort);
        subscribe(handleCyclonSample, cyclonSamplePort);
        subscribe(handleTManPartnersResponse, networkPort);
        subscribe(handleTManPartnersRequest, networkPort);

    }

    Handler<TManInit> handleInit = new Handler<TManInit>() {
        @Override
        public void handle(TManInit init) {

            self = init.getSelf();
            tmanConfiguration = init.getConfiguration();
            period = tmanConfiguration.getPeriod();
            r = new Random(tmanConfiguration.getSeed());
            availableResources = init.getAvailableResources();
            //TODO: Gradient Change
            shuffleLength = tmanConfiguration.getShuffleLength();
            similarViewSize = tmanConfiguration.getSimilarViewSize();
            shuffleTimeout = tmanConfiguration.getShuffleTimeout();
            gradientEnum = init.getGradientEnum();
            gradientCache = new GradientCache(similarViewSize, self, availableResources, tmanConfiguration.getTemperature(), r, gradientEnum);

            // Finger Implementation.
            fingerList = new ArrayList<PeerDescriptor>();
            outstandingRandomShuffles = new HashMap<UUID, Address>();

        }
    };

    /**
     * Periodically send the TMan Sample up to the Resource Manager.
     */
    Handler<TManSchedule> handleRound = new Handler<TManSchedule>() {

        @Override
        public void handle(TManSchedule event) {

            Snapshot.updateTManPartners(self, getSimilarPeers());
            // Increment the age and remove the entry with oldest age to keep rotating.
//            gradientCache.incrementDescriptorAges();
            Address randomPeer = gradientCache.getSoftMaxAddressForGradient();
            // Only if you find a peer to shuffle the view with you progress.
            if (randomPeer != null) {

                //Here you will initiate the shuffle with the neighbors for now.
                initiateGradientShuffle(shuffleLength, randomPeer);
                // Publish sample to connected components.
                trigger(new TManSample(getSimilarPeers(), getSimilarPeersInfo(), gradientEnum, fingerList), tmanPort);
            } else {
                logger.info("Random Peer To Talk to is null .....");
////                logger.info("Cyclon Sample Length : " + cyclonPeerDescriptors.size());
            }
        }
    };

    private void printSelfInformation() {
        logger.info("Node: " + self + " ~~ Self Cpu:  " + availableResources.getNumFreeCpus() + " ~~ Self Memory: " + availableResources.getFreeMemInMbs());
    }

    /**
     * Check if the gradient is converging by printing this information.
     */
    private void printNodeViewResourceInfo() {
        List<PeerDescriptor> partnerDescriptors = gradientCache.getAll();
        for (PeerDescriptor partner : partnerDescriptors) {
            logger.info("Node: " + self + " ~~ Self Cpu:  " + availableResources.getNumFreeCpus() + " ~~ PeerAddress: " + " ~~ Cpu: " + partner.getFreeCpu() + " ~~ Memory: " + partner.getFreeMemory());
        }
    }

    /**
     * Incorporate the data received by the Cyclon in the similar view
     * maintained by TMan.
     */
    Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
        @Override
        public void handle(CyclonSample event) {

            List<Address> randomCyclonPartners = event.getSample();
            ArrayList<PeerDescriptor> partnerDescriptors = (ArrayList<PeerDescriptor>) event.getPartnersDescriptor();

            if (!randomCyclonPartners.isEmpty()) {

                if (!joining) {
                    //First time exchanging the data.
                    initiateGradientShuffle(1, randomCyclonPartners.get(0));
                } 
                else {
                    gradientCache.selectToKeepRandom(partnerDescriptors);
                    refershFingerList(partnerDescriptors);
                }
            }
        }
    };

    /**
     * Simply refresh the finger list based on the gradient.
     */
    private void refershFingerList(List<PeerDescriptor> randomCyclonPartners) {

        // Create a separate list.
        ArrayList<PeerDescriptor> copyPeerDescriptors = new ArrayList<PeerDescriptor>(randomCyclonPartners);
        ComparatorByResource resourceBasedComparator = new ComparatorByResource(gradientEnum);
        Collections.sort(copyPeerDescriptors, resourceBasedComparator);

        // Based on the length of the finger list, shorten the resultant finger list.
        // For now lets use the shuffle length as the basis for the finger list length.
        if (copyPeerDescriptors.size() <= shuffleLength) {
            fingerList = copyPeerDescriptors;
        } else {
            //pick the top ones from the list according to the shuffle length.
            copyPeerDescriptors.subList(shuffleLength, copyPeerDescriptors.size()).clear();
            fingerList = copyPeerDescriptors;
        }
    }

    /**
     * Initiate shuffle with the neighbor.
     */
    private void initiateGradientShuffle(int shuffleLength, Address peerAddress) {

        ArrayList<PeerDescriptor> partnerDescriptors = gradientCache.selectToSendAtActive(shuffleLength - 1, peerAddress);
        partnerDescriptors.add(new PeerDescriptor(self, availableResources.getNumFreeCpus(), availableResources.getFreeMemInMbs()));

        // Create an instance of the descriptor buffer.
        DescriptorBuffer similarPeersBuffer = new DescriptorBuffer(self, partnerDescriptors);
        ScheduleTimeout sst = new ScheduleTimeout(shuffleTimeout);
        sst.setTimeoutEvent(new ShuffleTimeout(sst, peerAddress));
        UUID requestId = sst.getTimeoutEvent().getTimeoutId();

        //FIXME: Create a List holder for the schedule timeout list.
        ExchangeMsg.Request request = new ExchangeMsg.Request(requestId, similarPeersBuffer, self, peerAddress);

        outstandingRandomShuffles.put(requestId, peerAddress);

        // Trigger the timeout and the schuffle request.
        trigger(sst, timerPort);
        trigger(request, networkPort);

    }

    // Exchange the similar View with the neighbor.
    Handler<ExchangeMsg.Request> handleTManPartnersRequest = new Handler<ExchangeMsg.Request>() {
        @Override
        public void handle(ExchangeMsg.Request event) {

//            logger.info("Request For TMan Shuffling ....");
            //Handle the Schuffle Request.
            Address peer = event.getRandomBuffer().getFrom();
            DescriptorBuffer receivedDescriptorBuffer = event.getRandomBuffer();
            DescriptorBuffer descriptorBufferToSend = new DescriptorBuffer(self, gradientCache.selectToSendAtPassive(receivedDescriptorBuffer.getSize(), peer));

            gradientCache.selectToKeep(peer,receivedDescriptorBuffer.getDescriptors());
            ExchangeMsg.Response response = new ExchangeMsg.Response(event.getRequestId(), descriptorBufferToSend, self, peer);

            //Send reply back to the peer.
            trigger(response, networkPort);
        }
    };

    /**
     * T-Man View Shuffle Exchange Response Handler.
     */
    Handler<ExchangeMsg.Response> handleTManPartnersResponse = new Handler<ExchangeMsg.Response>() {
        @Override
        public void handle(ExchangeMsg.Response event) {

            if (!joining) {
                //First Response Received, start the periodic scheduler.
                SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(period, period);
                rst.setTimeoutEvent(new TManSchedule(rst));
                trigger(rst, timerPort);

                joining = true;
            }
            // FIXME: Create  a Handler for the timeout.
            UUID requestId = event.getRequestId();

            if (outstandingRandomShuffles.containsKey(requestId)) {
                outstandingRandomShuffles.remove(requestId);
                CancelTimeout cancelTimeout = new CancelTimeout(requestId);
                trigger(cancelTimeout, timerPort);
            }

            Address peer = event.getSelectedBuffer().getFrom();
            DescriptorBuffer receivedDescriptorBuffer = event.getSelectedBuffer();
            gradientCache.selectToKeep(peer, receivedDescriptorBuffer.getDescriptors());

        }
    };
    
    
    /**
     * Shuffle Timeout Handler.
     */
    Handler<ShuffleTimeout> shuffleTimeoutHandler =  new Handler<ShuffleTimeout>(){
        @Override
        public void handle(ShuffleTimeout event) {
            //Do Something Here.
        }
    };
    

    // TODO - if you call this method with a list of entries, it will
    // return a single node, weighted towards the 'best' node (as defined by
    // ComparatorById) with the temperature controlling the weighting.
    // A temperature of '1.0' will be greedy and always return the best node.
    // A temperature of '0.000001' will return a random node.
    // A temperature of '0.0' will throw a divide by zero exception :)
    // Reference:
    // http://webdocs.cs.ualberta.ca/~sutton/book/2/node4.html
    public Address getSoftMaxAddress(List<Address> entries) {
        Collections.sort(entries, new ComparatorById(self));

        double rnd = r.nextDouble();
        double total = 0.0d;
        double[] values = new double[entries.size()];
        int j = entries.size() + 1;
        for (int i = 0; i < entries.size(); i++) {
            // get inverse of values - lowest have highest value.
            double val = j;
            j--;
            values[i] = Math.exp(val / tmanConfiguration.getTemperature());
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
     * Get the list of peers in the view of the node.
     *
     * @return
     */
    private ArrayList<Address> getSimilarPeers() {

        ArrayList<Address> similarPartnersAddress = new ArrayList<Address>();
        for (PeerDescriptor partnerDescriptor : gradientCache.getAll()) {
            similarPartnersAddress.add(partnerDescriptor.getAddress());
        }

        return similarPartnersAddress;
    }

    /**
     * Fetch Similar Peers Information Also.
     *
     * @return
     */
    private ArrayList<PeerDescriptor> getSimilarPeersInfo() {

//        ArrayList<PeerDescriptor> descriptorsToBeReturned = new ArrayList<PeerDescriptor>();
//
//        for (PeerDescriptor neighbor : gradientCache.getAll()) {
//            descriptorsToBeReturned.add(new PeerDescriptor(neighbor));
//        }
//        return descriptorsToBeReturned;
        
        return gradientCache.getAll();
    }

}
