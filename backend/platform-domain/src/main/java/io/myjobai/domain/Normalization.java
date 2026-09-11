package io.myjobai.domain;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.Normalizer;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class Normalization {
    private Normalization() {}
    public static String text(String input) {
        return Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}+#]+", " ").strip();
    }
    public static String hash(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String url(String raw) {
        var uri = URI.create(Checks.publicUrl(raw));
        String query = uri.getRawQuery();
        var retained = query == null ? List.<String>of() : Arrays.stream(query.split("&")).filter(p -> !p.toLowerCase(Locale.ROOT).matches("(?:utm_[^=]*|ref|source|trk|trackingid|fbclid|gclid)=.*")).sorted().toList();
        String path = Optional.ofNullable(uri.getRawPath()).orElse("").replaceAll("/+$", "");
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT) + path + (retained.isEmpty() ? "" : "?" + String.join("&",retained));
    }
    public static String fingerprint(Opportunity.Job job) {
        return hash(text(job.company()) + "|" + text(job.title()) + "|" + text(job.location()) + "|" + text(job.employmentType()) + "|" + text(job.description()));
    }
    public static boolean sameJob(Opportunity.Job a, Opportunity.Job b) {
        if (!a.userId().equals(b.userId())) return false;
        if (url(a.canonicalUrl()).equals(url(b.canonicalUrl()))) return true;
        return Math.abs(ChronoUnit.DAYS.between(a.postedAt(),b.postedAt())) <= 45 && fingerprint(a).equals(fingerprint(b));
    }
}
