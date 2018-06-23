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

import PropTypes from 'prop-types';
import React, {PureComponent} from 'react';
import RuntimeArgsTabContent from 'components/PipelineDetails/PipelineRuntimeArgsDropdownBtn/RuntimeArgsTabContent';
import {updatePreferences, runPipeline} from 'components/PipelineConfigurations/Store/ActionCreator';
import BtnWithLoading from 'components/BtnWithLoading';

export default class PipelineRunTimeArgsAllRuns extends PureComponent {

  static propTypes = {
    onClose: PropTypes.func.isRequired
  };

  state = {
    saving: false,
    savingAndRun: false,
    error: null
  };

  toggleSaving = () => {
    this.setState({
      saving: !this.state.saving
    });
  };

  toggleSavingAndRun = () => {
    this.setState({
      savingAndRun: !this.state.savingAndRun
    });
  };

  saveRuntimeArgs = () => {
    this.toggleSaving();
    updatePreferences().subscribe(
      () => {
        this.props.onClose();
      },
      (err) => {
        this.setState({
          error: err.response || JSON.stringify(err),
          saving: false
        });
      }
    );
  };

  saveRuntimeArgsAndRun = () => {
    this.toggleSavingAndRun();
    updatePreferences().subscribe(
      () => {
        runPipeline();
        this.props.onClose();
      },
      (err) => {
        this.setState({
          error: err.response || JSON.stringify(err),
          savingAndRun: false
        });
      }
    );
  }

  render() {
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
          <BtnWithLoading
            loading={this.state.saving}
            className="btn btn-primary"
            onClick={this.saveRuntimeArgs}
            disabled={this.state.saving || this.state.savingAndRun}
            label="Save"
          />
          <BtnWithLoading
            loading={this.state.savingAndRun}
            className="btn btn-secondary"
            onClick={this.saveRuntimeArgsAndRun}
            disabled={this.state.saving || this.state.savingAndRun}
            label="Save And Run"
          />
        </div>
      </div>
    );
  }
}
