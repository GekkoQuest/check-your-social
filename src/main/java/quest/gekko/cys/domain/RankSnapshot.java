package quest.gekko.cys.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "rank_snapshot")
@Getter @Setter
public class RankSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    LocalDate snapshotDate;

    @Enumerated(EnumType.STRING)
    Platform platform;

    String metric; // "subscribers", "followers", "growth_7d", etc.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    Channel channel;

    Integer rank;
}
