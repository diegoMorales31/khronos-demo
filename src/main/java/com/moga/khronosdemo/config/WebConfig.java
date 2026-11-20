package com.moga.khronosdemo.config;

import com.moga.khronosdemo.security.PlanFeatureInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PlanFeatureInterceptor planFeatureInterceptor;

    public WebConfig(PlanFeatureInterceptor planFeatureInterceptor) {
        this.planFeatureInterceptor = planFeatureInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(planFeatureInterceptor).addPathPatterns("/api/**");
    }
}
