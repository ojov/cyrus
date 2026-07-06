package com.ojo.cyrus.utils;

import com.ojo.cyrus.exception.InvalidWebhookUrlException;
import lombok.experimental.UtilityClass;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Rejects webhook URLs that would let a merchant turn Cyrus's own outbound webhook delivery into an
 * SSRF vector — e.g. registering {@code http://169.254.169.254/...} (cloud metadata) or
 * {@code http://localhost:8000} (internal services) as their "webhook" so Cyrus's backend makes the
 * request on their behalf. Resolves the host and rejects loopback/link-local/private/multicast/
 * wildcard addresses; a hostname that currently resolves publicly can still be re-pointed at a
 * private address later (DNS rebinding), but this closes the straightforward case at registration time.
 */
@UtilityClass
public class WebhookUrlValidator {

    public static void validate(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidWebhookUrlException("Webhook URL is not a valid URI");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidWebhookUrlException("Webhook URL must use http:// or https://");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidWebhookUrlException("Webhook URL must include a host");
        }

        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new InvalidWebhookUrlException("Webhook URL host could not be resolved");
        }

        for (InetAddress address : resolved) {
            if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress() || address.isAnyLocalAddress()) {
                throw new InvalidWebhookUrlException(
                        "Webhook URL must not resolve to a private, loopback, or link-local address");
            }
        }
    }
}
