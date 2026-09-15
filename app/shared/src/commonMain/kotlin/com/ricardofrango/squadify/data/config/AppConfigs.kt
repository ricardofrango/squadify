package com.ricardofrango.squadify.data.config

import com.ricardofrango.squadify.domain.config.AppConfig

class DevAppConfig : AppConfig {
    override val baseUrl: String = "http://192.168.1.84:8080"
    override val isDebug: Boolean = true
}

class ProdAppConfig : AppConfig {
    override val baseUrl: String = "https://football-manager-production-e1ce.up.railway.app"
    override val isDebug: Boolean = false
}
