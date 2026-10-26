package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppSmokeTest {
    @Test
    void projectHasExpectedApplicationName() {
        assertTrue("java-tomcat-practice".contains("tomcat"));
    }
}
