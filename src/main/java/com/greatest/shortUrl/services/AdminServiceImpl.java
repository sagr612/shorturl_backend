package com.greatest.shortUrl.services;

import com.greatest.shortUrl.entitiy.User;
import com.greatest.shortUrl.model.*;
import com.greatest.shortUrl.repository.ShortUrlRepo;
import com.greatest.shortUrl.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ShortUrlRepo shortUrlRepo;

    private final UserRepo userRepo;

    private final EntityMapper entityMapper;

    private Pageable getPageable(int page, int size, String sortBy, String direction) {
        page = Math.max(page - 1, 0);
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        return PageRequest.of(page, size, sort);
    }

    @Override
    public PagedResult<ShortUrlDto> getAllUrls(int page, int size, String search, Boolean isPrivate, String sortBy, String direction) {
        Pageable pageable = getPageable(page, size, sortBy, direction);
        var urls = shortUrlRepo.findAllUrlsForAdmin(search, isPrivate, pageable).map(entityMapper::toShortUrlDto);
        return PagedResult.from(urls);
    }
    @Override
    @Transactional
    public void deleteUrls(List<String> ids) {
        shortUrlRepo.deleteAllById(ids);
    }

    @Override
    public List<AdminUserDto> getAllUsers() {
        List<User> users = userRepo.findAll();
        return users.stream().map(user -> new AdminUserDto(user.getId(), user.getName(), user.getEmail(), user.getRole().name(), user.getCreatedAt(), shortUrlRepo.countByCreatedById(user.getId()), shortUrlRepo.sumClicksByUserId(user.getId()))).toList();
    }

    @Override
    public AdminStatsDto getStats() {
        return new AdminStatsDto(
                userRepo.count(), shortUrlRepo.count(), shortUrlRepo.countByIsPrivateFalse(), shortUrlRepo.countByIsPrivateTrue(), shortUrlRepo.sumAllClicks());
    }
}