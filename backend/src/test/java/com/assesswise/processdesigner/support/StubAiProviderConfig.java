package com.assesswise.processdesigner.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Registers the scripted provider in place of the (disabled) live one. */
@TestConfiguration
public class StubAiProviderConfig {

    @Bean
    public StubAiProvider stubAiProvider() {
        return new StubAiProvider();
    }
}
