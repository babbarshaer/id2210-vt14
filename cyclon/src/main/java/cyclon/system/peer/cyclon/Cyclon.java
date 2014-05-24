package cyclon.system.peer.cyclon;

import common.configuration.CyclonConfiguration;
import common.peer.AvailableResources;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;

import se.sics.kompics.address.Address;

public final class Cyclon extends ComponentDefinition {

	Negative<CyclonPort> cyclonPort = negative(CyclonPort.class);
	Negative<CyclonPartnersPort> partnersPort = negative(CyclonPartnersPort.class);
	Negative<CyclonSamplePort> samplePort = negative(CyclonSamplePort.class);
	Positive<Network> networkPort = positive(Network.class);
	Positive<Timer> timerPort = positive(Timer.class);

	private Address self;
	private int shuffleLength;
	private long shufflePeriod;
	private long shuffleTimeout;
	private Cache cache;
	private boolean joining;
	private CyclonConfiguration cyclonConfiguration;
	private HashMap<UUID, Address> outstandingRandomShuffles;

                     private AvailableResources availableResources;
	
	public Cyclon() {
		outstandingRandomShuffles = new HashMap<UUID, Address>();

		subscribe(handleInit, control);
		subscribe(handleJoin, cyclonPort);
		subscribe(handleInitiateShuffle, timerPort);
		subscribe(handleShuffleTimeout, timerPort);
		subscribe(handleShuffleRequest, networkPort);
		subscribe(handleShuffleResponse, networkPort);
		subscribe(handlePartnersRequest, partnersPort);
	}

	
	Handler<CyclonInit> handleInit = new Handler<CyclonInit>() {
		public void handle(CyclonInit init) {
			cyclonConfiguration = init.getConfiguration();
                                                                availableResources = init.getAvailableResources();
			shuffleLength = cyclonConfiguration.getShuffleLength();
			shufflePeriod = cyclonConfiguration.getShufflePeriod();
			shuffleTimeout = cyclonConfiguration.getShuffleTimeout();
		}
	};

	
	/**
	 * Handles a request to join a Cyclon network using a set of insider
	 * nodes provided in the Join event.
	 */
	Handler<CyclonJoin> handleJoin = new Handler<CyclonJoin>() {
		public void handle(CyclonJoin event) {
			self = event.getSelf();
			cache = new Cache(cyclonConfiguration.getRandomViewSize(), self);
			
			LinkedList<Address> insiders = event.getCyclonInsiders();

                                                                //FIXME: Fix the problem of simultaneous Node Joins.. 
			if (insiders.size() == 0) {
				// I am the first peer, Potential Problem , when many systems simultaneously join  the system.
				trigger(new JoinCompleted(self), cyclonPort);

				// schedule shuffling
				SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(shufflePeriod, shufflePeriod);
				spt.setTimeoutEvent(new InitiateShuffle(spt));
				trigger(spt, timerPort);
				return;
			}

			Address peer = insiders.poll();
			initiateShuffle(1, peer);   // Only called with value 1 initially.
			joining = true;
		}
	};

	
	/**
	 * initiates a shuffle of size <code>shuffleSize</code>. Called either
	 * during the join protocol with a <code>shuffleSize</code> of 1, or
	 * periodically to initiate regular shuffles.
	 * 
	 * @param shuffleSize
	 * @param randomPeer
	 */
	private void initiateShuffle(int shuffleSize, Address randomPeer) {
		// send the random view to a random peer
		ArrayList<PeerDescriptor> randomDescriptors = cache.selectToSendAtActive(shuffleSize - 1, randomPeer);
                //TODO: Gradient Change.
		randomDescriptors.add(new PeerDescriptor(self ,availableResources.getNumFreeCpus() , availableResources.getFreeMemInMbs()));    // Here the fresh peer gets added in the exchanged view. 
		DescriptorBuffer randomBuffer = new DescriptorBuffer(self, randomDescriptors);
		
		ScheduleTimeout rst = new ScheduleTimeout(shuffleTimeout);
		rst.setTimeoutEvent(new ShuffleTimeout(rst, randomPeer));
		UUID rTimeoutId = rst.getTimeoutEvent().getTimeoutId();

		outstandingRandomShuffles.put(rTimeoutId, randomPeer);
		ShuffleRequest rRequest = new ShuffleRequest(rTimeoutId, randomBuffer, self, randomPeer);

		trigger(rst, timerPort);
		trigger(rRequest, networkPort);
	}

	
	/**
	 * Periodically, will initiate regular shuffles. 
	 * the "active thread" of the Cyclon specification.
	 */
	Handler<InitiateShuffle> handleInitiateShuffle = new Handler<InitiateShuffle>() {
		public void handle(InitiateShuffle event) {
			cache.incrementDescriptorAges();
			
			Address randomPeer = cache.selectPeerToShuffleWith();
			
			if (randomPeer != null) {
				initiateShuffle(shuffleLength, randomPeer);
                                trigger(new CyclonSample(getPartners(), getPartnersInfo()), samplePort);         //TODO: Gradient Change.
			}
                        else{
                            System.out.println("Cyclon Sample Is Null .... ");
                        }
		}
	};

	
	Handler<ShuffleRequest> handleShuffleRequest = new Handler<ShuffleRequest>() {
		public void handle(ShuffleRequest event) {
			Address peer = event.getRandomBuffer().getFrom();
			DescriptorBuffer receivedRandomBuffer = event.getRandomBuffer();
			DescriptorBuffer toSendRandomBuffer = new DescriptorBuffer(self, cache.selectToSendAtPassive(receivedRandomBuffer.getSize(), peer));
			cache.selectToKeep(peer, receivedRandomBuffer.getDescriptors());
			ShuffleResponse response = new ShuffleResponse(event.getRequestId(), 
                                toSendRandomBuffer, self, peer);
			trigger(response, networkPort);
			
		}
	};

	
	Handler<ShuffleResponse> handleShuffleResponse = new Handler<ShuffleResponse>() {
		public void handle(ShuffleResponse event) {
			if (joining) {
				joining = false;
				trigger(new JoinCompleted(self), cyclonPort);

				// schedule shuffling
				SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(shufflePeriod, shufflePeriod);
				spt.setTimeoutEvent(new InitiateShuffle(spt));
				trigger(spt, timerPort);
			}

			// cancel shuffle timeout
			UUID shuffleId = event.getRequestId();
			if (outstandingRandomShuffles.containsKey(shuffleId)) {
				outstandingRandomShuffles.remove(shuffleId);
				CancelTimeout ct = new CancelTimeout(shuffleId);
				trigger(ct, timerPort);
			}

			Address peer = event.getSource();
			DescriptorBuffer receivedRandomBuffer = event.getRandomBuffer();
			cache.selectToKeep(peer, receivedRandomBuffer.getDescriptors());
			
		}
	};

	
        /**
         * The peer has not replied to the shuffle request. Let the user know that they are talking with stale data.
         */
	Handler<ShuffleTimeout> handleShuffleTimeout = new Handler<ShuffleTimeout>() {
		public void handle(ShuffleTimeout event) {
                   
		}
	};
	
	
	Handler<CyclonPartnersRequest> handlePartnersRequest = new Handler<CyclonPartnersRequest>() {
		public void handle(CyclonPartnersRequest event) {
			CyclonPartnersResponse response = new CyclonPartnersResponse(getPartners());
			trigger(response, partnersPort);
		}
	};
	

	private ArrayList<Address> getPartners() {
		ArrayList<PeerDescriptor> partnersDescriptors = cache.getAll();
		ArrayList<Address> partners = new ArrayList<Address>();
		for (PeerDescriptor desc : partnersDescriptors)
			partners.add(desc.getAddress());
		
		return partners;
	}
                    
                    /**
                     * Simply return the PeerDescriptors of the nodes in the random view.
                     * @return List 
                     */
                    private List<PeerDescriptor> getPartnersInfo(){
                        
                        ArrayList<PeerDescriptor> partnersDescriptor = cache.getAll();
                        if(partnersDescriptor.isEmpty()){
                            return new ArrayList<PeerDescriptor>();
                        }
                        return (List<PeerDescriptor>)partnersDescriptor.clone();
                        // Simply add all the elements received from the cache.
//                        descriptorsToBeReturned.addAll(partnersDescriptor);
//                        return descriptorsToBeReturned;
                    }
}
