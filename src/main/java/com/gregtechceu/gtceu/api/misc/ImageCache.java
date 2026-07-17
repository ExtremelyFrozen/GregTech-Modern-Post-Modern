package com.gregtechceu.gtceu.api.misc;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ImageCache {

    public static final long REFRESH_SECS = 120;
    public static final long EXPIRE_SECS = 300;
    private static final String[] ALLOWED_PROTOCOLS = new String[] { "http", "https" };
    private static final int MAX_REDIRECTS = 5;

    private static final LoadingCache<String, ImageRequestResult> CACHE = CacheBuilder.newBuilder()
            .refreshAfterWrite(REFRESH_SECS, TimeUnit.SECONDS)
            .expireAfterAccess(EXPIRE_SECS, TimeUnit.SECONDS)
            .concurrencyLevel(3)
            .build(CacheLoader.from(ImageCache::loadConfiguredImage));

    public static void queryServerImage(String url, Consumer<ImageRequestResult> callback) {
        if (url == null || url.isBlank()) {
            GTCEu.LOGGER.warn("Rejecting Central Monitor image request with an empty URL");
            callback.accept(ImageRequestResult.failure(ImageRequestResult.Status.EMPTY_URL));
            return;
        }

        ImageRequestResult result;
        try {
            result = CACHE.get(url);
            if (!result.successful()) {
                CACHE.invalidate(url);
            }
        } catch (ExecutionException | RuntimeException exception) {
            CACHE.invalidate(url);
            GTCEu.LOGGER.error("Could not resolve Central Monitor image {}", url, exception);
            result = ImageRequestResult.failure(ImageRequestResult.Status.INTERNAL_ERROR);
        }
        callback.accept(result);
    }

    private static ImageRequestResult loadConfiguredImage(String url) {
        boolean singleplayer = GTCEu.getMinecraftServer().isSingleplayer() &&
                !GTCEu.getMinecraftServer().isPublished();
        String[] allowedDomains = ConfigHolder.INSTANCE.gameplay.allowedImageDomains;
        return loadImage(url, singleplayer, allowedDomains,
                validatedUrl -> readImage(validatedUrl, singleplayer, allowedDomains, URL::openConnection));
    }

    static ImageRequestResult loadImage(String urlString, boolean singleplayer, String[] allowedDomains,
                                        ImageReader imageReader) {
        if (urlString == null || urlString.isBlank()) {
            GTCEu.LOGGER.warn("Rejecting Central Monitor image request with an empty URL");
            return ImageRequestResult.failure(ImageRequestResult.Status.EMPTY_URL);
        }

        URL url;
        try {
            url = new URI(urlString).toURL();
        } catch (IllegalArgumentException | MalformedURLException | URISyntaxException exception) {
            GTCEu.LOGGER.warn("Rejecting malformed Central Monitor image URL {}", urlString, exception);
            return ImageRequestResult.failure(ImageRequestResult.Status.MALFORMED_URL);
        }

        ImageRequestResult.Status policyStatus = validateUrlPolicy(url, singleplayer, allowedDomains);
        if (policyStatus != ImageRequestResult.Status.SUCCESS) {
            logRejectedUrl(urlString, policyStatus);
            return ImageRequestResult.failure(policyStatus);
        }

        try {
            byte[] image = imageReader.read(url);
            if (image.length > ImageRequestLimits.MAX_IMAGE_BYTES) {
                GTCEu.LOGGER.warn("Rejecting oversized Central Monitor image {}", url);
                return ImageRequestResult.failure(ImageRequestResult.Status.IMAGE_TOO_LARGE);
            }
            GTCEu.LOGGER.debug("Downloaded Central Monitor image {}", url);
            return ImageRequestResult.success(image);
        } catch (ImageRequestFailureException exception) {
            logRejectedUrl(exception.url.toString(), exception.status);
            return ImageRequestResult.failure(exception.status);
        } catch (IOException exception) {
            GTCEu.LOGGER.error("Could not download Central Monitor image {}", url, exception);
            return ImageRequestResult.failure(ImageRequestResult.Status.DOWNLOAD_FAILED);
        } catch (RuntimeException exception) {
            GTCEu.LOGGER.error("Could not read Central Monitor image {}", url, exception);
            return ImageRequestResult.failure(ImageRequestResult.Status.INTERNAL_ERROR);
        }
    }

    static byte[] readImage(URL initialUrl, boolean singleplayer, String[] allowedDomains,
                            ImageConnectionFactory connectionFactory) throws IOException {
        URL currentUrl = initialUrl;
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            URLConnection connection = connectionFactory.open(currentUrl);
            if (connection instanceof HttpURLConnection httpConnection) {
                try {
                    httpConnection.setInstanceFollowRedirects(false);
                    int responseCode = httpConnection.getResponseCode();
                    if (isRedirect(responseCode)) {
                        if (redirectCount == MAX_REDIRECTS) {
                            throw new IOException("Central Monitor image exceeded the redirect limit");
                        }
                        String location = httpConnection.getHeaderField("Location");
                        if (location == null || location.isBlank()) {
                            throw new IOException("Central Monitor image redirect omitted its destination");
                        }
                        URL redirectUrl = resolveRedirect(currentUrl, location);
                        ImageRequestResult.Status policyStatus = validateUrlPolicy(redirectUrl, singleplayer,
                                allowedDomains);
                        if (policyStatus != ImageRequestResult.Status.SUCCESS) {
                            throw new ImageRequestFailureException(redirectUrl, policyStatus);
                        }
                        currentUrl = redirectUrl;
                        continue;
                    }
                    return readConnection(currentUrl, httpConnection);
                } finally {
                    httpConnection.disconnect();
                }
            }
            return readConnection(currentUrl, connection);
        }
        throw new IOException("Central Monitor image redirect loop did not terminate");
    }

    private static byte[] readConnection(URL url, URLConnection connection) throws IOException {
        long declaredLength = connection.getContentLengthLong();
        if (declaredLength > ImageRequestLimits.MAX_IMAGE_BYTES) {
            throw new ImageRequestFailureException(url, ImageRequestResult.Status.IMAGE_TOO_LARGE);
        }
        try (InputStream stream = connection.getInputStream()) {
            byte[] image = stream.readNBytes(ImageRequestLimits.MAX_IMAGE_BYTES + 1);
            if (image.length > ImageRequestLimits.MAX_IMAGE_BYTES) {
                throw new ImageRequestFailureException(url, ImageRequestResult.Status.IMAGE_TOO_LARGE);
            }
            return image;
        }
    }

    private static ImageRequestResult.Status validateUrlPolicy(URL url, boolean singleplayer,
                                                               String[] allowedDomains) {
        boolean webProtocol = false;
        for (String protocol : ALLOWED_PROTOCOLS) {
            if (url.getProtocol().equalsIgnoreCase(protocol)) {
                webProtocol = true;
                break;
            }
        }
        if (webProtocol && url.getHost().isBlank()) {
            return ImageRequestResult.Status.MALFORMED_URL;
        }
        if (!singleplayer && !webProtocol) {
            return ImageRequestResult.Status.PROTOCOL_NOT_ALLOWED;
        }
        if (singleplayer) {
            return ImageRequestResult.Status.SUCCESS;
        }
        for (String domain : allowedDomains) {
            if (url.getHost().equalsIgnoreCase(domain)) {
                return ImageRequestResult.Status.SUCCESS;
            }
        }
        return ImageRequestResult.Status.DOMAIN_NOT_ALLOWED;
    }

    private static URL resolveRedirect(URL source, String location) throws ImageRequestFailureException {
        try {
            return source.toURI().resolve(location).toURL();
        } catch (IllegalArgumentException | MalformedURLException | URISyntaxException exception) {
            throw new ImageRequestFailureException(source, ImageRequestResult.Status.MALFORMED_URL, exception);
        }
    }

    private static boolean isRedirect(int responseCode) {
        return responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                responseCode == 307 || responseCode == 308;
    }

    private static void logRejectedUrl(String url, ImageRequestResult.Status status) {
        GTCEu.LOGGER.warn("Rejecting Central Monitor image URL {}: {}", url, status);
    }

    /**
     * Reads image bytes after URL policy validation, allowing tests to avoid real network access.
     */
    @FunctionalInterface
    interface ImageReader {

        /**
         * Reads all bytes for the validated image URL.
         */
        byte[] read(URL url) throws IOException;
    }

    /** Opens a connection so redirect and size handling can be exercised without real network access. */
    @FunctionalInterface
    interface ImageConnectionFactory {

        /** Opens the connection for the current validated URL. */
        URLConnection open(URL url) throws IOException;
    }

    private static final class ImageRequestFailureException extends IOException {

        private final URL url;
        private final ImageRequestResult.Status status;

        private ImageRequestFailureException(URL url, ImageRequestResult.Status status) {
            this.url = url;
            this.status = status;
        }

        private ImageRequestFailureException(URL url, ImageRequestResult.Status status, Throwable cause) {
            super(cause);
            this.url = url;
            this.status = status;
        }
    }
}
