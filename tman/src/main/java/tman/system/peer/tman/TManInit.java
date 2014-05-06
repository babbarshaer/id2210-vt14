package tman.system.peer.tman;

import common.configuration.TManConfiguration;
import common.peer.AvailableResources;
import se.sics.kompics.Init;
import se.sics.kompics.address.Address;

public final class TManInit extends Init {

    private final Address peerSelf;
    private final TManConfiguration configuration;
    private final AvailableResources availableResources;
    private final GradientEnum gradientEnum;

    public TManInit(Address peerSelf, TManConfiguration configuration,
            AvailableResources availableResources, GradientEnum gradientEnum) {
        super();
        this.peerSelf = peerSelf;
        this.configuration = configuration;
        this.availableResources = availableResources;
        this.gradientEnum = gradientEnum;
    }

    public AvailableResources getAvailableResources() {
        return availableResources;
    }

    public Address getSelf() {
        return this.peerSelf;
    }

    public TManConfiguration getConfiguration() {
        return this.configuration;
    }
    
    public GradientEnum getGradientEnum(){
        return this.gradientEnum;
    }
}
