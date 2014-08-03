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

package co.cask.cdap.data;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.data2.dataset.api.DataSetManager;
import co.cask.cdap.data2.dataset.lib.table.ConflictDetection;
import co.cask.cdap.data2.dataset.lib.table.OrderedColumnarTable;
import co.cask.cdap.data2.dataset.lib.table.leveldb.LevelDBOcTableClient;
import co.cask.cdap.data2.dataset.lib.table.leveldb.LevelDBOcTableManager;
import co.cask.cdap.data2.dataset.lib.table.leveldb.LevelDBOcTableService;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import java.util.Map;

/**
 *
 */
public class LocalDataSetAccessor extends AbstractDataSetAccessor {

  private final LevelDBOcTableService service;

  @Inject
  public LocalDataSetAccessor(CConfiguration conf,
                              LevelDBOcTableService service) {
    super(conf);
    this.service = service;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> T getOcTableClient(String name, ConflictDetection level, int ttl) throws Exception {
    // ttl is ignored in local mode
    return (T) new LevelDBOcTableClient(name, level, service);
  }

  @Override
  protected DataSetManager getOcTableManager() throws Exception {
    return new LevelDBOcTableManager(service);
  }

  @Override
  protected Map<String, Class<?>> list(String prefix) throws Exception {
    Map<String, Class<?>> datasets = Maps.newHashMap();
    for (String tableName : service.list()) {
      if (tableName.startsWith(prefix)) {
        datasets.put(tableName, OrderedColumnarTable.class);
      }
    }
    return datasets;
  }
}

