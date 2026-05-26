package com.greatest.shortUrl.services;

import com.greatest.shortUrl.model.AdminStatsDto;
import com.greatest.shortUrl.model.AdminUserDto;
import com.greatest.shortUrl.model.PagedResult;
import com.greatest.shortUrl.model.ShortUrlDto;

import java.util.List;

public interface AdminService {

    PagedResult<ShortUrlDto> getAllUrls(

            int page,

            int size,

            String search,

            Boolean isPrivate,

            String sortBy,

            String direction
    );

    void deleteUrls(List<String> ids);

    List<AdminUserDto> getAllUsers();

    AdminStatsDto getStats();
}