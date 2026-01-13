package kafkaspringboot.demo;

import java.time.Instant;

public record MockEvent(
        String id,
        String type,
        Instant timestamp,
        String source,
        int value
) {}
