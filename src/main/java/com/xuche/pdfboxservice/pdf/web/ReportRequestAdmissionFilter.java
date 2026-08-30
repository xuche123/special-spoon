package com.xuche.pdfboxservice.pdf.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces the request-body limit even when the client does not send Content-Length. */
@Component
final class ReportRequestAdmissionFilter extends OncePerRequestFilter {
    private final long maxRequestBodyBytes;
    private final ObjectMapper objectMapper;

    ReportRequestAdmissionFilter(
            @Value("${pdf.limits.max-request-body-bytes:1048576}") long maxRequestBodyBytes,
            ObjectMapper objectMapper) {
        this.maxRequestBodyBytes = maxRequestBodyBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isReportIntake(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > maxRequestBodyBytes) {
            writeLimitExceeded(response);
            return;
        }
        filterChain.doFilter(new LimitedBodyRequest(request, maxRequestBodyBytes), response);
    }

    private boolean isReportIntake(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/reports/") || uri.startsWith("/api/template-previews/");
    }

    private void writeLimitExceeded(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ReportError.requestLimitExceeded());
    }

    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {
        private final long maxBytes;
        private ServletInputStream limitedInputStream;

        LimitedBodyRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limitedInputStream == null) {
                ServletInputStream delegate = super.getInputStream();
                limitedInputStream =
                        new ServletInputStream() {
                            private long bytesRead;

                            @Override
                            public int read() throws IOException {
                                int value = delegate.read();
                                if (value >= 0) check(1);
                                return value;
                            }

                            @Override
                            public int read(byte[] bytes, int offset, int length)
                                    throws IOException {
                                int count = delegate.read(bytes, offset, length);
                                if (count > 0) check(count);
                                return count;
                            }

                            @Override
                            public long skip(long count) throws IOException {
                                long skipped = delegate.skip(count);
                                if (skipped > 0) check(skipped);
                                return skipped;
                            }

                            private void check(long count) throws RequestBodyLimitIOException {
                                bytesRead += count;
                                if (bytesRead > maxBytes) throw new RequestBodyLimitIOException();
                            }

                            @Override
                            public boolean isFinished() {
                                return delegate.isFinished();
                            }

                            @Override
                            public boolean isReady() {
                                return delegate.isReady();
                            }

                            @Override
                            public void setReadListener(jakarta.servlet.ReadListener listener) {
                                delegate.setReadListener(listener);
                            }
                        };
            }
            return limitedInputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset =
                    encoding == null ? StandardCharsets.ISO_8859_1 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
