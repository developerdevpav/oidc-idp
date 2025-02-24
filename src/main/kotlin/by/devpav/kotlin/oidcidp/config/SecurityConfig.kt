package by.devpav.kotlin.oidcidp.config

import by.devpav.kotlin.oidcidp.config.csrf.CsrfCookieFilter
import by.devpav.kotlin.oidcidp.config.csrf.SpaCsrfTokenRequestHandler
import by.devpav.kotlin.oidcidp.config.csrf.properties.CsrfCookieProperties
import by.devpav.kotlin.oidcidp.config.entrypoints.JsonUnauthorizedAuthenticationEntryPoint
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseCookie.ResponseCookieBuilder
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy
import org.springframework.security.web.csrf.CsrfTokenRequestHandler
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

@EnableMethodSecurity
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity(debug = true)
@Profile("development")
class SecurityDevelopmentConfig {

    @Bean
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorizeRequests ->
                authorizeRequests.requestMatchers("/h2-console/**", "**.css", "**.js", "**,jsp", "/graphiql/**", "/graphql/**").permitAll()
                    .anyRequest().authenticated()
            }
            .csrf { csrf -> csrf.disable() }
            .cors { cors -> cors.disable() }
            .formLogin(Customizer.withDefaults())

        return http.build()
    }

}


@EnableMethodSecurity
@Configuration(proxyBeanMethods = true)
@EnableWebSecurity(debug = true)
@Profile("production", "sandbox")
class SecurityProductionConfig {

    @Autowired
    private lateinit var jsonUnauthorizedAuthenticationEntryPoint: JsonUnauthorizedAuthenticationEntryPoint

    @Autowired
    private lateinit var csrfCookieProperties: CsrfCookieProperties

    @Bean
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorizeRequests -> authorizeRequests.anyRequest().authenticated() }
            .csrf { csrfSpec -> csrfConfigurer(csrfSpec) }
            .addFilterAfter(CsrfCookieFilter(), BasicAuthenticationFilter::class.java) // csrf filter for SPA
            .cors { cors -> cors.disable() }
            .exceptionHandling { exceptionHandling ->
                exceptionHandling.authenticationEntryPoint(jsonUnauthorizedAuthenticationEntryPoint)
            }
            .formLogin(Customizer.withDefaults())

        return http.build()
    }

    private fun csrfConfigurer(csrfConfigurer: CsrfConfigurer<HttpSecurity>) {
        val cookieCsrfTokenRepository = getCookieCsrfTokenRepository(csrfCookieProperties)
        val csrfAuthenticationStrategy = CsrfAuthenticationStrategy(cookieCsrfTokenRepository)
        val spaCsrfTokenRequestHandler = SpaCsrfTokenRequestHandler(XorCsrfTokenRequestAttributeHandler())

        csrfConfigurer
            .ignoringRequestMatchers(
                AntPathRequestMatcher.antMatcher(HttpMethod.GET),
                AntPathRequestMatcher.antMatcher(HttpMethod.OPTIONS)
            )
            .csrfTokenRepository(cookieCsrfTokenRepository)
            .csrfTokenRequestHandler(spaCsrfTokenRequestHandler)
            .sessionAuthenticationStrategy(csrfAuthenticationStrategy)
    }

    private fun getCookieCsrfTokenRepository(csrfCookieProperties: CsrfCookieProperties): CookieCsrfTokenRepository {
        val cookieCsrfTokenRepository = CookieCsrfTokenRepository()

        cookieCsrfTokenRepository.cookiePath = csrfCookieProperties.path
        cookieCsrfTokenRepository.setCookieName(csrfCookieProperties.name)

        cookieCsrfTokenRepository.setCookieCustomizer { cookieBuilder: ResponseCookieBuilder ->
            cookieBuilder.httpOnly(csrfCookieProperties.httpOnly)
            cookieBuilder.domain(csrfCookieProperties.domain)
            cookieBuilder.secure(csrfCookieProperties.secure)
        }

        return cookieCsrfTokenRepository
    }

    @Bean
    fun spaCsrfTokenRequestHandler(): CsrfTokenRequestHandler {
        return SpaCsrfTokenRequestHandler(XorCsrfTokenRequestAttributeHandler())
    }

}
