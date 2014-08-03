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

package co.cask.cdap.data2.dataset2.lib.table.inmemory;

import co.cask.cdap.api.dataset.DatasetAdmin;
import co.cask.cdap.api.dataset.table.ConflictDetection;
import co.cask.cdap.data2.dataset.lib.table.inmemory.InMemoryOcTableClient;
import co.cask.cdap.data2.dataset2.lib.table.BufferingOrederedTableTest;

/**
 *
 */
public class InMemoryOrderedTableTest extends BufferingOrederedTableTest<InMemoryOcTableClient> {
  @Override
  protected InMemoryOcTableClient getTable(String name, ConflictDetection conflictLevel) throws Exception {
    return new InMemoryOcTableClient(name,
                       co.cask.cdap.data2.dataset.lib.table.ConflictDetection.valueOf(conflictLevel.name()));
  }

  @Override
  protected DatasetAdmin getTableAdmin(String name) throws Exception {
    return new InMemoryOrderedTableAdmin(name);
  }
}
