/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.security.oauth2.client.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.BiFunction;


/**
 * Converts from a {@link ServerWebExchange} to an {@link OAuth2LoginAuthenticationToken} that can be authenticated. The
 * converter does not validate any errors it only performs a conversion.
 *
 * @author Rob Winch
 * @see org.springframework.security.web.server.authentication.AuthenticationWebFilter#setAuthenticationConverter(BiFunction)
 * @since 5.1
 */
public class ServerOAuth2LoginAuthenticationTokenConverter implements
		BiFunction<ServerWebExchange, Map<String, Object>, Mono<Authentication>> {

	static final String AUTHORIZATION_REQUEST_NOT_FOUND_ERROR_CODE = "authorization_request_not_found";

	static final String CLIENT_REGISTRATION_NOT_FOUND_ERROR_CODE = "client_registration_not_found";

	private ReactiveAuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository =
			new WebSessionOAuth2ReactiveAuthorizationRequestRepository();

	private final ReactiveClientRegistrationRepository clientRegistrationRepository;

	public ServerOAuth2LoginAuthenticationTokenConverter(
			ReactiveClientRegistrationRepository clientRegistrationRepository) {
		Assert.notNull(clientRegistrationRepository, "clientRegistrationRepository cannot be null");
		this.clientRegistrationRepository = clientRegistrationRepository;
	}

	/**
	 * Sets the {@link ReactiveAuthorizationRequestRepository} to be used. The default is
	 * {@link WebSessionOAuth2ReactiveAuthorizationRequestRepository}.
	 *
	 * @param authorizationRequestRepository the repository to use.
	 */
	public void setAuthorizationRequestRepository(
			ReactiveAuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository) {
		Assert.notNull(authorizationRequestRepository, "authorizationRequestRepository cannot be null");
		this.authorizationRequestRepository = authorizationRequestRepository;
	}

	@Override
	public Mono<Authentication> apply(ServerWebExchange serverWebExchange, Map<String, Object> variables) {
		if (variables == null) {
			return oauth2AuthenticationException(CLIENT_REGISTRATION_NOT_FOUND_ERROR_CODE);
		}
		String registrationId = (String) variables.get("registrationId");
		return this.clientRegistrationRepository.findByRegistrationId(registrationId)
				.switchIfEmpty(oauth2AuthenticationException(CLIENT_REGISTRATION_NOT_FOUND_ERROR_CODE))
				.flatMap(clientRegistration ->
						this.authorizationRequestRepository.removeAuthorizationRequest(serverWebExchange, clientRegistration)
								.switchIfEmpty(oauth2AuthenticationException(AUTHORIZATION_REQUEST_NOT_FOUND_ERROR_CODE))
								.flatMap(authorizationRequest -> authenticationRequest(serverWebExchange, authorizationRequest, clientRegistration))
				);
	}

	private <T> Mono<T> oauth2AuthenticationException(String errorCode) {
		return Mono.defer(() -> {
			OAuth2Error oauth2Error = new OAuth2Error(errorCode);
			return Mono.error(new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString()));
		});
	}

	private Mono<OAuth2LoginAuthenticationToken> authenticationRequest(ServerWebExchange exchange, OAuth2AuthorizationRequest authorizationRequest, ClientRegistration clientRegistration) {
		return Mono.just(authorizationRequest)
				.map(request -> {
					OAuth2AuthorizationResponse authorizationResponse = convert(exchange, clientRegistration);
					OAuth2LoginAuthenticationToken authenticationRequest = new OAuth2LoginAuthenticationToken(
							clientRegistration, new OAuth2AuthorizationExchange(request, authorizationResponse));
					return authenticationRequest;
				});
	}

	private static OAuth2AuthorizationResponse convert(ServerWebExchange exchange, ClientRegistration clientRegistration) {
		MultiValueMap<String, String> queryParams = exchange.getRequest()
				.getQueryParams();
		String redirectUri = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
				.query(null)
				.build()
				.toUriString();

		return OAuth2AuthorizationResponseUtils.convert(queryParams, redirectUri, clientRegistration);
	}
}
