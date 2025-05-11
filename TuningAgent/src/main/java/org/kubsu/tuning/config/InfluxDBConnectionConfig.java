package org.kubsu.tuning.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:influx.properties")
public class InfluxDBConnectionConfig {
    @Value("${influx.url}")
    private String url;

    @Value("${influx.token}")
    private char[] token;

    @Value("${influx.org}")
    private String org;
}
