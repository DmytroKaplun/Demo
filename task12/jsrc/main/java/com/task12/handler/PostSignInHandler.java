package com.task12.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import static com.task12.ApiHandler.createResponse;

public class PostSignInHandler extends CognitoSupport implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    public PostSignInHandler(CognitoIdentityProviderClient cognitoClient) {
        super(cognitoClient);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        try {
            JSONObject requestBody = new JSONObject(requestEvent.getBody());
            validateRequestBody(requestBody);

            String email = requestBody.getString("email");
            String password = requestBody.getString("password");

            String idToken = cognitoSignIn(email, password)
                    .authenticationResult()
                    .idToken();

            JSONObject responseBody = new JSONObject();
            responseBody.put("idToken", idToken);

            return createResponse(200, responseBody.toString());
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createResponse(400, "There was an error in the request.");
        }
    }
}