package com.xuche.pdfboxservice.pdf;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces the request-body limit even when the client does not send Content-Length. */
@Component
final class ReportRequestBodyLimitFilter extends OncePerRequestFilter {
    private final long maxRequestBodyBytes;

    ReportRequestBodyLimitFilter(
            @Value("${pdf.limits.max-request-body-bytes:1048576}") long maxRequestBodyBytes) {
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/reports/")) {
            filterChain.doFilter(request, response);
            return;
        }
        filterChain.doFilter(new LimitedBodyRequest(request, maxRequestBodyBytes), response);
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

                            private void check(int count) throws RequestBodyLimitIOException {
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
    }
}
