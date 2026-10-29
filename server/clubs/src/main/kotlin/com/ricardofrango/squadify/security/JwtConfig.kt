package com.ricardofrango.squadify.security

import com.ricardofrango.squadify.config.ServerConfig

// The clubs server's tokens. The secret was previously a hardcoded literal (anyone with repo read
// access could forge tokens for any user); it's ServerConfig's JWT_SECRET, which the server refuses to
// start in production without. See "issues to fix.md" -- Critical -- security.
object JwtConfig : JwtTokens(ServerConfig.jwtSecret, audience = "club-manager-users")
