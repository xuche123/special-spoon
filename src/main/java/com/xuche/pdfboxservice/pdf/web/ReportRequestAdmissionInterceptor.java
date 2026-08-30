package com.xuche.pdfboxservice.pdf.web;

import com.xuche.pdfboxservice.pdf.limits.PdfRequestLimits;
import com.xuche.pdfboxservice.pdf.limits.RequestLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Rejects oversized report bodies before JSON deserialization allocates the request. */
@Component
final class ReportRequestAdmissionInterceptor implements HandlerInterceptor {
    private final long maxRequestBodyBytes;

    ReportRequestAdmissionInterceptor(PdfRequestLimits limits) {
        this.maxRequestBodyBytes = limits.maxRequestBodyBytes();
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getContentLengthLong() > maxRequestBodyBytes) {
            throw new RequestLimitExceededException(
                    "The report request body exceeds the configured size limit.", null, null);
        }
        return true;
    }
}
