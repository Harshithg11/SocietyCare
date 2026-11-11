package com.societycare.security;

import com.societycare.util.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Clean JSON 401 for missing, invalid or expired tokens. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorResponseWriter.write(response, HttpStatus.UNAUTHORIZED,
                "Authentication is required. Provide a valid Bearer token.", request.getRequestURI());
    }
}
