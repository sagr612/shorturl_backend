package com.greatest.shortUrl.repository;

import com.greatest.shortUrl.entity.ShortUrl;
import com.greatest.shortUrl.model.UrlStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShortUrlRepo extends JpaRepository<ShortUrl, String> {

    boolean existsByShortKey(String shortKey);

    Optional<ShortUrl> findByShortKey(String shortKey);

    @EntityGraph(attributePaths = "createdBy")
    Page<ShortUrl> findByCreatedById(String userId, Pageable pageable);


    @Query("""
            SELECT su
            FROM ShortUrl su
            WHERE su.createdBy.id = :userId
            AND su.status <> 'DELETED'
            AND (
                 COALESCE(:search, '') = ''
                 OR LOWER(su.originalUrl)
                    LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(su.shortKey)
                    LIKE LOWER(CONCAT('%', :search, '%'))
            )
            AND (
                 :isPrivate IS NULL
                 OR su.isPrivate = :isPrivate
            )
            """)
    Page<ShortUrl> findUserUrls(
            @Param("userId") String userId,
            @Param("search") String search,
            @Param("isPrivate") Boolean isPrivate,
            Pageable pageable
    );


    @Query("""
            SELECT su
            FROM ShortUrl su
            WHERE su.isPrivate = false
            AND su.status = :status
            AND (
                 COALESCE(:search, '') = ''
                 OR LOWER(su.originalUrl)
                    LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(su.shortKey)
                    LIKE LOWER(CONCAT('%', :search, '%'))
            )
            """)
    Page<ShortUrl> findPublicUrls(

            @Param("search")
            String search,

            @Param("status")
            UrlStatus status,

            Pageable pageable
    );


    Long countByCreatedById(String userId);

    Long countByCreatedByIdAndIsPrivateFalse(String userId);

    Long countByCreatedByIdAndIsPrivateTrue(String userId);


    @Query("""
            SELECT COALESCE(SUM(su.clickCount), 0)
            FROM ShortUrl su
            WHERE su.createdBy.id = :userId
            """)
    Long sumClicksByUserId(@Param("userId") String userId);


    @EntityGraph(attributePaths = "createdBy")
    @Query("""
            SELECT su
            FROM ShortUrl su
            WHERE su.status <> 'DELETED'
            AND (
                 COALESCE(:search, '') = ''
                 OR LOWER(su.originalUrl)
                    LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(su.shortKey)
                    LIKE LOWER(CONCAT('%', :search, '%'))
            )
            AND (
                 :isPrivate IS NULL
                 OR su.isPrivate = :isPrivate
            )
            """)
    Page<ShortUrl> findAllUrlsForAdmin(

            @Param("search")
            String search,

            @Param("isPrivate")
            Boolean isPrivate,

            Pageable pageable
    );


    Long countByIsPrivateFalse();

    Long countByIsPrivateTrue();


    @Query("""
            SELECT COALESCE(SUM(su.clickCount), 0)
            FROM ShortUrl su
            """)
    Long sumAllClicks();

    @Query("""
            SELECT u.id, COUNT(s.id), COALESCE(SUM(s.clickCount), 0)
            FROM User u LEFT JOIN ShortUrl s ON s.createdBy.id = u.id
            GROUP BY u.id
            """)
    List<Object[]> findUserUrlStats();


    @Modifying
    @Transactional
    @Query("""
            UPDATE ShortUrl su
            SET su.clickCount = su.clickCount + 1
            WHERE su.id = :id
            """)
    void incrementClickCount(
            @Param("id") String id
    );


    @Modifying
    @Transactional
    @Query("""
            UPDATE ShortUrl su
            SET su.status = 'EXPIRED'
            WHERE su.expiresAt IS NOT NULL
            AND su.expiresAt < :now
            AND su.status = 'ACTIVE'
            """)
    int markExpiredUrls(@Param("now") Instant now);


    @Modifying
    @Transactional
    @Query("""
            DELETE FROM ShortUrl su
            WHERE su.status = 'EXPIRED'
            AND su.expiresAt < :cutoff
            """)
    int deleteExpiredUrlsOlderThan(@Param("cutoff") Instant cutoff);


    @Modifying
    @Transactional
    @Query("""
            UPDATE ShortUrl su
            SET su.status = 'DELETED'
            WHERE su.id IN :ids
            AND su.createdBy.id = :userId
            """)
    int softDeleteUserUrls(

            @Param("ids")
            List<String> ids,

            @Param("userId")
            String userId
    );

}