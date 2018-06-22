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

import React from 'react';
import RuntimeArgsTabContent from 'components/PipelineDetails/PipelineRuntimeArgsDropdownBtn/RuntimeArgsTabContent';

export default function PipelineRunTimeArgsAllRuns() {
  return (
    <div className="runtime-args-tab-wrapper">
      <div>
        <strong> Set Runtime Arguments for all the future runs of this pipeline. </strong>
        <div>
          The values you set here, will be used by all the runs of the pipeline that will
          occur from now until you change the Runtime Arguments.
        </div>
      </div>

      <RuntimeArgsTabContent />

      <div className="btns-container">
        <div className="btn btn-primary">
          Save
        </div>
        <div className="btn btn-secondary">
          Save and Run
        </div>
      </div>
    </div>
  );
}
