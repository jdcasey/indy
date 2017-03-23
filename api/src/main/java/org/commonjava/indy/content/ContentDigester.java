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
package org.commonjava.indy.content;

import org.apache.commons.io.IOUtils;
import org.commonjava.indy.IndyWorkflowException;
import org.commonjava.indy.content.ArtifactData;
import org.commonjava.indy.content.ContentDigest;
import org.commonjava.indy.content.DownloadManager;
import org.commonjava.indy.model.core.StoreKey;
import org.commonjava.maven.galley.model.Transfer;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jdcasey on 1/4/17.
 * Migrated out of DefaultContentManager so it can be used from other places. This isn't really specific to the
 * {@link org.commonjava.indy.content.ContentManager} interface anyway.
 */
@ApplicationScoped
public class ContentDigester
{

    @Inject
    private DownloadManager downloadManager;

    protected ContentDigester(){}

    public ContentDigester( DownloadManager downloadManager )
    {
        this.downloadManager = downloadManager;
    }

    public ArtifactData digest( final StoreKey key, final String path, final ContentDigest... types )
            throws IndyWorkflowException
    {
        final Transfer txfr = downloadManager.getStorageReference( key, path );
        if ( txfr == null || !txfr.exists() )
        {
            return new ArtifactData( Collections.emptyMap(), 0L);
        }

        try (InputStream stream = txfr.openInputStream( false ))
        {
            long artifactSize = 0L;

            final Map<ContentDigest, MessageDigest> digests = new HashMap<>();
            for ( final ContentDigest digest : types )
            {
                digests.put( digest, MessageDigest.getInstance( digest.digestName() ) );
            }

            final byte[] buf = new byte[16384];
            int read = -1;
            while ( ( read = stream.read( buf ) ) > -1 )
            {
                for ( final MessageDigest digest : digests.values() )
                {
                    digest.update( buf, 0, read );
                }
                artifactSize += read;
            }

            final Map<ContentDigest, String> digestResultMap = new HashMap<>();
            for ( final Map.Entry<ContentDigest, MessageDigest> entry : digests.entrySet() )
            {
                final StringBuilder sb = new StringBuilder();
                for ( final byte b : entry.getValue().digest() )
                {
                    final String hex = Integer.toHexString( b & 0xff );
                    if ( hex.length() < 2 )
                    {
                        sb.append( '0' );
                    }
                    sb.append( hex );
                }

                digestResultMap.put( entry.getKey(), sb.toString() );
            }

            return new ArtifactData( digestResultMap, artifactSize );
        }
        catch ( IOException | NoSuchAlgorithmException e )
        {
            throw new IndyWorkflowException( "Failed to calculate checksums (MD5, SHA-256) for: %s. Reason: %s", e,
                                             txfr, e.getMessage() );
        }
    }
}
