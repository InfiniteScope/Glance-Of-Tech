package site.infinitescope.glance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "digest_item")
public class DigestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "digest_id", nullable = false)
    private Digest digest;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String url;

    @Column
    private String imageUrl;

    @Column
    private String summary;

    @Column
    private String tags;

    @Column
    private Instant publishedAt;

    protected DigestItem() {
    }

    public DigestItem(String source, String title, String url, String imageUrl, Instant publishedAt) {
        this.source = source;
        this.title = title;
        this.url = url;
        this.imageUrl = imageUrl;
        this.publishedAt = publishedAt;
    }

    public void setDigest(Digest digest) {
        this.digest = digest;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Long getId() {
        return id;
    }

    public Digest getDigest() {
        return digest;
    }

    public String getSource() {
        return source;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getSummary() {
        return summary;
    }

    public String getTags() {
        return tags;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
