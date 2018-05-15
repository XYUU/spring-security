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

package org.springframework.security.oauth2.client.authentication;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


/**
 * @author Rob Winch
 * @since 5.1
 */
@RunWith(MockitoJUnitRunner.class)
public class OAuth2LoginReactiveAuthenticationManagerTests {
	@Mock
	private ReactiveOAuth2UserService<OAuth2UserRequest, OAuth2User> userService;

	@Mock
	private ReactiveOAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient;

	@Mock
	private ReactiveOAuth2AuthorizedClientService authorizedClientService;

	private ClientRegistration.Builder registration = ClientRegistration.withRegistrationId("github")
			.redirectUriTemplate("{baseUrl}/{action}/oauth2/code/{registrationId}")
			.clientAuthenticationMethod(ClientAuthenticationMethod.BASIC)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.scope("read:user")
			.authorizationUri("https://github.com/login/oauth/authorize")
			.tokenUri("https://github.com/login/oauth/access_token")
			.userInfoUri("https://api.github.com/user")
			.userNameAttributeName("id")
			.clientName("GitHub")
			.clientId("clientId")
			.jwkSetUri("https://example.com/oauth2/jwk")
			.clientSecret("clientSecret");

	OAuth2AuthorizationResponse.Builder authorizationResponseBldr = OAuth2AuthorizationResponse
			.success("code")
			.state("state");

	private OAuth2LoginReactiveAuthenticationManager manager;

	@Before
	public void setup() {
		this.manager = new OAuth2LoginReactiveAuthenticationManager(this.accessTokenResponseClient, this.userService,
				this.authorizedClientService);
		when(this.authorizedClientService.saveAuthorizedClient(any(), any())).thenReturn(Mono.empty());
	}

	@Test
	public void constructorWhenNullAccessTokenResponseClientThenIllegalArgumentException() {
		this.accessTokenResponseClient = null;
		assertThatThrownBy(() -> new OAuth2LoginReactiveAuthenticationManager(this.accessTokenResponseClient, this.userService,
				this.authorizedClientService))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void constructorWhenNullUserServiceThenIllegalArgumentException() {
		this.userService = null;
		assertThatThrownBy(() -> new OAuth2LoginReactiveAuthenticationManager(this.accessTokenResponseClient, this.userService,
				this.authorizedClientService))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void constructorWhenNullAuthorizedClientServiceThenIllegalArgumentException() {
		this.authorizedClientService = null;
		assertThatThrownBy(() -> new OAuth2LoginReactiveAuthenticationManager(this.accessTokenResponseClient, this.userService,
				this.authorizedClientService))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void authenticateWhenNoSubscriptionThenDoesNothing() {
		// we didn't do anything because it should cause a ClassCastException (as verified below)
		TestingAuthenticationToken token = new TestingAuthenticationToken("a", "b");

		assertThatCode(()-> this.manager.authenticate(token))
			.doesNotThrowAnyException();

		assertThatThrownBy(() -> this.manager.authenticate(token).block())
			.isInstanceOf(Throwable.class);
	}

	@Test
	@Ignore
	public void authenticationWhenOidcThenEmpty() {
		this.registration.scope("openid");
		assertThat(this.manager.authenticate(loginToken()).block()).isNull();
	}

	@Test
	public void authenticationWhenErrorThenOAuth2AuthenticationException() {
		this.authorizationResponseBldr = OAuth2AuthorizationResponse
				.error("error")
				.state("state");
		assertThatThrownBy(() -> this.manager.authenticate(loginToken()).block())
				.isInstanceOf(OAuth2AuthenticationException.class);
	}

	@Test
	public void authenticationWhenStateDoesNotMatchThenOAuth2AuthenticationException() {
		this.authorizationResponseBldr.state("notmatch");
		assertThatThrownBy(() -> this.manager.authenticate(loginToken()).block())
				.isInstanceOf(OAuth2AuthenticationException.class);
	}

	@Test
	public void authenticationWhenOAuth2UserNotFoundThenEmpty() {
		OAuth2AccessTokenResponse accessTokenResponse = OAuth2AccessTokenResponse.withToken("foo")
				.tokenType(OAuth2AccessToken.TokenType.BEARER)
				.build();
		when(this.accessTokenResponseClient.getTokenResponse(any())).thenReturn(Mono.just(accessTokenResponse));
		when(this.userService.loadUser(any())).thenReturn(Mono.empty());
		assertThat(this.manager.authenticate(loginToken()).block()).isNull();
	}

	@Test
	public void authenticationWhenOAuth2UserNotFoundThenSuccess() {
		OAuth2AccessTokenResponse accessTokenResponse = OAuth2AccessTokenResponse.withToken("foo")
				.tokenType(OAuth2AccessToken.TokenType.BEARER)
				.build();
		when(this.accessTokenResponseClient.getTokenResponse(any())).thenReturn(Mono.just(accessTokenResponse));
		DefaultOAuth2User user = new DefaultOAuth2User(AuthorityUtils.createAuthorityList("ROLE_USER"), Collections.singletonMap("user", "rob"), "user");
		when(this.userService.loadUser(any())).thenReturn(Mono.just(user));

		OAuth2AuthenticationToken result = (OAuth2AuthenticationToken) this.manager.authenticate(loginToken()).block();

		assertThat(result.getPrincipal()).isEqualTo(user);
		assertThat(result.getAuthorities()).containsOnlyElementsOf(user.getAuthorities());
		assertThat(result.isAuthenticated()).isTrue();
	}

	private OAuth2LoginAuthenticationToken loginToken() {
		ClientRegistration clientRegistration = this.registration.build();
		OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest
				.authorizationCode()
				.state("state")
				.clientId(clientRegistration.getClientId())
				.authorizationUri(clientRegistration.getProviderDetails().getAuthorizationUri())
				.redirectUri(clientRegistration.getRedirectUriTemplate())
				.scopes(clientRegistration.getScopes())
				.build();
		OAuth2AuthorizationResponse authorizationResponse = this.authorizationResponseBldr
				.redirectUri(clientRegistration.getRedirectUriTemplate())
				.build();
		OAuth2AuthorizationExchange authorizationExchange = new OAuth2AuthorizationExchange(authorizationRequest,
				authorizationResponse);
		return new OAuth2LoginAuthenticationToken(clientRegistration, authorizationExchange);
	}
}
