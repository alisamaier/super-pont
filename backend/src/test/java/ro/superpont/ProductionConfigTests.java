package ro.superpont;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import ro.superpont.config.ProductionConfig;
import ro.superpont.config.ProductionEnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.io.support.SpringFactoriesLoader;

class ProductionConfigTests {
    @Test void registersAndRunsTheGuardBeforeDatabaseInitialization() {
        assertThat(SpringFactoriesLoader.loadFactoryNames(org.springframework.boot.EnvironmentPostProcessor.class,
                getClass().getClassLoader())).contains(ProductionEnvironmentPostProcessor.class.getName());
        var env = environment(); env.setActiveProfiles("prod", "dev");
        assertThatThrownBy(() -> new ProductionEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication()))
                .hasMessageContaining("cannot be combined");
        assertThatCode(() -> new ProductionEnvironmentPostProcessor().postProcessEnvironment(new MockEnvironment(), new SpringApplication()))
                .doesNotThrowAnyException();
    }
    private MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.test/db?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory")
                .withProperty("spring.flyway.locations", "classpath:db/migration")
                .withProperty("app.team-logos.storage", "database")
                .withProperty("app.frontend-url", "https://frontend.example.test")
                .withProperty("app.password-reset.frontend-url", "https://frontend.example.test")
                .withProperty("server.servlet.session.cookie.secure", "true")
                .withProperty("server.servlet.session.cookie.same-site", "lax")
                .withProperty("app.mail.from", "sender@verified.test");
    }
    @Test void acceptsHostedSettingsAndRejectsDevSeedsAndEphemeralLogoStorage() {
        assertThatCode(() -> new ProductionConfig(environment())).doesNotThrowAnyException();
        var dev = environment(); dev.setActiveProfiles("prod", "dev");
        assertThatThrownBy(() -> new ProductionConfig(dev)).hasMessageContaining("cannot be combined");
        assertThatThrownBy(() -> new ProductionConfig(environment().withProperty("spring.flyway.locations", "classpath:db/devdata")))
                .hasMessageContaining("Flyway");
        assertThatThrownBy(() -> new ProductionConfig(environment().withProperty("app.team-logos.storage", "filesystem")))
                .hasMessageContaining("database storage");
    }
    @Test void rejectsInsecureDatabaseCookiesUrlsAndAutomaticSync() {
        assertThatThrownBy(() -> new ProductionConfig(environment().withProperty("spring.datasource.url", "jdbc:postgresql://db.test/db?sslmode=require")))
                .hasMessageContaining("verify-full");
        assertThatThrownBy(() -> new ProductionConfig(environment().withProperty("spring.datasource.url",
                environment().getProperty("spring.datasource.url") + "&sslmode=disable"))).hasMessageContaining("verify-full");
        assertThatThrownBy(() -> new ProductionConfig(environment().withProperty("app.frontend-url", "http://localhost:5173")))
                .hasMessageContaining("public HTTPS");
        assertThatThrownBy(() -> new ProductionConfig(environment().withProperty("server.servlet.session.cookie.secure", "false")))
                .hasMessageContaining("Secure");
        assertThatThrownBy(() -> new ProductionConfig(environment().withProperty("app.sportstats.sync-enabled", "true")))
                .hasMessageContaining("manual");
    }
}
