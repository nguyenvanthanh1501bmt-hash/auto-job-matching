package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.common.dtos.ApplyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ApplyInformationNormalizer {

    private final TextNormalizer textNormalizer;

    public ApplyInformationResult normalize(
            String rawApplyUrl,
            ApplyType rawApplyType,
            String rawDetailUrl
    ) {
        String applyUrl =
                textNormalizer.normalizeInline(rawApplyUrl);

        String detailUrl =
                textNormalizer.normalizeInline(rawDetailUrl);

        if (applyUrl == null && detailUrl == null) {
            return new ApplyInformationResult(
                    null,
                    ApplyType.UNKNOWN
            );
        }

        /*
         * Không có apply URL riêng thì dùng detail page.
         */
        if (applyUrl == null) {
            return new ApplyInformationResult(
                    detailUrl,
                    ApplyType.DETAIL_PAGE
            );
        }

        ApplyType inferredType = inferType(
                applyUrl,
                detailUrl
        );

        ApplyType normalizedType = resolveType(
                rawApplyType,
                inferredType
        );

        return new ApplyInformationResult(
                applyUrl,
                normalizedType
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
         *
         * Nếu URL apply nằm ở domain khác detail URL,
         * normalizer sửa lại thành EXTERNAL_COMPANY_SITE.
         */
        if ((rawApplyType == ApplyType.DETAIL_PAGE
                || rawApplyType
                == ApplyType.DETAIL_PAGE_APPLY_BUTTON)
                && inferredType
                == ApplyType.EXTERNAL_COMPANY_SITE) {
            return ApplyType.EXTERNAL_COMPANY_SITE;
        }

        return rawApplyType;
    }

    private ApplyType inferType(
            String applyUrl,
            String detailUrl
    ) {
        if (applyUrl.toLowerCase(Locale.ROOT)
                .startsWith("mailto:")) {
            return ApplyType.EMAIL;
        }

        if (detailUrl == null) {
            return ApplyType.UNKNOWN;
        }

        URI detailUri = parseUri(detailUrl);

        if (detailUri == null) {
            return applyUrl.equals(detailUrl)
                    ? ApplyType.DETAIL_PAGE
                    : ApplyType.UNKNOWN;
        }

        URI applyUri = parseAndResolve(
                applyUrl,
                detailUri
        );

        if (applyUri == null) {
            return applyUrl.equals(detailUrl)
                    ? ApplyType.DETAIL_PAGE
                    : ApplyType.UNKNOWN;
        }

        if (sameNormalizedUri(applyUri, detailUri)) {
            return ApplyType.DETAIL_PAGE;
        }

        if (sameOrigin(applyUri, detailUri)) {
            return ApplyType.DETAIL_PAGE_APPLY_BUTTON;
        }

        return ApplyType.EXTERNAL_COMPANY_SITE;
    }

    private URI parseAndResolve(
            String value,
            URI baseUri
    ) {
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
                && effectivePort(first)
                == effectivePort(second);
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