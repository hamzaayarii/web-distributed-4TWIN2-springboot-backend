package tn.esprit.microservice.productservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;

@Service
public class TokenService {

    @Value("${ebay.client.id}")
    private String clientId;

    @Value("${ebay.client.secret}")
    private String clientSecret;

    private String accessToken;

    // Method to fetch the token
    public String getAccessToken() {
        // If credentials are not set properly, return a dummy token
        if ("default".equals(clientId) || "default".equals(clientSecret)) {
            System.out.println("Running in dev mode - skipping real eBay token fetch");
            return "dummy-dev-token";
        }

        // Otherwise, implement the actual token logic
        if (accessToken == null) {
            // fetchAccessToken(); ← uncomment this if you implement real logic later
            throw new RuntimeException("Access token logic not implemented");
        }

        return accessToken;
    }

    @Scheduled(fixedRate = 3600000)  // 3600000 ms = 1 hour
    public void refreshAccessToken() {
        try {
            fetchAccessToken();
            System.out.println("Access token refreshed !");
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            System.err.println("Error fetching access token: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
        }
    }


    private void fetchAccessToken() {
        String url = "https://api.sandbox.ebay.com/identity/v1/oauth2/token";

        String auth = "Basic " + encodeCredentials(clientId, clientSecret);

        // Log the Authorization header to check it's correctly set
        System.out.println("Authorization Header: " + auth);

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", auth);
        headers.set("Content-Type", "application/x-www-form-urlencoded");

        // Log the headers to ensure they are correctly set
        System.out.println("Request Headers: " + headers);

        // Create the form body for the request
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope","https://api.ebay.com/oauth/api_scope");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);


            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonResponse = parseJsonResponse(response.getBody());
                this.accessToken = jsonResponse.get("access_token").asText();
            } else {
                throw new RuntimeException("Failed to fetch access token");
            }
        } catch (Exception e) {
            System.out.println("Error fetching access token: " + e.getMessage());
            throw new RuntimeException("Failed to fetch access token", e);
        }
    }

    // method to encode client credentials to Basic Auth format
    private String encodeCredentials(String clientId, String clientSecret) {
        return Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
    }

    // Parse the JSON response to extract the access token
    private JsonNode parseJsonResponse(String jsonResponse) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readTree(jsonResponse);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing token response", e);
        }
    }
}



