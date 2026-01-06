package kafkaspringboot.demo;

public record MockEvent(
    String eventId,
    String type,
    String ts,
    String source,
    int value
) {}
