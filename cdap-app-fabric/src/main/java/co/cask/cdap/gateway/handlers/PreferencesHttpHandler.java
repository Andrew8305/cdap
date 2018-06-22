/*
 * Copyright © 2015 Cask Data, Inc.
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

package co.cask.cdap.gateway.handlers;

import co.cask.cdap.app.store.Store;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.security.AuditDetail;
import co.cask.cdap.common.security.AuditPolicy;
import co.cask.cdap.config.PreferencesService;
import co.cask.cdap.gateway.handlers.util.AbstractAppFabricHttpHandler;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.id.ApplicationId;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.ProgramId;
import co.cask.cdap.store.NamespaceStore;
import co.cask.http.HttpResponder;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Map;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

/**
 * Program Preferences HTTP Handler.
 */
@Path(Constants.Gateway.API_VERSION_3)
public class PreferencesHttpHandler extends AbstractAppFabricHttpHandler {

  private static final Gson GSON = new Gson();

  private final PreferencesService preferencesService;
  private final Store store;
  private final NamespaceStore nsStore;

  @Inject
  PreferencesHttpHandler(PreferencesService preferencesService, Store store, NamespaceStore nsStore) {
    this.preferencesService = preferencesService;
    this.store = store;
    this.nsStore = nsStore;
  }

  //Instance Level Properties
  @Path("/preferences")
  @GET
  public void getInstancePrefs(HttpRequest request, HttpResponder responder) throws Exception {
    responder.sendJson(HttpResponseStatus.OK, GSON.toJson(preferencesService.getProperties()));
  }

  @Path("/preferences")
  @DELETE
  public void deleteInstancePrefs(HttpRequest request, HttpResponder responder) throws Exception {
    preferencesService.deleteProperties();
    responder.sendStatus(HttpResponseStatus.OK);
  }

  @Path("/preferences")
  @PUT
  @AuditPolicy(AuditDetail.REQUEST_BODY)
  public void setInstancePrefs(FullHttpRequest request, HttpResponder responder) throws Exception {
    try {
      Map<String, String> propMap = decodeArguments(request);
      preferencesService.setProperties(propMap);
      responder.sendStatus(HttpResponseStatus.OK);
    } catch (JsonSyntaxException jsonEx) {
      responder.sendString(HttpResponseStatus.BAD_REQUEST, "Invalid JSON in body");
    }
  }

  //Namespace Level Properties
  //Resolved field, if set to true, returns the collapsed property map (Instance < Namespace)
  @Path("/namespaces/{namespace-id}/preferences")
  @GET
  public void getNamespacePrefs(HttpRequest request, HttpResponder responder,
                                @PathParam("namespace-id") String namespace, @QueryParam("resolved") boolean resolved)
    throws Exception {
    if (nsStore.get(new NamespaceId(namespace)) == null) {
      responder.sendString(HttpResponseStatus.NOT_FOUND, String.format("Namespace %s not present", namespace));
    } else {
      if (resolved) {
        responder.sendJson(HttpResponseStatus.OK, GSON.toJson(preferencesService.getResolvedProperties(namespace)));
      } else {
        responder.sendJson(HttpResponseStatus.OK, GSON.toJson(preferencesService.getProperties(namespace)));
      }
    }
  }

  @Path("/namespaces/{namespace-id}/preferences")
  @PUT
  @AuditPolicy(AuditDetail.REQUEST_BODY)
  public void setNamespacePrefs(FullHttpRequest request, HttpResponder responder,
                                @PathParam("namespace-id") String namespace) throws Exception {
    if (nsStore.get(new NamespaceId(namespace)) == null) {
      responder.sendString(HttpResponseStatus.NOT_FOUND, String.format("Namespace %s not present", namespace));
      return;
    }

    try {
      Map<String, String> propMap = decodeArguments(request);
      preferencesService.setProperties(namespace, propMap);
      responder.sendStatus(HttpResponseStatus.OK);
    } catch (JsonSyntaxException jsonEx) {
      responder.sendString(HttpResponseStatus.BAD_REQUEST, "Invalid JSON in body");
    }
  }

  @Path("/namespaces/{namespace-id}/preferences")
  @DELETE
  public void deleteNamespacePrefs(HttpRequest request, HttpResponder responder,
                                   @PathParam("namespace-id") String namespace) throws Exception {
    if (nsStore.get(new NamespaceId(namespace)) == null) {
      responder.sendString(HttpResponseStatus.NOT_FOUND, String.format("Namespace %s not present", namespace));
    } else {
      preferencesService.deleteProperties(namespace);
      responder.sendStatus(HttpResponseStatus.OK);
    }
  }

  //Application Level Properties
  //Resolved field, if set to true, returns the collapsed property map (Instance < Namespace < Application)
  @Path("/namespaces/{namespace-id}/apps/{application-id}/preferences")
  @GET
  public void getAppPrefs(HttpRequest request, HttpResponder responder,
                          @PathParam("namespace-id") String namespace, @PathParam("application-id") String appId,
                          @QueryParam("resolved") boolean resolved) throws Exception {
    if (store.getApplication(new ApplicationId(namespace, appId)) == null) {
      responder.sendString(HttpResponseStatus.NOT_FOUND, String.format("Application %s in Namespace %s not present",
                                                                       appId, namespace));
    } else {
      if (resolved) {
        responder.sendJson(HttpResponseStatus.OK,
                           GSON.toJson(preferencesService.getResolvedProperties(namespace, appId)));
      } else {
        responder.sendJson(HttpResponseStatus.OK, GSON.toJson(preferencesService.getProperties(namespace, appId)));
      }
    }
  }

  @Path("/namespaces/{namespace-id}/apps/{application-id}/preferences")
  @PUT
  @AuditPolicy(AuditDetail.REQUEST_BODY)
  public void putAppPrefs(FullHttpRequest request, HttpResponder responder,
                          @PathParam("namespace-id") String namespace, @PathParam("application-id") String appId)
    throws Exception {
    if (store.getApplication(new ApplicationId(namespace, appId)) == null) {
      responder.sendString(HttpResponseStatus.NOT_FOUND, String.format("Application %s in Namespace %s not present",
                                                                       appId, namespace));
      return;
    }

    try {
      Map<String, String> propMap = decodeArguments(request);
      preferencesService.setProperties(namespace, appId, propMap);
      responder.sendStatus(HttpResponseStatus.OK);
    } catch (JsonSyntaxException jsonEx) {
      responder.sendString(HttpResponseStatus.BAD_REQUEST, "Invalid JSON in body");
    }
  }

  @Path("/namespaces/{namespace-id}/apps/{application-id}/preferences")
  @DELETE
  public void deleteAppPrefs(HttpRequest request, HttpResponder responder,
                             @PathParam("namespace-id") String namespace, @PathParam("application-id") String appId)
    throws Exception {
    if (store.getApplication(new ApplicationId(namespace, appId)) == null) {
      responder.sendString(HttpResponseStatus.NOT_FOUND, String.format("Application %s in Namespace %s not present",
                                                                       appId, namespace));
    } else {
      preferencesService.deleteProperties(namespace, appId);
      responder.sendStatus(HttpResponseStatus.OK);
    }
  }

  //Program Level Properties
  //Resolved field, if set to true, returns the collapsed property map (Instance < Namespace < Application < Program)
  @Path("/namespaces/{namespace-id}/apps/{application-id}/{program-type}/{program-id}/preferences")
  @GET
  public void getProgramPrefs(HttpRequest request, HttpResponder responder,
                              @PathParam("namespace-id") String namespace, @PathParam("application-id") String appId,
                              @PathParam("program-type") String programType, @PathParam("program-id") String programId,
                              @QueryParam("resolved") boolean resolved) throws Exception {
    if (checkIfProgramExists(namespace, appId, programType, programId, responder)) {
      if (resolved) {
        responder.sendJson(HttpResponseStatus.OK,
                           GSON.toJson(preferencesService.getResolvedProperties(namespace, appId,
                                                                                programType, programId)));
      } else {
        responder.sendJson(HttpResponseStatus.OK,
                           GSON.toJson(preferencesService.getProperties(namespace, appId, programType, programId)));
      }
    }
  }

  @Path("/namespaces/{namespace-id}/apps/{application-id}/{program-type}/{program-id}/preferences")
  @PUT
  @AuditPolicy(AuditDetail.REQUEST_BODY)
  public void putProgramPrefs(FullHttpRequest request, HttpResponder responder,
                              @PathParam("namespace-id") String namespace, @PathParam("application-id") String appId,
                              @PathParam("program-type") String programType, @PathParam("program-id") String programId)
    throws Exception {
    if (checkIfProgramExists(namespace, appId, programType, programId, responder)) {
      try {
        Map<String, String> propMap = decodeArguments(request);
        preferencesService.setProperties(namespace, appId, programType, programId, propMap);
        responder.sendStatus(HttpResponseStatus.OK);
      } catch (JsonSyntaxException jsonEx) {
        responder.sendString(HttpResponseStatus.BAD_REQUEST, "Invalid JSON in body");
      }
    }
  }

  @Path("/namespaces/{namespace-id}/apps/{application-id}/{program-type}/{program-id}/preferences")
  @DELETE
  public void deleteProgramPrefs(HttpRequest request, HttpResponder responder,
                                 @PathParam("namespace-id") String namespace, @PathParam("application-id") String appId,
                                 @PathParam("program-type") String programType,
                                 @PathParam("program-id") String programId)
    throws Exception {
    if (checkIfProgramExists(namespace, appId, programType, programId, responder)) {
      preferencesService.deleteProperties(namespace, appId, programType, programId);
      responder.sendStatus(HttpResponseStatus.OK);
    }
  }

  private boolean checkIfProgramExists(String namespace, String appId, String programType, String programId,
                                       HttpResponder responder) throws Exception {
    ProgramType type;
    try {
      type = ProgramType.valueOfCategoryName(programType);
    } catch (IllegalArgumentException e) {
      responder.sendString(HttpResponseStatus.BAD_REQUEST, String.format("%s is invalid ProgramType", programType));
      return false;
    }

    if (!store.programExists(new ProgramId(namespace, appId, type, programId))) {
      responder.sendString(HttpResponseStatus.NOT_FOUND,
                           String.format("Program %s of Type %s in AppId %s in Namespace %s not present",
                                         programId, programType, appId, namespace));
      return false;
    }
    return true;
  }
}
