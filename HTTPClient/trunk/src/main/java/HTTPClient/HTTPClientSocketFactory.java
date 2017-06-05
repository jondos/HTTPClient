package HTTPClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public interface HTTPClientSocketFactory
	{
		Socket connect(InetAddress host,int port) throws IOException;
	}
