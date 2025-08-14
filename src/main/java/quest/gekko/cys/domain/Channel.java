package quest.gekko.cys.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "channel", uniqueConstraints = @UniqueConstraint(columnNames = { "platform", "platform_id" }))
@Getter @Setter
public class Channel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    Platform platform;

    @Column(name = "platform_id", nullable = false)
    String platformId;

    @Column(nullable = false)
    String handle;

    @Column(nullable = false)
    String title;

    String avatarUrl;
    String country;

    @Column(nullable = false)
    Instant createdAt =  Instant.now();

    @Transient
    private Map<String, Long> counters = new HashMap<>();
}
