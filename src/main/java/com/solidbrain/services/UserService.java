package com.solidbrain.services;

import com.getbase.Client;
import com.getbase.models.Contact;
import com.getbase.models.User;
import com.getbase.services.UsersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Created by Krzysztof Wilk on 17/10/2016.
 */

@Service
public class UserService {

    private Client baseClient;

    @Autowired
    public UserService(Client client) {
        this.baseClient = client;
    }

    private User getUserById(final long userId) {
        return baseClient.users()
                .get(userId);
    }

    Optional<User> getUserByEmail(final String email) {
        return baseClient.users()
                .list(new UsersService.SearchCriteria().email(email))
                .stream()
                .findFirst();
    }

    User getContactOwner(Contact contact) {
        return getUserById(contact.getOwnerId());
    }
}
