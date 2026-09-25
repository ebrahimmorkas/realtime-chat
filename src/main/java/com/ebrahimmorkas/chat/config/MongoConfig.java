package com.ebrahimmorkas.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** Populates {@code @CreatedDate} fields. */
@Configuration(proxyBeanMethods = false)
@EnableMongoAuditing
public class MongoConfig {
}
