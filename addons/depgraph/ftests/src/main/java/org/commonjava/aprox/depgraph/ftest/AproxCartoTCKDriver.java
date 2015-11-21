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
package org.commonjava.aprox.depgraph.ftest;

import org.apache.commons.io.FileUtils;
import org.commonjava.aprox.boot.AproxBootException;
import org.commonjava.aprox.boot.BootStatus;
import org.commonjava.aprox.client.core.Aprox;
import org.commonjava.aprox.client.core.module.AproxRawObjectMapperModule;
import org.commonjava.aprox.depgraph.client.DepgraphAproxClientModule;
import org.commonjava.aprox.depgraph.impl.ClientCartographer;
import org.commonjava.aprox.model.core.RemoteRepository;
import org.commonjava.aprox.model.core.StoreKey;
import org.commonjava.aprox.model.core.StoreType;
import org.commonjava.aprox.test.fixture.core.CoreServerFixture;
import org.commonjava.cartographer.Cartographer;
import org.commonjava.maven.cartographer.ftest.CartoTCKDriver;
import org.commonjava.test.http.stream.StreamServer;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.apache.commons.io.IOUtils.closeQuietly;

public class AproxCartoTCKDriver
    implements CartoTCKDriver
{
    protected Aprox client;

    protected CoreServerFixture fixture;

    private Set<StreamServer> servers = new HashSet<>();

    private ClientCartographer carto;

    private File etcDir;

    public Cartographer start( TemporaryFolder temp )
            throws Exception
    {
        fixture = newServerFixture( temp );
        fixture.start();

        if ( !fixture.isStarted() )
        {
            final BootStatus status = fixture.getBootStatus();
            throw new IllegalStateException( "server fixture failed to boot.", status.getError() );
        }

        client =
                new Aprox( fixture.getUrl(), new DepgraphAproxClientModule(), new AproxRawObjectMapperModule() ).connect();

        carto = new ClientCartographer( client );

        return carto;
    }

    public void stop()
    {
        closeQuietly( fixture );

        if ( carto != null )
        {
            carto.close();
        }

        closeQuietly( client );

        servers.forEach( StreamServer::stop );
    }

    @Override
    public void createRepoAlias( String alias, String repoResource )
            throws Exception
    {
        String url = repoResource;
        if ( repoResource.startsWith("file:") || repoResource.startsWith("jar:") )
        {
            StreamServer server = new StreamServer( repoResource ).start();
            servers.add( server );

            url = server.getBaseUri();
        }
        // else, it's already a remote repo (for a test server setup by the test itself)

        client.stores().create( new RemoteRepository(alias, url), "Adding test repo: " + alias, RemoteRepository.class );
        carto.setSourceAlias( alias, new StoreKey( StoreType.remote, alias ).toString() );
    }

    protected CoreServerFixture newServerFixture( TemporaryFolder temp )
        throws AproxBootException, IOException
    {
        CoreServerFixture fixture = new CoreServerFixture( temp );

        etcDir = new File( fixture.getBootOptions().getAproxHome(), "etc/aprox" );
        writeConfigFile( "conf.d/scheduler.conf", "[scheduler]\nenabled=false" );

        return fixture;
    }

    protected void writeConfigFile( String confPath, String contents )
            throws IOException
    {
        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.info( "Writing configuration to: {}\n\n{}\n\n", confPath, contents );
        File confFile = new File( etcDir, confPath );
        confFile.getParentFile().mkdirs();

        FileUtils.write( confFile, contents );
    }
}