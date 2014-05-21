package com.blankrock.backend

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseException
import groovyx.net.http.Method
import org.springframework.web.context.request.RequestContextHolder as RCH

/**
 * Created by Vladimir Havenchyk.
 */
class InteractionService {
    static transactional = false

    def grailsApplication

    final static String CONTENT_TYPE = "application/json; charset=utf-8"
    final static String UNAUTHORIZED_STATUS = '401'

    String makePostRequestToBackend(String path, Map query, Integer iteration = 0) {
        String value
        String status

        (value, status) = postRequest(path, query)

        if (status == UNAUTHORIZED_STATUS && !iteration) {
            value = postRequest(path, query)
        }

        return value
    }

    private List<String> postRequest(String path, Map query) {
        try {
            String baseUrl = grailsApplication.config.blankrock.backend.baseUrl
            path = "/dev${path}"

            def http = new HTTPBuilder(baseUrl)
            def responseValue = ""
            def session = RCH.currentRequestAttributes().session

            AuthParams authParams = AuthParams.findByApiKey('55555')

            RequestGenerator requestGenerator = session['requestGenerator'] as RequestGenerator ?: new RequestGenerator(
                    apiKey: authParams.apiKey, secretKey: authParams.secretKey, url: baseUrl + path
            )

            String responseStatus = ""
            http.request(Method.POST) {
                contentType = CONTENT_TYPE
                uri.path = path
                body = query
                headers["Auth"] = requestGenerator.authParams

                response.success = { resp, reader ->
                    resp.headers.each { h ->
                        log.info "header: ${h.name} : ${h.value}"
                    }

                    requestGenerator.cNumber++
                    responseValue = reader

                    log.info 'response data : '
                    log.info responseValue
                    log.info '----------------'
                }

                response.failure = { resp ->
                    requestGenerator.nounce = resp.headers.nounce
                    requestGenerator.cNumber++
                    responseStatus = resp.status
                    //@todo: use values from bootstrap for now
                    //requestGenerator.apiKey = resp.headers['apiKey']

                    log.error 'request fail'
                }
            }

            session['requestGenerator'] = requestGenerator

            [responseValue, responseStatus]

        } catch (HttpResponseException ex) {
            log.error "Unexpected response error: ${ex.statusCode}"
            log.error ex.cause.message
            return null
        } catch (ConnectException ex) {
            log.error "Unexpected connection error: ${ex.message}"
            return null
        }
    }
}
