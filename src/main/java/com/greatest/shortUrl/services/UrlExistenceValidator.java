package com.greatest.shortUrl.services;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class UrlExistenceValidator {
    private static final Logger log = LoggerFactory.getLogger(UrlExistenceValidator.class);

    public static boolean isUrlExists(String urlString) {
        try {
            log.debug("Checking if URL exists: {}", urlString);
            URL url = new URI(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000); // 5 seconds
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            return (responseCode >= 200 && responseCode < 400); // 2xx and 3xx are valid
        } catch (Exception e) {
            log.error("Error while checking URL: {}", urlString, e);
            return false; // URL is invalid or not reachable
        }
    }

    public static boolean isValid(String url) {
        if (url == null || url.isBlank()) return false;

        try {
            log.debug("Checking if URL exists: {}", url);
            url = url.trim();

            // remove trailing ###
            url = url.replaceAll(
                    "#+$",
                    ""
            );
            URL parsed = new URL(url);
            parsed.toURI(); // strict validation

            // Optional: only allow http/https
            String protocol = parsed.getProtocol();
            return protocol.equals("http") || protocol.equals("https");

        } catch (Exception e) {
            log.error("Error while checking URL: {}", url, e);

            return false;
        }
    }
}
