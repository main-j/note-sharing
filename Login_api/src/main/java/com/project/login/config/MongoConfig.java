package com.project.login.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(Arrays.asList(
                new DateToLocalDateTimeConverter(),
                new LocalDateTimeToDateConverter()
        ));
    }

    static class DateToLocalDateTimeConverter implements Converter<Date, LocalDateTime> {
        @Override
        public LocalDateTime convert(Date source) {
            return Instant.ofEpochMilli(source.getTime()).atZone(APP_ZONE).toLocalDateTime();
        }
    }

    static class LocalDateTimeToDateConverter implements Converter<LocalDateTime, Date> {
        @Override
        public Date convert(LocalDateTime source) {
            return Date.from(source.atZone(APP_ZONE).toInstant());
        }
    }
}
