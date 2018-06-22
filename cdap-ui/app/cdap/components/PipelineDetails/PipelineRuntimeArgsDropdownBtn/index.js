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

import React, {Component} from 'react';
import ConfigurableTab from 'components/ConfigurableTab';
import TabConfig from 'components/PipelineDetails/PipelineRuntimeArgsDropdownBtn/RunTabs/TabConfig';
import IconSVG from 'components/IconSVG';
import PipelineModeless from 'components/PipelineDetails/PipelineModeless';
import classnames from 'classnames';
import Popover from 'components/Popover';
import {Provider} from 'react-redux';
import PipelineConfigurationsStore from 'components/PipelineConfigurations/Store';
require('./PipelineRuntimeArgsDropdownBtn.scss');

export default class PipelineRuntimeArgsDropdownBtn extends Component {

  state = {
    showRunOptions: false
  };

  toggleRunConfigOption = (showRunOptions) => {
    this.setState({
      showRunOptions
    });
  };

  render() {
    const Btn = (
      <div className={classnames("btn pipeline-action-btn pipeline-run-btn", {
        'btn-popover-open': this.state.showRunOptions
      })}>
        <IconSVG name="icon-caret-down" />
      </div>
    );
    return (
      <Provider store={PipelineConfigurationsStore}>
        <Popover
          target={() => Btn}
          className="arrow-btn-container"
          placement="bottom"
          enableInteractionInPopover={true}
          showPopover={this.state.showRunOptions}
          onTogglePopover={this.toggleRunConfigOption}
          injectOnToggle={true}
        >
          <PipelineModeless
            title="Runtime Arguments"
            onClose={this.toggleRunConfigOption}
          >
            <ConfigurableTab tabConfig={TabConfig} />
          </PipelineModeless>
        </Popover>
      </Provider>
    );
  }
}
