package com.flowora.erp.system;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisProperties;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeProfileConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"local", "production"})
    void retainsIndexedSessionsWithBootFourPropertyBinding(String profile) throws Exception {
        var environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load(profile,
                new ClassPathResource("application-" + profile + ".yml"))
                .forEach(environment.getPropertySources()::addFirst);
        var properties = Binder.get(environment).bind("spring.session.data.redis",
                SessionDataRedisProperties.class).get();
        assertThat(properties.getRepositoryType()).isEqualTo(SessionDataRedisProperties.RepositoryType.INDEXED);
        assertThat(properties.getNamespace()).isEqualTo("spring:session");
    }
}
