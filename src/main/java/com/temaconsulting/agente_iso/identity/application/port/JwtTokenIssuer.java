package com.temaconsulting.agente_iso.identity.application.port;

import com.temaconsulting.agente_iso.identity.api.dto.TokenResponse;
import com.temaconsulting.agente_iso.identity.domain.User;

public interface JwtTokenIssuer {

    TokenResponse issue(User user);
}
