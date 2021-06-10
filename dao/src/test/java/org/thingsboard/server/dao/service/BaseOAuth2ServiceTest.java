/**
 * Copyright © 2016-2021 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.dao.service;

import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.server.common.data.oauth2.MapperType;
import org.thingsboard.server.common.data.oauth2.OAuth2ClientInfo;
import org.thingsboard.server.common.data.oauth2.OAuth2CustomMapperConfig;
import org.thingsboard.server.common.data.oauth2.OAuth2DomainInfo;
import org.thingsboard.server.common.data.oauth2.OAuth2Info;
import org.thingsboard.server.common.data.oauth2.OAuth2MapperConfig;
import org.thingsboard.server.common.data.oauth2.OAuth2MobileInfo;
import org.thingsboard.server.common.data.oauth2.OAuth2ParamsInfo;
import org.thingsboard.server.common.data.oauth2.OAuth2Registration;
import org.thingsboard.server.common.data.oauth2.OAuth2RegistrationInfo;
import org.thingsboard.server.common.data.oauth2.SchemeType;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.oauth2.OAuth2Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BaseOAuth2ServiceTest extends AbstractServiceTest {
    private static final OAuth2Info EMPTY_PARAMS = new OAuth2Info(false, Collections.emptyList());

    @Autowired
    protected OAuth2Service oAuth2Service;

    @Before
    public void beforeRun() {
        Assert.assertTrue(oAuth2Service.findAllRegistrations().isEmpty());
    }

    @After
    public void after() {
        oAuth2Service.saveOAuth2Info(EMPTY_PARAMS);
        Assert.assertTrue(oAuth2Service.findAllRegistrations().isEmpty());
        Assert.assertTrue(oAuth2Service.findOAuth2Info().getOauth2ParamsInfos().isEmpty());
    }

    @Test(expected = DataValidationException.class)
    public void testSaveHttpAndMixedDomainsTogether() {
        OAuth2Info oAuth2Info = new OAuth2Info(true, Lists.newArrayList(
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("first-domain").scheme(SchemeType.HTTP).build(),
                                OAuth2DomainInfo.builder().name("first-domain").scheme(SchemeType.MIXED).build(),
                                OAuth2DomainInfo.builder().name("third-domain").scheme(SchemeType.HTTPS).build()
                        ))
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo(),
                                validRegistrationInfo(),
                                validRegistrationInfo()
                        ))
                        .build()
        ));
        oAuth2Service.saveOAuth2Info(oAuth2Info);
    }

    @Test(expected = DataValidationException.class)
    public void testSaveHttpsAndMixedDomainsTogether() {
        OAuth2Info oAuth2Info = new OAuth2Info(true, Lists.newArrayList(
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("first-domain").scheme(SchemeType.HTTPS).build(),
                                OAuth2DomainInfo.builder().name("first-domain").scheme(SchemeType.MIXED).build(),
                                OAuth2DomainInfo.builder().name("third-domain").scheme(SchemeType.HTTPS).build()
                        ))
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo(),
                                validRegistrationInfo(),
                                validRegistrationInfo()
                        ))
                        .build()
        ));
        oAuth2Service.saveOAuth2Info(oAuth2Info);
    }

    @Test
    public void testCreateAndFindParams() {
        OAuth2Info oAuth2Info = createDefaultOAuth2Info();
        oAuth2Service.saveOAuth2Info(oAuth2Info);
        OAuth2Info foundOAuth2Info = oAuth2Service.findOAuth2Info();
        Assert.assertNotNull(foundOAuth2Info);
        // TODO ask if it's safe to check equality on AdditionalProperties
        Assert.assertEquals(oAuth2Info, foundOAuth2Info);
    }

    @Test
    public void testDisableParams() {
        OAuth2Info oAuth2Info = createDefaultOAuth2Info();
        oAuth2Info.setEnabled(true);
        oAuth2Service.saveOAuth2Info(oAuth2Info);
        OAuth2Info foundOAuth2Info = oAuth2Service.findOAuth2Info();
        Assert.assertNotNull(foundOAuth2Info);
        Assert.assertEquals(oAuth2Info, foundOAuth2Info);

        oAuth2Info.setEnabled(false);
        oAuth2Service.saveOAuth2Info(oAuth2Info);
        OAuth2Info foundDisabledOAuth2Info = oAuth2Service.findOAuth2Info();
        Assert.assertEquals(oAuth2Info, foundDisabledOAuth2Info);
    }

    @Test
    public void testClearDomainParams() {
        OAuth2Info oAuth2Info = createDefaultOAuth2Info();
        oAuth2Service.saveOAuth2Info(oAuth2Info);
        OAuth2Info foundOAuth2Info = oAuth2Service.findOAuth2Info();
        Assert.assertNotNull(foundOAuth2Info);
        Assert.assertEquals(oAuth2Info, foundOAuth2Info);

        oAuth2Service.saveOAuth2Info(EMPTY_PARAMS);
        OAuth2Info foundAfterClearClientsParams = oAuth2Service.findOAuth2Info();
        Assert.assertNotNull(foundAfterClearClientsParams);
        Assert.assertEquals(EMPTY_PARAMS, foundAfterClearClientsParams);
    }

    @Test
    public void testUpdateClientsParams() {
        OAuth2Info oAuth2Info = createDefaultOAuth2Info();
        oAuth2Service.saveOAuth2Info(oAuth2Info);
        OAuth2Info foundOAuth2Info = oAuth2Service.findOAuth2Info();
        Assert.assertNotNull(foundOAuth2Info);
        Assert.assertEquals(oAuth2Info, foundOAuth2Info);

        OAuth2Info newOAuth2Info = new OAuth2Info(true, Lists.newArrayList(
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("another-domain").scheme(SchemeType.HTTPS).build()
                        ))
                        .mobileInfos(Collections.emptyList())
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo()
                        ))
                        .build(),
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("test-domain").scheme(SchemeType.MIXED).build()
                        ))
                        .mobileInfos(Collections.emptyList())
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo()
                        ))
                        .build()
        ));
        oAuth2Service.saveOAuth2Info(newOAuth2Info);
        OAuth2Info foundAfterUpdateOAuth2Info = oAuth2Service.findOAuth2Info();
        Assert.assertNotNull(foundAfterUpdateOAuth2Info);
        Assert.assertEquals(newOAuth2Info, foundAfterUpdateOAuth2Info);
    }

    @Test
    public void testGetOAuth2Clients() {
        List<OAuth2RegistrationInfo> firstGroup = Lists.newArrayList(
                validRegistrationInfo(),
                validRegistrationInfo(),
                validRegistrationInfo(),
                validRegistrationInfo()
        );
        List<OAuth2RegistrationInfo> secondGroup = Lists.newArrayList(
                validRegistrationInfo(),
                validRegistrationInfo()
        );
        List<OAuth2RegistrationInfo> thirdGroup = Lists.newArrayList(
                validRegistrationInfo()
        );
        OAuth2Info oAuth2Info = new OAuth2Info(true, Lists.newArrayList(
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("first-domain").scheme(SchemeType.HTTP).build(),
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.MIXED).build(),
                                OAuth2DomainInfo.builder().name("third-domain").scheme(SchemeType.HTTPS).build()
                        ))
                        .mobileInfos(Collections.emptyList())
                        .clientRegistrations(firstGroup)
                        .build(),
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.HTTP).build(),
                                OAuth2DomainInfo.builder().name("fourth-domain").scheme(SchemeType.MIXED).build()
                        ))
                        .mobileInfos(Collections.emptyList())
                        .clientRegistrations(secondGroup)
                        .build(),
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.HTTPS).build(),
                                OAuth2DomainInfo.builder().name("fifth-domain").scheme(SchemeType.HTTP).build()
                        ))
                        .mobileInfos(Collections.emptyList())
                        .clientRegistrations(thirdGroup)
                        .build()
        ));

        oAuth2Service.saveOAuth2Info(oAuth2Info);
        OAuth2Info foundOAuth2Info = oAuth2Service.findOAuth2Info();
        Assert.assertNotNull(foundOAuth2Info);
        Assert.assertEquals(oAuth2Info, foundOAuth2Info);

        List<OAuth2ClientInfo> firstGroupClientInfos = firstGroup.stream()
                .map(registrationInfo -> new OAuth2ClientInfo(
                        registrationInfo.getLoginButtonLabel(), registrationInfo.getLoginButtonIcon(), null))
                .collect(Collectors.toList());
        List<OAuth2ClientInfo> secondGroupClientInfos = secondGroup.stream()
                .map(registrationInfo -> new OAuth2ClientInfo(
                        registrationInfo.getLoginButtonLabel(), registrationInfo.getLoginButtonIcon(), null))
                .collect(Collectors.toList());
        List<OAuth2ClientInfo> thirdGroupClientInfos = thirdGroup.stream()
                .map(registrationInfo -> new OAuth2ClientInfo(
                        registrationInfo.getLoginButtonLabel(), registrationInfo.getLoginButtonIcon(), null))
                .collect(Collectors.toList());

        List<OAuth2ClientInfo> nonExistentDomainClients = oAuth2Service.getOAuth2Clients("http", "non-existent-domain", null);
        Assert.assertTrue(nonExistentDomainClients.isEmpty());

        List<OAuth2ClientInfo> firstDomainHttpClients = oAuth2Service.getOAuth2Clients("http", "first-domain", null);
        Assert.assertEquals(firstGroupClientInfos.size(), firstDomainHttpClients.size());
        firstGroupClientInfos.forEach(firstGroupClientInfo -> {
            Assert.assertTrue(
                    firstDomainHttpClients.stream().anyMatch(clientInfo ->
                            clientInfo.getIcon().equals(firstGroupClientInfo.getIcon())
                                    && clientInfo.getName().equals(firstGroupClientInfo.getName()))
            );
        });

        List<OAuth2ClientInfo> firstDomainHttpsClients = oAuth2Service.getOAuth2Clients("https", "first-domain", null);
        Assert.assertTrue(firstDomainHttpsClients.isEmpty());

        List<OAuth2ClientInfo> fourthDomainHttpClients = oAuth2Service.getOAuth2Clients("http", "fourth-domain", null);
        Assert.assertEquals(secondGroupClientInfos.size(), fourthDomainHttpClients.size());
        secondGroupClientInfos.forEach(secondGroupClientInfo -> {
            Assert.assertTrue(
                    fourthDomainHttpClients.stream().anyMatch(clientInfo ->
                            clientInfo.getIcon().equals(secondGroupClientInfo.getIcon())
                                    && clientInfo.getName().equals(secondGroupClientInfo.getName()))
            );
        });
        List<OAuth2ClientInfo> fourthDomainHttpsClients = oAuth2Service.getOAuth2Clients("https", "fourth-domain", null);
        Assert.assertEquals(secondGroupClientInfos.size(), fourthDomainHttpsClients.size());
        secondGroupClientInfos.forEach(secondGroupClientInfo -> {
            Assert.assertTrue(
                    fourthDomainHttpsClients.stream().anyMatch(clientInfo ->
                            clientInfo.getIcon().equals(secondGroupClientInfo.getIcon())
                                    && clientInfo.getName().equals(secondGroupClientInfo.getName()))
            );
        });

        List<OAuth2ClientInfo> secondDomainHttpClients = oAuth2Service.getOAuth2Clients("http", "second-domain", null);
        Assert.assertEquals(firstGroupClientInfos.size() + secondGroupClientInfos.size(), secondDomainHttpClients.size());
        firstGroupClientInfos.forEach(firstGroupClientInfo -> {
            Assert.assertTrue(
                    secondDomainHttpClients.stream().anyMatch(clientInfo ->
                            clientInfo.getIcon().equals(firstGroupClientInfo.getIcon())
                                    && clientInfo.getName().equals(firstGroupClientInfo.getName()))
            );
        });
        secondGroupClientInfos.forEach(secondGroupClientInfo -> {
            Assert.assertTrue(
                    secondDomainHttpClients.stream().anyMatch(clientInfo ->
                            clientInfo.getIcon().equals(secondGroupClientInfo.getIcon())
                                    && clientInfo.getName().equals(secondGroupClientInfo.getName()))
            );
        });

        List<OAuth2ClientInfo> secondDomainHttpsClients = oAuth2Service.getOAuth2Clients("https", "second-domain", null);
        Assert.assertEquals(firstGroupClientInfos.size() + thirdGroupClientInfos.size(), secondDomainHttpsClients.size());
        firstGroupClientInfos.forEach(firstGroupClientInfo -> {
            Assert.assertTrue(
                    secondDomainHttpsClients.stream().anyMatch(clientInfo ->
                            clientInfo.getIcon().equals(firstGroupClientInfo.getIcon())
                                    && clientInfo.getName().equals(firstGroupClientInfo.getName()))
            );
        });
        thirdGroupClientInfos.forEach(thirdGroupClientInfo -> {
            Assert.assertTrue(
                    secondDomainHttpsClients.stream().anyMatch(clientInfo ->
                            clientInfo.getIcon().equals(thirdGroupClientInfo.getIcon())
                                    && clientInfo.getName().equals(thirdGroupClientInfo.getName()))
            );
        });
    }

    @Test
    public void testGetOAuth2ClientsForHttpAndHttps() {
        List<OAuth2RegistrationInfo> firstGroup = Lists.newArrayList(
                validRegistrationInfo(),
                validRegistrationInfo(),
                validRegistrationInfo(),
                validRegistrationInfo()
        );
        OAuth2Info oAuth2Info = new OAuth2Info(true, Lists.newArrayList(
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("first-domain").scheme(SchemeType.HTTP).build(),
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.MIXED).build(),
                                OAuth2DomainInfo.builder().name("first-domain").scheme(SchemeType.HTTPS).build()
                        ))
                        .mobileInfos(Collections.emptyList())
                        .clientRegistrations(firstGroup)
                        .build()
        ));

        oAuth2Service.saveOAuth2Info(oAuth2Info);
        OAuth2Info foundOAuth2Info = oAuth2Service.findOAuth2Info();
        Assert.assertNotNull(foundOAuth2Info);
        Assert.assertEquals(oAuth2Info, foundOAuth2Info);

        List<OAuth2ClientInfo> firstGroupClientInfos = firstGroup.stream()
                .map(registrationInfo -> new OAuth2ClientInfo(
                        registrationInfo.getLoginButtonLabel(), registrationInfo.getLoginButtonIcon(), null))
                .collect(Collectors.toList());

        List<OAuth2ClientInfo> firstDomainHttpClients = oAuth2Service.getOAuth2Clients("http", "first-domain", null);
        Assert.assertEquals(firstGroupClientInfos.size(), firstDomainHttpClients.size());
        firstGroupClientInfos.forEach(firstGroupClientInfo -> {
            Assert.assertTrue(
                    firstDomainHttpClients.stream().anyMatch(clientInfo ->
                            clientInfo.getIcon().equals(firstGroupClientInfo.getIcon())
                                    && clientInfo.getName().equals(firstGroupClientInfo.getName()))
            );
        });

        List<OAuth2ClientInfo> firstDomainHttpsClients = oAuth2Service.getOAuth2Clients("https", "first-domain", null);
        Assert.assertEquals(firstGroupClientInfos.size(), firstDomainHttpsClients.size());
        firstGroupClientInfos.forEach(firstGroupClientInfo -> {
            Assert.assertTrue(
                    firstDomainHttpsClients.stream().anyMatch(clientInfo ->
                            clientInfo.getIcon().equals(firstGroupClientInfo.getIcon())
                                    && clientInfo.getName().equals(firstGroupClientInfo.getName()))
            );
        });
    }

    @Test
    public void testGetDisabledOAuth2Clients() {
        OAuth2Info oAuth2Info = new OAuth2Info(true, Lists.newArrayList(
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("first-domain").scheme(SchemeType.HTTP).build(),
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.MIXED).build(),
                                OAuth2DomainInfo.builder().name("third-domain").scheme(SchemeType.HTTPS).build()
                        ))
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo(),
                                validRegistrationInfo(),
                                validRegistrationInfo()
                        ))
                        .build(),
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.HTTP).build(),
                                OAuth2DomainInfo.builder().name("fourth-domain").scheme(SchemeType.MIXED).build()
                        ))
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo(),
                                validRegistrationInfo()
                        ))
                        .build()
        ));

        oAuth2Service.saveOAuth2Info(oAuth2Info);

        List<OAuth2ClientInfo> secondDomainHttpClients = oAuth2Service.getOAuth2Clients("http", "second-domain", null);
        Assert.assertEquals(5, secondDomainHttpClients.size());

        oAuth2Info.setEnabled(false);
        oAuth2Service.saveOAuth2Info(oAuth2Info);

        List<OAuth2ClientInfo> secondDomainHttpDisabledClients = oAuth2Service.getOAuth2Clients("http", "second-domain", null);
        Assert.assertEquals(0, secondDomainHttpDisabledClients.size());
    }

    @Test
    public void testFindAllRegistrations() {
        OAuth2Info oAuth2Info = new OAuth2Info(true, Lists.newArrayList(
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("first-domain").scheme(SchemeType.HTTP).build(),
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.MIXED).build(),
                                OAuth2DomainInfo.builder().name("third-domain").scheme(SchemeType.HTTPS).build()
                        ))
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo(),
                                validRegistrationInfo(),
                                validRegistrationInfo()
                        ))
                        .build(),
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.HTTP).build(),
                                OAuth2DomainInfo.builder().name("fourth-domain").scheme(SchemeType.MIXED).build()
                        ))
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo(),
                                validRegistrationInfo()
                        ))
                        .build(),
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.HTTPS).build(),
                                OAuth2DomainInfo.builder().name("fifth-domain").scheme(SchemeType.HTTP).build()
                        ))
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo()
                        ))
                        .build()
        ));

        oAuth2Service.saveOAuth2Info(oAuth2Info);
        List<OAuth2Registration> foundRegistrations = oAuth2Service.findAllRegistrations();
        Assert.assertEquals(6, foundRegistrations.size());
        oAuth2Info.getOauth2ParamsInfos().stream()
                .flatMap(paramsInfo -> paramsInfo.getClientRegistrations().stream())
                .forEach(registrationInfo ->
                        Assert.assertTrue(
                                foundRegistrations.stream()
                                        .anyMatch(registration -> registration.getClientId().equals(registrationInfo.getClientId()))
                        )
                );
    }

    @Test
    public void testFindRegistrationById() {
        OAuth2Info oAuth2Info = new OAuth2Info(true, Lists.newArrayList(
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("first-domain").scheme(SchemeType.HTTP).build(),
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.MIXED).build(),
                                OAuth2DomainInfo.builder().name("third-domain").scheme(SchemeType.HTTPS).build()
                        ))
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo(),
                                validRegistrationInfo(),
                                validRegistrationInfo()
                        ))
                        .build(),
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.HTTP).build(),
                                OAuth2DomainInfo.builder().name("fourth-domain").scheme(SchemeType.MIXED).build()
                        ))
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo(),
                                validRegistrationInfo()
                        ))
                        .build(),
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.HTTPS).build(),
                                OAuth2DomainInfo.builder().name("fifth-domain").scheme(SchemeType.HTTP).build()
                        ))
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo()
                        ))
                        .build()
        ));

        oAuth2Service.saveOAuth2Info(oAuth2Info);
        List<OAuth2Registration> foundRegistrations = oAuth2Service.findAllRegistrations();
        foundRegistrations.forEach(registration -> {
            OAuth2Registration foundRegistration = oAuth2Service.findRegistration(registration.getUuidId());
            Assert.assertEquals(registration, foundRegistration);
        });
    }

    @Test
    public void testFindCallbackUrlScheme() {
        OAuth2Info oAuth2Info = new OAuth2Info(true, Lists.newArrayList(
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("first-domain").scheme(SchemeType.HTTP).build(),
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.MIXED).build(),
                                OAuth2DomainInfo.builder().name("third-domain").scheme(SchemeType.HTTPS).build()
                        ))
                        .mobileInfos(Lists.newArrayList(
                                OAuth2MobileInfo.builder().pkgName("com.test.pkg1").callbackUrlScheme("testPkg1Callback").build(),
                                OAuth2MobileInfo.builder().pkgName("com.test.pkg2").callbackUrlScheme("testPkg2Callback").build()
                        ))
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo(),
                                validRegistrationInfo(),
                                validRegistrationInfo()
                        ))
                        .build(),
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.HTTP).build(),
                                OAuth2DomainInfo.builder().name("fourth-domain").scheme(SchemeType.MIXED).build()
                        ))
                        .mobileInfos(Collections.emptyList())
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo(),
                                validRegistrationInfo()
                        ))
                        .build()
        ));
        oAuth2Service.saveOAuth2Info(oAuth2Info);

        OAuth2Info foundOAuth2Info = oAuth2Service.findOAuth2Info();
        Assert.assertEquals(oAuth2Info, foundOAuth2Info);

        List<OAuth2ClientInfo> firstDomainHttpClients = oAuth2Service.getOAuth2Clients("http", "first-domain", "com.test.pkg1");
        Assert.assertEquals(3, firstDomainHttpClients.size());
        for (OAuth2ClientInfo clientInfo : firstDomainHttpClients) {
            String[] segments = clientInfo.getUrl().split("/");
            String registrationId = segments[segments.length-1];
            String callbackUrlScheme = oAuth2Service.findCallbackUrlScheme(UUID.fromString(registrationId), "com.test.pkg1");
            Assert.assertNotNull(callbackUrlScheme);
            Assert.assertEquals("testPkg1Callback", callbackUrlScheme);
            callbackUrlScheme = oAuth2Service.findCallbackUrlScheme(UUID.fromString(registrationId), "com.test.pkg2");
            Assert.assertNotNull(callbackUrlScheme);
            Assert.assertEquals("testPkg2Callback", callbackUrlScheme);
            callbackUrlScheme = oAuth2Service.findCallbackUrlScheme(UUID.fromString(registrationId), "com.test.pkg3");
            Assert.assertNull(callbackUrlScheme);
        }
    }

    private OAuth2Info createDefaultOAuth2Info() {
        return new OAuth2Info(true, Lists.newArrayList(
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("first-domain").scheme(SchemeType.HTTP).build(),
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.MIXED).build(),
                                OAuth2DomainInfo.builder().name("third-domain").scheme(SchemeType.HTTPS).build()
                        ))
                        .mobileInfos(Collections.emptyList())
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo(),
                                validRegistrationInfo(),
                                validRegistrationInfo(),
                                validRegistrationInfo()
                        ))
                        .build(),
                OAuth2ParamsInfo.builder()
                        .domainInfos(Lists.newArrayList(
                                OAuth2DomainInfo.builder().name("second-domain").scheme(SchemeType.MIXED).build(),
                                OAuth2DomainInfo.builder().name("fourth-domain").scheme(SchemeType.MIXED).build()
                        ))
                        .mobileInfos(Collections.emptyList())
                        .clientRegistrations(Lists.newArrayList(
                                validRegistrationInfo(),
                                validRegistrationInfo()
                        ))
                        .build()
        ));
    }

    private OAuth2RegistrationInfo validRegistrationInfo() {
        return OAuth2RegistrationInfo.builder()
                .clientId(UUID.randomUUID().toString())
                .clientSecret(UUID.randomUUID().toString())
                .authorizationUri(UUID.randomUUID().toString())
                .accessTokenUri(UUID.randomUUID().toString())
                .scope(Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .userInfoUri(UUID.randomUUID().toString())
                .userNameAttributeName(UUID.randomUUID().toString())
                .jwkSetUri(UUID.randomUUID().toString())
                .clientAuthenticationMethod(UUID.randomUUID().toString())
                .loginButtonLabel(UUID.randomUUID().toString())
                .loginButtonIcon(UUID.randomUUID().toString())
                .additionalInfo(mapper.createObjectNode().put(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .mapperConfig(
                        OAuth2MapperConfig.builder()
                                .allowUserCreation(true)
                                .activateUser(true)
                                .type(MapperType.CUSTOM)
                                .custom(
                                        OAuth2CustomMapperConfig.builder()
                                                .url(UUID.randomUUID().toString())
                                                .build()
                                )
                                .build()
                )
                .build();
    }
}
