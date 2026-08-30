package com.xuche.pdfboxservice.pdf.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class ReportWebConfiguration implements WebMvcConfigurer {
    private final ReportRequestAdmissionInterceptor requestAdmissionInterceptor;

    ReportWebConfiguration(ReportRequestAdmissionInterceptor requestAdmissionInterceptor) {
        this.requestAdmissionInterceptor = requestAdmissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestAdmissionInterceptor)
                .addPathPatterns("/api/reports/**", "/api/template-previews/**");
    }
}
