package com.temaconsulting.agente_iso.integration.infrastructure;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.temaconsulting.agente_iso.integration.domain.SourceHealth;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
class AdcDriveAccessTokenProvider implements DriveAccessTokenProvider {
    private static final String METADATA_READONLY =
            "https://www.googleapis.com/auth/drive.metadata.readonly";

    private GoogleCredentials credentials;

    @Override
    public synchronized String accessToken() {
        try {
            if (credentials == null) {
                credentials = GoogleCredentials.getApplicationDefault()
                        .createScoped(List.of(METADATA_READONLY));
            }
            credentials.refreshIfExpired();
            AccessToken token = credentials.getAccessToken();
            if (token == null || token.getTokenValue() == null || token.getTokenValue().isBlank()) {
                throw new IOException("Google Drive access token is unavailable");
            }
            return token.getTokenValue();
        } catch (IOException | RuntimeException ex) {
            throw new DriveReadException(SourceHealth.AUTH_ERROR, ex);
        }
    }
}
