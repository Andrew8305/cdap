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
import React, {Component} from 'react';
import ConfigurableTab from 'components/ConfigurableTab';
import IconSVG from 'components/IconSVG';
import PipelineModeless from 'components/PipelineDetails/PipelineModeless';
import classnames from 'classnames';
import Popover from 'components/Popover';
import {Provider} from 'react-redux';
import PipelineConfigurationsStore from 'components/PipelineConfigurations/Store';
import PipelineRunTimeArgsAllRuns from 'components/PipelineDetails/PipelineRuntimeArgsDropdownBtn/RunTabs/AllRuns';
import OneRun from 'components/PipelineDetails/PipelineRuntimeArgsDropdownBtn/RunTabs/OneRun';
import {fetchAndUpdateRuntimeArgs} from 'components/PipelineConfigurations/Store/ActionCreator';
import {preventPropagation} from 'services/helpers';

require('./PipelineRuntimeArgsDropdownBtn.scss');
require('./RunTabs/RunTabs.scss');

export default class PipelineRuntimeArgsDropdownBtn extends Component {

  static propTypes = {
    showRunOptions: PropTypes.bool,
    onToggle: PropTypes.fun,
    disabled: PropTypes.bool
  };

  static defaultProps = {
    showRunOptions: false
  };

  state = {
    showRunOptions: this.props.showRunOptions
  };

  toggleRunConfigOption = (showRunOptions) => {
    if (showRunOptions === this.state.showRunOptions) {
      return;
    }
    this.setState({
      showRunOptions: showRunOptions || !this.state.showRunOptions
    }, () => {
      if (this.state.showRunOptions) {
        // FIXME: This can become expensive since each time the user opens the 
        // run time options modeless we make this call to stay upto date to the backend.
        fetchAndUpdateRuntimeArgs();
      }
      if (this.props.onToggle) {
        this.props.onToggle(this.state.showRunOptions);
      }
    });
  };

  TabConfig = {
    tabs: [
      {
        id: 1,
        name: 'Set for all runs',
        content: (<PipelineRunTimeArgsAllRuns onClose={this.toggleRunConfigOption} />),
      },
      {
        id: 2,
        name: 'Set for one run',
        content: (<OneRun onClose={this.toggleRunConfigOption} />),
      }
    ],
    layout: 'vertical',
    defaultTab: 1
  };

  componentWillReceiveProps = (nextProps) => {
    if (nextProps.showRunOptions !== this.state.showRunOptions) {
      this.setState({
        showRunOptions: nextProps.showRunOptions
      });
    }
  };

  render() {
    const Btn = (
      <div
        className={classnames("btn pipeline-action-btn pipeline-run-btn", {
          'btn-popover-open': this.state.showRunOptions
        })}
        onClick={(e) => {
          /*
            ugh. This is NOT a good approach.
            The react-popper should have had a disabled prop that
            which passed on should just disable the popover like a button.
            Right I am circumventing that by preventing the propagation/bubbling the event
            here so it looks like it is disabled.

            We should upgrade react-popper and see if later versions have this prop.
          */
          if (this.props.disabled) {
            preventPropagation(e);
            return false;
          }
        }}
      >
        <IconSVG name="icon-caret-down" />
      </div>
    );
    return (
      <fieldset disabled={this.props.disabled}>
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
              <ConfigurableTab tabConfig={this.TabConfig} />
            </PipelineModeless>
          </Popover>
        </Provider>
      </fieldset>
    );
  }
}
