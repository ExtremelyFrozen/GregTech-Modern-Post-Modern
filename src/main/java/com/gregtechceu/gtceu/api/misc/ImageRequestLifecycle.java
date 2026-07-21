package com.gregtechceu.gtceu.api.misc;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks in-flight client image requests and assembles their response parts.
 */
public final class ImageRequestLifecycle {

    private static final byte[] NO_IMAGE = new byte[0];

    private final Map<Long, PendingRequest> pendingRequests = new HashMap<>();
    private final Map<String, Long> activeRequestIds = new HashMap<>();
    private long nextRequestId = 1;

    /**
     * Starts a request unless its URL is empty or the same URL is already in flight.
     */
    public synchronized StartResult start(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return StartResult.rejectedEmpty();
        }
        if (activeRequestIds.containsKey(url)) {
            return StartResult.alreadyActive();
        }

        return startNewRequest(url);
    }

    /**
     * Starts a cache reload and replaces any request for the same URL that never reached a terminal response.
     */
    public synchronized StartResult restart(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return StartResult.rejectedEmpty();
        }
        Long previousRequestId = activeRequestIds.remove(url);
        if (previousRequestId != null) {
            pendingRequests.remove(previousRequestId);
        }

        return startNewRequest(url);
    }

    /**
     * Completes a matching request with a server-reported failure.
     */
    public synchronized ReceiveResult receiveFailure(String url, long requestId,
                                                     ImageRequestResult.Status status) {
        if (status == null || status == ImageRequestResult.Status.SUCCESS) {
            throw new IllegalArgumentException("Image request failure requires a failure status");
        }

        PendingRequest request = matchingRequest(url, requestId);
        if (request == null) {
            return ReceiveResult.ignored();
        }
        removeRequest(url, requestId);
        return ReceiveResult.failed();
    }

    /**
     * Accepts one response part, preserving out-of-order parts and ignoring duplicates.
     */
    public synchronized ReceiveResult receivePart(String url, long requestId, byte[] imagePart, int index,
                                                  int totalParts) {
        PendingRequest request = matchingRequest(url, requestId);
        if (request == null) {
            return ReceiveResult.ignored();
        }
        if (imagePart == null || imagePart.length > ImageRequestLimits.MAX_BYTES_PER_PART ||
                index < 0 || totalParts <= index || totalParts > ImageRequestLimits.MAX_PARTS) {
            removeRequest(url, requestId);
            return ReceiveResult.rejected();
        }

        if (request.parts.length == 0) {
            request.parts = new byte[totalParts][];
        } else if (request.parts.length != totalParts) {
            removeRequest(url, requestId);
            return ReceiveResult.rejected();
        }

        if (request.parts[index] != null) {
            return ReceiveResult.duplicatePart();
        }
        long receivedBytes = (long) request.receivedBytes + imagePart.length;
        if (receivedBytes > ImageRequestLimits.MAX_IMAGE_BYTES) {
            removeRequest(url, requestId);
            return ReceiveResult.rejected();
        }
        request.parts[index] = imagePart;
        request.receivedParts++;
        request.receivedBytes = (int) receivedBytes;
        if (request.receivedParts < request.parts.length) {
            return ReceiveResult.partAccepted();
        }

        byte[] imageBytes = new byte[request.receivedBytes];
        int destinationIndex = 0;
        for (byte[] part : request.parts) {
            System.arraycopy(part, 0, imageBytes, destinationIndex, part.length);
            destinationIndex += part.length;
        }
        removeRequest(url, requestId);
        return ReceiveResult.complete(imageBytes);
    }

    private @Nullable PendingRequest matchingRequest(String url, long requestId) {
        PendingRequest request = pendingRequests.get(requestId);
        if (request == null || !request.url.equals(url)) {
            return null;
        }
        return request;
    }

    private StartResult startNewRequest(String url) {
        long requestId = nextRequestId;
        nextRequestId = Math.incrementExact(nextRequestId);
        pendingRequests.put(requestId, new PendingRequest(url));
        activeRequestIds.put(url, requestId);
        return StartResult.started(requestId);
    }

    private void removeRequest(String url, long requestId) {
        pendingRequests.remove(requestId);
        activeRequestIds.remove(url, requestId);
    }

    /**
     * Possible outcomes when attempting to start an image request.
     */
    public enum StartOutcome {
        STARTED,
        REJECTED_EMPTY,
        ALREADY_ACTIVE
    }

    /**
     * Possible outcomes when consuming an image response.
     */
    public enum ReceiveOutcome {
        PART_ACCEPTED,
        DUPLICATE_PART,
        COMPLETE,
        FAILED,
        REJECTED,
        IGNORED
    }

    /**
     * Carries the new request identifier only when a request was started.
     */
    public static final class StartResult {

        private final StartOutcome outcome;
        private final long requestId;

        private StartResult(StartOutcome outcome, long requestId) {
            this.outcome = outcome;
            this.requestId = requestId;
        }

        private static StartResult started(long requestId) {
            return new StartResult(StartOutcome.STARTED, requestId);
        }

        private static StartResult rejectedEmpty() {
            return new StartResult(StartOutcome.REJECTED_EMPTY, 0);
        }

        private static StartResult alreadyActive() {
            return new StartResult(StartOutcome.ALREADY_ACTIVE, 0);
        }

        /**
         * Returns the start outcome.
         */
        public StartOutcome outcome() {
            return outcome;
        }

        /**
         * Returns the positive request identifier when {@link #outcome()} is {@code STARTED}.
         */
        public long requestId() {
            return requestId;
        }
    }

    /**
     * Carries the response-consumption outcome and completed bytes when available.
     */
    public static final class ReceiveResult {

        private final ReceiveOutcome outcome;
        private final byte[] imageBytes;

        private ReceiveResult(ReceiveOutcome outcome, byte[] imageBytes) {
            this.outcome = outcome;
            this.imageBytes = imageBytes;
        }

        private static ReceiveResult partAccepted() {
            return new ReceiveResult(ReceiveOutcome.PART_ACCEPTED, NO_IMAGE);
        }

        private static ReceiveResult duplicatePart() {
            return new ReceiveResult(ReceiveOutcome.DUPLICATE_PART, NO_IMAGE);
        }

        private static ReceiveResult complete(byte[] imageBytes) {
            return new ReceiveResult(ReceiveOutcome.COMPLETE, imageBytes);
        }

        private static ReceiveResult failed() {
            return new ReceiveResult(ReceiveOutcome.FAILED, NO_IMAGE);
        }

        private static ReceiveResult rejected() {
            return new ReceiveResult(ReceiveOutcome.REJECTED, NO_IMAGE);
        }

        private static ReceiveResult ignored() {
            return new ReceiveResult(ReceiveOutcome.IGNORED, NO_IMAGE);
        }

        /**
         * Returns how the response changed the request lifecycle.
         */
        public ReceiveOutcome outcome() {
            return outcome;
        }

        /**
         * Returns assembled image bytes only for a {@code COMPLETE} outcome.
         */
        public byte[] imageBytes() {
            return imageBytes;
        }
    }

    private static final class PendingRequest {

        private final String url;
        private byte[][] parts = new byte[0][];
        private int receivedParts;
        private int receivedBytes;

        private PendingRequest(String url) {
            this.url = url;
        }
    }
}
