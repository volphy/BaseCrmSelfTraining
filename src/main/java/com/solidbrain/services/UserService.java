package com.solidbrain.services;

import com.getbase.Client;
import com.getbase.models.User;
import com.getbase.services.UsersService;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Created by Krzysztof Wilk on 17/10/2016.
 */

@Slf4j
public class UserService {

    private Client baseClient;

    public UserService(Client client) {
        this.baseClient = client;
    }

    public User getUserById(final long userId) {
        return baseClient.users()
                .get(userId);
    }

    public Optional<User> getUserByEmail(final String email) {
        return baseClient.users()
                .list(new UsersService.SearchCriteria().email(email))
                .stream()
                .findFirst();
    }
}
