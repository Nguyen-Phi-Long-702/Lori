package com.example.lori.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Ket noi Bucket4j toi Redis (Upstash) bang Lettuce.
 * Dung chung SPRING_DATA_REDIS_URL (dang rediss://default:<password>@<host>:<port>) voi phan con lai cua app.
 */
@Configuration
public class RateLimitConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient rateLimitRedisClient(@Value("${spring.data.redis.url}") String redisUrl) {
        return RedisClient.create(RedisURI.create(redisUrl));
    }

    @Bean
    public ProxyManager<String> rateLimitProxyManager(RedisClient rateLimitRedisClient) {
        StatefulRedisConnection<String, byte[]> connection =
                rateLimitRedisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        return Bucket4jLettuce.casBasedBuilder(connection)
                // Key tu het han khi bucket day lai (toi da 1 phut) de khong chiem dung luong Upstash
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                .build();
    }
}