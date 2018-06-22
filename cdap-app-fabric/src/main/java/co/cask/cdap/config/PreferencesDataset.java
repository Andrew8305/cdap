/*
 * Copyright © 2018 Cask Data, Inc.
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

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.data.DatasetContext;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.proto.element.EntityType;
import co.cask.cdap.proto.id.ApplicationId;
import co.cask.cdap.proto.id.EntityId;
import co.cask.cdap.proto.id.InstanceId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.ParentedId;
import co.cask.cdap.proto.id.ProgramId;

import java.util.HashMap;
import java.util.Map;

public class PreferencesDataset {
  private static final String PREFERENCES_CONFIG_TYPE = "preferences";
  // Namespace where instance level properties are stored
  private static final String EMPTY_NAMESPACE = "";
  // Id for Properties config stored at the instance level
  private static final String INSTANCE_PROPERTIES = "instance";

  private final ConfigDataset configDataset;

  private PreferencesDataset(ConfigDataset configDataset) {
    this.configDataset = configDataset;
  }

  public static PreferencesDataset get(DatasetContext datasetContext, DatasetFramework dsFramework) {
    return new PreferencesDataset(ConfigDataset.get(datasetContext, dsFramework));
  }

  private Map<String, String> getConfigProperties(String namespace, String id) {
    Map<String, String> value = new HashMap<>();
    try {
      Config config = configDataset.get(namespace, PREFERENCES_CONFIG_TYPE, id);
      value.putAll(config.getProperties());
    } catch (ConfigNotFoundException e) {
      //no-op - return empty map
    }
    return value;
  }

  private void setConfig(String namespace, String id, Map<String, String> propertyMap) {
    Config config = new Config(id, propertyMap);
    configDataset.createOrUpdate(namespace, PREFERENCES_CONFIG_TYPE, config);
  }

  private void deleteConfig(String namespace, String id) {
    try {
      configDataset.delete(namespace, PREFERENCES_CONFIG_TYPE, id);
    } catch (ConfigNotFoundException e) {
      //no-op
    }
  }

  public Map<String, String> getProperties(EntityId entityId) {
    switch (entityId.getEntityType()) {
      case INSTANCE:
        return getConfigProperties(EMPTY_NAMESPACE, getMultipartKey(INSTANCE_PROPERTIES));
      case NAMESPACE:
        NamespaceId namespaceId = (NamespaceId) entityId;
        return getConfigProperties(namespaceId.getNamespace(), getMultipartKey(namespaceId.getNamespace()));
      case APPLICATION:
        ApplicationId appId = (ApplicationId) entityId;
        return getConfigProperties(appId.getNamespace(), getMultipartKey(appId.getNamespace(), appId.getApplication()));
      case PROGRAM:
        ProgramId programId = (ProgramId) entityId;
        return getConfigProperties(programId.getNamespace(),
                                   getMultipartKey(programId.getNamespace(), programId.getApplication(),
                                                   programId.getType().getCategoryName(), programId.getProgram()));
      default:
        return new HashMap<>();
    }
  }

  public Map<String, String> getResolvedProperties(EntityId entityId) {
    // if it is instance level get the properties and return
    if (entityId.getEntityType().equals(EntityType.INSTANCE)) {
      return getProperties(entityId);
    }

    // get properties from current level
    Map<String, String> properties = getProperties(entityId);
    // if the entity id has a parent id, get the preference from its parent
    if (entityId instanceof ParentedId) {
      properties.putAll(getProperties(((ParentedId) entityId).getParent()));
    } else {
      // otherwise it is a namespace id, which we want to look at the instance level
      properties.putAll(getProperties(new InstanceId("")));
    }
    return properties;
  }

  public void setProperties(EntityId entityId, Map<String, String> propMap) {
    switch (entityId.getEntityType()) {
      case INSTANCE:
        setConfig(EMPTY_NAMESPACE, getMultipartKey(INSTANCE_PROPERTIES), propMap);
        break;
      case NAMESPACE:
        NamespaceId namespaceId = (NamespaceId) entityId;
        setConfig(namespaceId.getNamespace(), getMultipartKey(namespaceId.getNamespace()), propMap);
        break;
      case APPLICATION:
        ApplicationId appId = (ApplicationId) entityId;
        setConfig(appId.getNamespace(), getMultipartKey(appId.getNamespace(), appId.getApplication()), propMap);
        break;
      case PROGRAM:
        ProgramId programId = (ProgramId) entityId;
        setConfig(programId.getNamespace(),
                  getMultipartKey(programId.getNamespace(), programId.getApplication(),
                                  programId.getType().getCategoryName(), programId.getProgram()), propMap);
        break;
      default:
        // do nothing
    }
  }

  public void deleteProperties(EntityId entityId) {
    switch (entityId.getEntityType()) {
      case INSTANCE:
        deleteConfig(EMPTY_NAMESPACE, getMultipartKey(INSTANCE_PROPERTIES));
        break;
      case NAMESPACE:
        NamespaceId namespaceId = (NamespaceId) entityId;
        deleteConfig(namespaceId.getNamespace(), getMultipartKey(namespaceId.getNamespace()));
        break;
      case APPLICATION:
        ApplicationId appId = (ApplicationId) entityId;
        deleteConfig(appId.getNamespace(), getMultipartKey(appId.getNamespace(), appId.getApplication()));
        break;
      case PROGRAM:
        ProgramId programId = (ProgramId) entityId;
        deleteConfig(programId.getNamespace(),
                     getMultipartKey(programId.getNamespace(), programId.getApplication(),
                                     programId.getType().getCategoryName(), programId.getProgram()));
        break;
      default:
        // do nothing
    }
  }

  private String getMultipartKey(String... parts) {
    int sizeOfParts = 0;
    for (String part : parts) {
      sizeOfParts += part.length();
    }

    byte[] result = new byte[sizeOfParts + (parts.length * Bytes.SIZEOF_INT)];

    int offset = 0;
    for (String part : parts) {
      Bytes.putInt(result, offset, part.length());
      offset += Bytes.SIZEOF_INT;
      Bytes.putBytes(result, offset, part.getBytes(), 0, part.length());
      offset += part.length();
    }
    return Bytes.toString(result);
  }
}
