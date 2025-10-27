package com.smartsplit.smartsplitback.it.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void bindProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        r.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "UTC");
        r.add("app.jwt.secret", () -> "f9wvJfbA1AZQeGlc1x3B8joQcXokci8Z/k57Q4Evu7/d7pqnuKmiyjqGFO9Rkjr7vmghxbV+Ob6vR3k0f/7eU7A7uhwYW18489kmUU14OJYuIk/EJ9s8A3p5hhCUZS7BAAq/nDj2GvabgbXCP+PWmkzEZw96OnkRUwDw90dlA5Q0Pw/xjgNyhELSprXPJD6NjPu9cSSEALSFrB7lHZDQYtLcenYwo38YLNFCc8Ppp0/U9SWm513HDynszLAkg5bQD/S8KjpkNiC16wnosp15RMVFG0LlWekuo4KZYTC4CKe26b5+BWqtLYXrMqPn4y3Ln+iOKmV2Imc0M1bOfKf4iYJ3k0ubhw2ew8teJBjpHt0=");
    }

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");

        truncate("payment_receipts");
        truncate("expense_item_shares");
        truncate("expense_items");
        truncate("expense_payments");
        truncate("expenses");
        truncate("group_members");
        truncate("groups_tbl");
        truncate("users");

        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
    }

    private void truncate(String table) {
        try {
            jdbc.execute("TRUNCATE TABLE " + table);
        } catch (Exception ignored) {
        }
    }
}
