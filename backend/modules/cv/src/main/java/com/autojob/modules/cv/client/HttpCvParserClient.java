package com.autojob.modules.cv.client;

import com.autojob.modules.cv.client.dto.CvParseRequest;
import com.autojob.modules.cv.client.dto.CvParseResponse;
import com.autojob.modules.cv.client.dto.CvParserErrorResponse;
import com.autojob.modules.cv.config.CvParserProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class HttpCvParserClient implements CvParserClient {

    private static final String PARSE_PATH =
            "/api/v1/cv/parse";

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.get("application/json; charset=utf-8");

    private final Call.Factory callFactory;
    private final ObjectMapper objectMapper;
    private final CvParserProperties properties;

    @Autowired
    public HttpCvParserClient(
            ObjectMapper objectMapper,
            CvParserProperties properties
    ) {
        this(
                buildHttpClient(properties),
                objectMapper,
                properties
        );
    }

    HttpCvParserClient(
            Call.Factory callFactory,
            ObjectMapper objectMapper,
            CvParserProperties properties
    ) {
        this.callFactory = Objects.requireNonNull(
                callFactory,
                "callFactory"
        );
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper"
        );
        this.properties = Objects.requireNonNull(
                properties,
                "properties"
        );
    }

    @Override
    public CvParseResponse parse(CvParseRequest request) {
        Objects.requireNonNull(request, "request");

        String rawCvId = request.rawCvId();
        Request httpRequest = buildRequest(request, rawCvId);

        try (Response response = callFactory
                .newCall(httpRequest)
                .execute()) {
            int status = response.code();

            if (status == 200) {
                String body = readBody(
                        response.body(),
                        properties.getMaxResponseSizeBytes(),
                        rawCvId
                );

                if (body.isBlank()) {
                    throw CvParserClientException
                            .emptyResponse(rawCvId);
                }

                try {
                    return objectMapper.readValue(
                            body,
                            CvParseResponse.class
                    );
                } catch (JsonProcessingException exception) {
                    throw CvParserClientException
                            .malformedJson(
                                    rawCvId,
                                    exception
                            );
                }
            }

            if (status >= 400 && status <= 599) {
                String parserCode = readParserErrorCode(
                        response.body(),
                        rawCvId
                );

                throw CvParserClientException.httpError(
                        rawCvId,
                        status,
                        parserCode
                );
            }

            throw CvParserClientException.unexpectedStatus(
                    rawCvId,
                    status
            );
        } catch (CvParserClientException exception) {
            throw exception;
        } catch (ConnectException exception) {
            if (isConnectionRefused(exception)) {
                throw CvParserClientException.connectionRefused(
                        rawCvId,
                        exception
                );
            }

            throw CvParserClientException.connectionFailure(
                    rawCvId,
                    exception
            );
        } catch (SocketTimeoutException exception) {
            if (isConnectTimeout(exception)) {
                throw CvParserClientException.connectTimeout(
                        rawCvId,
                        exception
                );
            }

            throw CvParserClientException.responseTimeout(
                    rawCvId,
                    exception
            );
        } catch (IOException exception) {
            ConnectException connectException = findCause(
                    exception,
                    ConnectException.class
            );

            if (connectException != null
                    && isConnectionRefused(connectException)) {
                throw CvParserClientException.connectionRefused(
                        rawCvId,
                        exception
                );
            }

            SocketTimeoutException timeoutException = findCause(
                    exception,
                    SocketTimeoutException.class
            );

            if (timeoutException != null) {
                if (isConnectTimeout(timeoutException)) {
                    throw CvParserClientException.connectTimeout(
                            rawCvId,
                            exception
                    );
                }

                throw CvParserClientException.responseTimeout(
                        rawCvId,
                        exception
                );
            }

            throw CvParserClientException.connectionFailure(
                    rawCvId,
                    exception
            );
        }
    }

    private Request buildRequest(
            CvParseRequest request,
            String rawCvId
    ) {
        final String json;

        try {
            json = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw CvParserClientException.malformedJson(
                    rawCvId,
                    exception
            );
        }

        RequestBody requestBody = RequestBody.create(
                json,
                JSON_MEDIA_TYPE
        );

        return new Request.Builder()
                .url(
                        properties.normalizedBaseUrl()
                                + PARSE_PATH
                )
                .header("Accept", "application/json")
                .post(requestBody)
                .build();
    }

    private String readParserErrorCode(
            ResponseBody responseBody,
            String rawCvId
    ) throws SocketTimeoutException {
        if (responseBody == null) {
            return null;
        }

        int maxErrorBodyBytes = Math.min(
                properties.getMaxResponseSizeBytes(),
                Math.max(
                        properties.getMaxErrorLength() * 4,
                        4_096
                )
        );

        final String body;

        try {
            body = readBody(
                    responseBody,
                    maxErrorBodyBytes,
                    rawCvId
            );
        } catch (CvParserClientException exception) {
            return null;
        } catch (SocketTimeoutException exception) {
            throw exception;
        } catch (IOException exception) {
            return null;
        }

        if (body.isBlank()) {
            return null;
        }

        try {
            CvParserErrorResponse errorResponse =
                    objectMapper.readValue(
                            body,
                            CvParserErrorResponse.class
                    );

            String code = errorResponse.code();

            if (code == null || code.isBlank()) {
                return null;
            }

            return truncate(
                    code.trim(),
                    100
            );
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String readBody(
            ResponseBody responseBody,
            int maxBytes,
            String rawCvId
    ) throws IOException {
        if (responseBody == null) {
            throw CvParserClientException
                    .emptyResponse(rawCvId);
        }

        long contentLength = responseBody.contentLength();

        if (contentLength > maxBytes) {
            throw CvParserClientException
                    .responseTooLarge(rawCvId);
        }

        try (InputStream input = responseBody.byteStream()) {
            byte[] bytes = input.readNBytes(maxBytes + 1);

            if (bytes.length > maxBytes) {
                throw CvParserClientException
                        .responseTooLarge(rawCvId);
            }

            return new String(
                    bytes,
                    StandardCharsets.UTF_8
            );
        }
    }

    private boolean isConnectionRefused(
            ConnectException exception
    ) {
        String message = exception.getMessage();

        return message != null
                && message
                .toLowerCase(Locale.ROOT)
                .contains("refused");
    }

    private <T extends Throwable> T findCause(
            Throwable throwable,
            Class<T> type
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }

            current = current.getCause();
        }

        return null;
    }

    private boolean isConnectTimeout(
            SocketTimeoutException exception
    ) {
        String message = exception.getMessage();

        return message != null
                && message
                .toLowerCase(Locale.ROOT)
                .contains("connect");
    }

    private String truncate(
            String value,
            int maxLength
    ) {
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }

    private static OkHttpClient buildHttpClient(
            CvParserProperties properties
    ) {
        long connectTimeoutMillis =
                properties.getConnectTimeout().toMillis();
        long responseTimeoutMillis =
                properties.getResponseTimeout().toMillis();

        return new OkHttpClient.Builder()
                .connectTimeout(
                        connectTimeoutMillis,
                        TimeUnit.MILLISECONDS
                )
                .readTimeout(
                        responseTimeoutMillis,
                        TimeUnit.MILLISECONDS
                )
                .writeTimeout(
                        responseTimeoutMillis,
                        TimeUnit.MILLISECONDS
                )
                .callTimeout(
                        responseTimeoutMillis,
                        TimeUnit.MILLISECONDS
                )
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build();
    }
}