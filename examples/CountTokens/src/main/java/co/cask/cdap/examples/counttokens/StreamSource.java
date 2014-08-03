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
package co.cask.cdap.examples.counttokens;

import co.cask.cdap.api.annotation.ProcessInput;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.flow.flowlet.AbstractFlowlet;
import co.cask.cdap.api.flow.flowlet.OutputEmitter;
import co.cask.cdap.api.flow.flowlet.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class StreamSource extends AbstractFlowlet {
  private static final Logger LOG = LoggerFactory.getLogger(StreamSource
                                                              .class);

  private OutputEmitter<String> output;

  @ProcessInput
  public void process(StreamEvent event) {
    LOG.info(this.getContext().getName() + ": Received event " + event);

    byte[] body = Bytes.toBytes(event.getBody());
    String text = Bytes.toString(body);

    LOG.info(this.getContext().getName() + ": Emitting line: " + text);

    output.emit(text);
  }
}
