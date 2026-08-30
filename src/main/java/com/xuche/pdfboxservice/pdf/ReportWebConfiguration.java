package com.xuche.pdfboxservice.pdf;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class ReportWebConfiguration implements WebMvcConfigurer {
    private final ReportRequestLimitInterceptor requestLimitInterceptor;

    ReportWebConfiguration(ReportRequestLimitInterceptor requestLimitInterceptor) {
        this.requestLimitInterceptor = requestLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLimitInterceptor)
                .addPathPatterns("/api/reports/**", "/api/template-previews/**");
    }
}
