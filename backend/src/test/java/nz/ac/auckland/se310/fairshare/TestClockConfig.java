package nz.ac.auckland.se310.fairshare;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@TestConfiguration
public class TestClockConfig {

    public static class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        public MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        public void setInstant(Instant instant) { this.instant = instant; }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }
        @Override public Instant instant() { return instant; }
    }

    // Named distinctly from AppConfig's "clock" bean, which Spring won't silently override;
    // @Primary still makes this the one that gets autowired wherever a Clock is needed.
    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(Instant.now(), ZoneId.systemDefault());
    }
}
