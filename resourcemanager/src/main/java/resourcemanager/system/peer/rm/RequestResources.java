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
public class RequestResources {

    public static class Request extends Message {

        private final long requestId;
        private final int numCpus;
        private final int amountMemInMb;
        private final int timeToHoldResource;
        private final List<Address> peerList;
        private final UUID resourceRequestUUID;
        
        private final boolean isBatchRequest;
        private final int  withinBatchRequestId;
        

        public Request(Address source, Address destination, int numCpus, int amountMemInMb, long requestId, int timeToHoldResource, List<Address> peerList, UUID resourceRequestUUID) {
            super(source, destination);
            this.numCpus = numCpus;
            this.amountMemInMb = amountMemInMb;
            this.requestId = requestId;
            this.timeToHoldResource = timeToHoldResource;
            this.peerList = peerList;
            this.resourceRequestUUID = resourceRequestUUID;
            this.isBatchRequest = false;
            this.withinBatchRequestId = -1;
        }
        
        public Request(Address source, Address destination, int numCpus, int amountMemInMb, long requestId, int timeToHoldResource, List<Address> peerList, UUID resourceRequestUUID , boolean isBatchRequest , int  withinBatchRequestId) {
            super(source, destination);
            this.numCpus = numCpus;
            this.amountMemInMb = amountMemInMb;
            this.requestId = requestId;
            this.timeToHoldResource = timeToHoldResource;
            this.peerList = peerList;
            this.resourceRequestUUID = resourceRequestUUID;
            this.isBatchRequest = isBatchRequest;
            this.withinBatchRequestId = withinBatchRequestId;
        }
        
        

        public int getAmountMemInMb() {
            return amountMemInMb;
        }

        public int getNumCpus() {
            return numCpus;
        }

        public long getRequestId() {
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
        public List<Address> getPeers() {
            return this.peerList;
        }

        public UUID getResourceRequestUUID() {
            return this.resourceRequestUUID;
        }
        
        public boolean isBatchRequest(){
            return this.isBatchRequest;
        }
        
        public int withinBatchRequestId(){
            return this.withinBatchRequestId;
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

        public boolean isSuccessful() {
            return this.success;
        }

        public long getId() {
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

    public static class BatchProbeRequest extends Message {

        private static final long serialVersionUID = 1L;
        private final long batchRequestId;

        public BatchProbeRequest(Address source, Address destination, long batchRequestId) {
            super(source, destination);
            this.batchRequestId = batchRequestId;
        }

        public long getBatchRequestId() {
            return this.batchRequestId;
        }

    }

    public static class BatchProbeResponse extends Message {

        private static final long serialVersionUID = 1L;
        private final long batchRequestId;
        private final long queueLength;

        public BatchProbeResponse(Address source, Address destination, long batchRequestId, long queueLength) {
            super(source, destination);
            this.batchRequestId = batchRequestId;
            this.queueLength = queueLength;
        }

        public long getBatchRequestId() {
            return this.batchRequestId;
        }

        public long getQueueLength() {
            return this.queueLength;
        }

    }
//
//    public static class BatchWorkerRequest extends Message {
//
//        private static final long serialVersionUID = 1L;
//
//        private final long batchRequestId;
//        private final long withinBatchId;
//
//        public BatchWorkerRequest(Address source, Address destination, long batchRequestId, long withinBatchId) {
//            super(source, destination);
//            this.batchRequestId = batchRequestId;
//            this.withinBatchId = withinBatchId;
//        }
//
//        @Override
//        public boolean equals(Object obj) {
//
//            if (obj instanceof BatchWorkerRequest) {
//                BatchWorkerRequest other = (BatchWorkerRequest) obj;
//                if (this.batchRequestId == other.batchRequestId && this.withinBatchId == other.withinBatchId) {
//                    return true;
//                }
//            }
//            return false;
//        }
//
//        @Override
//        public int hashCode() {
//            int hash = 7;
//            hash = 29 * hash + (int) (this.batchRequestId ^ (this.batchRequestId >>> 32));
//            hash = 29 * hash + (int) (this.withinBatchId ^ (this.withinBatchId >>> 32));
//            return hash;
//        }
//
//        /**
//         * Return the batch request id.
//         *
//         * @return
//         */
//        public long getBatchRequestId() {
//            return this.batchRequestId;
//        }
//
//        /**
//         * Return Id of request within a particular batch.
//         *
//         * @return
//         */
//        public long getWithinBatchId() {
//            return this.withinBatchId;
//        }
//
//    }
//
//    public static class WorkerBatchRequestResponse extends Message {
//
//        private static final long serialVersionUID = 1L;
//
//        private final long batchRequestId;
//        private final long withInBatchId;
//
//        public WorkerBatchRequestResponse(Address source, Address destination, long batchRequestId, long withInBatchId) {
//            super(source, destination);
//            this.batchRequestId = batchRequestId;
//            this.withInBatchId = withInBatchId;
//        }
//
//        public long getBatchRequestId() {
//            return this.batchRequestId;
//        }
//
//        public long getWithinBatchId() {
//            return this.withInBatchId;
//        }
//
//    }

}
