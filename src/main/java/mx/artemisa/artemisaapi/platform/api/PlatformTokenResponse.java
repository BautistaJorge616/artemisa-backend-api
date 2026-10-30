package mx.artemisa.artemisaapi.platform.api;

public record PlatformTokenResponse(String accessToken, String tokenType, long expiresIn) { }
