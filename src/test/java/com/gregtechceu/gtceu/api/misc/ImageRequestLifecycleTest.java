package com.gregtechceu.gtceu.api.misc;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("unused")
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class ImageRequestLifecycleTest {

    private static final String BATCH = "ImageRequestLifecycle";
    private static final String ALLOWED_URL = "https://allowed.example/image.png";
    private static final String[] ALLOWED_DOMAINS = new String[] { "allowed.example" };

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void urlValidationReturnsExplicitFailuresWithoutNetworkAccess(GameTestHelper helper) {
        AtomicInteger reads = new AtomicInteger();
        ImageCache.ImageReader unusedReader = url -> {
            reads.incrementAndGet();
            return new byte[] { 1 };
        };

        assertStatus(helper, ImageCache.loadImage(null, false, ALLOWED_DOMAINS, unusedReader),
                ImageRequestResult.Status.EMPTY_URL, "null URL");
        assertStatus(helper, ImageCache.loadImage("   ", false, ALLOWED_DOMAINS, unusedReader),
                ImageRequestResult.Status.EMPTY_URL, "blank URL");
        assertStatus(helper, ImageCache.loadImage("http://[invalid", false, ALLOWED_DOMAINS, unusedReader),
                ImageRequestResult.Status.MALFORMED_URL, "malformed URL");
        assertStatus(helper, ImageCache.loadImage("relative-path", false, ALLOWED_DOMAINS, unusedReader),
                ImageRequestResult.Status.MALFORMED_URL, "relative URL");
        assertStatus(helper, ImageCache.loadImage("http:/missing-host", false, ALLOWED_DOMAINS, unusedReader),
                ImageRequestResult.Status.MALFORMED_URL, "hostless web URL");
        assertStatus(helper, ImageCache.loadImage("file:///tmp/image.png", false, ALLOWED_DOMAINS, unusedReader),
                ImageRequestResult.Status.PROTOCOL_NOT_ALLOWED, "disallowed protocol");
        assertStatus(helper, ImageCache.loadImage("https://denied.example/image.png", false,
                ALLOWED_DOMAINS, unusedReader), ImageRequestResult.Status.DOMAIN_NOT_ALLOWED, "disallowed domain");
        helper.assertTrue(reads.get() == 0, "rejected image URLs reached the byte reader");

        ImageRequestResult ioFailure = ImageCache.loadImage(ALLOWED_URL, false, ALLOWED_DOMAINS,
                url -> {
                    throw new IOException("simulated download failure");
                });
        assertStatus(helper, ioFailure, ImageRequestResult.Status.DOWNLOAD_FAILED, "download I/O failure");

        byte[] expected = new byte[] { 4, 5, 6 };
        ImageRequestResult success = ImageCache.loadImage(ALLOWED_URL, false, ALLOWED_DOMAINS,
                url -> expected);
        assertStatus(helper, success, ImageRequestResult.Status.SUCCESS, "allowed image URL");
        helper.assertTrue(Arrays.equals(success.imageBytes(), expected),
                "successful image result changed the downloaded bytes");

        ImageRequestResult oversized = ImageCache.loadImage(ALLOWED_URL, false, ALLOWED_DOMAINS,
                url -> new byte[ImageRequestLimits.MAX_IMAGE_BYTES + 1]);
        assertStatus(helper, oversized, ImageRequestResult.Status.IMAGE_TOO_LARGE, "oversized image");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void redirectsReapplyUrlPolicyWithoutNetworkAccess(GameTestHelper helper) {
        AtomicInteger deniedRedirectConnections = new AtomicInteger();
        ImageRequestResult deniedRedirect = ImageCache.loadImage(ALLOWED_URL, false, ALLOWED_DOMAINS,
                url -> ImageCache.readImage(url, false, ALLOWED_DOMAINS, currentUrl -> {
                    deniedRedirectConnections.incrementAndGet();
                    return new StubHttpConnection(currentUrl, HttpURLConnection.HTTP_MOVED_TEMP,
                            "https://denied.example/image.png", new byte[0], -1);
                }));
        assertStatus(helper, deniedRedirect, ImageRequestResult.Status.DOMAIN_NOT_ALLOWED,
                "cross-domain redirect");
        helper.assertTrue(deniedRedirectConnections.get() == 1,
                "cross-domain redirect opened the rejected destination");

        byte[] expected = new byte[] { 7, 8, 9 };
        AtomicInteger allowedRedirectConnections = new AtomicInteger();
        ImageRequestResult allowedRedirect = ImageCache.loadImage(ALLOWED_URL, false, ALLOWED_DOMAINS,
                url -> ImageCache.readImage(url, false, ALLOWED_DOMAINS, currentUrl -> {
                    int connectionIndex = allowedRedirectConnections.getAndIncrement();
                    if (connectionIndex == 0) {
                        return new StubHttpConnection(currentUrl, HttpURLConnection.HTTP_MOVED_TEMP,
                                "redirected.png", new byte[0], -1);
                    }
                    helper.assertTrue(currentUrl.toString().equals("https://allowed.example/redirected.png"),
                            "relative redirect resolved to the wrong URL");
                    return new StubHttpConnection(currentUrl, HttpURLConnection.HTTP_OK, null, expected, -1);
                }));
        assertStatus(helper, allowedRedirect, ImageRequestResult.Status.SUCCESS, "allowed redirect");
        helper.assertTrue(Arrays.equals(allowedRedirect.imageBytes(), expected),
                "allowed redirect changed the response bytes");
        helper.assertTrue(allowedRedirectConnections.get() == 2,
                "allowed redirect did not open exactly two connections");

        byte[] oversizedBody = new byte[ImageRequestLimits.MAX_IMAGE_BYTES + 1];
        ImageRequestResult oversized = ImageCache.loadImage(ALLOWED_URL, false, ALLOWED_DOMAINS,
                url -> ImageCache.readImage(url, false, ALLOWED_DOMAINS,
                        currentUrl -> new StubHttpConnection(currentUrl, HttpURLConnection.HTTP_OK,
                                null, oversizedBody, -1)));
        assertStatus(helper, oversized, ImageRequestResult.Status.IMAGE_TOO_LARGE,
                "streamed oversized image");

        AtomicReference<StubHttpConnection> failingConnection = new AtomicReference<>();
        ImageRequestResult connectionFailure = ImageCache.loadImage(ALLOWED_URL, false, ALLOWED_DOMAINS,
                url -> ImageCache.readImage(url, false, ALLOWED_DOMAINS, currentUrl -> {
                    StubHttpConnection connection = new StubHttpConnection(
                            currentUrl, HttpURLConnection.HTTP_OK, null, new byte[0], -1, true);
                    failingConnection.set(connection);
                    return connection;
                }));
        assertStatus(helper, connectionFailure, ImageRequestResult.Status.DOWNLOAD_FAILED,
                "HTTP response failure");
        helper.assertTrue(failingConnection.get().wasDisconnected(),
                "HTTP connection was not disconnected after response-code failure");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void emptyAndMalformedRequestsDoNotBlockAValidRequest(GameTestHelper helper) {
        ImageRequestLifecycle lifecycle = new ImageRequestLifecycle();
        helper.assertTrue(lifecycle.start(null).outcome() == ImageRequestLifecycle.StartOutcome.REJECTED_EMPTY,
                "null URL entered the request lifecycle");
        helper.assertTrue(lifecycle.start(" ").outcome() == ImageRequestLifecycle.StartOutcome.REJECTED_EMPTY,
                "blank URL entered the request lifecycle");

        ImageRequestLifecycle.StartResult malformed = lifecycle.start("not a URL");
        ImageRequestLifecycle.StartResult valid = lifecycle.start(ALLOWED_URL);
        helper.assertTrue(malformed.outcome() == ImageRequestLifecycle.StartOutcome.STARTED,
                "malformed URL was not available for explicit server rejection");
        helper.assertTrue(valid.outcome() == ImageRequestLifecycle.StartOutcome.STARTED,
                "in-flight malformed URL blocked a later valid URL");

        ImageRequestLifecycle.ReceiveResult failure = lifecycle.receiveFailure(
                "not a URL", malformed.requestId(), ImageRequestResult.Status.MALFORMED_URL);
        helper.assertTrue(failure.outcome() == ImageRequestLifecycle.ReceiveOutcome.FAILED,
                "malformed URL failure did not complete its request");
        ImageRequestLifecycle.ReceiveResult completed = lifecycle.receivePart(
                ALLOWED_URL, valid.requestId(), new byte[] { 1, 2 }, 0, 1);
        helper.assertTrue(completed.outcome() == ImageRequestLifecycle.ReceiveOutcome.COMPLETE,
                "valid request did not complete after malformed request failure");
        helper.assertTrue(lifecycle.start(ALLOWED_URL).outcome() == ImageRequestLifecycle.StartOutcome.STARTED,
                "completed valid request remained permanently active");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void everyFailureResponseReleasesOnlyItsMatchingRequest(GameTestHelper helper) {
        ImageRequestLifecycle lifecycle = new ImageRequestLifecycle();
        for (ImageRequestResult.Status status : ImageRequestResult.Status.values()) {
            if (status == ImageRequestResult.Status.SUCCESS) {
                continue;
            }

            String url = "https://allowed.example/" + status.name().toLowerCase(Locale.ROOT) + ".png";
            ImageRequestLifecycle.StartResult request = lifecycle.start(url);
            helper.assertTrue(request.outcome() == ImageRequestLifecycle.StartOutcome.STARTED,
                    status + " test request did not start");
            helper.assertTrue(lifecycle.receiveFailure(url, request.requestId() + 1, status).outcome() ==
                    ImageRequestLifecycle.ReceiveOutcome.IGNORED,
                    status + " failure for a different request id changed lifecycle state");
            helper.assertTrue(lifecycle.start(url).outcome() == ImageRequestLifecycle.StartOutcome.ALREADY_ACTIVE,
                    status + " mismatched failure released the active request");
            helper.assertTrue(lifecycle.receiveFailure(url, request.requestId(), status).outcome() ==
                    ImageRequestLifecycle.ReceiveOutcome.FAILED,
                    status + " matching failure was not accepted");

            ImageRequestLifecycle.StartResult retry = lifecycle.start(url);
            helper.assertTrue(retry.outcome() == ImageRequestLifecycle.StartOutcome.STARTED,
                    status + " failure left the URL permanently active");
            lifecycle.receiveFailure(url, retry.requestId(), ImageRequestResult.Status.INTERNAL_ERROR);
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void outOfOrderAndDuplicatePartsAssembleExactlyOnce(GameTestHelper helper) {
        ImageRequestLifecycle lifecycle = new ImageRequestLifecycle();
        ImageRequestLifecycle.StartResult request = lifecycle.start(ALLOWED_URL);

        helper.assertTrue(lifecycle.receivePart(ALLOWED_URL, request.requestId(), new byte[] { 3, 4 }, 2, 3)
                .outcome() == ImageRequestLifecycle.ReceiveOutcome.PART_ACCEPTED,
                "out-of-order final part was not retained");
        helper.assertTrue(lifecycle.receivePart(ALLOWED_URL, request.requestId(), new byte[] { 9 }, 2, 3)
                .outcome() == ImageRequestLifecycle.ReceiveOutcome.DUPLICATE_PART,
                "duplicate part was not ignored");
        helper.assertTrue(lifecycle.receivePart(ALLOWED_URL, request.requestId(), new byte[] { 0 }, 0, 3)
                .outcome() == ImageRequestLifecycle.ReceiveOutcome.PART_ACCEPTED,
                "first part completed an incomplete response");

        ImageRequestLifecycle.ReceiveResult complete = lifecycle.receivePart(
                ALLOWED_URL, request.requestId(), new byte[] { 1, 2 }, 1, 3);
        helper.assertTrue(complete.outcome() == ImageRequestLifecycle.ReceiveOutcome.COMPLETE,
                "all out-of-order parts did not complete the response");
        helper.assertTrue(Arrays.equals(complete.imageBytes(), new byte[] { 0, 1, 2, 3, 4 }),
                "out-of-order response assembled bytes in arrival order instead of part order");
        helper.assertTrue(lifecycle.receivePart(ALLOWED_URL, request.requestId(), new byte[] { 1, 2 }, 1, 3)
                .outcome() == ImageRequestLifecycle.ReceiveOutcome.IGNORED,
                "late duplicate response reopened a completed request");
        helper.assertTrue(lifecycle.start(ALLOWED_URL).outcome() == ImageRequestLifecycle.StartOutcome.STARTED,
                "successful multipart response did not release its URL");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void invalidPartMetadataReleasesTheAffectedRequest(GameTestHelper helper) {
        ImageRequestLifecycle lifecycle = new ImageRequestLifecycle();
        ImageRequestLifecycle.StartResult request = lifecycle.start(ALLOWED_URL);
        helper.assertTrue(lifecycle.receivePart(ALLOWED_URL, request.requestId(), new byte[] { 1 }, 2, 2)
                .outcome() == ImageRequestLifecycle.ReceiveOutcome.REJECTED,
                "out-of-range image part metadata was accepted");
        helper.assertTrue(lifecycle.start(ALLOWED_URL).outcome() == ImageRequestLifecycle.StartOutcome.STARTED,
                "invalid response metadata left the request permanently active");

        ImageRequestLifecycle lifecycleWithZeroParts = new ImageRequestLifecycle();
        ImageRequestLifecycle.StartResult zeroParts = lifecycleWithZeroParts.start(ALLOWED_URL);
        helper.assertTrue(lifecycleWithZeroParts.receivePart(
                ALLOWED_URL, zeroParts.requestId(), new byte[0], 0, 0).outcome() ==
                ImageRequestLifecycle.ReceiveOutcome.REJECTED,
                "zero-part image response was accepted");
        helper.assertTrue(lifecycleWithZeroParts.start(ALLOWED_URL).outcome() ==
                ImageRequestLifecycle.StartOutcome.STARTED,
                "zero-part image response left the request permanently active");

        ImageRequestLifecycle lifecycleWithChangedTotal = new ImageRequestLifecycle();
        ImageRequestLifecycle.StartResult changedTotal = lifecycleWithChangedTotal.start(ALLOWED_URL);
        helper.assertTrue(lifecycleWithChangedTotal.receivePart(
                ALLOWED_URL, changedTotal.requestId(), new byte[] { 1 }, 0, 2).outcome() ==
                ImageRequestLifecycle.ReceiveOutcome.PART_ACCEPTED,
                "valid first image part was rejected before total-parts consistency could be tested");
        helper.assertTrue(lifecycleWithChangedTotal.receivePart(
                ALLOWED_URL, changedTotal.requestId(), new byte[] { 2 }, 1, 3).outcome() ==
                ImageRequestLifecycle.ReceiveOutcome.REJECTED,
                "image response changed its declared total part count");
        helper.assertTrue(lifecycleWithChangedTotal.start(ALLOWED_URL).outcome() ==
                ImageRequestLifecycle.StartOutcome.STARTED,
                "inconsistent part count left the request permanently active");

        ImageRequestLifecycle lifecycleWithExcessiveParts = new ImageRequestLifecycle();
        ImageRequestLifecycle.StartResult excessiveParts = lifecycleWithExcessiveParts.start(ALLOWED_URL);
        helper.assertTrue(lifecycleWithExcessiveParts.receivePart(
                ALLOWED_URL, excessiveParts.requestId(), new byte[0], 0,
                ImageRequestLimits.MAX_PARTS + 1).outcome() == ImageRequestLifecycle.ReceiveOutcome.REJECTED,
                "excessive response part count reached the part-table allocation");

        ImageRequestLifecycle lifecycleWithOversizedPart = new ImageRequestLifecycle();
        ImageRequestLifecycle.StartResult oversizedPart = lifecycleWithOversizedPart.start(ALLOWED_URL);
        helper.assertTrue(lifecycleWithOversizedPart.receivePart(
                ALLOWED_URL, oversizedPart.requestId(),
                new byte[ImageRequestLimits.MAX_BYTES_PER_PART + 1], 0, 1).outcome() ==
                ImageRequestLifecycle.ReceiveOutcome.REJECTED,
                "oversized response part was retained");

        ImageRequestLifecycle lifecycleWithOversizedTotal = new ImageRequestLifecycle();
        ImageRequestLifecycle.StartResult oversizedTotal = lifecycleWithOversizedTotal.start(ALLOWED_URL);
        byte[] fullPart = new byte[ImageRequestLimits.MAX_BYTES_PER_PART];
        ImageRequestLifecycle.ReceiveOutcome finalOutcome = ImageRequestLifecycle.ReceiveOutcome.PART_ACCEPTED;
        for (int index = 0; index < ImageRequestLimits.MAX_PARTS; index++) {
            finalOutcome = lifecycleWithOversizedTotal.receivePart(
                    ALLOWED_URL, oversizedTotal.requestId(), fullPart, index, ImageRequestLimits.MAX_PARTS).outcome();
            if (finalOutcome == ImageRequestLifecycle.ReceiveOutcome.REJECTED) {
                break;
            }
        }
        helper.assertTrue(finalOutcome == ImageRequestLifecycle.ReceiveOutcome.REJECTED,
                "multipart response exceeded the total image byte limit");
        helper.assertTrue(lifecycleWithOversizedTotal.start(ALLOWED_URL).outcome() ==
                ImageRequestLifecycle.StartOutcome.STARTED,
                "oversized multipart response left the request permanently active");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void cacheRestartReplacesARequestThatNeverReceivedAResponse(GameTestHelper helper) {
        ImageRequestLifecycle lifecycle = new ImageRequestLifecycle();
        ImageRequestLifecycle.StartResult stale = lifecycle.start(ALLOWED_URL);
        ImageRequestLifecycle.StartResult replacement = lifecycle.restart(ALLOWED_URL);

        helper.assertTrue(replacement.outcome() == ImageRequestLifecycle.StartOutcome.STARTED &&
                replacement.requestId() != stale.requestId(),
                "cache restart did not replace the stale image request identity");
        helper.assertTrue(lifecycle.receivePart(
                ALLOWED_URL, stale.requestId(), new byte[] { 1 }, 0, 1).outcome() ==
                ImageRequestLifecycle.ReceiveOutcome.IGNORED,
                "late response for the replaced image request changed lifecycle state");
        helper.assertTrue(lifecycle.start(ALLOWED_URL).outcome() == ImageRequestLifecycle.StartOutcome.ALREADY_ACTIVE,
                "late stale response released the replacement image request");
        helper.assertTrue(lifecycle.receivePart(
                ALLOWED_URL, replacement.requestId(), new byte[] { 2 }, 0, 1).outcome() ==
                ImageRequestLifecycle.ReceiveOutcome.COMPLETE,
                "replacement image request did not complete normally");
        helper.succeed();
    }

    private static void assertStatus(GameTestHelper helper, ImageRequestResult result,
                                     ImageRequestResult.Status expected, String phase) {
        helper.assertTrue(result.status() == expected, phase + " produced " + result.status());
        helper.assertTrue(result.successful() == (expected == ImageRequestResult.Status.SUCCESS),
                phase + " reported inconsistent success state");
        if (expected != ImageRequestResult.Status.SUCCESS) {
            helper.assertTrue(result.imageBytes().length == 0, phase + " exposed bytes for a failed result");
        }
    }

    private static final class StubHttpConnection extends HttpURLConnection {

        private final int stubResponseCode;
        private final String redirectLocation;
        private final byte[] body;
        private final long declaredLength;
        private final boolean failResponse;
        private boolean disconnected;

        private StubHttpConnection(URL url, int responseCode, String redirectLocation, byte[] body,
                                   long declaredLength) {
            this(url, responseCode, redirectLocation, body, declaredLength, false);
        }

        private StubHttpConnection(URL url, int responseCode, String redirectLocation, byte[] body,
                                   long declaredLength, boolean failResponse) {
            super(url);
            this.stubResponseCode = responseCode;
            this.redirectLocation = redirectLocation;
            this.body = body;
            this.declaredLength = declaredLength;
            this.failResponse = failResponse;
        }

        @Override
        public int getResponseCode() throws IOException {
            if (failResponse) {
                throw new IOException("simulated response failure");
            }
            return stubResponseCode;
        }

        @Override
        public String getHeaderField(String name) {
            if ("Location".equalsIgnoreCase(name)) {
                return redirectLocation;
            }
            return null;
        }

        @Override
        public long getContentLengthLong() {
            return declaredLength;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() throws IOException {}

        private boolean wasDisconnected() {
            return disconnected;
        }
    }
}
