package com.greatest.shortUrl.services;

import com.greatest.shortUrl.entitiy.ShortUrl;
import com.greatest.shortUrl.entitiy.User;
import com.greatest.shortUrl.model.CachedShortUrlDto;
import com.greatest.shortUrl.model.ShortUrlDto;
import com.greatest.shortUrl.model.UserDto;
import org.springframework.stereotype.Component;

@Component
public class EntityMapper {
    public ShortUrlDto toShortUrlDto(ShortUrl shortUrl) {
        UserDto userDto = null;
        if (shortUrl.getCreatedBy() != null) {
            userDto = toUserDto(shortUrl.getCreatedBy());
        }


        return ShortUrlDto.builder().id(shortUrl.getId()).shortKey(shortUrl.getShortKey()).originalUrl(shortUrl.getOriginalUrl()).isPrivate(shortUrl.getIsPrivate()).expiresAt(shortUrl.getExpiresAt()).createdBy(userDto).clickCount(shortUrl.getClickCount()).createdAt(shortUrl.getCreatedAt()).status(shortUrl.getStatus()).build();
    }

    public UserDto toUserDto(User user) {

        return UserDto.builder().id(user.getId()).name(user.getName()).email(user.getEmail()).role(String.valueOf(user.getRole())).createdAt(user.getCreatedAt()).build();
    }

    public CachedShortUrlDto toCachedShortUrlDto(ShortUrl shortUrl) {
        UserDto userDto = null;
        if (shortUrl.getCreatedBy() != null) {
            userDto = toUserDto(shortUrl.getCreatedBy());
        }

        return CachedShortUrlDto.builder().id(shortUrl.getId()).shortKey(shortUrl.getShortKey()).originalUrl(shortUrl.getOriginalUrl()).isPrivate(shortUrl.getIsPrivate()).expiresAt(shortUrl.getExpiresAt()).status(shortUrl.getStatus()).clickCount(shortUrl.getClickCount()).createdById(userDto.getId()).build();

    }
}
