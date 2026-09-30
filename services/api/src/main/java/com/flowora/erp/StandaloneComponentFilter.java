package com.flowora.erp;

import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

import java.io.IOException;
import java.util.Set;

/** The default standalone runtime exposes only health, version and their security support. */
public final class StandaloneComponentFilter implements TypeFilter, EnvironmentAware {
    private static final Set<String> STANDALONE_COMPONENTS = Set.of(
            "com.flowora.erp.system.HealthController",
            "com.flowora.erp.system.SystemVersionController",
            "com.flowora.erp.config.SecurityConfig",
            "com.flowora.erp.identity.DemoUserStore",
            "com.flowora.erp.common.api.GlobalExceptionHandler"
    );
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
            throws IOException {
        return environment.getProperty("flowora.standalone.minimal", Boolean.class, false)
                && environment.acceptsProfiles(Profiles.of("standalone"))
                && !STANDALONE_COMPONENTS.contains(metadataReader.getClassMetadata().getClassName());
    }
}
