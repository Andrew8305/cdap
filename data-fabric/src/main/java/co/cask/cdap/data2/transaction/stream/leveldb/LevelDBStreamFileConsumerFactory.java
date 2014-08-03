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
package co.cask.cdap.data2.transaction.stream.leveldb;

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.data.DataSetAccessor;
import co.cask.cdap.data.file.FileReader;
import co.cask.cdap.data.file.ReadFilter;
import co.cask.cdap.data.stream.StreamEventOffset;
import co.cask.cdap.data.stream.StreamFileOffset;
import co.cask.cdap.data.stream.StreamFileType;
import co.cask.cdap.data.stream.StreamUtils;
import co.cask.cdap.data2.dataset.lib.table.leveldb.LevelDBOcTableCore;
import co.cask.cdap.data2.dataset.lib.table.leveldb.LevelDBOcTableService;
import co.cask.cdap.data2.queue.ConsumerConfig;
import co.cask.cdap.data2.queue.QueueClientFactory;
import co.cask.cdap.data2.transaction.queue.leveldb.LevelDBStreamAdmin;
import co.cask.cdap.data2.transaction.stream.AbstractStreamFileConsumerFactory;
import co.cask.cdap.data2.transaction.stream.StreamAdmin;
import co.cask.cdap.data2.transaction.stream.StreamConfig;
import co.cask.cdap.data2.transaction.stream.StreamConsumer;
import co.cask.cdap.data2.transaction.stream.StreamConsumerState;
import co.cask.cdap.data2.transaction.stream.StreamConsumerStateStore;
import co.cask.cdap.data2.transaction.stream.StreamConsumerStateStoreFactory;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.apache.twill.filesystem.Location;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

/**
 * A {@link co.cask.cdap.data2.transaction.stream.StreamConsumerFactory} that reads from stream file
 * and uses LevelDB as the store for consumer process states.
 */
public final class LevelDBStreamFileConsumerFactory extends AbstractStreamFileConsumerFactory {

  private final CConfiguration cConf;
  private final LevelDBOcTableService tableService;
  private final ConcurrentMap<String, Object> dbLocks;

  @Inject
  LevelDBStreamFileConsumerFactory(DataSetAccessor dataSetAccessor, StreamAdmin streamAdmin,
                                   StreamConsumerStateStoreFactory stateStoreFactory,
                                   CConfiguration cConf, LevelDBOcTableService tableService,
                                   QueueClientFactory queueClientFactory, LevelDBStreamAdmin oldStreamAdmin) {
    super(dataSetAccessor, streamAdmin, stateStoreFactory, queueClientFactory, oldStreamAdmin);
    this.cConf = cConf;
    this.tableService = tableService;
    this.dbLocks = Maps.newConcurrentMap();
  }


  @Override
  protected StreamConsumer create(String tableName, StreamConfig streamConfig, ConsumerConfig consumerConfig,
                                  StreamConsumerStateStore stateStore, StreamConsumerState beginConsumerState,
                                  FileReader<StreamEventOffset, Iterable<StreamFileOffset>> reader,
                                  @Nullable ReadFilter extraFilter) throws IOException {

    tableService.ensureTableExists(tableName);

    LevelDBOcTableCore tableCore = new LevelDBOcTableCore(tableName, tableService);
    Object dbLock = getDBLock(tableName);
    return new LevelDBStreamFileConsumer(cConf, streamConfig, consumerConfig, reader,
                                         stateStore, beginConsumerState, extraFilter,
                                         tableCore, dbLock);
  }

  @Override
  protected void dropTable(String tableName) throws IOException {
    tableService.dropTable(tableName);
  }

  @Override
  protected void getFileOffsets(Location partitionLocation,
                                Collection<? super StreamFileOffset> fileOffsets,
                                int generation) throws IOException {
    // Assumption is it's used in local mode, hence only one instance
    Location eventLocation = StreamUtils.createStreamLocation(partitionLocation,
                                                              cConf.get(Constants.Stream.FILE_PREFIX) + ".0",
                                                              0, StreamFileType.EVENT);
    fileOffsets.add(new StreamFileOffset(eventLocation, 0, generation));
  }

  private Object getDBLock(String name) {
    Object lock = dbLocks.get(name);
    if (lock == null) {
      lock = new Object();
      Object existing = dbLocks.putIfAbsent(name, lock);
      if (existing != null) {
        lock = existing;
      }
    }
    return lock;

  }
}
