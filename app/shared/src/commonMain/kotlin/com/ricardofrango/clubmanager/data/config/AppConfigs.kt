package com.ricardofrango.clubmanager.data.config

import com.ricardofrango.clubmanager.domain.config.AppConfig

class DevAppConfig : AppConfig {
    override val baseUrl: String = "http://192.168.1.84:8080"
    override val isDebug: Boolean = true
}

class ProdAppConfig : AppConfig {
    // For now the same address as requested
    override val baseUrl: String = "http://192.168.1.84:8080"
    override val isDebug: Boolean = false
}
