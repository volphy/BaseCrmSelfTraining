package com.solidbrain.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.httpclient.HttpClientMetricNameStrategies;
import com.codahale.metrics.httpclient.InstrumentedHttpClients;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.codahale.metrics.logback.InstrumentedAppender;
import com.ryantenney.metrics.spring.config.annotation.MetricsConfigurerAdapter;
import org.apache.http.client.HttpClient;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.ryantenney.metrics.spring.config.annotation.EnableMetrics;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

import java.util.concurrent.TimeUnit;

/**
 * Created by Krzysztof Wilk on 24/10/2016.
 */
@Configuration
@EnableAutoConfiguration
@EnableMetrics
public class MetricsConfig extends MetricsConfigurerAdapter {

    @Bean
    @Autowired
    public HttpClient getHttpClient(MetricRegistry registry) {
        return InstrumentedHttpClients.createDefault(registry,
                HttpClientMetricNameStrategies.QUERYLESS_URL_AND_METHOD
        );
    }


    @Override
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    @Primary
    public MetricRegistry getMetricRegistry() {
        MetricRegistry registry = new MetricRegistry();


        // register JVM metrics
        registry.registerAll(new GarbageCollectorMetricSet());
        registry.registerAll(new MemoryUsageGaugeSet());
        registry.registerAll(new ThreadStatesGaugeSet());

        // register logging metrics
        LoggerContext factory = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = factory.getLogger(Logger.ROOT_LOGGER_NAME);

        InstrumentedAppender metrics = new InstrumentedAppender(registry);
        metrics.setContext(root.getLoggerContext());
        metrics.start();
        root.addAppender(metrics);

        return registry;
    }

    @Override
    public HealthCheckRegistry getHealthCheckRegistry() {
        return new HealthCheckRegistry();
    }

    @Override
    public void configureReporters(MetricRegistry metricRegistry) {
        // registerReporter allows the MetricsConfigurerAdapter to
        // shut down the reporter when the Spring context is closed
        registerReporter(ConsoleReporter
                .forRegistry(metricRegistry)
                .build())
                .start(1, TimeUnit.MINUTES);

        registerReporter(Slf4jReporter
                .forRegistry(metricRegistry)
                .build())
                .start(1, TimeUnit.MINUTES);

        JmxReporter.forRegistry(metricRegistry).build().start();
    }
}
