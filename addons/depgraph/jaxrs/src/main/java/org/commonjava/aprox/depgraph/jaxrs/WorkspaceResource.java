/**
 * Copyright (C) 2011 Red Hat, Inc. (jdcasey@commonjava.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.aprox.depgraph.jaxrs;

import static org.commonjava.aprox.bind.jaxrs.util.ResponseUtils.formatResponse;
import static org.commonjava.aprox.bind.jaxrs.util.ResponseUtils.throwError;
import static org.commonjava.aprox.util.ApplicationContent.application_aprox_star_json;
import static org.commonjava.aprox.util.ApplicationContent.application_json;

import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.commonjava.aprox.AproxWorkflowException;
import org.commonjava.aprox.bind.jaxrs.AproxResources;
import org.commonjava.aprox.depgraph.model.WorkspaceList;
import org.commonjava.aprox.depgraph.rest.WorkspaceController;

@Path( "/api/depgraph/ws" )
@Produces( { application_json, application_aprox_star_json } )
public class WorkspaceResource
    implements AproxResources
{

    @Inject
    private WorkspaceController controller;

    @Path( "/{wsid}" )
    @DELETE
    public Response delete( final @PathParam( "wsid" ) String id )
    {
        Response response;
        try
        {
            controller.delete( id );
            response = Response.noContent()
                               .build();
        }
        catch ( final AproxWorkflowException e )
        {
            response = formatResponse( e );
        }

        return response;
    }

    @GET
    public WorkspaceList list()
    {
        try
        {
            return controller.list();
        }
        catch ( final AproxWorkflowException e )
        {
            throwError( e );
        }

        return null;
    }
}