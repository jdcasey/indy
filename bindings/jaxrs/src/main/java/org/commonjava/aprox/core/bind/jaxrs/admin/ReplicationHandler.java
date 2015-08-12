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
package org.commonjava.aprox.core.bind.jaxrs.admin;

import static org.commonjava.aprox.bind.jaxrs.util.ResponseUtils.formatOkResponseWithJsonEntity;
import static org.commonjava.aprox.bind.jaxrs.util.ResponseUtils.formatResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.commonjava.aprox.AproxWorkflowException;
import org.commonjava.aprox.bind.jaxrs.AproxResources;
import org.commonjava.aprox.bind.jaxrs.util.SecurityParam;
import org.commonjava.aprox.core.ctl.ReplicationController;
import org.commonjava.aprox.model.core.StoreKey;
import org.commonjava.aprox.model.core.dto.ReplicationDTO;
import org.commonjava.aprox.util.ApplicationContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiImplicitParam;
import com.wordnik.swagger.annotations.ApiImplicitParams;
import com.wordnik.swagger.annotations.ApiOperation;

@Api( description = "Replicate the artifact stores on a remote AProx instance, either by proxying the remote system's stores or by cloning the store definitions", value = "/api/admin/replicate" )
@Path( "/api/admin/replicate" )
public class ReplicationHandler
    implements AproxResources
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    private ReplicationController controller;

    @Inject
    private ObjectMapper serializer;

    @ApiOperation( "Replicate the stores of a remote AProx" )
    @ApiImplicitParams( { @ApiImplicitParam( paramType = "body", name = "body", dataType = "org.commonjava.aprox.model.core.dto.ReplicationDTO", required = true, value = "The configuration determining how replication should be handled, and what remote site to replicate." ) } )
    @POST
    @Produces( ApplicationContent.application_json )
    public Response replicate( @Context final HttpServletRequest request )
    {
        Response response;
        try
        {
            final String user = (String) request.getSession( true )
                                                .getAttribute( SecurityParam.user.key() );

            final ReplicationDTO dto = serializer.readValue( request.getInputStream(), ReplicationDTO.class );
            final Set<StoreKey> replicated = controller.replicate( dto, user );

            final Map<String, Object> params = new LinkedHashMap<String, Object>();
            params.put( "replicationCount", replicated.size() );
            params.put( "items", replicated );

            response = formatOkResponseWithJsonEntity( params, serializer );
        }
        catch ( final AproxWorkflowException | IOException e )
        {
            logger.error( String.format( "Replication failed: %s", e.getMessage() ), e );
            response = formatResponse( e );
        }

        return response;
    }

}
