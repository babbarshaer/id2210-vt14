package resourcemanager.system.peer.rm;

import common.configuration.RmConfiguration;
import common.peer.AvailableResources;
import se.sics.kompics.Init;
import se.sics.kompics.address.Address;

public final class RmInit extends Init {

    private final Address peerSelf;
    private final RmConfiguration configuration;
    private final AvailableResources availableResources;

    public RmInit(Address peerSelf, RmConfiguration configuration,
            AvailableResources availableResources) {
        super();
        this.peerSelf = peerSelf;
        this.configuration = configuration;
        this.availableResources = availableResources;
        
    }

    public AvailableResources getAvailableResources() {
        return availableResources;
    }

    public Address getSelf() {
        return this.peerSelf;
    }

    public RmConfiguration getConfiguration() {
        return this.configuration;
    }
}
