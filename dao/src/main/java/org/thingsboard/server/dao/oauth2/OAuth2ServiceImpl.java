/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.server.dao.oauth2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.*;
import org.thingsboard.server.common.data.id.*;
import org.thingsboard.server.common.data.kv.*;
import org.thingsboard.server.common.data.oauth2.*;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.exception.IncorrectParameterException;
import org.thingsboard.server.dao.settings.AdminSettingsService;
import org.thingsboard.server.dao.tenant.TenantService;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OAuth2ServiceImpl implements OAuth2Service {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String OAUTH2_CLIENT_REGISTRATIONS_PARAMS = "oauth2ClientRegistrationsParams";
    private static final String OAUTH2_CLIENT_REGISTRATIONS_DOMAIN_NAME_PREFIX = "oauth2ClientRegistrationsDomainNamePrefix";

    private static final String ALLOW_OAUTH2_CONFIGURATION = "allowOAuth2Configuration";


    private static final String SYSTEM_SETTINGS_OAUTH2_VALUE = "value";

    private static final String OAUTH2_AUTHORIZATION_PATH_TEMPLATE = "/oauth2/authorization/%s";

    @Autowired
    private Environment environment;

    @Autowired
    private AdminSettingsService adminSettingsService;

    @Autowired
    private AttributesService attributesService;

    @Autowired
    private TenantService tenantService;

    private final ReentrantLock lock = new ReentrantLock();

    private final Map<TenantId, OAuth2ClientsParams> clientsParams = new ConcurrentHashMap<>();


    private boolean isInstall() {
        return environment.acceptsProfiles("install");
    }

    // TODO add field that invalidates cache in case write to cache fails after successful saving in DB
    @PostConstruct
    public void init() {
        if (isInstall()) return;

        Map<TenantId, OAuth2ClientsParams> allOAuth2ClientsParams = getAllOAuth2ClientsParams();

        allOAuth2ClientsParams.forEach(clientsParams::put);
    }

    @Override
    public OAuth2ClientRegistration getClientRegistration(String registrationId) {
       return clientsParams.values().stream()
                .flatMap(oAuth2ClientsParams -> oAuth2ClientsParams.getClientRegistrations().stream())
                .filter(clientRegistration -> registrationId.equals(clientRegistration.getRegistrationId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<OAuth2ClientInfo> getOAuth2Clients(String domainName) {
        OAuth2ClientsParams oAuth2ClientsParams = getMergedOAuth2ClientsParams(domainName);
        return oAuth2ClientsParams != null && oAuth2ClientsParams.getClientRegistrations() != null ?
                oAuth2ClientsParams.getClientRegistrations().stream()
                        .map(this::toClientInfo)
                        .collect(Collectors.toList())
                : Collections.emptyList()
                ;
    }

    private OAuth2ClientInfo toClientInfo(OAuth2ClientRegistration clientRegistration) {
        OAuth2ClientInfo client = new OAuth2ClientInfo();
        client.setName(clientRegistration.getLoginButtonLabel());
        client.setUrl(String.format(OAUTH2_AUTHORIZATION_PATH_TEMPLATE, clientRegistration.getRegistrationId()));
        client.setIcon(clientRegistration.getLoginButtonIcon());
        return client;
    }

    @Override
    public OAuth2ClientsParams saveSystemOAuth2ClientsParams(OAuth2ClientsParams oAuth2ClientsParams) {
        validate(oAuth2ClientsParams);

        validateUniqueRegistrationId(oAuth2ClientsParams, TenantId.SYS_TENANT_ID);
        lock.lock();
        try {
            validateUniqueRegistrationId(oAuth2ClientsParams, TenantId.SYS_TENANT_ID);
            AdminSettings clientRegistrationParamsSettings = createSystemAdminSettings(oAuth2ClientsParams);
            adminSettingsService.saveAdminSettings(TenantId.SYS_TENANT_ID, clientRegistrationParamsSettings);
            clientsParams.put(TenantId.SYS_TENANT_ID, oAuth2ClientsParams);
        } finally {
            lock.unlock();
        }

        return getSystemOAuth2ClientsParams(TenantId.SYS_TENANT_ID);
    }

    @Override
    public OAuth2ClientsParams saveTenantOAuth2ClientsParams(TenantId tenantId, OAuth2ClientsParams oAuth2ClientsParams) {
        // TODO what if tenant saves config for several different domain names, do we need to check it
        validate(oAuth2ClientsParams);
        // TODO check by registration ID in system

        String adminSettingsId = processTenantAdminSettings(tenantId, oAuth2ClientsParams.getDomainName(), oAuth2ClientsParams.getAdminSettingsId());
        oAuth2ClientsParams.setAdminSettingsId(adminSettingsId);

        List<AttributeKvEntry> attributes = createOAuth2ClientsParamsAttributes(oAuth2ClientsParams);
        try {
            // TODO ask if I need .get() here
            attributesService.save(tenantId, tenantId, DataConstants.SERVER_SCOPE, attributes).get();
        } catch (Exception e) {
            log.error("Unable to save OAuth2 Client Registration Params to attributes!", e);
            throw new IncorrectParameterException("Unable to save OAuth2 Client Registration Params to attributes!");
        }
        return getTenantOAuth2ClientsParams(tenantId);
    }

    private List<AttributeKvEntry> createOAuth2ClientsParamsAttributes(OAuth2ClientsParams oAuth2ClientsParams) {
        String json = toJson(oAuth2ClientsParams);
        List<AttributeKvEntry> attributes = new ArrayList<>();
        long ts = System.currentTimeMillis();
        attributes.add(new BaseAttributeKvEntry(new StringDataEntry(OAUTH2_CLIENT_REGISTRATIONS_PARAMS, json), ts));
        return attributes;
    }

    private String processTenantAdminSettings(TenantId tenantId, String domainName, String prevAdminSettingsId) {
        String selectedDomainSettingsKey = constructAdminSettingsDomainKey(domainName);
        AdminSettings existentAdminSettingsByKey = adminSettingsService.findAdminSettingsByKey(tenantId, selectedDomainSettingsKey);
        if (StringUtils.isEmpty(prevAdminSettingsId)) {
            if (existentAdminSettingsByKey == null) {
                AdminSettings tenantAdminSettings = createTenantAdminSettings(tenantId, selectedDomainSettingsKey);
                existentAdminSettingsByKey = adminSettingsService.saveAdminSettings(tenantId, tenantAdminSettings);
                return existentAdminSettingsByKey.getId().getId().toString();
            } else {
                log.error("Current domain name [{}] already registered in the system!", domainName);
                throw new IncorrectParameterException("Current domain name [" + domainName + "] already registered in the system!");
            }
        } else {
            AdminSettings existentOAuth2ClientsSettingsById = adminSettingsService.findAdminSettingsById(
                    tenantId,
                    new AdminSettingsId(UUID.fromString(prevAdminSettingsId))
            );

            if (existentOAuth2ClientsSettingsById == null) {
                log.error("Admin setting ID is already set in login white labeling object, but doesn't exist in the database");
                throw new IllegalStateException("Admin setting ID is already set in login white labeling object, but doesn't exist in the database");
            }

            if (!existentOAuth2ClientsSettingsById.getKey().equals(selectedDomainSettingsKey)) {
                if (existentAdminSettingsByKey == null) {
                    AdminSettings newOAuth2ClientsSettings = replaceExistentAdminSettings(tenantId, selectedDomainSettingsKey, existentOAuth2ClientsSettingsById.getKey());
                    return newOAuth2ClientsSettings.getId().getId().toString();
                } else {
                    log.error("Current domain name [{}] already registered in the system!", domainName);
                    throw new IncorrectParameterException("Current domain name [" + domainName + "] already registered in the system!");
                }
            }
            return prevAdminSettingsId;
        }
    }

    private AdminSettings replaceExistentAdminSettings(TenantId tenantId, String newKey, String oldKey) {
        adminSettingsService.deleteAdminSettingsByKey(tenantId, oldKey);
        AdminSettings tenantAdminSettings = createTenantAdminSettings(tenantId, newKey);
        return adminSettingsService.saveAdminSettings(tenantId, tenantAdminSettings);
    }

    private AdminSettings createTenantAdminSettings(TenantId tenantId, String clientRegistrationsKey) {
        AdminSettings clientRegistrationParamsSettings = new AdminSettings();
        clientRegistrationParamsSettings.setKey(clientRegistrationsKey);
        ObjectNode node = mapper.createObjectNode();
        node.put("entityType", EntityType.TENANT.name());
        node.put("entityId", tenantId.toString());
        clientRegistrationParamsSettings.setJsonValue(node);
        return clientRegistrationParamsSettings;
    }

    private AdminSettings createSystemAdminSettings(OAuth2ClientsParams oAuth2ClientsParams) {
        AdminSettings clientRegistrationParamsSettings = new AdminSettings();
        clientRegistrationParamsSettings.setKey(OAUTH2_CLIENT_REGISTRATIONS_PARAMS);
        ObjectNode clientRegistrationsNode = mapper.createObjectNode();

        String json = toJson(oAuth2ClientsParams);
        clientRegistrationsNode.put(SYSTEM_SETTINGS_OAUTH2_VALUE, json);
        clientRegistrationParamsSettings.setJsonValue(clientRegistrationsNode);

        return clientRegistrationParamsSettings;
    }

    private void validateUniqueRegistrationId(OAuth2ClientsParams inputOAuth2ClientsParams, TenantId tenantId) {
        inputOAuth2ClientsParams.getClientRegistrations().stream()
                .map(OAuth2ClientRegistration::getRegistrationId)
                .forEach(registrationId -> {
                    clientsParams.forEach((paramsTenantId, oAuth2ClientsParams) -> {
                        boolean registrationExists = oAuth2ClientsParams.getClientRegistrations().stream()
                                .map(OAuth2ClientRegistration::getRegistrationId)
                                .anyMatch(registrationId::equals);
                        if (registrationExists && !tenantId.equals(paramsTenantId)) {
                            log.error("Current registrationId [{}] already registered in the system!", registrationId);
                            throw new IncorrectParameterException("Current registrationId [" + registrationId + "] already registered in the system!");
                        }
                    });
                });
    }

    private void validate(OAuth2ClientsParams oAuth2ClientsParams) {
        for (OAuth2ClientRegistration clientRegistration : oAuth2ClientsParams.getClientRegistrations()) {
            validator.accept(clientRegistration);
        }
    }

    @Override
    public OAuth2ClientsParams getSystemOAuth2ClientsParams(TenantId tenantId) {
        return clientsParams.get(tenantId);
    }

    @Override
    public OAuth2ClientsParams getTenantOAuth2ClientsParams(TenantId tenantId) {
        return clientsParams.get(tenantId);
    }

    private Map<TenantId, OAuth2ClientsParams> getAllOAuth2ClientsParams() {
        OAuth2ClientsParams systemOAuth2ClientsParams = getSystemOAuth2ClientsParams(TenantId.SYS_TENANT_ID);
        ListenableFuture<Map<String, String>> jsonFuture = getAllOAuth2ClientsParamsAttribute();
        try {
            return Futures.transform(jsonFuture,
                    clientsParamsByKvEntryKey -> {
                        Map<TenantId, OAuth2ClientsParams> tenantClientParams = clientsParamsByKvEntryKey.entrySet().stream()
                                .collect(Collectors.toMap(
                                        entry -> new TenantId(UUIDConverter.fromString(entry.getKey())),
                                        entry -> constructOAuth2ClientsParams(entry.getValue())
                                ));
                        tenantClientParams.put(TenantId.SYS_TENANT_ID, systemOAuth2ClientsParams);
                        return tenantClientParams;
                    },
                    MoreExecutors.directExecutor()
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to read OAuth2 Clients Params from attributes!", e);
            throw new RuntimeException("Failed to read OAuth2 Clients Params from attributes!", e);
        }
    }

    @Override
    public void deleteDomainOAuth2ClientRegistrationByTenant(TenantId tenantId) {
        OAuth2ClientsParams params = getTenantOAuth2ClientsParams(tenantId);
        if (!StringUtils.isEmpty(params.getDomainName())) {
            // TODO don't we need to delete from attributes?
            String settingsKey = constructAdminSettingsDomainKey(params.getDomainName());
            adminSettingsService.deleteAdminSettingsByKey(tenantId, settingsKey);
        }
    }

    @Override
    public boolean isOAuth2ClientRegistrationAllowed(TenantId tenantId) {
        Tenant tenant = tenantService.findTenantById(tenantId);
        JsonNode allowOAuth2ConfigurationJsonNode = tenant.getAdditionalInfo() != null ? tenant.getAdditionalInfo().get(ALLOW_OAUTH2_CONFIGURATION) : null;
        if (allowOAuth2ConfigurationJsonNode == null) {
            return true;
        } else {
            return allowOAuth2ConfigurationJsonNode.asBoolean();
        }
    }

    private ListenableFuture<Map<String, String>> getAllOAuth2ClientsParamsAttribute() {
        ListenableFuture<List<EntityAttributeKvEntry>> entityAttributeKvEntriesFuture;
        try {
            entityAttributeKvEntriesFuture = attributesService.findAllByAttributeKey(OAUTH2_CLIENT_REGISTRATIONS_PARAMS);
        } catch (Exception e) {
            log.error("Unable to read OAuth2 Clients Params from attributes!", e);
            throw new IncorrectParameterException("Unable to read OAuth2 Clients Params from attributes!");
        }
        return Futures.transform(entityAttributeKvEntriesFuture, attributeKvEntries -> {
            if (attributeKvEntries != null && !attributeKvEntries.isEmpty()) {
                return attributeKvEntries.stream()
                        .collect(Collectors.toMap(EntityAttributeKvEntry::getEntityId, EntityAttributeKvEntry::getValueAsString));
            } else {
                return Collections.emptyMap();
            }
        }, MoreExecutors.directExecutor());
    }

    private String constructAdminSettingsDomainKey(String domainName) {
        String clientRegistrationsKey;
        if (StringUtils.isEmpty(domainName)) {
            clientRegistrationsKey = OAUTH2_CLIENT_REGISTRATIONS_PARAMS;
        } else {
            clientRegistrationsKey = OAUTH2_CLIENT_REGISTRATIONS_DOMAIN_NAME_PREFIX + "_" + domainName;
        }
        return clientRegistrationsKey;
    }

    private OAuth2ClientsParams constructOAuth2ClientsParams(String json) {
        OAuth2ClientsParams result = null;
        if (!StringUtils.isEmpty(json)) {
            try {
                result = mapper.readValue(json, OAuth2ClientsParams.class);
            } catch (IOException e) {
                log.error("Unable to read OAuth2 Clients Params from JSON!", e);
                throw new IncorrectParameterException("Unable to read OAuth2 Clients Params from JSON!");
            }
        }
        if (result == null) {
            result = new OAuth2ClientsParams();
        }
        return result;
    }

    private OAuth2ClientsParams getMergedOAuth2ClientsParams(String domainName) {
        AdminSettings oauth2ClientsSettings = adminSettingsService.findAdminSettingsByKey(TenantId.SYS_TENANT_ID, constructAdminSettingsDomainKey(domainName));
        OAuth2ClientsParams result;
        if (oauth2ClientsSettings != null) {
            String strEntityType = oauth2ClientsSettings.getJsonValue().get("entityType").asText();
            String strEntityId = oauth2ClientsSettings.getJsonValue().get("entityId").asText();
            EntityId entityId = EntityIdFactory.getByTypeAndId(strEntityType, strEntityId);
            if (!entityId.getEntityType().equals(EntityType.TENANT)) {
                log.error("Only tenant can configure OAuth2 for certain domain!");
                throw new IllegalStateException("Only tenant can configure OAuth2 for certain domain!");
            }
            TenantId tenantId = (TenantId) entityId;
            result = getTenantOAuth2ClientsParams(tenantId);
            OAuth2ClientsParams systemOAuth2ClientsParams = getSystemOAuth2ClientsParams(TenantId.SYS_TENANT_ID);
            result.getClientRegistrations().addAll(systemOAuth2ClientsParams.getClientRegistrations());
        } else {
            result = getSystemOAuth2ClientsParams(TenantId.SYS_TENANT_ID);
        }
        return result;
    }

    private String toJson(OAuth2ClientsParams oAuth2ClientsParams) {
        String json;
        try {
            json = mapper.writeValueAsString(oAuth2ClientsParams);
        } catch (JsonProcessingException e) {
            log.error("Unable to convert OAuth2 Client Registration Params to JSON!", e);
            throw new IncorrectParameterException("Unable to convert OAuth2 Client Registration Params to JSON!");
        }
        return json;
    }

    private final Consumer<OAuth2ClientRegistration> validator = clientRegistration -> {
        if (StringUtils.isEmpty(clientRegistration.getRegistrationId())) {
            throw new DataValidationException("Registration ID should be specified!");
        }
        if (StringUtils.isEmpty(clientRegistration.getClientId())) {
            throw new DataValidationException("Client ID should be specified!");
        }
        if (StringUtils.isEmpty(clientRegistration.getClientSecret())) {
            throw new DataValidationException("Client secret should be specified!");
        }
        if (StringUtils.isEmpty(clientRegistration.getAuthorizationUri())) {
            throw new DataValidationException("Authorization uri should be specified!");
        }
        if (StringUtils.isEmpty(clientRegistration.getTokenUri())) {
            throw new DataValidationException("Token uri should be specified!");
        }
        if (StringUtils.isEmpty(clientRegistration.getRedirectUriTemplate())) {
            throw new DataValidationException("Redirect uri template should be specified!");
        }
        if (StringUtils.isEmpty(clientRegistration.getScope())) {
            throw new DataValidationException("Scope should be specified!");
        }
        if (StringUtils.isEmpty(clientRegistration.getAuthorizationGrantType())) {
            throw new DataValidationException("Authorization grant type should be specified!");
        }
        if (StringUtils.isEmpty(clientRegistration.getUserInfoUri())) {
            throw new DataValidationException("User info uri should be specified!");
        }
        if (StringUtils.isEmpty(clientRegistration.getUserNameAttributeName())) {
            throw new DataValidationException("User name attribute name should be specified!");
        }
        if (StringUtils.isEmpty(clientRegistration.getJwkSetUri())) {
            throw new DataValidationException("Jwk set uri should be specified!");
        }
        if (StringUtils.isEmpty(clientRegistration.getClientAuthenticationMethod())) {
            throw new DataValidationException("Client authentication method should be specified!");
        }
        if (StringUtils.isEmpty(clientRegistration.getClientName())) {
            throw new DataValidationException("Client name should be specified!");
        }
        if (StringUtils.isEmpty(clientRegistration.getLoginButtonLabel())) {
            throw new DataValidationException("Login button label should be specified!");
        }
        OAuth2MapperConfig mapperConfig = clientRegistration.getMapperConfig();
        if (mapperConfig == null) {
            throw new DataValidationException("Mapper config should be specified!");
        }
        if (mapperConfig.getType() == null) {
            throw new DataValidationException("Mapper config type should be specified!");
        }
        if (mapperConfig.getType() == MapperType.BASIC) {
            OAuth2BasicMapperConfig basicConfig = mapperConfig.getBasicConfig();
            if (basicConfig == null) {
                throw new DataValidationException("Basic config should be specified!");
            }
            if (StringUtils.isEmpty(basicConfig.getEmailAttributeKey())) {
                throw new DataValidationException("Email attribute key should be specified!");
            }
            if (basicConfig.getTenantNameStrategy() == null) {
                throw new DataValidationException("Tenant name strategy should be specified!");
            }
            if (basicConfig.getTenantNameStrategy() == TenantNameStrategyType.CUSTOM
                    && StringUtils.isEmpty(basicConfig.getTenantNamePattern())) {
                throw new DataValidationException("Tenant name pattern should be specified!");
            }
        }
        if (mapperConfig.getType() == MapperType.CUSTOM) {
            OAuth2CustomMapperConfig customConfig = mapperConfig.getCustomConfig();
            if (customConfig == null) {
                throw new DataValidationException("Custom config should be specified!");
            }
            if (StringUtils.isEmpty(customConfig.getUrl())) {
                throw new DataValidationException("Custom mapper URL should be specified!");
            }
            if (StringUtils.isEmpty(customConfig.getUsername())) {
                throw new DataValidationException("Custom mapper username should be specified!");
            }
            if (StringUtils.isEmpty(customConfig.getPassword())) {
                throw new DataValidationException("Custom mapper password should be specified!");
            }
        }
    };
}
