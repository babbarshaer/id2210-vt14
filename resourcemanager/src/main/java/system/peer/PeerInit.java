package system.peer;

import common.configuration.RmConfiguration;
import common.configuration.CyclonConfiguration;
import common.configuration.TManConfiguration;
import common.peer.AvailableResources;
import se.sics.kompics.Component;
import se.sics.kompics.Init;
import se.sics.kompics.address.Address;
import se.sics.kompics.p2p.bootstrap.BootstrapConfiguration;
import se.sics.kompics.p2p.overlay.chord.ChordConfiguration;

public final class PeerInit extends Init {

    private final Address peerSelf;
    private final BootstrapConfiguration bootstrapConfiguration;
    private final CyclonConfiguration cyclonConfiguration;
    private final RmConfiguration applicationConfiguration;
    private final AvailableResources availableResources;
    private final Component utilizationManager;

    //TODO: Gradient Change.
    private final TManConfiguration tManConfiguration;
    
    //TODO: Chord Change.
    private final ChordConfiguration chordConfiguration;
    
    public PeerInit(Address peerSelf, BootstrapConfiguration bootstrapConfiguration,
            CyclonConfiguration cyclonConfiguration, RmConfiguration applicationConfiguration, TManConfiguration tManConfiguration,ChordConfiguration chordConfig,
            AvailableResources availableResources, Component utilizationManager) {
        super();
        this.peerSelf = peerSelf;
        this.bootstrapConfiguration = bootstrapConfiguration;
        this.cyclonConfiguration = cyclonConfiguration;
        this.applicationConfiguration = applicationConfiguration;
        this.tManConfiguration = tManConfiguration;
        this.chordConfiguration = chordConfig;
        this.availableResources = availableResources;
        this.utilizationManager = utilizationManager;
    }

    public AvailableResources getAvailableResources() {
        return availableResources;
    }

    
    public Address getPeerSelf() {
        return this.peerSelf;
    }

    public BootstrapConfiguration getBootstrapConfiguration() {
        return this.bootstrapConfiguration;
    }

    public CyclonConfiguration getCyclonConfiguration() {
        return this.cyclonConfiguration;
    }

    public RmConfiguration getApplicationConfiguration() {
        return this.applicationConfiguration;
    }
    
    public TManConfiguration getTManConfiguration(){
        return this.tManConfiguration;
    }

    public Component getUtilizationManagerComponent(){
        return this.utilizationManager;
    }
    
    public ChordConfiguration getChordConfiguration(){
        return this.chordConfiguration;
    }
}
