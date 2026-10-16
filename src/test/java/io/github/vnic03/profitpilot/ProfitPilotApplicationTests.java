package io.github.vnic03.profitpilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:profitpilot_context;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "profitpilot.external-api.enabled=false",
        "profitpilot.alpha-vantage.enabled=false",
        "profitpilot.marketaux.enabled=false",
        "profitpilot.sync.on-startup=false",
        "profitpilot.sync.cron=-"
})
class ProfitPilotApplicationTests {

    @Test
    void contextLoads() {
    }
}
