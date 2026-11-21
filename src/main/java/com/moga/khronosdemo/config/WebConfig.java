package com.moga.khronosdemo.config;

import com.moga.khronosdemo.security.PlanFeatureInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PlanFeatureInterceptor planFeatureInterceptor;

    public WebConfig(PlanFeatureInterceptor planFeatureInterceptor) {
        this.planFeatureInterceptor = planFeatureInterceptor;
    }

    /**
     * The language of the request, from Accept-Language: the app sends the one the person chose.
     * Spanish or English, and English for anything else or no header. The field messages of a failed
     * validation (fieldErrors) come in it; every other message stays in English, which clients read.
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.forLanguageTag("es")));
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(planFeatureInterceptor).addPathPatterns("/api/**");
    }
}
