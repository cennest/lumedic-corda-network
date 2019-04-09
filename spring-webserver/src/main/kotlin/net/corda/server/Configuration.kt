package net.corda.server

import com.fasterxml.jackson.databind.Module
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import net.corda.client.jackson.JacksonSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jackson.JsonComponentModule
import org.springframework.context.annotation.Bean


@Configuration
open class Plugin
{
    @Bean
    open fun jsonComponentModule(): Module {
        return JsonComponentModule()
    }

    //Force Spring/Jackson to use only provided Corda ObjectMapper for serialization.
    @Bean
    open fun mappingJackson2HttpMessageConverter(@Autowired rpcConnection: NodeRPCConnection): MappingJackson2HttpMessageConverter {
        val mapper = JacksonSupport.createDefaultMapper(rpcConnection.proxy/*, new JsonFactory(), true*/)
        mapper.registerModule(jsonComponentModule())

        val converter = MappingJackson2HttpMessageConverter()
        converter.objectMapper = mapper
        return converter
    }
}