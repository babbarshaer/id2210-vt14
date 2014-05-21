package common.simulation;

import common.configuration.CyclonConfiguration;
import common.configuration.RmConfiguration;
import common.configuration.TManConfiguration;
import se.sics.kompics.Component;
import se.sics.kompics.Init;
import se.sics.kompics.p2p.bootstrap.BootstrapConfiguration;
import se.sics.kompics.p2p.overlay.chord.ChordConfiguration;

public final class SimulatorInit extends Init {

    private final BootstrapConfiguration bootstrapConfiguration;
    private final CyclonConfiguration cyclonConfiguration;
    private final TManConfiguration tmanConfiguration;
    private final RmConfiguration aggregationConfiguration;
    private final Component utilizationManagerComponent;
    
    //Adding Chord Protocol Support.
    private final ChordConfiguration chordConfiguration;

	
    public SimulatorInit(BootstrapConfiguration bootstrapConfiguration,
            CyclonConfiguration cyclonConfiguration, TManConfiguration tmanConfiguration,
            RmConfiguration aggregationConfiguration,Component utilizationManager , ChordConfiguration chordConfig) {
        super();
        this.bootstrapConfiguration = bootstrapConfiguration;
        this.cyclonConfiguration = cyclonConfiguration;
        this.tmanConfiguration = tmanConfiguration;
        this.aggregationConfiguration = aggregationConfiguration;
        this.utilizationManagerComponent = utilizationManager;
        this.chordConfiguration = chordConfig;
    }

    public RmConfiguration getAggregationConfiguration() {
        return aggregationConfiguration;
    }

	
    public BootstrapConfiguration getBootstrapConfiguration() {
        return this.bootstrapConfiguration;
    }

	
    public CyclonConfiguration getCyclonConfiguration() {
        return this.cyclonConfiguration;
    }

	
    public TManConfiguration getTmanConfiguration() {
        return this.tmanConfiguration;
    }
    
    public Component  getUtilizationManagerComponent(){
        return this.utilizationManagerComponent;
    }
    
    public ChordConfiguration getChordConfiguration(){
        return this.chordConfiguration;
    }

}
