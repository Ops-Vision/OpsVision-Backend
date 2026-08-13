package com.opsvision.config;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
class FlywayMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayBaselineMigrationCreatesAppMeta() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_meta WHERE meta_key = ?",
                Integer.class,
                "schema_initialized"
        );

        assertThat(count).isEqualTo(1);
    }
}
