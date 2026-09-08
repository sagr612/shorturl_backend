package com.greatest.shortUrl.services;

import com.greatest.shortUrl.entity.User;
import com.greatest.shortUrl.model.UserDto;
import com.greatest.shortUrl.model.UserStatsDto;
import com.greatest.shortUrl.repository.ShortUrlRepo;
import com.greatest.shortUrl.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepo userRepo;

    private final ShortUrlRepo shortUrlRepo;

    private final SecurityUtils securityUtils;
    private final EntityMapper entityMapper;

    public UserDto getCurrentUser() {
        String currentUserId = securityUtils.getCurrentUserId();
        User user = userRepo.findById(currentUserId).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return entityMapper.toUserDto(user);
    }

    public UserStatsDto getCurrentUserStats() {
        String currentUserId = securityUtils.getCurrentUserId();
        Long totalUrls =shortUrlRepo.countByCreatedById(currentUserId);
        Long publicUrls = shortUrlRepo.countByCreatedByIdAndIsPrivateFalse(currentUserId);
        Long privateUrls = shortUrlRepo.countByCreatedByIdAndIsPrivateTrue(currentUserId);
        Long totalClicks = shortUrlRepo.sumClicksByUserId(currentUserId);
        return new UserStatsDto(totalUrls, publicUrls, privateUrls, totalClicks != null ? totalClicks : 0L);
    }
}