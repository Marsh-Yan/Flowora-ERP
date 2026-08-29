package com.flowora.erp.identity;

public interface IdentityAuthenticator {
    FloworaPrincipal authenticate(String username, String password);
}
