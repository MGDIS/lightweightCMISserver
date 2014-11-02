package org.apache.chemistry.opencmis.utils;

import java.util.Collection;

import org.apache.chemistry.opencmis.commons.server.CallContext;

public interface IUserManager {

    /**
     * Returns all logins.
     */
    public abstract Collection<String> getLogins();

    /**
     * Adds a login.
     */
    public abstract void addLogin(String username, String password);

    /**
     * Takes user and password from the CallContext and checks them.
     */
    public abstract String authenticate(CallContext context);

}