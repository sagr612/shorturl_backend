package com.greatest.shortUrl.model;

import com.greatest.shortUrl.entity.User;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SignUpResponse{
    Boolean isSignedUp;
    User user;
}
