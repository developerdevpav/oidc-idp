package by.devpav.kotlin.oidcidp.web.rest.controller

import by.devpav.kotlin.oidcidp.extencions.findCookieByName
import by.devpav.kotlin.oidcidp.extencions.findCookieValueByName
import by.devpav.kotlin.oidcidp.records.I18Codes
import by.devpav.kotlin.oidcidp.web.rest.exceptions.I18RestException
import by.devpav.kotlin.oidcidp.web.rest.facade.AccountRegistryFacade
import by.devpav.kotlin.oidcidp.web.rest.mapper.AccountRegistrationCookieBuilder
import by.devpav.kotlin.oidcidp.web.rest.model.accounts.registration.AccountOtpConfirm
import by.devpav.kotlin.oidcidp.web.rest.model.accounts.registration.AccountOtpRefresh
import by.devpav.kotlin.oidcidp.web.rest.model.accounts.registration.AccountRegistration
import by.devpav.kotlin.oidcidp.web.rest.model.accounts.registration.AccountRegistrationResult
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

private const val REGISTRATION_COOKIE_NAME = "registrationId"
private const val OTP_EXP_TIME_COOKIE_NAME = "otp_exp_time"

@RestController
@RequestMapping("/api/accounts")
@PreAuthorize("isAnonymous()")
class RegistryAccountController(
    private val accountRegistryFacade: AccountRegistryFacade,
    private val accountRegistrationCookieBuilder: AccountRegistrationCookieBuilder
) {

    @PostMapping(value = ["/signup"])
    fun signup(@RequestBody userRegistration: @Valid AccountRegistration, request: HttpServletRequest, response: HttpServletResponse): AccountRegistrationResult {
        userRegistration.registrationId = request.findCookieValueByName(REGISTRATION_COOKIE_NAME)

        val accountRegistrationResult = accountRegistryFacade.registry(userRegistration)

        accountRegistrationResult.apply {
            response.addCookie(accountRegistrationCookieBuilder.buildId(this.registrationId))
            response.addCookie(accountRegistrationCookieBuilder.buildOtpExpiredAt(this.otpExpiredTime))
        }

        return accountRegistrationResult
    }

    @PostMapping(value = ["/confirm"])
    fun confirmSignup(@RequestBody confirmAccount: AccountOtpConfirm, request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val registrationCookie = request.findCookieByName(REGISTRATION_COOKIE_NAME)
            ?: throw I18RestException(
                message = "Cookie with name [registrationId] is not present in the request",
                i18Code = I18Codes.I18AccountRegistryCodes.ACCOUNT_DATA_NOT_FOUND
            )

        val confirmation = accountRegistryFacade.confirmByOtp(registrationCookie.value, confirmAccount.code)

        if (confirmation) {
            val dropRegistrationCookie = registrationCookie.apply {
                maxAge = 0
            }

            response.addCookie(dropRegistrationCookie)

            val dropOtpExpTimeCookie = request.findCookieByName(OTP_EXP_TIME_COOKIE_NAME)

            if (dropOtpExpTimeCookie != null) {
                dropOtpExpTimeCookie.apply {
                    maxAge = 0
                }

                response.addCookie(dropOtpExpTimeCookie)
            }

        }

        return confirmation
    }

    @PutMapping(value = ["/otp"])
    fun updateOtp(request: HttpServletRequest, response: HttpServletResponse): AccountOtpRefresh {
        val registrationId = request.findCookieValueByName(REGISTRATION_COOKIE_NAME)
            ?: throw I18RestException(
                message = "Cookie with name [registrationId] is not present in the request",
                i18Code = I18Codes.I18AccountRegistryCodes.ACCOUNT_DATA_NOT_FOUND
            )

        val refreshOtp = accountRegistryFacade.refreshOtp(registrationId)

        response.addCookie(accountRegistrationCookieBuilder.buildOtpExpiredAt(refreshOtp.otpExpTime))

        return refreshOtp
    }

}
