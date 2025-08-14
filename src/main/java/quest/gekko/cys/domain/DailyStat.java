package quest.gekko.cys.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "daily_stat", uniqueConstraints = @UniqueConstraint(columnNames = { "channel_id", "snapshot_date" }))
@Getter @Setter
public class DailyStat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    Channel channel;

    @Column(name = "snapshot_date", nullable = false)
    LocalDate snapshotDate;

    Long subscribers;
    Long views;
    Long videos;
    Long followers;
    Long liveViews;
}
