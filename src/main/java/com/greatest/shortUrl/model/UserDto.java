package com.greatest.shortUrl.model;


import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto implements Serializable {
    String id;
    String name;
    String email;
    String role;
    Instant createdAt;
}