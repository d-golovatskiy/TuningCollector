package org.kubsu.tuning.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Configuration
@PropertySource("classpath:s3minio.properties")
public class S3MinioConfig {
    @Value("${minio.url}")
    private String url;

    @Value("${minio.accessKey}")
    String accessKey;

    @Value("${minio.secretKey}")
    String secretKey;

    @Value("${minio.bucket}")
    String bucket;

    @Bean
    MinioClient minioClient() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        MinioClient minioClient = MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();

        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())){
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }

        return minioClient;
    }
}
