/**
 * Copyright (C) 2011-2020 Red Hat, Inc. (https://github.com/Commonjava/indy)
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
package org.commonjava.indy.pkg.maven.content;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.codahale.metrics.Timer;
import org.apache.commons.lang.StringUtils;
import org.commonjava.atlas.maven.ident.util.SnapshotUtils;
import org.commonjava.atlas.maven.ident.version.part.SnapshotPart;
import org.commonjava.indy.metrics.IndyMetricsManager;
import org.commonjava.maven.galley.event.EventMetadata;
import org.commonjava.maven.galley.io.AbstractTransferDecorator;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.model.TransferOperation;
import org.commonjava.maven.galley.io.OverriddenBooleanValue;
import org.commonjava.maven.galley.transport.htcli.model.HttpLocation;
import org.commonjava.maven.galley.util.IdempotentCloseOutputStream;
import org.commonjava.maven.galley.util.TransferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Represents a decorator responsible for filtering out location contents based on location settings. Effectively it is
 * designed to filter out snapshot or release versions from remote repositories when configured to not
 * provide the snapshot or release versions.
 *
 * @author pkocandr
 */
@ApplicationScoped
public class MavenContentsFilteringTransferDecorator
                extends AbstractTransferDecorator
{
    private final Logger logger = LoggerFactory.getLogger( this.getClass() );

    @Inject
    private IndyMetricsManager metricsManager;

    @Override
    public OverriddenBooleanValue decorateExists( final Transfer transfer, final EventMetadata metadata )
    {
        final Location loc = transfer.getLocation();
        final boolean isHttp = loc instanceof HttpLocation;
        final boolean filtered = TransferUtils.filterTransfer( transfer );
        if ( isHttp && filtered )
        {
            return OverriddenBooleanValue.OVERRIDE_FALSE;
        }
        return OverriddenBooleanValue.DEFER;
    }

    @Override
    public OutputStream decorateWrite( final OutputStream stream, final Transfer transfer, final TransferOperation op,
                                       final EventMetadata metadata )
    {
        final Location loc = transfer.getLocation();
        final boolean allowsSnapshots = loc.allowsSnapshots();
        final boolean allowsReleases = loc.allowsReleases();
        if ( loc instanceof HttpLocation && ( !allowsSnapshots || !allowsReleases ) && transfer.getFullPath()
                                                                                               .endsWith( "maven-metadata.xml" ) )
        {
            return new MetadataFilteringOutputStream( stream, allowsSnapshots, allowsReleases, transfer, metricsManager );
        }
        else
        {
            return stream;
        }
    }

    /**
     * Alters the listing to filter out artifacts belonging to a version that
     * should not be provided via the proxy.
     */
    @Override
    public String[] decorateListing( final Transfer transfer, final String[] listing, final EventMetadata metadata )
    {
        final Location loc = transfer.getLocation();
        final boolean allowsSnapshots = loc.allowsSnapshots();
        final boolean allowsReleases = loc.allowsReleases();

        // process only proxied locations, i.e. HttpLocation instances
        if ( loc instanceof HttpLocation && ( !allowsSnapshots || !allowsReleases ) )
        {
            final String[] pathElements = transfer.getPath().split( "/" );
            // process only paths that *can* be a GAV
            if ( pathElements.length >= 3 )
            {
                final String artifactId = pathElements[pathElements.length - 2];
                final String version = pathElements[pathElements.length - 1];
                final boolean snapshotVersion = SnapshotUtils.isSnapshotVersion( version );
                // NOS-1434 Forbid all snapshot files if allowSnapshots not enabled
                if ( ( allowsSnapshots && snapshotVersion ) || ( allowsReleases && !snapshotVersion ) )
                {
                    return listing;
                }
                return new String[0];
            }
            else
            {
                // process paths that does not contain version.
                // if list element contains snapshot folder, ignore them.
                if ( !allowsSnapshots )
                {
                    final List<String> result = new ArrayList<>( listing.length );
                    for ( final String element : listing )
                    {
                        String version = element;
                        if ( element.endsWith( "/" ) )
                        {
                            version = element.substring( 0, version.length() - 1 );
                        }
                        if ( !SnapshotUtils.isSnapshotVersion( version ) )
                        {
                            result.add( element );
                        }
                    }
                    return result.toArray( new String[0] );
                }
            }
        }
        return listing;
    }

    /**
     * Checks if the given element is an artifact. Artifacts always starts with
     * &lt;artifactId&gt;-&lt;version&gt;.
     */
    private boolean isArtifact( final String element, final String artifactId, final String version )
    {
        if ( element.endsWith( "/" ) )
        {
            return false;
        }

        boolean isRemoteSnapshot = false;
        if ( SnapshotUtils.isSnapshotVersion( version ) && element.startsWith( artifactId + '-' )
                        && !element.startsWith( artifactId + '-' + version ) )
        {
            final SnapshotPart snapshotPart = SnapshotUtils.extractSnapshotVersionPart( version );
            final int artIdLenght = artifactId.length() + 1 + version.length() - snapshotPart.getLiteral().length() + 1;
            isRemoteSnapshot = SnapshotUtils.isRemoteSnapshotVersionPart(
                            StringUtils.substring( element, artIdLenght, artIdLenght + 17 ) );
        }

        return element.startsWith( artifactId + '-' + version + '-' ) || element.startsWith(
                        artifactId + '-' + version + '.' ) || isRemoteSnapshot;
    }

    private static class MetadataFilteringOutputStream
            extends IdempotentCloseOutputStream
    {
        private static final String TIMER = "io.maven.metadata.out.filter";

        private final Logger logger = LoggerFactory.getLogger( this.getClass() );

        private static final String LATEST = "<latest>([^<]+)</latest>";

        private static final String RELEASE = "<release>([^<]+)</release>";

        private static final String VERSION = "<version>([^<]+)</version>";

        private static final String VERSIONS = "<versions>[\\s]*(?:(" + VERSION + ")[\\s]*)+</versions>";

        private StringBuilder buffer = new StringBuilder();

        private OutputStream stream;

        private final boolean allowsSnapshots;

        private final boolean allowsReleases;

        private Transfer transfer;

        private IndyMetricsManager metricsManager;

        private MetadataFilteringOutputStream( final OutputStream stream, final boolean allowsSnapshots,
                                               final boolean allowsReleases, Transfer transfer,
                                               final IndyMetricsManager metricsManager )
        {
            super( stream );
            this.stream = stream;
            this.allowsSnapshots = allowsSnapshots;
            this.allowsReleases = allowsReleases;
            this.transfer = transfer;
            this.metricsManager = metricsManager;
        }

        private String filterMetadata()
        {

            if ( buffer.length() == 0 )
            {
                return "";
            }

            Timer.Context timer = metricsManager == null ? null : metricsManager.startTimer( TIMER );
            try
            {
                // filter versions from GA metadata
                final List<String> versions = fetchVersions();

                boolean changed = false;
                for ( final String version : new ArrayList<>( versions ) )
                {
                    final boolean isSnapshot = SnapshotUtils.isSnapshotVersion( version );
                    if ( !allowsSnapshots && isSnapshot || !allowsReleases && !isSnapshot )
                    {
                        logger.debug( "FILTER: Removing prohibited version: {} from: {}", version, transfer );
                        versions.remove( version );
                        changed = true;
                    }
                }

                String filteredMetadata = buffer.toString();
                if ( changed )
                {
                    filteredMetadata = replaceOriginal( filteredMetadata, versions );
                }

                filteredMetadata = resetLatest( filteredMetadata, versions );

                // filter release from GAV metadata
                filteredMetadata = prohibitRelease( filteredMetadata );

                // filter snapshots from GAV metadata
                filteredMetadata = prohibitSnapshots( filteredMetadata );

                return filteredMetadata;
            }
            catch ( IOException | SAXException | ParserConfigurationException | XPathExpressionException e )
            {
                //FIXME: Not sure if it is a good idea to just log these xml parsing exception and return original metadata content.
                logger.error( "Error: Can not filtering {} as it is not a valid maven-metadata.xml.",
                              transfer.getPath() );
                return buffer.toString();
            }
            finally
            {
                if ( timer != null )
                {
                    metricsManager.stopTimer( TIMER );
                }
            }
        }

        private List<String> fetchVersions()
                throws IOException, SAXException, ParserConfigurationException, XPathExpressionException
        {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware( true );
            final Document doc =
                    factory.newDocumentBuilder().parse( new ByteArrayInputStream( buffer.toString().getBytes() ) );

            final NodeList nodes = (NodeList) XPathFactory.newInstance()
                                                          .newXPath()
                                                          .compile( "//version/text()" )
                                                          .evaluate( doc, XPathConstants.NODESET );
            final List<String> versions = new ArrayList<>();
            for ( int i = 0; i < nodes.getLength(); i++ )
            {
                versions.add( nodes.item( i ).getNodeValue() );
            }

            return versions;
        }

        private String replaceOriginal( final String originalMetadata, final List<String> versions )
        {
            String filteredVersions;
            if ( versions.size() == 0 )
            {
                filteredVersions = "<versions></versions>";
            }
            else
            {
                filteredVersions = "<versions>\n<version>" + StringUtils.join( versions, "</version>\n<version>" )
                        + "</version>\n</versions>";
            }
            return originalMetadata.replaceFirst( VERSIONS, filteredVersions );
        }

        private String resetLatest( final String originalMetadata, final List<String> versions )
        {
            String filteredMetadata = originalMetadata;
            final Pattern latestPattern = Pattern.compile( LATEST );
            final Matcher latestMatcher = latestPattern.matcher( filteredMetadata );

            if ( latestMatcher.find() )
            {
                final String latestVersion = latestMatcher.group( 1 );
                final boolean isSnapshot = latestVersion.endsWith( "-SNAPSHOT" );
                if ( ( !allowsSnapshots && isSnapshot ) || ( !allowsReleases && !isSnapshot ) )
                {
                    logger.debug( "FILTER: Recalculating LATEST version; supplied value is prohibited: {} from: {}",
                                  latestVersion, transfer );

                    String newLatest;
                    if ( versions.size() > 0 )
                    {
                        newLatest = "<latest>" + versions.get( versions.size() - 1 ) + "</latest>";
                    }
                    else
                    {
                        newLatest = "<latest></latest>";
                    }
                    filteredMetadata = filteredMetadata.replaceFirst( LATEST, newLatest );
                }
            }

            return filteredMetadata;
        }

        private String prohibitRelease( final String originalMetadata )
        {
            String filteredMetadata = originalMetadata;
            if ( !allowsReleases )
            {
                final Pattern releasePattern = Pattern.compile( RELEASE );
                final Matcher releaseMatcher = releasePattern.matcher( filteredMetadata );
                if ( releaseMatcher.find() )
                {
                    logger.debug( "FILTER: Suppressing prohibited release fields from: {}", transfer );

                    filteredMetadata = filteredMetadata.replaceFirst( RELEASE, "<release></release>" );
                }
            }
            return filteredMetadata;
        }

        private String prohibitSnapshots( final String originalMetadata){
            String filteredMetadata = originalMetadata;
            if ( !allowsSnapshots )
            {
                logger.debug( "FILTER: Suppressing prohibited snapshot fields from: {}", transfer );

                final String snapshots = StringUtils.substringBetween( filteredMetadata, "<snapshotVersions>",
                                                                       "</snapshotVersions>" );
                if ( snapshots != null )
                {
                    filteredMetadata = filteredMetadata.replace( snapshots, "" );
                }

                final String snapshot = StringUtils.substringBetween( filteredMetadata, "<snapshot>", "</snapshot>" );
                if ( snapshot != null )
                {
                    filteredMetadata = filteredMetadata.replace( snapshot, "" );
                }
            }
            return filteredMetadata;
        }

        @Override
        public void write( final int b )
        {
            buffer.append( (char) b );
        }

        @Override
        public void write( byte[] b, int off, int len ) throws IOException
        {
            for(int i=off; i<len; i++)
            {
                buffer.append( (char) b[i] );
            }
        }

        @Override
        public void flush() throws IOException
        {
            try
            {
                stream.write( filterMetadata().getBytes() );
                stream.flush();
            }
            finally
            {
                buffer = new StringBuilder();
            }
        }
    }
}
