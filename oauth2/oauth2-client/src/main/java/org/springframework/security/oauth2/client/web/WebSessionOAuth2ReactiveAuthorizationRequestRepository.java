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

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of an {@link ReactiveAuthorizationRequestRepository} that stores
 * {@link OAuth2AuthorizationRequest} in the {@code WebSession}.
 *
 * @author Rob Winch
 * @since 5.1
 * @see AuthorizationRequestRepository
 * @see OAuth2AuthorizationRequest
 */
public final class WebSessionOAuth2ReactiveAuthorizationRequestRepository implements ReactiveAuthorizationRequestRepository<OAuth2AuthorizationRequest> {

	private static final String DEFAULT_AUTHORIZATION_REQUEST_ATTR_NAME =
			WebSessionOAuth2ReactiveAuthorizationRequestRepository.class.getName() +  ".AUTHORIZATION_REQUEST";

	private final String sessionAttributeName = DEFAULT_AUTHORIZATION_REQUEST_ATTR_NAME;

	@Override
	public Mono<Void> saveAuthorizationRequest(
			OAuth2AuthorizationRequest authorizationRequest, ServerWebExchange exchange, ClientRegistration clientRegistration) {
		Assert.notNull(authorizationRequest, "authorizationRequest cannot be null");
		return getStateToAuthorizationRequest(exchange, true)
				.doOnNext(stateToAuthorizationRequest -> stateToAuthorizationRequest.put(authorizationRequest.getState(), authorizationRequest))
				.then();
	}

	@Override
	public Mono<OAuth2AuthorizationRequest> removeAuthorizationRequest(
			ServerWebExchange exchange, ClientRegistration clientRegistration) {
		String state = getStateParameter(exchange, clientRegistration);
		if (state == null) {
			return Mono.empty();
		}
		return exchange.getSession()
			.map(WebSession::getAttributes)
			.handle((sessionAttrs, sink) -> {
				Map<String, OAuth2AuthorizationRequest> stateToAuthzRequest = sessionAttrsMapStateToAuthorizationRequest(sessionAttrs);
				if (stateToAuthzRequest == null) {
					sink.complete();
					return;
				}
				OAuth2AuthorizationRequest removedValue = stateToAuthzRequest.remove(state);
				if (stateToAuthzRequest.isEmpty()) {
					sessionAttrs.remove(this.sessionAttributeName);
				}
				if (removedValue != null)
					sink.next(removedValue);
			});
	}

	/**
	 * Gets the state parameter from the {@link ServerHttpRequest}
	 * @param exchange the exchange to use
	 * @return the state parameter or null if not found
	 */
	private String getStateParameter(ServerWebExchange exchange, ClientRegistration clientRegistration) {
		Assert.notNull(exchange, "exchange cannot be null");
		return exchange.getRequest().getQueryParams().getFirst(clientRegistration.getProviderDetails().getStateAttributeName());
	}

	private Mono<Map<String, Object>> getSessionAttributes(ServerWebExchange exchange) {
		return exchange.getSession().map(WebSession::getAttributes);
	}

	private Mono<Map<String, OAuth2AuthorizationRequest>> getStateToAuthorizationRequest(ServerWebExchange exchange, boolean create) {
		Assert.notNull(exchange, "exchange cannot be null");

		return getSessionAttributes(exchange)
			.doOnNext(sessionAttrs -> {
				if (create) {
					sessionAttrs.putIfAbsent(this.sessionAttributeName, new HashMap<String, OAuth2AuthorizationRequest>());
				}
			})
			.flatMap(sessionAttrs -> Mono.justOrEmpty(this.sessionAttrsMapStateToAuthorizationRequest(sessionAttrs)));
	}

	private Map<String, OAuth2AuthorizationRequest> sessionAttrsMapStateToAuthorizationRequest(Map<String, Object> sessionAttrs) {
		return (Map<String, OAuth2AuthorizationRequest>) sessionAttrs.get(this.sessionAttributeName);
	}
}
