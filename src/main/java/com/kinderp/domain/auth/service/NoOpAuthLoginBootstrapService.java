package com.kinderp.domain.auth.service;

import org.springframework.stereotype.Component;

@Component
public class NoOpAuthLoginBootstrapService implements AuthLoginBootstrapService {

    @Override
    public void afterAuthenticated(String email) {
        // no-op
    }
}
