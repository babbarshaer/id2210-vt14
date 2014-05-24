package system.peer;

import java.util.LinkedList;
import java.util.Set;

import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.p2p.bootstrap.BootstrapCompleted;
import se.sics.kompics.p2p.bootstrap.BootstrapRequest;
import se.sics.kompics.p2p.bootstrap.BootstrapResponse;
import se.sics.kompics.p2p.bootstrap.P2pBootstrap;
import se.sics.kompics.p2p.bootstrap.PeerEntry;
import se.sics.kompics.p2p.bootstrap.client.BootstrapClient;
import se.sics.kompics.p2p.bootstrap.client.BootstrapClientInit;
import se.sics.kompics.timer.Timer;

import resourcemanager.system.peer.rm.ResourceManager;
import resourcemanager.system.peer.rm.RmInit;
import common.configuration.RmConfiguration;
import common.configuration.CyclonConfiguration;
import common.peer.AvailableResources;
import common.peer.PeerDescriptor;
import cyclon.system.peer.cyclon.*;
import resourcemanager.system.peer.rm.ResourceManagerUpdated;
import se.sics.kompics.p2p.fd.FailureDetector;
import se.sics.kompics.p2p.overlay.chord.Chord;
import se.sics.kompics.p2p.overlay.chord.ChordConfiguration;
import se.sics.kompics.p2p.overlay.chord.ChordInit;
import simulator.snapshot.UtilizationPort;
import tman.system.peer.tman.GradientEnum;
import tman.system.peer.tman.TMan;
import tman.system.peer.tman.TManInit;
import tman.system.peer.tman.TManSamplePort;
import tman.system.peer.tman.TManUpdated;

public final class Peer extends ComponentDefinition {

    Positive<RmPort> rmPort = positive(RmPort.class);

    Positive<Network> network = positive(Network.class);
    Positive<Timer> timer = positive(Timer.class);
    Positive<FailureDetector> failureDetector = positive(FailureDetector.class);
    
    private Component cyclon, cpuTman, rm, bootstrap, memoryTman , chord;
    private Address self;
    private int bootstrapRequestPeerCount;          // View Size.
    private boolean bootstrapped;
    private RmConfiguration rmConfiguration;
    private ChordConfiguration chordConfig;

    private AvailableResources availableResources;
    private Component utilizationManager;


    public Peer() {
        cyclon = create(Cyclon.class);
        cpuTman = create(TManUpdated.class);
        memoryTman = create(TManUpdated.class);
        rm = create(ResourceManagerUpdated.class);
        bootstrap = create(BootstrapClient.class);
        chord = create(Chord.class);

        connect(network, rm.getNegative(Network.class));
        connect(network, cyclon.getNegative(Network.class));
        connect(network, bootstrap.getNegative(Network.class));
        connect(network, cpuTman.getNegative(Network.class));
        connect(network, memoryTman.getNegative(Network.class));
        connect(timer, rm.getNegative(Timer.class));
        connect(timer, cyclon.getNegative(Timer.class));
        connect(timer, bootstrap.getNegative(Timer.class));
        connect(timer, cpuTman.getNegative(Timer.class));
        connect(timer, memoryTman.getNegative(Timer.class));
        connect(cyclon.getPositive(CyclonSamplePort.class),
                rm.getNegative(CyclonSamplePort.class));
        connect(cyclon.getPositive(CyclonSamplePort.class),
                cpuTman.getNegative(CyclonSamplePort.class));
         connect(cyclon.getPositive(CyclonSamplePort.class),
                memoryTman.getNegative(CyclonSamplePort.class));
        connect(cpuTman.getPositive(TManSamplePort.class),
                rm.getNegative(TManSamplePort.class));
        connect(memoryTman.getPositive(TManSamplePort.class),
                rm.getNegative(TManSamplePort.class));

        connect(rmPort, rm.getNegative(RmPort.class));
        
        // connect chord to other components.
        connect(network, chord.getNegative(Network.class));
        connect(timer, chord.getNegative(Timer.class));
        connect(failureDetector, chord.getNegative(FailureDetector.class));

        subscribe(handleInit, control);
        subscribe(handleJoinCompleted, cyclon.getPositive(CyclonPort.class));
        subscribe(handleBootstrapResponse, bootstrap.getPositive(P2pBootstrap.class));
    }

    Handler<PeerInit> handleInit = new Handler<PeerInit>() {
        @Override
        public void handle(PeerInit init) {
            self = init.getPeerSelf();
            CyclonConfiguration cyclonConfiguration = init.getCyclonConfiguration();
            rmConfiguration = init.getApplicationConfiguration();
            bootstrapRequestPeerCount = cyclonConfiguration.getBootstrapRequestPeerCount();

            utilizationManager = init.getUtilizationManagerComponent();
            availableResources = init.getAvailableResources();
            
            chordConfig = init.getChordConfiguration();

            // Booting up the cyclon by sending event to its control port.
            trigger(new CyclonInit(cyclonConfiguration, availableResources), cyclon.getControl());
            // While booting up the peer sends the self address and initial configuration received from application to the BootStrapComponent.
            trigger(new BootstrapClientInit(self, init.getBootstrapConfiguration()), bootstrap.getControl());
            // Sending request to the bootstrap with the overlay details .
            BootstrapRequest request = new BootstrapRequest("Cyclon", bootstrapRequestPeerCount);
            trigger(request, bootstrap.getPositive(P2pBootstrap.class));

            // Create Both Gradients.
            trigger(new TManInit(self, init.getTManConfiguration(), availableResources, GradientEnum.CPU), cpuTman.getControl());
            trigger(new TManInit(self, init.getTManConfiguration(), availableResources, GradientEnum.MEMORY), memoryTman.getControl());
            
            //TODO: Chord Change. (Trigger the Chord Initiation Here.)
            trigger(new ChordInit(chordConfig.getLog2RingSize(), chordConfig.getSuccessorListLength(), chordConfig.getSuccessorStabilizationPeriod() , chordConfig.getFingerStabilizationPeriod() , chordConfig.getRpcTimeout()), chord.getControl());
        }

       
    };

    Handler<BootstrapResponse> handleBootstrapResponse = new Handler<BootstrapResponse>() {
        @Override
        public void handle(BootstrapResponse event) {
            if (!bootstrapped) {

                Set<PeerEntry> somePeers = event.getPeers();
                LinkedList<Address> cyclonInsiders = new LinkedList<Address>();

                for (PeerEntry peerEntry : somePeers) {
                    cyclonInsiders.add(
                            peerEntry.getOverlayAddress().getPeerAddress());
                }
                trigger(new CyclonJoin(self, cyclonInsiders),
                        cyclon.getPositive(CyclonPort.class));
                bootstrapped = true;
            }
            //FIXME: The code for the chord initialization should go here because here I receive the entries from the bootstrapper.
            // I can create the Chord Address here and based on the entries returned, then I can perform checking and trigger a create ring or join node request.
        }
    };

    // Event received once the cyclon join has been completed successfully.
    Handler<JoinCompleted> handleJoinCompleted = new Handler<JoinCompleted>() {
        @Override
        public void handle(JoinCompleted event) {
            trigger(new BootstrapCompleted("Cyclon", new PeerDescriptor(self,
                    availableResources.getNumFreeCpus(),
                    availableResources.getFreeMemInMbs())),
                    bootstrap.getPositive(P2pBootstrap.class));
            // Now initialize the Resource Manager which communicates with the Cyclon to fetch the random samples.
            connect(utilizationManager.getPositive(UtilizationPort.class) , rm.getNegative(UtilizationPort.class));
            trigger(new RmInit(self, rmConfiguration, availableResources), rm.getControl());
        }
    };

}
