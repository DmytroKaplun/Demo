package com.task12.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import static com.task12.ApiHandler.createResponse;

public class PostSignUpHandler extends CognitoSupport implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    public PostSignUpHandler(CognitoIdentityProviderClient cognitoClient) {
        super(cognitoClient);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        try {
            JSONObject requestBody = new JSONObject(requestEvent.getBody());
            validateRequestBody(requestBody);

            cognitoSignUp(requestBody);
            confirmSignUp(requestBody);

            return createResponse(200, "Sign-up process is successful");
        } catch (Exception e) {
            String error = new JSONObject().put("error", e.getStackTrace()).toString();
            return createResponse(400, error);
        }
    }
}
