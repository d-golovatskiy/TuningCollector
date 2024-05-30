package com.example.tuningagent.config;


import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
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

    @Value("${influx.bucket}")
    private String bucket;


    @Bean
    public InfluxDBClient influxDBConnection(){
        return InfluxDBClientFactory.create(url, token, org, bucket);
    }


}
