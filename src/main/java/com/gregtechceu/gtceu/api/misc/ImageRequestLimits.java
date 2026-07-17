package com.gregtechceu.gtceu.api.misc;

/**
 * Defines the shared transfer bounds for Central Monitor image requests.
 */
public final class ImageRequestLimits {

    /** Keeps each response below the packet payload limit used by the existing protocol. */
    public static final int MAX_BYTES_PER_PART = 120_000;
    /** Prevents remote image responses from exhausting the server or client heap. */
    public static final int MAX_IMAGE_BYTES = 16 * 1024 * 1024;
    /** Bounds response metadata before the client allocates its part table. */
    public static final int MAX_PARTS = (MAX_IMAGE_BYTES + MAX_BYTES_PER_PART - 1) / MAX_BYTES_PER_PART;

    private ImageRequestLimits() {}
}
