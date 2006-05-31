package org.apache.maven.shared.invoker;

import java.io.PrintWriter;
import java.io.StringWriter;

public class SystemOutLogger
    implements InvokerLogger
{
    
    private int threshold = INFO;

    public void debug( String message )
    {
        log( DEBUG, message, null );
    }

    private void log( int level, String message, Throwable error )
    {
        if ( level > threshold )
        {
            // don't log when it doesn't match your threshold.
            return;
        }
        
        if ( message == null && error == null )
        {
            // don't log when there's nothing to log.
            return;
        }
        
        StringBuffer buffer = new StringBuffer();
        
        switch ( level )
        {
            case( DEBUG ) :
            {
                buffer.append( "[DEBUG]" );
                break;
            }
            case( INFO ) :
            {
                buffer.append( "[INFO]" );
                break;
            }
            case( WARN ) :
            {
                buffer.append( "[WARN]" );
                break;
            }
            case( ERROR ) :
            {
                buffer.append( "[ERROR]" );
                break;
            }
            case( FATAL ) :
            {
                buffer.append( "[FATAL]" );
                break;
            }
        }
        
        buffer.append( ' ' );
        
        if ( message != null )
        {
            buffer.append( message );
        }
        
        if ( error != null )
        {
            StringWriter writer = new StringWriter();
            PrintWriter pWriter = new PrintWriter( writer );
            
            error.printStackTrace( pWriter );
            
            if ( message != null )
            {
                buffer.append( '\n' );
            }
            
            buffer.append( "Error:\n" );
            buffer.append( writer.toString() );
        }
        
        System.out.println( buffer.toString() );
    }

    public void debug( String message, Throwable throwable )
    {
        log( DEBUG, message, throwable );
    }

    public void error( String message )
    {
        log( ERROR, message, null );
    }

    public void error( String message, Throwable throwable )
    {
        log( ERROR, message, throwable );
    }

    public void fatalError( String message )
    {
        log( FATAL, message, null );
    }

    public void fatalError( String message, Throwable throwable )
    {
        log( FATAL, message, throwable );
    }

    public int getThreshold()
    {
        return threshold;
    }

    public void info( String message )
    {
        log( INFO, message, null );
    }

    public void info( String message, Throwable throwable )
    {
        log( INFO, message, throwable );
    }

    public boolean isDebugEnabled()
    {
        return threshold >= DEBUG;
    }

    public boolean isErrorEnabled()
    {
        return threshold >= ERROR;
    }

    public boolean isFatalErrorEnabled()
    {
        return threshold >= FATAL;
    }

    public boolean isInfoEnabled()
    {
        return threshold >= INFO;
    }

    public boolean isWarnEnabled()
    {
        return threshold >= WARN;
    }

    public void warn( String message )
    {
        log( WARN, message, null );
    }

    public void warn( String message, Throwable throwable )
    {
        log( WARN, message, throwable );
    }

    public void setThreshold( int threshold )
    {
        this.threshold = threshold;
    }

}
