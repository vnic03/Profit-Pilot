package io.github.vnic03.profitpilot.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.profiles.active=server",
      "spring.datasource.url=jdbc:h2:mem:profitpilot_security;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "profitpilot.external-api.enabled=false",
      "profitpilot.alpha-vantage.enabled=false",
      "profitpilot.marketaux.enabled=false",
      "profitpilot.sync.on-startup=false",
      "profitpilot.sync.cron=-"
    })
@AutoConfigureMockMvc
class ServerSecurityConfigTest {

  @Autowired private MockMvc mvc;

  @Autowired private ApplicationContext context;

  @Test
  void serverDoesNotCreateDefaultUsers() {
    assertTrue(
        context.getBeansOfType(UserDetailsService.class).isEmpty(),
        "The server profile must not create a default user service.");
  }

  @Test
  void buildInformationAllowsGetAndHead() throws Exception {
    mvc.perform(get("/api/operations/build")).andExpect(status().isOk());

    mvc.perform(head("/api/operations/build")).andExpect(status().isOk());
  }

  @Test
  void writeMethodsAreForbidden() throws Exception {
    for (HttpMethod method :
        new HttpMethod[] {HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
      mvc.perform(request(method, "/api/operations/build")).andExpect(status().isForbidden());
    }
  }
}
