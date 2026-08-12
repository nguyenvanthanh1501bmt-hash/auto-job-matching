package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.common.dtos.ApplyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ApplyInformationNormalizer {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
    );

    private final TextNormalizer textNormalizer;

    public ApplyInformationResult normalize(
            String rawApplyUrl,
            ApplyType rawApplyType,
            String rawDetailUrl
    ) {
        String applyUrl = textNormalizer.normalizeInline(rawApplyUrl);
        String detailUrl = textNormalizer.normalizeInline(rawDetailUrl);

        String safeDetailUrl = safeHttpUrl(detailUrl);

        if (applyUrl == null) {
            return fallbackToDetailPage(safeDetailUrl);
        }

        String safeApplyUrl = safeApplyTarget(
                applyUrl,
                safeDetailUrl
        );

        if (safeApplyUrl == null) {
            return fallbackToDetailPage(safeDetailUrl);
        }

        ApplyType inferredType = inferType(
                safeApplyUrl,
                safeDetailUrl
        );

        ApplyType normalizedType = resolveType(
                rawApplyType,
                inferredType
        );

        return new ApplyInformationResult(
                safeApplyUrl,
                normalizedType
        );
    }

    private ApplyInformationResult fallbackToDetailPage(
            String safeDetailUrl
    ) {
        if (safeDetailUrl == null) {
            return new ApplyInformationResult(
                    null,
                    ApplyType.UNKNOWN
            );
        }

        return new ApplyInformationResult(
                safeDetailUrl,
                ApplyType.DETAIL_PAGE
        );
    }

    private ApplyType resolveType(
            ApplyType rawApplyType,
            ApplyType inferredType
    ) {
        /*
         * mailto luôn là EMAIL dù parser gán nhầm type.
         */
        if (inferredType == ApplyType.EMAIL) {
            return ApplyType.EMAIL;
        }

        if (rawApplyType == null
                || rawApplyType == ApplyType.UNKNOWN) {
            return inferredType;
        }

        /*
         * Parser có thể mặc định mọi apply URL là DETAIL_PAGE.
         * Nếu URL apply nằm ở origin khác detail URL, sửa type chứ không sửa
         * URL người dùng sẽ mở.
         */
        if ((rawApplyType == ApplyType.DETAIL_PAGE
                || rawApplyType == ApplyType.DETAIL_PAGE_APPLY_BUTTON)
                && inferredType == ApplyType.EXTERNAL_COMPANY_SITE) {
            return ApplyType.EXTERNAL_COMPANY_SITE;
        }

        return rawApplyType;
    }

    private ApplyType inferType(
            String applyUrl,
            String detailUrl
    ) {
        if (isMailto(applyUrl)) {
            return ApplyType.EMAIL;
        }

        if (detailUrl == null) {
            return ApplyType.UNKNOWN;
        }

        URI detailUri = parseUri(detailUrl);
        URI applyUri = parseAndResolve(
                applyUrl,
                detailUri
        );

        if (detailUri == null || applyUri == null) {
            return ApplyType.UNKNOWN;
        }

        if (sameNormalizedUri(applyUri, detailUri)) {
            return ApplyType.DETAIL_PAGE;
        }

        if (sameOrigin(applyUri, detailUri)) {
            return ApplyType.DETAIL_PAGE_APPLY_BUTTON;
        }

        return ApplyType.EXTERNAL_COMPANY_SITE;
    }

    private String safeApplyTarget(
            String value,
            String safeDetailUrl
    ) {
        URI parsed = parseUri(value);

        if (parsed == null) {
            return null;
        }

        if (parsed.isAbsolute()) {
            if (isMailto(parsed)) {
                return validMailto(parsed) ? value : null;
            }

            return isSafeHttpUri(parsed) ? value : null;
        }

        if (safeDetailUrl == null) {
            return null;
        }

        URI detailUri = parseUri(safeDetailUrl);
        URI resolved = parseAndResolve(
                value,
                detailUri
        );

        return isSafeHttpUri(resolved) ? value : null;
    }

    private String safeHttpUrl(String value) {
        if (value == null) {
            return null;
        }

        URI uri = parseUri(value);

        return isSafeHttpUri(uri) ? value : null;
    }

    private boolean isSafeHttpUri(URI uri) {
        if (uri == null || !uri.isAbsolute()) {
            return false;
        }

        String scheme = uri.getScheme();

        if (!"http".equalsIgnoreCase(scheme)
                && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }

        return uri.getHost() != null
                && !uri.getHost().isBlank();
    }

    private boolean isMailto(String value) {
        URI uri = parseUri(value);
        return isMailto(uri);
    }

    private boolean isMailto(URI uri) {
        return uri != null
                && "mailto".equalsIgnoreCase(uri.getScheme());
    }

    private boolean validMailto(URI uri) {
        String address = uri.getSchemeSpecificPart();

        if (address == null) {
            return false;
        }

        int queryIndex = address.indexOf('?');

        if (queryIndex >= 0) {
            address = address.substring(0, queryIndex);
        }

        return EMAIL_PATTERN.matcher(address).matches();
    }

    private URI parseAndResolve(
            String value,
            URI baseUri
    ) {
        if (baseUri == null) {
            return null;
        }

        URI parsed = parseUri(value);

        if (parsed == null) {
            return null;
        }

        if (!parsed.isAbsolute()) {
            return baseUri.resolve(parsed).normalize();
        }

        return parsed.normalize();
    }

    private URI parseUri(String value) {
        if (value == null) {
            return null;
        }

        try {
            return URI.create(value).normalize();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean sameNormalizedUri(
            URI first,
            URI second
    ) {
        return first.normalize().equals(second.normalize());
    }

    private boolean sameOrigin(
            URI first,
            URI second
    ) {
        return equalsIgnoreCase(
                first.getScheme(),
                second.getScheme()
        )
                && equalsIgnoreCase(
                first.getHost(),
                second.getHost()
        )
                && effectivePort(first) == effectivePort(second);
    }

    private boolean equalsIgnoreCase(
            String first,
            String second
    ) {
        if (first == null || second == null) {
            return first == null && second == null;
        }

        return first.equalsIgnoreCase(second);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }

        if ("http".equalsIgnoreCase(uri.getScheme())) {
            return 80;
        }

        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }

        return -1;
    }

    public record ApplyInformationResult(
            String applyUrl,
            ApplyType applyType
    ) {
    }
}