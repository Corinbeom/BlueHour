package com.bluehour.infra.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionJwtConfigurationTest {

    @Test
    @DisplayName("운영 프로필은 fallback 없이 JWT_SECRET 환경변수를 요구한다")
    void productionProfile_requiresJwtSecretEnvironmentVariable() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));

        Properties properties = yaml.getObject();

        assertThat(properties)
                .isNotNull()
                .containsEntry("bluehour.jwt.secret", "${JWT_SECRET}");
    }
}
