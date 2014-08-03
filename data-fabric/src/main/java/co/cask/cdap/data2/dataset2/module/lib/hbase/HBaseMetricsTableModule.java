/*
 * Copyright 2014 Cask, Inc.
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

package co.cask.cdap.data2.dataset2.module.lib.hbase;

import co.cask.cdap.api.dataset.module.DatasetDefinitionRegistry;
import co.cask.cdap.api.dataset.module.DatasetModule;
import co.cask.cdap.data2.dataset.lib.table.MetricsTable;
import co.cask.cdap.data2.dataset.lib.table.hbase.HBaseMetricsTable;
import co.cask.cdap.data2.dataset.lib.table.hbase.HBaseMetricsTableDefinition;

/**
 * Registers HBase-backed implementations of the metrics system datasets
 */
public class HBaseMetricsTableModule implements DatasetModule {
  @Override
  public void register(DatasetDefinitionRegistry registry) {
    registry.add(new HBaseMetricsTableDefinition(HBaseMetricsTable.class.getName()));
    registry.add(new HBaseMetricsTableDefinition(MetricsTable.class.getName()));
  }
}
