package com.cebims.view.dialogs;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.cebims.api.ApiClient;
import com.cebims.view.dialogs.FormDialog.Field;
import com.cebims.view.util.Ui;

import javafx.stage.Window;

/** Everything a visitor can do without being logged in, plus the profile edit form. */
public final class AuthDialogs {
    private AuthDialogs() {}

    private static boolean strongPassword(String p) {
        int bytes = p.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 8 || bytes > 72) return false;
        boolean letter = false, digit = false;
        for (char c : p.toCharArray()) {
            if (Character.isLetter(c)) letter = true;
            else if (Character.isDigit(c)) digit = true;
        }
        return letter && digit;
    }

    /** The emailed link looks like http://.../verify-email?token=XYZ. Accept the link or the bare token. */
    static String token(String linkOrToken) {
        String s = linkOrToken.trim();
        int i = s.indexOf("token=");
        if (i >= 0) {
            s = s.substring(i + 6);
            int end = 0;
            while (end < s.length() && "&# \r\n\t".indexOf(s.charAt(end)) < 0) end++;
            s = s.substring(0, end);
        }
        return s;
    }

    private static String messageOf(ApiClient.Response r) {
        return r.json().path("message").asText("Done.");
    }

    public static void register(Window owner) {
        FormDialog.show(owner, "Create a student account",
                "After registering, open the verification email and use \"Verify email\" on the login screen. "
                        + "You cannot log in until your email is verified.",
                "Register",
                List.of(Field.text("firstName", "First name"), Field.text("lastName", "Last name"),
                        Field.text("studentId", "Student ID (digits)"), Field.text("email", "Email"),
                        Field.text("phoneNum", "Phone (09XXXXXXXXX)"),
                        Field.password("password", "Password"), Field.password("confirm", "Confirm password")),
                null,
                v -> {
                    for (String k : List.of("firstName", "lastName", "studentId", "email", "phoneNum", "password")) {
                        if (v.get(k).isEmpty()) return "Please fill in every field.";
                    }
                    if (!v.get("studentId").matches("\\d{4,20}")) return "Student ID must be 4 to 20 digits.";
                    if (!v.get("email").contains("@")) return "Enter a valid email address.";
                    if (!v.get("phoneNum").matches("09\\d{9}")) return "Phone number must be 11 digits starting with 09.";
                    if (!strongPassword(v.get("password")))
                        return "Password must be 8 to 72 characters with at least one letter and one number.";
                    if (!v.get("password").equals(v.get("confirm"))) return "Passwords do not match.";
                    return null;
                },
                v -> ApiClient.post("/v1/auth/register", Map.of(
                        "firstName", v.get("firstName"), "lastName", v.get("lastName"),
                        "studentId", v.get("studentId"), "email", v.get("email"),
                        "phoneNum", v.get("phoneNum"), "password", v.get("password"))),
                r -> Ui.info("Account created. We sent a verification link to your email.\n"
                        + "Use \"Verify email\" on the login screen, then log in."));
    }

    public static void verifyEmail(Window owner) {
        FormDialog.show(owner, "Verify email",
                "Paste the whole link from the verification email (or just the token). "
                        + "The link points to the website address, so copy it instead of clicking it.",
                "Verify",
                List.of(Field.text("link", "Link or token")), null,
                v -> v.get("link").isEmpty() ? "Paste the link or token from the email." : null,
                v -> ApiClient.post("/v1/auth/verify-email", Map.of("token", token(v.get("link")))),
                r -> Ui.info(messageOf(r) + "\nYou can log in now."));
    }

    public static void resendVerification(Window owner) {
        FormDialog.show(owner, "Resend verification link",
                "Enter the email you registered with. If it is not verified yet, we send a new link.",
                "Send link",
                List.of(Field.text("email", "Email")), null,
                v -> v.get("email").contains("@") ? null : "Enter a valid email address.",
                v -> ApiClient.post("/v1/auth/resend-verification", Map.of("email", v.get("email"))),
                r -> Ui.info(messageOf(r)));
    }

    public static void forgotPassword(Window owner) {
        FormDialog.show(owner, "Forgot password",
                "Enter your account email. If it exists, we send a reset link that is valid for 60 minutes.",
                "Send link",
                List.of(Field.text("email", "Email")), null,
                v -> v.get("email").contains("@") ? null : "Enter a valid email address.",
                v -> ApiClient.post("/v1/auth/forgot-password", Map.of("email", v.get("email"))),
                r -> Ui.info(messageOf(r) + "\nThen use \"Reset password\" with the link from the email."));
    }

    public static void resetPassword(Window owner) {
        FormDialog.show(owner, "Reset password",
                "Paste the whole link from the reset email (or just the token) and choose a new password.",
                "Reset password",
                List.of(Field.text("link", "Link or token"),
                        Field.password("password", "New password"), Field.password("confirm", "Confirm password")),
                null,
                v -> {
                    if (v.get("link").isEmpty()) return "Paste the link or token from the email.";
                    if (!strongPassword(v.get("password")))
                        return "Password must be 8 to 72 characters with at least one letter and one number.";
                    if (!v.get("password").equals(v.get("confirm"))) return "Passwords do not match.";
                    return null;
                },
                v -> ApiClient.post("/v1/auth/reset-password",
                        Map.of("token", token(v.get("link")), "newPassword", v.get("password"))),
                r -> Ui.info(messageOf(r) + "\nYou can log in with the new password."));
    }

    // ---- profile edit (own profile, or a student's profile for staff) -----------------------

    /** Splits "Maria Dela Cruz" into ["Maria Dela", "Cruz"] (the staff views only get the full name). */
    public static String[] splitName(String full) {
        int i = full.lastIndexOf(' ');
        return i < 0 ? new String[]{full, ""} : new String[]{full.substring(0, i), full.substring(i + 1)};
    }

    public static void editProfile(Window owner, String title, String intro, String path,
                                   String first, String last, String phone, Runnable onSaved) {
        FormDialog.show(owner, title, intro, "Save",
                List.of(Field.text("firstName", "First name"), Field.text("lastName", "Last name"),
                        Field.text("phoneNum", "Phone (09XXXXXXXXX)")),
                Map.of("firstName", first, "lastName", last, "phoneNum", phone),
                v -> {
                    if (v.get("firstName").isEmpty() || v.get("lastName").isEmpty()) return "Name cannot be empty.";
                    if (!v.get("phoneNum").matches("09\\d{9}")) return "Phone number must be 11 digits starting with 09.";
                    return null;
                },
                v -> ApiClient.put(path, Map.of("firstName", v.get("firstName"),
                        "lastName", v.get("lastName"), "phoneNum", v.get("phoneNum"))),
                r -> { Ui.info("Profile saved."); if (onSaved != null) onSaved.run(); });
    }
}

