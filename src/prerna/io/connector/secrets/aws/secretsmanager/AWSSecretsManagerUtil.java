/*******************************************************************************
 * Copyright 2015 Defense Health Agency (DHA)
 *
 * If your use of this software does not include any GPLv2 components:
 * 	Licensed under the Apache License, Version 2.0 (the "License");
 * 	you may not use this file except in compliance with the License.
 * 	You may obtain a copy of the License at
 *
 * 	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * 	Unless required by applicable law or agreed to in writing, software
 * 	distributed under the License is distributed on an "AS IS" BASIS,
 * 	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 	See the License for the specific language governing permissions and
 * 	limitations under the License.
 * ----------------------------------------------------------------------------
 * If your use of this software includes any GPLv2 components:
 * 	This program is free software; you can redistribute it and/or
 * 	modify it under the terms of the GNU General Public License
 * 	as published by the Free Software Foundation; either version 2
 * 	of the License, or (at your option) any later version.
 *
 * 	This program is distributed in the hope that it will be useful,
 * 	but WITHOUT ANY WARRANTY; without even the implied warranty of
 * 	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * 	GNU General Public License for more details.
 *******************************************************************************/
package prerna.io.connector.secrets.aws.secretsmanager;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import prerna.engine.api.IEngine;
import prerna.engine.api.IEngine.CATALOG_TYPE;
import prerna.io.connector.secrets.AbstractSecrets;
import prerna.util.Constants;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/**
 * AWS Secrets Manager implementation for storing and retrieving secrets.
 *
 * Configuration via environment variables or RDF_Map:
 * - AWS_SECRETS_MANAGER_REGION: AWS region (default: us-east-1)
 * - AWS_SECRETS_MANAGER_ENDPOINT: Optional custom endpoint (for LocalStack, etc.)
 * - AWS_ACCESS_KEY_ID: AWS access key (optional, uses default credential chain if not provided)
 * - AWS_SECRET_ACCESS_KEY: AWS secret key (optional, uses default credential chain if not provided)
 * - SECRETS_NAME_PREFIX: Optional prefix for all secret names (default: semoss)
 */
public class AWSSecretsManagerUtil extends AbstractSecrets {

	private static final Logger classLogger = LogManager.getLogger(AWSSecretsManagerUtil.class);

	private static final String AWS_REGION = "AWS_SECRETS_MANAGER_REGION";
	private static final String AWS_ENDPOINT = "AWS_SECRETS_MANAGER_ENDPOINT";
	private static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
	private static final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
	private static final String SECRETS_NAME_PREFIX = "SECRETS_NAME_PREFIX";

	private static final String DEFAULT_REGION = "us-east-1";
	private static final String DEFAULT_PREFIX = "semoss";

	private static AWSSecretsManagerUtil instance;

	private SecretsManagerClient secretsClient;
	private String secretPrefix;

	private AWSSecretsManagerUtil() {
		createSecretsClient();
	}

	/**
	 * Creates and configures the AWS Secrets Manager client.
	 *
	 * Authentication precedence:
	 * 1. Explicit AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
	 * 2. Default credential chain (IAM role, environment, config file, etc.)
	 */
	private void createSecretsClient() {
		SecretsManagerClientBuilder builder = SecretsManagerClient.builder();

		// Configure region
		String region = getInput(AWS_REGION);
		if (region == null || (region = region.trim()).isEmpty()) {
			region = DEFAULT_REGION;
			classLogger.info("Using default AWS region: {}", DEFAULT_REGION);
		} else {
			classLogger.info("Using AWS region: {}", region);
		}
		builder.region(Region.of(region));

		// Configure credentials
		String accessKeyId = getInput(AWS_ACCESS_KEY_ID);
		String secretAccessKey = getInput(AWS_SECRET_ACCESS_KEY);

		if (accessKeyId != null && !accessKeyId.trim().isEmpty()
				&& secretAccessKey != null && !secretAccessKey.trim().isEmpty()) {
			classLogger.info("Using explicit AWS credentials");
			builder.credentialsProvider(
				StaticCredentialsProvider.create(
					AwsBasicCredentials.create(accessKeyId, secretAccessKey)
				)
			);
		} else {
			classLogger.info("Using default AWS credential chain");
			builder.credentialsProvider(DefaultCredentialsProvider.create());
		}

		// Configure custom endpoint (for LocalStack, Minio, etc.)
		String endpoint = getInput(AWS_ENDPOINT);
		if (endpoint != null && !(endpoint = endpoint.trim()).isEmpty()) {
			classLogger.info("Using custom AWS Secrets Manager endpoint: {}", endpoint);
			builder.endpointOverride(URI.create(endpoint));
		}

		this.secretsClient = builder.build();

		// Configure secret name prefix
		this.secretPrefix = getInput(SECRETS_NAME_PREFIX);
		if (this.secretPrefix == null || (this.secretPrefix = this.secretPrefix.trim()).isEmpty()) {
			this.secretPrefix = DEFAULT_PREFIX;
		}
		classLogger.info("Using secret name prefix: {}", this.secretPrefix);
	}

	/**
	 * Gets the singleton instance of AWSSecretsManagerUtil.
	 *
	 * @return AWSSecretsManagerUtil instance, or null if initialization fails
	 */
	public static AWSSecretsManagerUtil getInstance() {
		if (instance != null) {
			return instance;
		}

		if (instance == null) {
			synchronized (AWSSecretsManagerUtil.class) {
				if (instance == null) {
					try {
						instance = new AWSSecretsManagerUtil();
					} catch (Exception e) {
						classLogger.error(Constants.STACKTRACE, e);
					}
				}
			}
		}

		return instance;
	}

	/**
	 * Builds the secret name for an engine.
	 * Format: {prefix}/{engine-type}/{engine-id}
	 *
	 * @param eType Engine type
	 * @param engineId Engine ID
	 * @return Formatted secret name
	 */
	private String getSecretNameForEngine(IEngine.CATALOG_TYPE eType, String engineId) {
		String base = getBaseForEngine(eType);
		if (base != null && !(base = base.trim()).isEmpty()) {
			return secretPrefix + "/" + base + "/" + engineId;
		}
		return secretPrefix + "/" + eType.toString().toLowerCase() + "/" + engineId;
	}

	/**
	 * Builds the secret name for an insight.
	 * Format: {prefix}/project/{project-id}/{insight-id}
	 *
	 * @param projectId Project ID
	 * @param insightId Insight ID
	 * @return Formatted secret name
	 */
	private String getSecretNameForInsight(String projectId, String insightId) {
		String base = getBaseForEngine(IEngine.CATALOG_TYPE.PROJECT);
		if (base != null && !(base = base.trim()).isEmpty()) {
			return secretPrefix + "/" + base + "/" + projectId + "/" + insightId;
		}
		return secretPrefix + "/project/" + projectId + "/" + insightId;
	}

	/**
	 * Builds the secret name for insight encryption keys.
	 *
	 * @param projectId Project ID
	 * @param insightId Insight ID
	 * @return Formatted secret name
	 */
	private String getSecretNameForInsightEncryption(String projectId, String insightId) {
		return getSecretNameForInsight(projectId, insightId) + "/" + INSIGHT_ENCRYPTION_NAME;
	}

	@Override
	public Map<String, Object> getEngineSecrets(IEngine.CATALOG_TYPE eType, String engineId, String engineName) {
		String secretName = getSecretNameForEngine(eType, engineId);
		return getSecret(secretName);
	}

	@Override
	public Map<String, Object> getInsightSecrets(String projectId, String projectName, String insightId) {
		String secretName = getSecretNameForInsight(projectId, insightId);
		return getSecret(secretName);
	}

	@Override
	public Map<String, Object> getInsightEncryptionSecrets(String projectId, String projectName, String insightId) {
		String secretName = getSecretNameForInsightEncryption(projectId, insightId);
		return getSecret(secretName);
	}

	/**
	 * Retrieves a secret from AWS Secrets Manager and parses it as a JSON map.
	 *
	 * @param secretName Name of the secret
	 * @return Map of secret values, or empty map if secret doesn't exist or can't be parsed
	 */
	private Map<String, Object> getSecret(String secretName) {
		try {
			GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
				.secretId(secretName)
				.build();

			GetSecretValueResponse getSecretValueResponse = secretsClient.getSecretValue(getSecretValueRequest);
			String secretString = getSecretValueResponse.secretString();

			if (secretString == null || secretString.trim().isEmpty()) {
				classLogger.warn("Secret '{}' exists but has no value", secretName);
				return new HashMap<>();
			}

			// Parse JSON string to Map
			try {
				Gson gson = new GsonBuilder().disableHtmlEscaping().create();
				Map<String, Object> data = gson.fromJson(secretString, new TypeToken<Map<String, Object>>() {}.getType());
				return data != null ? data : new HashMap<>();
			} catch (JsonSyntaxException e) {
				classLogger.error("Invalid JSON format for secret '{}': {}", secretName, e.getMessage());
				throw new IllegalArgumentException(
					"Invalid format for secret storage. Must be a valid JSON object (map)", e);
			}

		} catch (ResourceNotFoundException e) {
			classLogger.debug("Secret '{}' not found, returning empty map", secretName);
			return new HashMap<>();
		} catch (SecretsManagerException e) {
			classLogger.error("Error retrieving secret '{}': {}", secretName, e.awsErrorDetails().errorMessage());
			classLogger.error(Constants.STACKTRACE, e);
			return new HashMap<>();
		}
	}

	@Override
	public boolean appendEngineSecret(IEngine.CATALOG_TYPE eType, String engineId, String engineName, String key,
			Object value) {
		// Retrieve existing secrets
		Map<String, Object> nameValuePairs = getEngineSecrets(eType, engineId, engineName);
		if (nameValuePairs == null) {
			nameValuePairs = new HashMap<>();
		}
		// Add new key-value pair
		nameValuePairs.put(key, value);
		// Write back
		return writeEngineSecrets(eType, engineId, engineName, nameValuePairs);
	}

	@Override
	public boolean writeEngineSecrets(IEngine.CATALOG_TYPE eType, String engineId, String engineName,
			Map<String, Object> nameValuePairs) {
		String secretName = getSecretNameForEngine(eType, engineId);
		return putSecret(secretName, nameValuePairs);
	}

	@Override
	public boolean deleteEngineSecrets(CATALOG_TYPE eType, String engineId, String engineName) {
		String secretName = getSecretNameForEngine(eType, engineId);
		return deleteSecret(secretName);
	}

	@Override
	public boolean writeInsightSecret(String projectId, String projectName, String insightId, String key,
			Object value) {
		// Retrieve existing secrets
		Map<String, Object> nameValuePairs = getInsightSecrets(projectId, projectName, insightId);
		if (nameValuePairs == null) {
			nameValuePairs = new HashMap<>();
		}
		// Add new key-value pair
		nameValuePairs.put(key, value);
		// Write back
		return writeInsightSecrets(projectId, projectName, insightId, nameValuePairs);
	}

	@Override
	public boolean writeInsightSecrets(String projectId, String projectName, String insightId,
			Map<String, Object> nameValuePairs) {
		String secretName = getSecretNameForInsight(projectId, insightId);
		return putSecret(secretName, nameValuePairs);
	}

	@Override
	public boolean writeInsightEncryptionSecrets(String projectId, String projectName, String insightId,
			Map<String, Object> nameValuePairs) {
		String secretName = getSecretNameForInsightEncryption(projectId, insightId);
		return putSecret(secretName, nameValuePairs);
	}

	/**
	 * Stores or updates a secret in AWS Secrets Manager.
	 * Creates the secret if it doesn't exist, updates if it does.
	 *
	 * @param secretName Name of the secret
	 * @param nameValuePairs Map of secret values
	 * @return true if successful, false otherwise
	 */
	private boolean putSecret(String secretName, Map<String, Object> nameValuePairs) {
		try {
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String secretValue = gson.toJson(nameValuePairs);

			try {
				// Try to update existing secret
				PutSecretValueRequest putSecretValueRequest = PutSecretValueRequest.builder()
					.secretId(secretName)
					.secretString(secretValue)
					.build();

				secretsClient.putSecretValue(putSecretValueRequest);
				classLogger.debug("Updated secret: {}", secretName);
				return true;

			} catch (ResourceNotFoundException e) {
				// Secret doesn't exist, create it
				CreateSecretRequest createSecretRequest = CreateSecretRequest.builder()
					.name(secretName)
					.secretString(secretValue)
					.description("SEMOSS secret for " + secretName)
					.build();

				secretsClient.createSecret(createSecretRequest);
				classLogger.info("Created new secret: {}", secretName);
				return true;
			}

		} catch (SecretsManagerException e) {
			classLogger.error("Error writing secret '{}': {}", secretName, e.awsErrorDetails().errorMessage());
			classLogger.error(Constants.STACKTRACE, e);
			return false;
		}
	}

	/**
	 * Deletes a secret from AWS Secrets Manager.
	 * Note: By default, secrets are scheduled for deletion with a recovery window.
	 *
	 * @param secretName Name of the secret to delete
	 * @return true if successful, false otherwise
	 */
	private boolean deleteSecret(String secretName) {
		try {
			DeleteSecretRequest deleteSecretRequest = DeleteSecretRequest.builder()
				.secretId(secretName)
				.forceDeleteWithoutRecovery(false) // Allow recovery for safety
				.recoveryWindowInDays(7L) // 7-day recovery window
				.build();

			DeleteSecretResponse response = secretsClient.deleteSecret(deleteSecretRequest);
			classLogger.info("Scheduled deletion of secret '{}', can recover until: {}",
				secretName, response.deletionDate());
			return true;

		} catch (ResourceNotFoundException e) {
			classLogger.debug("Secret '{}' not found, nothing to delete", secretName);
			return true; // Consider this success since secret doesn't exist
		} catch (SecretsManagerException e) {
			classLogger.error("Error deleting secret '{}': {}", secretName, e.awsErrorDetails().errorMessage());
			classLogger.error(Constants.STACKTRACE, e);
			return false;
		}
	}

	/**
	 * Closes the secrets manager client and releases resources.
	 */
	public void close() {
		if (secretsClient != null) {
			try {
				secretsClient.close();
				classLogger.debug("Closed AWS Secrets Manager client");
			} catch (Exception e) {
				classLogger.error("Error closing AWS Secrets Manager client", e);
			}
		}
	}
}
