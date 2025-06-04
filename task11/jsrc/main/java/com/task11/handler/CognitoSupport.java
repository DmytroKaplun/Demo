package com.task11.handler;

import org.json.JSONObject;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRespondToAuthChallengeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeliveryMediumType;
import java.util.Map;
import java.util.regex.Pattern;

public abstract class CognitoSupport {
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[$%^\\*\\-_])[A-Za-z\\d$%^\\*\\-_]{12,}$");
    public static final String EMAIL = "email";
    public static final String PASSWORD = "password";
    private final String userPoolId = System.getenv("COGNITO_ID");
    private final String clientId = System.getenv("CLIENT_ID");
    private final CognitoIdentityProviderClient cognitoClient;

    protected CognitoSupport(CognitoIdentityProviderClient cognitoClient) {
        this.cognitoClient = cognitoClient;
    }

    protected AdminInitiateAuthResponse cognitoSignIn(String email, String password) {
        Map<String, String> authParams = Map.of(
                "EMAIL", email,
                "PASSWORD", password
        );

        return cognitoClient.adminInitiateAuth(AdminInitiateAuthRequest.builder()
                .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                .authParameters(authParams)
                .userPoolId(userPoolId)
                .clientId(clientId)
                .build());
    }

    protected void cognitoSignUp(JSONObject requestBody) {
        String email = requestBody.getString("email");
        String password = requestBody.getString("password");

        cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
                        .userPoolId(userPoolId)
                        .username(email)
                        .temporaryPassword(password)
                        .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
                        .messageAction("SUPPRESS")
                        .forceAliasCreation(Boolean.FALSE)
                        .build()
        );
    }

    protected void confirmSignUp(JSONObject requestBody) {
        String email = requestBody.getString("email");
        String password = requestBody.getString("password");
        AdminInitiateAuthResponse adminInitiateAuthResponse = cognitoSignIn(email, password);


        if (!ChallengeNameType.NEW_PASSWORD_REQUIRED.name().equals(adminInitiateAuthResponse.challengeNameAsString())) {
            throw new RuntimeException("unexpected challenge: " + adminInitiateAuthResponse.challengeNameAsString());
        }

        Map<String, String> challengeResponses = Map.of(
                "USERNAME", email,
                "PASSWORD", password,
                "NEW_PASSWORD", password
        );

        cognitoClient.adminRespondToAuthChallenge(AdminRespondToAuthChallengeRequest.builder()
                .challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
                .challengeResponses(challengeResponses)
                .userPoolId(userPoolId)
                .clientId(clientId)
                .session(adminInitiateAuthResponse.session())
                .build());
    }

    protected void validateRequestBody(JSONObject requestBody) {
        if (!requestBody.has(EMAIL) || requestBody.getString(EMAIL).isEmpty()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (!requestBody.has(PASSWORD) || requestBody.getString(PASSWORD).isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }

        // Validate email format
        String email = requestBody.getString(EMAIL);
        if (!email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email format.");
        }

        // Validate password format
        String password = requestBody.getString(PASSWORD);
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("Password must be at least 12 characters long, include letters, numbers," +
                    " and at least one of the following special characters: $%^*-_");
        }
    }

}
