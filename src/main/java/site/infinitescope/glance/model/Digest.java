package site.infinitescope.glance.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "digest", uniqueConstraints = @UniqueConstraint(columnNames = {"date", "period"}))
public class Digest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Period period;

    @Column(nullable = false)
    private String title;

    @Column
    private String summary;

    @Column(nullable = false)
    private boolean degraded;

    @Column(nullable = false)
    private OffsetDateTime generatedAt;

    @OneToMany(mappedBy = "digest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DigestItem> items = new ArrayList<>();

    protected Digest() {
    }

    public Digest(LocalDate date, Period period, String title, String summary, boolean degraded, OffsetDateTime generatedAt) {
        this.date = date;
        this.period = period;
        this.title = title;
        this.summary = summary;
        this.degraded = degraded;
        this.generatedAt = generatedAt;
    }

    public void addItem(DigestItem item) {
        item.setDigest(this);
        this.items.add(item);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public Period getPeriod() {
        return period;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public List<DigestItem> getItems() {
        return items;
    }
}
