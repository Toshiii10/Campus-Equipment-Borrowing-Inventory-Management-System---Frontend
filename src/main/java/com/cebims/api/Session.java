package com.cebims.api;

/** Holds the logged-in user's tokens in memory. Cleared on logout. */
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

    static void setTokens(String access, String refresh) {
        accessToken = access;
        refreshToken = refresh;
    }

    public static void clear() {
        accessToken = refreshToken = role = userId = displayName = null;
    }

    public static void setOnLoggedOut(Runnable r) { onLoggedOut = r; }
    static void fireLoggedOut() { clear(); onLoggedOut.run(); }

    public static String accessToken()  { return accessToken; }
    public static String refreshToken() { return refreshToken; }
    public static String role()         { return role; }
    public static String userId()       { return userId; }
    public static String displayName()  { return displayName; }
    public static boolean loggedIn()    { return accessToken != null; }
    public static boolean isStaff()     { return "Moderator".equalsIgnoreCase(role) || "Admin".equalsIgnoreCase(role); }
    public static boolean isAdmin()     { return "Admin".equalsIgnoreCase(role); }
}