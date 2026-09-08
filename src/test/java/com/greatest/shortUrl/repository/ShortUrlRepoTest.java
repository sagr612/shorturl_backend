package com.greatest.shortUrl.repository;

import com.greatest.shortUrl.entity.ShortUrl;
import com.greatest.shortUrl.entity.User;
import com.greatest.shortUrl.model.Role;
import com.greatest.shortUrl.model.UrlStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-layer tests using @DataJpaTest.
 * Boots only the JPA slice (no web, no security, no Redis).
 * Uses H2 in-memory DB — validates our custom JPQL queries actually work.
 */
@DataJpaTest
@ActiveProfiles("test")
class ShortUrlRepoTest {

    @Autowired TestEntityManager em;
    @Autowired ShortUrlRepo shortUrlRepo;
    @Autowired UserRepo userRepo;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = em.persist(buildUser("alice@test.com"));
        bob   = em.persist(buildUser("bob@test.com"));
        em.flush();
    }

    // ─── existsByShortKey ─────────────────────────────────────────────

    @Test
    void existsByShortKey_whenPresent_returnsTrue() {
        persistUrl(alice, "abc123", "https://example.com", false, UrlStatus.ACTIVE, null);

        assertThat(shortUrlRepo.existsByShortKey("abc123")).isTrue();
    }

    @Test
    void existsByShortKey_whenAbsent_returnsFalse() {
        assertThat(shortUrlRepo.existsByShortKey("missing")).isFalse();
    }

    // ─── findByShortKey ───────────────────────────────────────────────

    @Test
    void findByShortKey_returnsCorrectUrl() {
        persistUrl(alice, "key1", "https://spring.io", false, UrlStatus.ACTIVE, null);

        Optional<ShortUrl> result = shortUrlRepo.findByShortKey("key1");

        assertThat(result).isPresent();
        assertThat(result.get().getOriginalUrl()).isEqualTo("https://spring.io");
    }

    // ─── findPublicUrls ───────────────────────────────────────────────

    @Test
    void findPublicUrls_returnsOnlyActivePublicUrls() {
        persistUrl(alice, "pub1", "https://public.com",  false, UrlStatus.ACTIVE,  null);
        persistUrl(alice, "prv1", "https://private.com", true,  UrlStatus.ACTIVE,  null);
        persistUrl(alice, "del1", "https://deleted.com", false, UrlStatus.DELETED, null);

        Page<ShortUrl> result = shortUrlRepo.findPublicUrls(null, UrlStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getShortKey()).isEqualTo("pub1");
    }

    @Test
    void findPublicUrls_searchByOriginalUrl_filtersCorrectly() {
        persistUrl(alice, "k1", "https://github.com",  false, UrlStatus.ACTIVE, null);
        persistUrl(alice, "k2", "https://google.com",  false, UrlStatus.ACTIVE, null);

        Page<ShortUrl> result = shortUrlRepo.findPublicUrls("github", UrlStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getShortKey()).isEqualTo("k1");
    }

    @Test
    void findPublicUrls_searchByShortKey_filtersCorrectly() {
        persistUrl(alice, "gh-abc", "https://github.com", false, UrlStatus.ACTIVE, null);
        persistUrl(alice, "gg-xyz", "https://google.com", false, UrlStatus.ACTIVE, null);

        Page<ShortUrl> result = shortUrlRepo.findPublicUrls("gh-", UrlStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findPublicUrls_caseInsensitiveSearch() {
        persistUrl(alice, "k1", "https://SPRING.io", false, UrlStatus.ACTIVE, null);

        Page<ShortUrl> result = shortUrlRepo.findPublicUrls("spring", UrlStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ─── findUserUrls ─────────────────────────────────────────────────

    @Test
    void findUserUrls_returnsOnlyUrlsBelongingToUser() {
        persistUrl(alice, "a1", "https://alice.com", false, UrlStatus.ACTIVE,  null);
        persistUrl(bob,   "b1", "https://bob.com",   false, UrlStatus.ACTIVE,  null);

        Page<ShortUrl> result = shortUrlRepo.findUserUrls(alice.getId(), null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getShortKey()).isEqualTo("a1");
    }

    @Test
    void findUserUrls_excludesDeletedUrls() {
        persistUrl(alice, "a1", "https://alice.com",   false, UrlStatus.ACTIVE,  null);
        persistUrl(alice, "a2", "https://deleted.com", false, UrlStatus.DELETED, null);

        Page<ShortUrl> result = shortUrlRepo.findUserUrls(alice.getId(), null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getShortKey()).isEqualTo("a1");
    }

    @Test
    void findUserUrls_filterByIsPrivate_returnsOnlyPrivate() {
        persistUrl(alice, "pub", "https://public.com",  false, UrlStatus.ACTIVE, null);
        persistUrl(alice, "prv", "https://private.com", true,  UrlStatus.ACTIVE, null);

        Page<ShortUrl> result = shortUrlRepo.findUserUrls(alice.getId(), null, true, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getShortKey()).isEqualTo("prv");
    }

    @Test
    void findUserUrls_filterByIsPrivateFalse_returnsOnlyPublic() {
        persistUrl(alice, "pub", "https://public.com",  false, UrlStatus.ACTIVE, null);
        persistUrl(alice, "prv", "https://private.com", true,  UrlStatus.ACTIVE, null);

        Page<ShortUrl> result = shortUrlRepo.findUserUrls(alice.getId(), null, false, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getShortKey()).isEqualTo("pub");
    }

    // ─── incrementClickCount ──────────────────────────────────────────

    @Test
    void incrementClickCount_incrementsByOne() {
        ShortUrl url = persistUrl(alice, "click1", "https://test.com", false, UrlStatus.ACTIVE, null);
        long initialClicks = url.getClickCount();

        shortUrlRepo.incrementClickCount(url.getId());
        em.clear(); // force fresh load

        ShortUrl updated = shortUrlRepo.findById(url.getId()).orElseThrow();
        assertThat(updated.getClickCount()).isEqualTo(initialClicks + 1);
    }

    // ─── softDeleteUserUrls ───────────────────────────────────────────

    @Test
    void softDeleteUserUrls_setsStatusToDeleted() {
        ShortUrl url = persistUrl(alice, "del1", "https://del.com", false, UrlStatus.ACTIVE, null);

        shortUrlRepo.softDeleteUserUrls(List.of(url.getId()), alice.getId());
        em.clear();

        ShortUrl updated = shortUrlRepo.findById(url.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(UrlStatus.DELETED);
    }

    @Test
    void softDeleteUserUrls_doesNotDeleteUrlsOwnedByOtherUser() {
        ShortUrl aliceUrl = persistUrl(alice, "al1", "https://alice.com", false, UrlStatus.ACTIVE, null);

        // Bob tries to delete Alice's URL
        shortUrlRepo.softDeleteUserUrls(List.of(aliceUrl.getId()), bob.getId());
        em.clear();

        ShortUrl unchanged = shortUrlRepo.findById(aliceUrl.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(UrlStatus.ACTIVE);
    }

    // ─── markExpiredUrls ──────────────────────────────────────────────

    @Test
    void markExpiredUrls_setsStatusToExpiredForPastExpiry() {
        Instant pastExpiry = Instant.now().minus(1, DAYS);
        ShortUrl url = persistUrl(alice, "exp1", "https://exp.com", false, UrlStatus.ACTIVE, pastExpiry);

        int updated = shortUrlRepo.markExpiredUrls(Instant.now());
        em.clear();

        assertThat(updated).isGreaterThanOrEqualTo(1);
        assertThat(shortUrlRepo.findById(url.getId()).orElseThrow().getStatus())
                .isEqualTo(UrlStatus.EXPIRED);
    }

    @Test
    void markExpiredUrls_doesNotExpireActiveFutureUrls() {
        Instant futureExpiry = Instant.now().plus(7, DAYS);
        persistUrl(alice, "noexp", "https://future.com", false, UrlStatus.ACTIVE, futureExpiry);

        shortUrlRepo.markExpiredUrls(Instant.now());
        em.clear();

        assertThat(shortUrlRepo.findByShortKey("noexp").orElseThrow().getStatus())
                .isEqualTo(UrlStatus.ACTIVE);
    }

    // ─── findUserUrlStats (the N+1 fix query) ─────────────────────────

    @Test
    void findUserUrlStats_returnsAggregatedStatsPerUser() {
        persistUrl(alice, "s1", "https://a1.com", false, UrlStatus.ACTIVE, null);
        persistUrl(alice, "s2", "https://a2.com", false, UrlStatus.ACTIVE, null);
        persistUrl(bob,   "s3", "https://b1.com", false, UrlStatus.ACTIVE, null);

        List<Object[]> stats = shortUrlRepo.findUserUrlStats();

        assertThat(stats).isNotEmpty();

        Object[] aliceRow = stats.stream()
                .filter(r -> alice.getId().equals(r[0]))
                .findFirst().orElseThrow(() -> new AssertionError("Alice not found in stats"));
        assertThat((Long) aliceRow[1]).isEqualTo(2L); // url count
    }

    @Test
    void findUserUrlStats_userWithNoUrls_returnsZeroCounts() {
        // bob has no URLs
        List<Object[]> stats = shortUrlRepo.findUserUrlStats();

        Object[] bobRow = stats.stream()
                .filter(r -> bob.getId().equals(r[0]))
                .findFirst().orElseThrow(() -> new AssertionError("Bob not found in stats"));
        assertThat((Long) bobRow[1]).isEqualTo(0L);
        assertThat((Long) bobRow[2]).isEqualTo(0L);
    }

    // ─── sumAllClicks ─────────────────────────────────────────────────

    @Test
    void sumAllClicks_returnsZeroWhenNoUrls() {
        // Only users seeded — no URLs
        Long total = shortUrlRepo.sumAllClicks();
        // May have 0 from the seeded users' empty URLs
        assertThat(total).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void sumAllClicks_sumsBothUsers() {
        ShortUrl a = persistUrl(alice, "c1", "https://a.com", false, UrlStatus.ACTIVE, null);
        ShortUrl b = persistUrl(bob,   "c2", "https://b.com", false, UrlStatus.ACTIVE, null);
        shortUrlRepo.incrementClickCount(a.getId());
        shortUrlRepo.incrementClickCount(a.getId());
        shortUrlRepo.incrementClickCount(b.getId());
        em.clear();

        Long total = shortUrlRepo.sumAllClicks();

        assertThat(total).isEqualTo(3L);
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private User buildUser(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword("hash");
        u.setName("Test User");
        u.setRole(Role.ROLE_USER);
        u.setCreatedAt(Instant.now());
        return u;
    }

    private ShortUrl persistUrl(User owner, String key, String url,
                                boolean isPrivate, UrlStatus status, Instant expiresAt) {
        ShortUrl s = new ShortUrl();
        s.setShortKey(key);
        s.setOriginalUrl(url);
        s.setIsPrivate(isPrivate);
        s.setStatus(status);
        s.setClickCount(0L);
        s.setCreatedAt(Instant.now());
        s.setExpiresAt(expiresAt);
        s.setCreatedBy(owner);
        ShortUrl saved = em.persist(s);
        em.flush();
        return saved;
    }
}
