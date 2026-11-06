package com.vyapaaros;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class VyapaarOsApplicationContextTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void applicationContextStarts() {
    }

    @Test
    void legacySalespersonAssignmentAndWorkspaceRoutesAreNotExposed() {
        var paths = handlerMapping.getHandlerMethods().keySet().stream()
            .flatMap(mapping -> mapping.getPatternValues().stream())
            .toList();

        assertFalse(paths.stream().anyMatch(path -> path.startsWith("/api/salesperson/retailers")));
        assertFalse(paths.stream().anyMatch(path -> path.startsWith("/api/retailers/assigned")));
        assertFalse(paths.stream().anyMatch(path -> path.contains("/salespeople")));
    }
}
