package cyclon.system.peer.cyclon;

import java.io.Serializable;
import se.sics.kompics.address.Address;


/**
 * Represents the information about the peer in the system.
 */
public class PeerDescriptor implements Comparable<PeerDescriptor>, Serializable {
    
	private static final long serialVersionUID = 1906679375438244117L;
	private final Address peerAddress;
	private int age;
                      
                    //TODO: Gradient Change.
                    private int freeCpu;
                    private int freeMemory;
                    

	public PeerDescriptor(Address peerAddress) {
		this.peerAddress = peerAddress;
		this.age = 0;
	}
        
                      public PeerDescriptor(Address peerAddress , int freeCpu , int freeMemory){
                          
                          this(peerAddress);
                          this.freeCpu = freeCpu;
                          this.freeMemory  = freeMemory;
                      }
                      
                      // Basic Cloning of the object.
                      public PeerDescriptor(PeerDescriptor other){
                          this.peerAddress = other.peerAddress;
                          this.age = other.age;
                          this.freeCpu = other.freeCpu;
                          this.freeMemory = other.freeMemory;
                      }
                      
                      public int getFreeMemory(){
                          return this.freeMemory;
                      }
                      
                      public int getFreeCpu(){
                          return this.freeCpu;
                      }
                      
	public int incrementAndGetAge() {
		age++;
		return age;
	}


	public int getAge() {
		return age;
	}


	public Address getAddress() {
		return peerAddress;
	}


	@Override
	public int compareTo(PeerDescriptor that) {
		if (this.age > that.age)
			return 1;
		if (this.age < that.age)
			return -1;
		return 0;
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((peerAddress == null) ? 0 : peerAddress.hashCode());
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PeerDescriptor other = (PeerDescriptor) obj;
		if (peerAddress == null) {
			if (other.peerAddress != null)
				return false;
		} else if (!peerAddress.equals(other.peerAddress))
			return false;
		return true;
	}


	@Override
	public String toString() {
		return peerAddress + "";
	}
	
}
