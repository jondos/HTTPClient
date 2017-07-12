package HTTPClient;

import java.net.InetAddress;
import java.net.UnknownHostException;

public interface IHTTPClientDNSResolver
	{
		InetAddress[]	getAllByName(String host) throws UnknownHostException;
	}
