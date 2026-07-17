package com.gregtechceu.gtceu.api.misc;

/**
 * Describes the server-side outcome of resolving one Central Monitor image request.
 */
public final class ImageRequestResult {

    private static final byte[] NO_IMAGE = new byte[0];

    private final Status status;
    private final byte[] imageBytes;

    private ImageRequestResult(Status status, byte[] imageBytes) {
        this.status = status;
        this.imageBytes = imageBytes;
    }

    /**
     * Creates a successful result containing the bytes that will be sent to the client.
     */
    public static ImageRequestResult success(byte[] imageBytes) {
        if (imageBytes == null) {
            throw new IllegalArgumentException("Successful image request result requires image bytes");
        }
        if (imageBytes.length > ImageRequestLimits.MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Successful image request result exceeds the transfer limit");
        }
        return new ImageRequestResult(Status.SUCCESS, imageBytes);
    }

    /**
     * Creates a failed result with no image payload.
     */
    public static ImageRequestResult failure(Status status) {
        if (status == null || status == Status.SUCCESS) {
            throw new IllegalArgumentException("Failed image request result requires a failure status");
        }
        return new ImageRequestResult(status, NO_IMAGE);
    }

    /**
     * Returns the outcome sent over the image response packet.
     */
    public Status status() {
        return status;
    }

    /**
     * Returns the downloaded image bytes, or an empty array for a failed result.
     */
    public byte[] imageBytes() {
        return imageBytes;
    }

    /**
     * Returns whether the request produced an image payload.
     */
    public boolean successful() {
        return status == Status.SUCCESS;
    }

    /**
     * Stable wire statuses for every terminal server-side request outcome.
     */
    public enum Status {
        SUCCESS,
        EMPTY_URL,
        MALFORMED_URL,
        PROTOCOL_NOT_ALLOWED,
        DOMAIN_NOT_ALLOWED,
        IMAGE_TOO_LARGE,
        DOWNLOAD_FAILED,
        INTERNAL_ERROR
    }
}
