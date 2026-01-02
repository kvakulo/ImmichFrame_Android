package com.immichframe.immichframe.moderntls;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Enables TLS v1.2 and TLS v1.3 when creating SSLSockets.
 * <p/>
 * Android supports TLS v1.2 from API {@link Build.VERSION_CODES#JELLY_BEAN}, but enables it
 * by default only from API {@link Build.VERSION_CODES#LOLLIPOP}.TLS v1.3 is enabled only
 * from API {@link Build.VERSION_CODES#Q}.
 *
 * @link https://developer.android.com/reference/javax/net/ssl/SSLSocket.html
 * @see SSLSocketFactory
 */
public final class ModernTlsSocketFactory extends SSLSocketFactory {
    private static final String[] TLS_12_AND_13_ONLY = new String[] {"TLSv1.2", "TLSv1.3"};
    private final SSLSocketFactory delegate;

    public ModernTlsSocketFactory(SSLSocketFactory sslSocketFactory)  {
        delegate = sslSocketFactory;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return enableTLSOnSocket(delegate.createSocket());
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose)
            throws IOException {
        return enableTLSOnSocket(delegate.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return enableTLSOnSocket(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException {
        return enableTLSOnSocket(delegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return enableTLSOnSocket(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException {
        return enableTLSOnSocket(delegate.createSocket(address, port, localAddress, localPort));
    }

    private Socket enableTLSOnSocket(Socket socket) {
        if (socket instanceof SSLSocket) {
            ((SSLSocket) socket).setEnabledProtocols(TLS_12_AND_13_ONLY);
        }
        return socket;
    }
}