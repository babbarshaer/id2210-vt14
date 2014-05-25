package common.simulation;

import se.sics.kompics.PortType;
import se.sics.kompics.p2p.experiment.dsl.events.TerminateExperiment;

public class SimulatorPort extends PortType {{
	positive(PeerJoin.class);
	positive(PeerFail.class);
	positive(RequestResource.class);
	positive(TerminateExperiment.class);
                      positive(BootstrapUtilizationHandler.class);
                      positive(ResourceRequestInitiation.class);
                      positive(BatchRequest.class);
	negative(TerminateExperiment.class);
        
}}
