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

package co.cask.cdap.internal.app.runtime.monitor;

import co.cask.cdap.api.dataset.lib.CloseableIterator;
import co.cask.cdap.api.messaging.Message;
import co.cask.cdap.api.messaging.MessageFetcher;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.logging.LogSamplers;
import co.cask.cdap.common.logging.Loggers;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.http.AbstractHttpHandler;
import co.cask.http.BodyProducer;
import co.cask.http.HttpResponder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import javax.annotation.Nullable;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

/**
 * {@link co.cask.http.HttpHandler} for exposing metadata of a runtime.
 */
@Path("/v1/runtime")
public class RuntimeHandler extends AbstractHttpHandler {

  private static final Logger LOG = LoggerFactory.getLogger(RuntimeHandler.class);
  // For outage, only log once per 60 seconds per message.
  private static final Logger OUTAGE_LOG = Loggers.sampling(LOG, LogSamplers.perMessage(
    () -> LogSamplers.limitRate(60000)));

  private final CConfiguration cConf;
  private final MessageFetcher messageFetcher;
  private final Runnable shutdownRunnable;
  // caches request key to topic
  private final Map<String, String> requestKeyToLocalTopic;
  private static int messageChunkSize;

  public RuntimeHandler(CConfiguration cConf, MessageFetcher messageFetcher, Runnable shutdownRunnable) {
    this.cConf = cConf;
    messageChunkSize = cConf.getInt(Constants.RuntimeMonitor.SERVER_CONSUME_CHUNK_SIZE);
    this.messageFetcher = messageFetcher;
    this.shutdownRunnable = shutdownRunnable;
    this.requestKeyToLocalTopic = new HashMap<>();
  }

  /**
   * Gets list of topics along with offsets and limit as request and returns list of messages
   */
  @POST
  @Path("/metadata")
  public void metadata(FullHttpRequest request, HttpResponder responder) throws Exception {
    Map<String, GenericRecord> consumeRequests = decodeConsumeRequest(request);
    MessagesBodyProducer messagesBodyProducer = new MessagesBodyProducer(fetchAllMessages(consumeRequests),
                                                                         consumeRequests.size());
    responder.sendContent(HttpResponseStatus.OK, messagesBodyProducer,
                          new DefaultHttpHeaders().set(HttpHeaderNames.CONTENT_TYPE, "avro/binary"));
  }

  /**
   * shuts down remote runtime
   */
  @POST
  @Path("/shutdown")
  public void shutdown(FullHttpRequest request, HttpResponder responder) throws Exception {
    responder.sendString(HttpResponseStatus.OK, "Triggering shutdown down Runtime Http Server.");
    shutdownRunnable.run();
  }

  /**
   * Decode consume request from avro binary format
   */
  private Map<String, GenericRecord> decodeConsumeRequest(FullHttpRequest request) throws IOException {
    Decoder decoder = DecoderFactory.get().directBinaryDecoder(new ByteBufInputStream(request.content()), null);
    Schema requestSchema = MonitorSchemas.V1.MonitorConsumeRequest.SCHEMA;
    // Avro converts java String to UTF8 while deserializing strings. Set String type to Java String
    GenericData.setStringType(requestSchema, GenericData.StringType.String);
    DatumReader<Map<String, GenericRecord>> datumReader = new GenericDatumReader<>(requestSchema);

    return datumReader.read(null, decoder);
  }

  /**
   * Fetches all the messages for each topic provided in consumeRequests and returns iterator of topicConfig
   * to message iterator
   */
  private Iterator<Map.Entry<String, CloseableIterator<Message>>> fetchAllMessages(Map<String, GenericRecord>
                                                                                     consumeRequests) throws Exception {
    Map<String, CloseableIterator<Message>> messages = new HashMap<>();

    for (Map.Entry<String, GenericRecord> entry : consumeRequests.entrySet()) {
      String topicConfig = String.valueOf(entry.getKey());
      String topic = requestKeyToLocalTopic.computeIfAbsent(topicConfig, this::getTopic);

      int limit = (int) entry.getValue().get("limit");
      String fromMessage = entry.getValue().get("messageId") == null ? null :
        entry.getValue().get("messageId").toString();

      messages.put(topicConfig, messageFetcher.fetch(NamespaceId.SYSTEM.getNamespace(), topic, limit, fromMessage));

    }

    return messages.entrySet().iterator();
  }


  /**
   * A {@link BodyProducer} to encode and send back messages.
   * Instead of using GenericDatumWriter, we perform the map and array encoding manually so that we don't have to buffer
   * all messages in memory before sending out.
   */
  private static class MessagesBodyProducer extends BodyProducer {
    private final Iterator<Map.Entry<String, CloseableIterator<Message>>> responseIterator;
    private final Schema elementSchema = MonitorSchemas.V1.MonitorResponse.SCHEMA.getValueType().getElementType();
    private final int numOfRequests;
    private final Deque<GenericRecord> monitorMessages;
    private final ByteBuf chunk;
    private final Encoder encoder;
    private final DatumWriter<GenericRecord> messageWriter;
    private CloseableIterator<Message> iterator;
    private boolean mapStarted;
    private boolean mapEnded;

    MessagesBodyProducer(Iterator<Map.Entry<String, CloseableIterator<Message>>> responseIterators, int numOfRequests) {
      this.responseIterator = responseIterators;
      this.numOfRequests = numOfRequests;
      this.monitorMessages = new LinkedList<>();
      this.chunk = Unpooled.buffer(messageChunkSize);
      this.encoder = EncoderFactory.get().directBinaryEncoder(new ByteBufOutputStream(chunk), null);
      this.messageWriter = new GenericDatumWriter<GenericRecord>(elementSchema) {
        @Override
        protected void writeBytes(Object datum, Encoder out) throws IOException {
          if (datum instanceof byte[]) {
            out.writeBytes((byte[]) datum);
          } else {
            super.writeBytes(datum, out);
          }
        }
      };
    }

    @Override
    public ByteBuf nextChunk() throws Exception {
      // Already sent all messages, return empty to signal the end of response
      if (mapEnded) {
        return Unpooled.EMPTY_BUFFER;
      }

      chunk.clear();

      if (!mapStarted) {
        encoder.writeMapStart();
        encoder.setItemCount(numOfRequests);
        mapStarted = true;
      }

      // Try to buffer up to buffer size
      int size = 0;

      while (responseIterator.hasNext() && size < messageChunkSize) {
        Map.Entry<String, CloseableIterator<Message>> next = responseIterator.next();
        encoder.startItem();
        encoder.writeString(next.getKey());
        encoder.writeArrayStart();
        iterator = next.getValue();

        monitorMessages.clear();

        while (iterator.hasNext() && size < messageChunkSize) {
          Message rawMessage = iterator.next();
          // Avro requires number of objects to be written first so we will have to buffer messages
          GenericRecord record = createGenericRecord(rawMessage);
          monitorMessages.addLast(record);
          // Avro prefixes string and byte array with its length which is a 32 bit int. So add 8 bytes to size for
          // correct calculation of number bytes on the buffer.
          size += rawMessage.getId().length() + rawMessage.getPayload().length + 8;
        }

        encoder.setItemCount(monitorMessages.size());
        for (GenericRecord monitorMessage : monitorMessages) {
          encoder.startItem();
          messageWriter.write(monitorMessage, encoder);
        }

        if (!iterator.hasNext()) {
          // close this iterator before moving to next one.
          iterator.close();
          encoder.writeArrayEnd();
        }
      }

      if (!responseIterator.hasNext()) {
        encoder.writeMapEnd();
        mapEnded = true;
      }

      return chunk.copy();
    }

    @Override
    public void finished() throws Exception {
      chunk.release();
    }

    @Override
    public void handleError(@Nullable Throwable cause) {
      // close all the iterators
      while (responseIterator.hasNext()) {
        responseIterator.next().getValue().close();
      }

      OUTAGE_LOG.error("Error occurred while sending chunks from Runtime Handler", cause);
    }

    private GenericRecord createGenericRecord(Message rawMessage) {
      GenericRecord record = new GenericData.Record(elementSchema);
      record.put("messageId", rawMessage.getId());
      record.put("message", rawMessage.getPayload());
      return record;
    }
  }

  private String getTopic(String topicConfig) {
    int idx = topicConfig.lastIndexOf(':');
    return idx < 0 ? cConf.get(topicConfig) : cConf.get(topicConfig.substring(0, idx)) + topicConfig.substring(idx + 1);
  }
}
