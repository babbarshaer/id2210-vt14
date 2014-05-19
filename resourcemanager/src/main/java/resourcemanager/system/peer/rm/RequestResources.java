package resourcemanager.system.peer.rm;

import java.util.List;
import java.util.UUID;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 * User: jdowling
 */
public class RequestResources  {

    public static class Request extends Message {

        private final long requestId;
        private final int numCpus;
        private final int amountMemInMb;
        private final int timeToHoldResource;
        private final List<Address> peerList;
        private final UUID resourceRequestUUID;
        
        
        public Request(Address source, Address destination, int numCpus, int amountMemInMb, long requestId , int timeToHoldResource , List<Address> peerList , UUID resourceRequestUUID) {
            super(source, destination);
            this.numCpus = numCpus;
            this.amountMemInMb = amountMemInMb;
            this.requestId = requestId;
            this.timeToHoldResource = timeToHoldResource;
            this.peerList = peerList;
            this.resourceRequestUUID  = resourceRequestUUID;
        }

        public int getAmountMemInMb() {
            return amountMemInMb;
        }

        public int getNumCpus() {
            return numCpus;
        }

        public long getRequestId(){
            return this.requestId;
        }

        /**
         * @return the timeToHoldResource
         */
        public int getTimeToHoldResource() {
            return timeToHoldResource;
        }
        
        /**
         * 
         * @return peer list
         */
        public List<Address> getPeers(){
            return this.peerList;
        }
        
        public UUID getResourceRequestUUID(){
            return this.resourceRequestUUID;
        }
        
        
    }
    
    public static class Response extends Message {

        private final long id;
        private final boolean success;
        public Response(Address source, Address destination, boolean success, long id) {
            super(source, destination);
            this.success = success;
            this.id = id;
        }
        
        public boolean  isSuccessful(){
            return this.success;
        }
        public long getId(){
            return this.id;
        }
    }
    
    public static class RequestTimeout extends Timeout {
        private final Address destination;
        RequestTimeout(ScheduleTimeout st, Address destination) {
            super(st);
            this.destination = destination;
        }

        public Address getDestination() {
            return destination;
        }
    }
}
