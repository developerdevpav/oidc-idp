package by.devpav.kotlin.oidcidp.service.impl

import by.devpav.kotlin.oidcidp.graphql.Session
import by.devpav.kotlin.oidcidp.graphql.UserSession
import by.devpav.kotlin.oidcidp.graphql.services.SessionService
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder

@Service
class DefaultSessionService(private val sessionRepository: FindByIndexNameSessionRepository<out org.springframework.session.Session>) : SessionService {

    override fun getAllByUsername(username: String): List<Session> =
        sessionRepository.findByPrincipalName(username)
            .mapNotNull { entry -> entry.value }
            .map { session ->  buildUserSession(RequestContextHolder.currentRequestAttributes().sessionId, session) }
            .toList()

    private fun <T : org.springframework.session.Session> buildUserSession(activeId: String, session: T): Session {
        return Session()
            .apply {
                id = session.id
                createdAt = session.creationTime.toEpochMilli()
                active = activeId.equals(session.id, ignoreCase = true)
            }
    }

}