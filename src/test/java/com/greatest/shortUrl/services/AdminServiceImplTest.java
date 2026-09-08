package com.greatest.shortUrl.services;

import com.greatest.shortUrl.entity.User;
import com.greatest.shortUrl.model.AdminStatsDto;
import com.greatest.shortUrl.model.AdminUserDto;
import com.greatest.shortUrl.repository.ShortUrlRepo;
import com.greatest.shortUrl.repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock ShortUrlRepo shortUrlRepo;
    @Mock UserRepo userRepo;
    @Mock EntityMapper entityMapper;

    @InjectMocks AdminServiceImpl adminService;

    // ─── getAllUsers — N+1 fix ────────────────────────────────────────

    @Test
    void getAllUsers_fetchesPerUserStatsViaSingleGroupByQuery_notN1() {
        User user1 = buildUser("u1");
        User user2 = buildUser("u2");

        when(userRepo.findAll()).thenReturn(List.of(user1, user2));

        // Explicit List<Object[]> to avoid Java type-inference ambiguity
        List<Object[]> stats = new ArrayList<>();
        stats.add(new Object[]{"u1", 5L, 120L});
        stats.add(new Object[]{"u2", 3L, 45L});
        when(shortUrlRepo.findUserUrlStats()).thenReturn(stats);

        List<AdminUserDto> result = adminService.getAllUsers();

        assertThat(result).hasSize(2);

        AdminUserDto dto1 = result.stream().filter(d -> d.id().equals("u1")).findFirst().orElseThrow();
        assertThat(dto1.totalUrls()).isEqualTo(5L);
        assertThat(dto1.totalClicks()).isEqualTo(120L);

        AdminUserDto dto2 = result.stream().filter(d -> d.id().equals("u2")).findFirst().orElseThrow();
        assertThat(dto2.totalUrls()).isEqualTo(3L);
        assertThat(dto2.totalClicks()).isEqualTo(45L);

        // Prove the old N+1 per-user query methods are never called
        verify(shortUrlRepo, never()).countByCreatedById(any());
        verify(shortUrlRepo, never()).sumClicksByUserId(any());
        verify(shortUrlRepo, times(1)).findUserUrlStats();
    }

    @Test
    void getAllUsers_whenUserHasNoUrls_defaultsStatsToZero() {
        User user = buildUser("lonely");
        when(userRepo.findAll()).thenReturn(List.of(user));

        // User not in stats result — getOrDefault should kick in
        List<Object[]> emptyStats = new ArrayList<>();
        when(shortUrlRepo.findUserUrlStats()).thenReturn(emptyStats);

        List<AdminUserDto> result = adminService.getAllUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalUrls()).isEqualTo(0L);
        assertThat(result.get(0).totalClicks()).isEqualTo(0L);
    }

    @Test
    void getAllUsers_mapsAllUserFieldsCorrectly() {
        User user = buildUser("u1");
        when(userRepo.findAll()).thenReturn(List.of(user));

        List<Object[]> stats = new ArrayList<>();
        stats.add(new Object[]{"u1", 2L, 10L});
        when(shortUrlRepo.findUserUrlStats()).thenReturn(stats);

        AdminUserDto dto = adminService.getAllUsers().get(0);

        assertThat(dto.id()).isEqualTo("u1");
        assertThat(dto.email()).isEqualTo("u1@test.com");
        assertThat(dto.name()).isEqualTo("Test User");
        assertThat(dto.role()).isEqualTo("ROLE_USER");
        assertThat(dto.createdAt()).isNotNull();
    }

    // ─── getStats ─────────────────────────────────────────────────────

    @Test
    void getStats_aggregatesAllCountsCorrectly() {
        when(userRepo.count()).thenReturn(10L);
        when(shortUrlRepo.count()).thenReturn(50L);
        when(shortUrlRepo.countByIsPrivateFalse()).thenReturn(30L);
        when(shortUrlRepo.countByIsPrivateTrue()).thenReturn(20L);
        when(shortUrlRepo.sumAllClicks()).thenReturn(500L);

        AdminStatsDto stats = adminService.getStats();

        assertThat(stats.totalUsers()).isEqualTo(10L);
        assertThat(stats.totalUrls()).isEqualTo(50L);
        assertThat(stats.publicUrls()).isEqualTo(30L);
        assertThat(stats.privateUrls()).isEqualTo(20L);
        assertThat(stats.totalClicks()).isEqualTo(500L);
    }

    // ─── deleteUrls ───────────────────────────────────────────────────

    @Test
    void deleteUrls_delegatesToRepoDeleteAllById() {
        List<String> ids = List.of("id-1", "id-2");

        adminService.deleteUrls(ids);

        verify(shortUrlRepo).deleteAllById(ids);
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private User buildUser(String id) {
        return User.builder()
                .id(id)
                .email(id + "@test.com")
                .name("Test User")
                .password("hash")
                .createdAt(Instant.now())
                .build();
    }
}
