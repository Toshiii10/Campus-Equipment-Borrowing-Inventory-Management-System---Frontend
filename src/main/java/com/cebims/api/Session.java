package com.cebims.api;

/** Holds the logged-in user's CEBIMS session tokens in memory. Cleared on logout. */
public final class Session {
    private static String accessToken, refreshToken, role, userId, displayName;
    private static Runnable onLoggedOut = () -> {};

    private Session() {}

    public static void set(String access, String refresh, String role, String userId, String displayName) {
        accessToken = access;
        refreshToken = refresh;
        Session.role = role;
        Session.userId = userId;
        Session.displayName = displayName;
    }

    /** Called by ApiClient after a refresh succeeds: new token pair, same user. */
    static void setTokens(String access, String refresh) {
        accessToken = access;
        refreshToken = refresh;
    }

    public static void clear() {
        accessToken = refreshToken = role = userId = displayName = null;
    }

    /** Registered by App: what to do when the session can no longer be refreshed. */
    public static void setOnLoggedOut(Runnable r) { onLoggedOut = r; }
    static void fireLoggedOut() { clear(); onLoggedOut.run(); }

    public static String accessToken()  { return accessToken; }
    public static String refreshToken() { return refreshToken; }
    public static String role()         { return role; }
    public static String userId()       { return userId; }
    public static String displayName()  { return displayName; }
    public static boolean loggedIn()    { return accessToken != null; }
    public static boolean isStaff()     { return "MODERATOR".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role); }
}