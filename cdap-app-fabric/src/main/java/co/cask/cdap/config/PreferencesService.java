/*
 * Copyright © 2015-2018 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.config;

import co.cask.cdap.api.Transactional;
import co.cask.cdap.api.Transactionals;
import co.cask.cdap.common.NotFoundException;
import co.cask.cdap.common.ProfileConflictException;
import co.cask.cdap.data.dataset.SystemDatasetInstantiator;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.data2.dataset2.MultiThreadDatasetCache;
import co.cask.cdap.data2.transaction.Transactions;
import co.cask.cdap.internal.app.runtime.SystemArguments;
import co.cask.cdap.internal.app.store.profile.ProfileDataset;
import co.cask.cdap.internal.profile.ProfileMetadataPublisher;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.id.ApplicationId;
import co.cask.cdap.proto.id.EntityId;
import co.cask.cdap.proto.id.InstanceId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.NamespacedEntityId;
import co.cask.cdap.proto.id.ProfileId;
import co.cask.cdap.proto.id.ProgramId;
import co.cask.cdap.runtime.spi.profile.ProfileStatus;
import com.google.inject.Inject;
import org.apache.tephra.RetryStrategies;
import org.apache.tephra.TransactionSystemClient;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Use config dataset to perform operations around preference.
 */
public class PreferencesService {

  private final DatasetFramework datasetFramework;
  private final Transactional transactional;
  private final ProfileMetadataPublisher profileMetadataPublisher;

  @Inject
  public PreferencesService(DatasetFramework datasetFramework, TransactionSystemClient txClient,
                            ProfileMetadataPublisher profileMetadataPublisher) {
    this.datasetFramework = datasetFramework;
    this.transactional = Transactions.createTransactionalWithRetry(
      Transactions.createTransactional(new MultiThreadDatasetCache(new SystemDatasetInstantiator(datasetFramework),
        txClient, NamespaceId.SYSTEM,
        Collections.emptyMap(), null, null)),
      RetryStrategies.retryOnConflict(20, 100)
    );
    this.profileMetadataPublisher = profileMetadataPublisher;
  }

  private Map<String, String> getConfigProperties(EntityId entityId) {
    return Transactionals.execute(transactional, context -> {
      return PreferencesDataset.get(context, datasetFramework).getProperties(entityId);
    });
  }

  /**
   * Validate the profile status is enabled and set the preferences in same transaction
   */
  private void setConfig(EntityId entityId,
                         Map<String, String> propertyMap) throws NotFoundException, ProfileConflictException {
    Transactionals.execute(transactional, context -> {
      ProfileDataset profileDataset = ProfileDataset.get(context, datasetFramework);
      PreferencesDataset preferencesDataset = PreferencesDataset.get(context, datasetFramework);

      // need to get old property and check if it contains profile information
      Map<String, String> oldProperties = preferencesDataset.getProperties(entityId);
      preferencesDataset.setProperties(entityId, propertyMap);
      NamespaceId namespaceId =
        entityId instanceof NamespacedEntityId ? ((NamespacedEntityId) entityId).getNamespaceId() : NamespaceId.SYSTEM;

      // validate the profile and publish the necessary metadata change if the profile exists in the property
      Optional<ProfileId> profile = SystemArguments.getProfileIdFromArgs(namespaceId, propertyMap);
      Optional<ProfileId> oldProfile = SystemArguments.getProfileIdFromArgs(namespaceId, oldProperties);
      if (profile.isPresent()) {
        ProfileId profileId = profile.get();
        if (profileDataset.getProfile(profileId).getStatus() == ProfileStatus.DISABLED) {
          throw new ProfileConflictException(String.format("Profile %s in namespace %s is disabled. It cannot be " +
                                                             "assigned to any programs or schedules",
                                                           profileId.getProfile(), profileId.getNamespace()),
                                             profileId);
        }
      }

      if (profile.isPresent() || oldProfile.isPresent()) {
        profileMetadataPublisher.updateProfileMetadata(entityId);
      }
    }, NotFoundException.class, ProfileConflictException.class);
  }

  private void deleteConfig(EntityId entityId) {
    Transactionals.execute(transactional, context -> {
      PreferencesDataset dataset = PreferencesDataset.get(context, datasetFramework);
      Map<String, String> oldProp = dataset.getProperties(entityId);
      dataset.deleteProperties(entityId);
      if (oldProp.containsKey(SystemArguments.PROFILE_NAME)) {
        profileMetadataPublisher.updateProfileMetadata(entityId);
      }
    });
  }

  public Map<String, String> getProperties() {
    return getConfigProperties(new InstanceId(""));
  }

  public Map<String, String> getProperties(String namespace) {
    return getConfigProperties(new NamespaceId(namespace));
  }

  public Map<String, String> getProperties(String namespace, String appId) {
    return getConfigProperties(new ApplicationId(namespace, appId));
  }

  public Map<String, String> getProperties(String namespace, String appId, String programType, String programId) {
    return getConfigProperties(new ProgramId(namespace, appId,
                                             ProgramType.valueOfCategoryName(programType), programId));
  }

  public Map<String, String> getResolvedProperties() {
    return getProperties();
  }

  public Map<String, String> getResolvedProperties(String namespace) {
    Map<String, String> propMap = getResolvedProperties();
    propMap.putAll(getProperties(namespace));
    return propMap;
  }

  public Map<String, String> getResolvedProperties(String namespace, String appId) {
    Map<String, String> propMap = getResolvedProperties(namespace);
    propMap.putAll(getProperties(namespace, appId));
    return propMap;
  }

  public Map<String, String> getResolvedProperties(String namespace, String appId, String programType,
                                                   String programId) {
    Map<String, String> propMap = getResolvedProperties(namespace, appId);
    propMap.putAll(getProperties(namespace, appId, programType, programId));
    return propMap;
  }

  public void setProperties(Map<String, String> propMap) throws NotFoundException, ProfileConflictException {
    setConfig(new InstanceId(""), propMap);
  }

  public void setProperties(String namespace,
                            Map<String, String> propMap) throws NotFoundException, ProfileConflictException {
    setConfig(new NamespaceId(namespace), propMap);
  }

  public void setProperties(String namespace, String appId, Map<String, String> propMap)
    throws NotFoundException, ProfileConflictException {
    setConfig(new ApplicationId(namespace, appId), propMap);
  }

  public void setProperties(String namespace, String appId, String programType, String programId,
                            Map<String, String> propMap) throws NotFoundException, ProfileConflictException {
    setConfig(new ProgramId(namespace, appId, ProgramType.valueOfCategoryName(programType), programId), propMap);
  }

  public void deleteProperties() {
    deleteConfig(new InstanceId(""));
  }

  public void deleteProperties(String namespace) {
    deleteConfig(new NamespaceId(namespace));
  }

  public void deleteProperties(String namespace, String appId) {
    deleteConfig(new ApplicationId(namespace, appId));
  }

  public void deleteProperties(String namespace, String appId, String programType, String programId) {
    deleteConfig(new ProgramId(namespace, appId, ProgramType.valueOfCategoryName(programType), programId));
  }
}
