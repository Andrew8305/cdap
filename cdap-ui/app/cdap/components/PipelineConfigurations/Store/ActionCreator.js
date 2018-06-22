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

import PipelineConfigurationsStore, {ACTIONS as PipelineConfigurationsActions} from 'components/PipelineConfigurations/Store';
import PipelineDetailStore, {ACTIONS as PipelineDetailActions} from 'components/PipelineDetails/store';
import {setRunButtonLoading, setRunError, setScheduleButtonLoading, setScheduleError, fetchScheduleStatus} from 'components/PipelineDetails/store/ActionCreator';
import KeyValueStore, {getDefaultKeyValuePair} from 'components/KeyValuePairs/KeyValueStore';
import KeyValueStoreActions, {convertKeyValuePairsObjToMap} from 'components/KeyValuePairs/KeyValueStoreActions';
import {GLOBALS} from 'services/global-constants';
import {MyPipelineApi} from 'api/pipeline';
import {MyProgramApi} from 'api/program';
import {getCurrentNamespace} from 'services/NamespaceStore';
import cloneDeep from 'lodash/cloneDeep';
import { MyPreferenceApi } from 'api/preference';
import {PROFILE_NAME_PREFERENCE_PROPERTY, PROFILE_PROPERTIES_PREFERENCE} from 'components/PipelineDetails/ProfilesListView';
import {convertKeyValuePairsToMap, objectQuery} from 'services/helpers';
import isEqual from 'lodash/isEqual';
import uuidV4 from 'uuid/v4';
import uniqBy from 'lodash/uniqBy';


const applyRuntimeArgs = () => {
  let runtimeArgs = PipelineConfigurationsStore.getState().runtimeArgs;
  PipelineConfigurationsStore.dispatch({
    type: PipelineConfigurationsActions.SET_SAVED_RUNTIME_ARGS,
    payload: { savedRuntimeArgs: cloneDeep(runtimeArgs) }
  });
};

// Filter certain preferences from being shown in the run time arguments 
// They are being represented in other places (like selected compute profile).
const getFilteredRuntimeArgs = (hideProvided) => {
  const RUNTIME_ARGS_TO_SKIP_DURING_DISPLAY = [
    PROFILE_NAME_PREFERENCE_PROPERTY,
    PROFILE_PROPERTIES_PREFERENCE,
    'logical.start.time'
  ];
  let {runtimeArgs, resolvedMacros} = PipelineConfigurationsStore.getState();
  let modifiedRuntimeArgs = {};
  let pairs = [...runtimeArgs.pairs];
  const skipIfProfilePropMatch = (prop) => {
    let isMatch = RUNTIME_ARGS_TO_SKIP_DURING_DISPLAY.filter(skipProp => prop.indexOf(skipProp) !== -1);
    return isMatch.length ? true : false;
  };
  pairs = pairs
    .filter(pair => !skipIfProfilePropMatch(pair.key))
    .map(pair => {
      if (pair.key in resolvedMacros) {
        return {
          notDeletable: true,
          provided: hideProvided ? null : pair.provided || false,
          ...pair
        };
      }
      return {
        ...pair,
        // This is needed because KeyValuePair will render a checkbox only if the provided is a boolean.
        provided: null
      };
    });
  if (!pairs.length) {
    pairs.push(getDefaultKeyValuePair());
  }
  modifiedRuntimeArgs.pairs = pairs;
  return modifiedRuntimeArgs;
};

// While adding runtime argument make sure to include the excluded preferences
const updateRunTimeArgs = (rtArgs) => {
  let {runtimeArgs} = PipelineConfigurationsStore.getState();
  let modifiedRuntimeArgs = {};
  let excludedPairs = [...runtimeArgs.pairs];
  const preferencesToFilter = [PROFILE_NAME_PREFERENCE_PROPERTY, PROFILE_PROPERTIES_PREFERENCE];
  const shouldExcludeProperty = (property) => preferencesToFilter.filter(prefProp => property.indexOf(prefProp) !== -1).length;
  excludedPairs = excludedPairs.filter(pair => shouldExcludeProperty(pair.key));
  modifiedRuntimeArgs.pairs = rtArgs.pairs.concat(excludedPairs);
  updatePipelineEditStatus();
  PipelineConfigurationsStore.dispatch({
    type: PipelineConfigurationsActions.SET_RUNTIME_ARGS,
    payload: {
      runtimeArgs: modifiedRuntimeArgs
    }
  });
};

const revertConfigsToSavedValues = () => {
  let savedRuntimeArgs = PipelineConfigurationsStore.getState().savedRuntimeArgs;
  KeyValueStore.dispatch({
    type: KeyValueStoreActions.onUpdate,
    payload: { pairs: savedRuntimeArgs.pairs }
  });
  PipelineConfigurationsStore.dispatch({
    type: PipelineConfigurationsActions.INITIALIZE_CONFIG,
    payload: {...PipelineDetailStore.getState().config}
  });
  PipelineConfigurationsStore.dispatch({
    type: PipelineConfigurationsActions.SET_RUNTIME_ARGS,
    payload: { runtimeArgs: cloneDeep(savedRuntimeArgs) }
  });
  PipelineConfigurationsStore.dispatch({
    type: PipelineConfigurationsActions.SET_MODELESS_OPEN_STATUS,
    payload: { open: false }
  });
};

const updateKeyValueStore = () => {
  let runtimeArgsPairs = PipelineConfigurationsStore.getState().runtimeArgs.pairs;
  KeyValueStore.dispatch({
    type: KeyValueStoreActions.onUpdate,
    payload: { pairs: runtimeArgsPairs }
  });
};

const getMacrosResolvedByPrefs = (resolvedPrefs = {}, macrosMap = {}) => {
  let resolvedMacros = {...macrosMap};
  for (let pref in resolvedPrefs) {
    if (resolvedPrefs.hasOwnProperty(pref) && resolvedMacros.hasOwnProperty(pref)) {
      resolvedMacros[pref] = resolvedPrefs[pref];
    }
  }
  return resolvedMacros;
};

const updatePreferences = () => {
  let {runtimeArgs} = PipelineConfigurationsStore.getState();
  let appId = PipelineDetailStore.getState().name;
  let prefObj = convertKeyValuePairsObjToMap(runtimeArgs);

  return MyPreferenceApi.setAppPreferences({
    namespace: getCurrentNamespace(),
    appId
  }, prefObj);
};

const updatePipelineEditStatus = () => {
  const isResourcesEqual = (oldvalue, newvalue) => {
    return oldvalue.memoryMB === newvalue.memoryMB && oldvalue.virtualCores === newvalue.virtualCores;
  };

  const getRunTimeArgsModified = (savedRunTime = [], newRunTime = []) => {
    let savedRunTimeObj = convertKeyValuePairsToMap(savedRunTime);
    let newRunTimeObj = convertKeyValuePairsToMap(newRunTime);
    return !isEqual(savedRunTimeObj, newRunTimeObj);
  };

  let oldConfig = PipelineDetailStore.getState().config;
  let updatedConfig = PipelineConfigurationsStore.getState();

  // These are the values that user can modify in Detail view
  let isResourcesModified = !isResourcesEqual(oldConfig.resources, updatedConfig.resources);
  let isDriverResourcesModidified = !isResourcesEqual(oldConfig.driverResources, updatedConfig.driverResources);
  let isInstrumentationModified = oldConfig.processTimingEnabled !== updatedConfig.processTimingEnabled;
  let isStageLoggingModified = oldConfig.stageLoggingEnabled !== updatedConfig.stageLoggingEnabled;
  let isCustomEngineConfigModified = oldConfig.properties !== updatedConfig.properties;
  let isRunTimeArgsModified = getRunTimeArgsModified(updatedConfig.runtimeArgs.pairs, updatedConfig.savedRuntimeArgs.pairs);

  let isModified = (
    isResourcesModified ||
    isDriverResourcesModidified ||
    isInstrumentationModified ||
    isStageLoggingModified ||
    isCustomEngineConfigModified ||
    isRunTimeArgsModified
  );

  if (PipelineDetailStore.getState().artifact.name === GLOBALS.etlDataStreams) {
    let isClientResourcesModified = !isResourcesEqual(oldConfig.clientResources, updatedConfig.clientResources);
    let isBatchIntervalModified = oldConfig.batchInterval !== updatedConfig.batchInterval;
    let isCheckpointModified = oldConfig.disableCheckpoints !== updatedConfig.disableCheckpoints;
    isModified = isModified || isClientResourcesModified || isBatchIntervalModified || isCheckpointModified;
  }

  if (isModified !== PipelineConfigurationsStore.getState().pipelineEdited) {
    PipelineConfigurationsStore.dispatch({
      type: PipelineConfigurationsActions.SET_PIPELINE_EDIT_STATUS,
      payload: { pipelineEdited: isModified }
    });
  }
};

const updatePipeline = () => {
  let detailStoreState = PipelineDetailStore.getState();
  let { name, description, artifact, principal } = detailStoreState;
  let { stages, connections, comments } = detailStoreState.config;

  let {
    batchInterval,
    engine,
    resources,
    driverResources,
    clientResources,
    postActions,
    properties,
    processTimingEnabled,
    stageLoggingEnabled,
    disableCheckpoints,
    stopGracefully,
    schedule,
    maxConcurrentRuns,
  } = PipelineConfigurationsStore.getState();

  let commonConfig = {
    stages,
    connections,
    comments,
    resources,
    driverResources,
    postActions,
    properties,
    processTimingEnabled,
    stageLoggingEnabled
  };

  let batchOnlyConfig = {
    engine,
    schedule,
    maxConcurrentRuns
  };

  let realtimeOnlyConfig = {
    batchInterval,
    clientResources,
    disableCheckpoints,
    stopGracefully
  };

  let config;
  if (artifact.name === GLOBALS.etlDataPipeline) {
    config = {...commonConfig, ...batchOnlyConfig};
  } else {
    config = {...commonConfig, ...realtimeOnlyConfig};
  }

  let publishObservable = MyPipelineApi.publish({
    namespace: getCurrentNamespace(),
    appId: name
  }, {
    name,
    description,
    artifact,
    config,
    principal
  });

  publishObservable.subscribe(() => {
    PipelineDetailStore.dispatch({
      type: PipelineDetailActions.SET_CONFIG,
      payload: { config }
    });
  });

  return publishObservable;
};

const runPipeline = () => {
  setRunButtonLoading(true);
  let { name, artifact } = PipelineDetailStore.getState();

  let params = {
    namespace: getCurrentNamespace(),
    appId: name,
    programType: GLOBALS.programType[artifact.name],
    programId: GLOBALS.programId[artifact.name],
    action: 'start'
  };
  MyProgramApi.action(params)
  .subscribe(
    () => {},
    (err) => {
    setRunButtonLoading(false);
    setRunError(err.response || err);
  });
};

const schedulePipeline = () => {
  scheduleOrSuspendPipeline(MyPipelineApi.schedule);
};

const suspendSchedule = () => {
  scheduleOrSuspendPipeline(MyPipelineApi.suspend);
};

const scheduleOrSuspendPipeline = (scheduleApi) => {
  setScheduleButtonLoading(true);
  let { name } = PipelineDetailStore.getState();

  let params = {
    namespace: getCurrentNamespace(),
    appId: name,
    scheduleId: GLOBALS.defaultScheduleId
  };
  scheduleApi(params)
  .subscribe(() => {
    setScheduleButtonLoading(false);
    fetchScheduleStatus(params);
  }, (err) => {
    setScheduleButtonLoading(false);
    setScheduleError(err.response || err);
  });
};

const getCustomizationMap = (properties) => {
  let profileCustomizations = {};
  Object.keys(properties).forEach(prop => {
    if (prop.indexOf(PROFILE_PROPERTIES_PREFERENCE) !== -1) {
      let propName = prop.replace(`${PROFILE_PROPERTIES_PREFERENCE}.`, '');
      profileCustomizations[propName] = properties[prop];
    }
  });
  return profileCustomizations;
};

const fetchAndUpdateRuntimeArgs = () => {
  const params = {
    namespace: getCurrentNamespace(),
    appId: PipelineDetailStore.getState().name
  };

  let observable$ = MyPipelineApi.fetchMacros(params)
    .combineLatest([
      MyPreferenceApi.getAppPreferences(params),
      // This is required to resolve macros from preferences
      // Say DEFAULT_STREAM is a namespace level preference used as a macro
      // in one of the plugins in the pipeline.
      MyPreferenceApi.getAppPreferencesResolved(params)
    ]);

  observable$.subscribe((res) => {
    let macrosSpec = res[0];
    let macrosMap = {};
    let macros = [];
    macrosSpec.map(ms => {
      if (objectQuery(ms, 'spec', 'properties', 'macros', 'lookupProperties')) {
        macros = macros.concat(ms.spec.properties.macros.lookupProperties);
      }
    });
    macros.forEach(macro => {
      macrosMap[macro] = '';
    });

    let currentAppPrefs = res[1];
    let currentAppResolvedPrefs = res[2];
    let resolvedMacros = getMacrosResolvedByPrefs(currentAppResolvedPrefs, macrosMap);
    // When a pipeline is published there won't be any profile related information
    // at app level preference. However the pipeline, when run will be run with the 'default'
    // profile that is set at the namespace level. So we populate in UI the default
    // profile for a pipeline until the user choose something else. This is populated from
    // resolved app level preference which will provide preferences from namespace.
    const isProfileProperty = (property) => (
      [PROFILE_NAME_PREFERENCE_PROPERTY, PROFILE_PROPERTIES_PREFERENCE]
        .filter(profilePrefix => property.indexOf(profilePrefix) !== -1)
        .length
    );
    Object.keys(currentAppResolvedPrefs).forEach(resolvePref => {
      if (isProfileProperty(resolvePref) !== 0) {
        currentAppPrefs[resolvePref] = currentAppResolvedPrefs[resolvePref];
      }
    });

    PipelineConfigurationsStore.dispatch({
      type: PipelineConfigurationsActions.SET_RESOLVED_MACROS,
      payload: { resolvedMacros }
    });
    const getPairs = (map) => (
      Object
        .entries(map)
        .filter(([key]) => key.length)
        .map(([key, value]) => ({
          key, value,
          uniqueId: uuidV4()
        }))
    );
    let runtimeArgsPairs = getPairs(currentAppPrefs);
    let resolveMacrosPairs = getPairs(resolvedMacros);

    PipelineConfigurationsStore.dispatch({
      type: PipelineConfigurationsActions.SET_RUNTIME_ARGS,
      payload: {
        runtimeArgs: {
          pairs: uniqBy(runtimeArgsPairs.concat(resolveMacrosPairs), (pair) => pair.key)
        }
      }
    });
  });
  return observable$;
};

const reset = () => {
  PipelineConfigurationsStore.dispatch({
    type: PipelineConfigurationsActions.RESET
  });
};

export {
  applyRuntimeArgs,
  getFilteredRuntimeArgs,
  updateRunTimeArgs,
  revertConfigsToSavedValues,
  updateKeyValueStore,
  getMacrosResolvedByPrefs,
  updatePipelineEditStatus,
  updatePipeline,
  updatePreferences,
  runPipeline,
  schedulePipeline,
  suspendSchedule,
  getCustomizationMap,
  fetchAndUpdateRuntimeArgs,
  reset
};
