package org.commonjava.indy.folo.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.commonjava.indy.model.core.io.IndyObjectMapper;
import org.infinispan.query.Transformer;

import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import java.io.IOException;

/**
 * A customized infinispan {@link org.infinispan.query.Transformer} used for {@link TrackedContentEntry}
 * to support it to be used as infinispan cache key in indexing.
 */
public class TrackedContentEntryTransformer implements Transformer
{
    @Inject
    private IndyObjectMapper objectMapper;

    public TrackedContentEntryTransformer(){
        initMapper();
    }

    private void initMapper()
    {
        if ( objectMapper == null )
        {
            final CDI<Object> cdi = CDI.current();
            objectMapper = cdi.select( IndyObjectMapper.class ).get();
        }
    }

    @Override
    public Object fromString( String s )
    {
        try
        {
            return objectMapper.readValue( s, TrackedContentEntry.class );
        }
        catch ( IOException e )
        {
            throw new IllegalStateException( e );
        }
    }

    @Override
    public String toString( Object customType )
    {
        if ( customType instanceof TrackedContentEntry )
        {
            try
            {
                return objectMapper.writeValueAsString( customType );
            }
            catch ( JsonProcessingException e )
            {
                throw new IllegalStateException( e );
            }
        }
        else
        {
            throw new IllegalArgumentException( "Expected customType to be a " + TrackedContentEntry.class.getName() );
        }
    }
}