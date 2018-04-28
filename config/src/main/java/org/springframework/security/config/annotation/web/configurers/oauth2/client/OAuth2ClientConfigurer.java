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
package org.springframework.security.config.annotation.web.configurers.oauth2.client;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.client.endpoint.DefaultAccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.util.Assert;

import java.util.LinkedHashMap;

/**
 * An {@link AbstractHttpConfigurer} for OAuth 2.0 Client support.
 *
 * <p>
 * The following configuration options are available:
 *
 * <ul>
 * <li>{@link #authorizationCodeGrant()} - enables the OAuth 2.0 Authorization Code Grant</li>
 * </ul>
 *
 * <p>
 * Defaults are provided for all configuration options with the only required configuration
 * being {@link #clientRegistrationRepository(ClientRegistrationRepository)}.
 * Alternatively, a {@link ClientRegistrationRepository} {@code @Bean} may be registered instead.
 *
 * <h2>Security Filters</h2>
 *
 * The following {@code Filter}'s are populated when {@link #authorizationCodeGrant()} is configured:
 *
 * <ul>
 * <li>{@link OAuth2AuthorizationRequestRedirectFilter}</li>
 * <li>{@link OAuth2AuthorizationCodeGrantFilter}</li>
 * </ul>
 *
 * <h2>Shared Objects Created</h2>
 *
 * The following shared objects are populated:
 *
 * <ul>
 * <li>{@link ClientRegistrationRepository} (required)</li>
 * <li>{@link OAuth2AuthorizedClientService} (optional)</li>
 * </ul>
 *
 * <h2>Shared Objects Used</h2>
 *
 * The following shared objects are used:
 *
 * <ul>
 * <li>{@link ClientRegistrationRepository}</li>
 * <li>{@link OAuth2AuthorizedClientService}</li>
 * </ul>
 *
 * @author Joe Grandja
 * @since 5.1
 * @see OAuth2AuthorizationRequestRedirectFilter
 * @see OAuth2AuthorizationCodeGrantFilter
 * @see ClientRegistrationRepository
 * @see OAuth2AuthorizedClientService
 * @see AbstractHttpConfigurer
 */
public final class OAuth2ClientConfigurer<B extends HttpSecurityBuilder<B>> extends
	AbstractHttpConfigurer<OAuth2ClientConfigurer<B>, B> {

	private AuthorizationCodeGrantConfigurer authorizationCodeGrantConfigurer;

	/**
	 * Sets the repository of client registrations.
	 *
	 * @param clientRegistrationRepository the repository of client registrations
	 * @return the {@link OAuth2ClientConfigurer} for further configuration
	 */
	public OAuth2ClientConfigurer<B> clientRegistrationRepository(ClientRegistrationRepository clientRegistrationRepository) {
		Assert.notNull(clientRegistrationRepository, "clientRegistrationRepository cannot be null");
		this.getBuilder().setSharedObject(ClientRegistrationRepository.class, clientRegistrationRepository);
		return this;
	}

	/**
	 * Sets the service for authorized client(s).
	 *
	 * @param authorizedClientService the authorized client service
	 * @return the {@link OAuth2ClientConfigurer} for further configuration
	 */
	public OAuth2ClientConfigurer<B> authorizedClientService(OAuth2AuthorizedClientService authorizedClientService) {
		Assert.notNull(authorizedClientService, "authorizedClientService cannot be null");
		this.getBuilder().setSharedObject(OAuth2AuthorizedClientService.class, authorizedClientService);
		return this;
	}

	/**
	 * Returns the {@link AuthorizationCodeGrantConfigurer} for configuring the OAuth 2.0 Authorization Code Grant.
	 *
	 * @return the {@link AuthorizationCodeGrantConfigurer}
	 */
	public AuthorizationCodeGrantConfigurer authorizationCodeGrant() {
		if (this.authorizationCodeGrantConfigurer == null) {
			this.authorizationCodeGrantConfigurer = new AuthorizationCodeGrantConfigurer();
		}
		return this.authorizationCodeGrantConfigurer;
	}

	/**
	 * Configuration options for the OAuth 2.0 Authorization Code Grant.
	 */
	public class AuthorizationCodeGrantConfigurer {
		private final AuthorizationEndpointConfig authorizationEndpointConfig = new AuthorizationEndpointConfig();
		private final TokenEndpointConfig tokenEndpointConfig = new TokenEndpointConfig();

		private AuthorizationCodeGrantConfigurer() {
		}

		/**
		 * Returns the {@link AuthorizationEndpointConfig} for configuring the Authorization Server's Authorization Endpoint.
		 *
		 * @return the {@link AuthorizationEndpointConfig}
		 */
		public AuthorizationEndpointConfig authorizationEndpoint() {
			return this.authorizationEndpointConfig;
		}

		/**
		 * Configuration options for the Authorization Server's Authorization Endpoint.
		 */
		public class AuthorizationEndpointConfig {
			private String authorizationRequestBaseUri;
			private AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository;

			private AuthorizationEndpointConfig() {
			}

			/**
			 * Sets the base {@code URI} used for authorization requests.
			 *
			 * @param authorizationRequestBaseUri the base {@code URI} used for authorization requests
			 * @return the {@link AuthorizationEndpointConfig} for further configuration
			 */
			public AuthorizationEndpointConfig baseUri(String authorizationRequestBaseUri) {
				Assert.hasText(authorizationRequestBaseUri, "authorizationRequestBaseUri cannot be empty");
				this.authorizationRequestBaseUri = authorizationRequestBaseUri;
				return this;
			}

			/**
			 * Sets the repository used for storing {@link OAuth2AuthorizationRequest}'s.
			 *
			 * @param authorizationRequestRepository the repository used for storing {@link OAuth2AuthorizationRequest}'s
			 * @return the {@link AuthorizationEndpointConfig} for further configuration
			 */
			public AuthorizationEndpointConfig authorizationRequestRepository(
				AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository) {

				Assert.notNull(authorizationRequestRepository, "authorizationRequestRepository cannot be null");
				this.authorizationRequestRepository = authorizationRequestRepository;
				return this;
			}

			/**
			 * Returns the {@link AuthorizationCodeGrantConfigurer} for further configuration.
			 *
			 * @return the {@link AuthorizationCodeGrantConfigurer}
			 */
			public AuthorizationCodeGrantConfigurer and() {
				return AuthorizationCodeGrantConfigurer.this;
			}
		}

		/**
		 * Returns the {@link TokenEndpointConfig} for configuring the Authorization Server's Token Endpoint.
		 *
		 * @return the {@link TokenEndpointConfig}
		 */
		public TokenEndpointConfig tokenEndpoint() {
			return this.tokenEndpointConfig;
		}

		/**
		 * Configuration options for the Authorization Server's Token Endpoint.
		 */
		public class TokenEndpointConfig {
			private OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient;

			private TokenEndpointConfig() {
			}

			/**
			 * Sets the client used for requesting the access token credential from the Token Endpoint.
			 *
			 * @param accessTokenResponseClient the client used for requesting the access token credential from the Token Endpoint
			 * @return the {@link TokenEndpointConfig} for further configuration
			 */
			public TokenEndpointConfig accessTokenResponseClient(
				OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient) {

				Assert.notNull(accessTokenResponseClient, "accessTokenResponseClient cannot be null");
				this.accessTokenResponseClient = accessTokenResponseClient;
				return this;
			}

			/**
			 * Returns the {@link AuthorizationCodeGrantConfigurer} for further configuration.
			 *
			 * @return the {@link AuthorizationCodeGrantConfigurer}
			 */
			public AuthorizationCodeGrantConfigurer and() {
				return AuthorizationCodeGrantConfigurer.this;
			}
		}

		/**
		 * Returns the {@link OAuth2ClientConfigurer} for further configuration.
		 *
		 * @return the {@link OAuth2ClientConfigurer}
		 */
		public OAuth2ClientConfigurer<B> and() {
			return OAuth2ClientConfigurer.this;
		}
	}

	@Override
	public void init(B builder) throws Exception {
		if (this.authorizationCodeGrantConfigurer != null) {
			this.init(builder, this.authorizationCodeGrantConfigurer);
		}
	}

	@Override
	public void configure(B builder) throws Exception {
		if (this.authorizationCodeGrantConfigurer != null) {
			this.configure(builder, this.authorizationCodeGrantConfigurer);
		}
	}

	private void init(B builder, AuthorizationCodeGrantConfigurer authorizationCodeGrantConfigurer) throws Exception {
		OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient =
			authorizationCodeGrantConfigurer.tokenEndpointConfig.accessTokenResponseClient;
		if (accessTokenResponseClient == null) {
			accessTokenResponseClient = new DefaultAccessTokenResponseClient();
		}

		OAuth2AuthorizationCodeAuthenticationProvider authorizationCodeAuthenticationProvider =
			new OAuth2AuthorizationCodeAuthenticationProvider(accessTokenResponseClient);
		builder.authenticationProvider(this.postProcess(authorizationCodeAuthenticationProvider));
	}

	private void configure(B builder, AuthorizationCodeGrantConfigurer authorizationCodeGrantConfigurer) throws Exception {
		String authorizationRequestBaseUri = authorizationCodeGrantConfigurer.authorizationEndpointConfig.authorizationRequestBaseUri;
		if (authorizationRequestBaseUri == null) {
			authorizationRequestBaseUri = OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI;
		}

		OAuth2AuthorizationRequestRedirectFilter authorizationRequestFilter = new OAuth2AuthorizationRequestRedirectFilter(
			OAuth2ClientConfigurerUtils.getClientRegistrationRepository(builder), authorizationRequestBaseUri);

		if (authorizationCodeGrantConfigurer.authorizationEndpointConfig.authorizationRequestRepository != null) {
			authorizationRequestFilter.setAuthorizationRequestRepository(
				authorizationCodeGrantConfigurer.authorizationEndpointConfig.authorizationRequestRepository);
		}
		RequestCache requestCache = builder.getSharedObject(RequestCache.class);
		if (requestCache != null) {
			authorizationRequestFilter.setRequestCache(requestCache);
		}
		LinkedHashMap<String, OAuth2AuthorizationRequestUriBuilder> uriBuilders = new LinkedHashMap<>(1);
		uriBuilders.put(DefaultAuthorizationRequestUriBuilder.DEFAULT, new DefaultAuthorizationRequestUriBuilder());
		uriBuilders.putAll(BeanFactoryUtils.
				beansOfTypeIncludingAncestors(builder.getSharedObject(ApplicationContext.class), OAuth2AuthorizationRequestUriBuilder.class));
		authorizationRequestFilter.setUriBuilders(uriBuilders);
		builder.addFilter(this.postProcess(authorizationRequestFilter));

		AuthenticationManager authenticationManager = builder.getSharedObject(AuthenticationManager.class);

		OAuth2AuthorizationCodeGrantFilter authorizationCodeGrantFilter = new OAuth2AuthorizationCodeGrantFilter(
			OAuth2ClientConfigurerUtils.getClientRegistrationRepository(builder),
			OAuth2ClientConfigurerUtils.getAuthorizedClientService(builder),
			authenticationManager);

		if (authorizationCodeGrantConfigurer.authorizationEndpointConfig.authorizationRequestRepository != null) {
			authorizationCodeGrantFilter.setAuthorizationRequestRepository(
				authorizationCodeGrantConfigurer.authorizationEndpointConfig.authorizationRequestRepository);
		}
		builder.addFilter(this.postProcess(authorizationCodeGrantFilter));
	}
}
