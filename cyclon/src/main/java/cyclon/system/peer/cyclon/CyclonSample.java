package cyclon.system.peer.cyclon;

import java.util.ArrayList;
import java.util.List;


import se.sics.kompics.Event;
import se.sics.kompics.address.Address;


public class CyclonSample extends Event {
	ArrayList<Address> nodes = new ArrayList<Address>();
                     List<PeerDescriptor> partnersDescriptor;

                    //TODO: Gradient Change.
	public CyclonSample(ArrayList<Address> nodes) {
		this.nodes = nodes;
	}
                    
                    public CyclonSample(ArrayList<Address> nodes , List<PeerDescriptor> partnersDescriptor) {
		this(nodes);
                                           this.partnersDescriptor = partnersDescriptor;
	}
                    
	public CyclonSample() {
	}


	public ArrayList<Address> getSample() {
		return this.nodes;
	}
                      
                      public List<PeerDescriptor> getPartnersDescriptor(){
                          return this.partnersDescriptor;
                      }
}
