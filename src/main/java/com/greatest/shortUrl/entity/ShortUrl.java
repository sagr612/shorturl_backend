package com.greatest.shortUrl.entity;


import com.greatest.shortUrl.model.UrlStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(
        name = "short_urls",
        indexes = {

                @Index(
                        name = "idx_short_key",
                        columnList = "short_key"
                ),

                @Index(
                        name = "idx_created_by",
                        columnList = "created_by"
                ),

                @Index(
                        name = "idx_status",
                        columnList = "status"
                ),

                @Index(
                        name = "idx_expires_at",
                        columnList = "expires_at"
                )
        }
)
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
@Setter
public class ShortUrl {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "short_key", nullable = false,unique = true, length = 10)
    private String shortKey;

    @Column(name = "original_url", nullable = false)
    private String originalUrl;

    @ColumnDefault("false")
    @Column(name = "is_private", nullable = false)
    private Boolean isPrivate = false;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "click_count", nullable = false)
    private Long clickCount = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UrlStatus status;

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
    }


}