package com.ojo.cyrus.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookPropertiesTest {

    private final WebhookProperties props = new WebhookProperties(5, 60, 3600);

    @Test
    void doublesEachAttempt_untilCap() {
        assertThat(props.backoffSecondsFor(1)).isEqualTo(60);
        assertThat(props.backoffSecondsFor(2)).isEqualTo(120);
        assertThat(props.backoffSecondsFor(3)).isEqualTo(240);
        assertThat(props.backoffSecondsFor(4)).isEqualTo(480);
    }

    @Test
    void neverExceedsMaxBackoff() {
        // 60 * 2^6 = 3840 > 3600 cap
        assertThat(props.backoffSecondsFor(7)).isEqualTo(3600);
        assertThat(props.backoffSecondsFor(20)).isEqualTo(3600);
    }

    @Test
    void firstAttemptUsesInitialBackoff() {
        assertThat(props.backoffSecondsFor(1)).isEqualTo(props.initialBackoffSeconds());
    }
}
