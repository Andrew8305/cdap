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
import PropTypes from 'prop-types';
import {connect} from 'react-redux';
import {handleRunsPageChange} from 'components/Reports/store/ActionCreator';
import PaginationWithTitle from 'components/PaginationWithTitle';

function RunsPaginationView({totalCount, totalPages, currentPage}) {
  return (
    <PaginationWithTitle
      handlePageChange={handleRunsPageChange}
      currentPage={currentPage}
      totalPages={totalPages}
      title={totalCount > 1 ? "Runs" : "Run"}
      numberOfEntities={totalCount}
    />
  );
}

RunsPaginationView.propTypes = {
  totalCount: PropTypes.number,
  totalPages: PropTypes.number,
  currentPage: PropTypes.number
};

const mapStateToProps = (state) => {
  let currentPage;
  if (state.details.runsOffset === 0) {
    currentPage = 1;
  } else {
    currentPage = Math.ceil((state.details.runsOffset + 1) / state.details.runsLimit);
  }

  return {
    totalCount: state.details.totalRunsCount,
    totalPages: state.details.totalRunsPages,
    currentPage
  };
};

const RunsPagination = connect(
  mapStateToProps
)(RunsPaginationView);

export default RunsPagination;
