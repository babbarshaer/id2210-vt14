package tman.system.peer.tman;

import cyclon.system.peer.cyclon.PeerDescriptor;
import java.util.ArrayList;


import se.sics.kompics.Event;
import se.sics.kompics.address.Address;

/**
 * Represents the information contained in the sample returned by the TMan sampling.
 */
public class TManSample extends Event {
    
                      //  Gradient Ascent Change.  
	ArrayList<Address> partners = new ArrayList<Address>();
                     ArrayList<PeerDescriptor> partnersDescriptor = new ArrayList<PeerDescriptor>();        //FIXME: Check whether a copy of the object is sent in the message and not the original ones, as concurrent access exception.
                     private GradientEnum gradientEnum;

	public TManSample(ArrayList<Address> partners) {
		this.partners = partners;
	}
        
	public TManSample() {
	}

                     public TManSample(ArrayList<Address> partners , ArrayList<PeerDescriptor> partnersDescriptor , GradientEnum gradientEnum){
                         this.partners = partners;
                         this.partnersDescriptor = partnersDescriptor;
                         this.gradientEnum = gradientEnum;
                     }

	public ArrayList<Address> getSample() {
		return this.partners;
	}
            
                    public ArrayList<PeerDescriptor> getPartnersDescriptor(){
                        return this.partnersDescriptor;
                    }
                    
                    
                    public GradientEnum getGradientEnum(){
                        return this.gradientEnum;
                    }
                    
}
