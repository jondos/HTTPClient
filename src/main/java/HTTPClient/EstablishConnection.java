package HTTPClient;

import java.net.Socket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * @author Rolf Wendolsky
 * @version 06/5/02
 */
final class EstablishConnection extends Thread
	{
		String actual_host;
		int actual_port;
		IOException exception;
		Socket sock;
		SocksClient Socks_client;
		boolean close;
		HTTPClientSocketFactory m_socketFactory=null;

		EstablishConnection(String host, int port, SocksClient socks,HTTPClientSocketFactory socketFactory)
			{
				super("EstablishConnection (" + host + ":" + port + ")");
				try
					{
						setDaemon(true);
					}
				catch (SecurityException se)
					{
					} // Oh well...

				actual_host = host;
				actual_port = port;
				Socks_client = socks;

				exception = null;
				sock = null;
				close = false;
				m_socketFactory=socketFactory;
			}

		public void run()
			{
				try
					{
						if (Socks_client != null)
							{
								sock = Socks_client.getSocket(actual_host, actual_port);
							}
						else
							{
								// try all A records
								InetAddress[] addr_list = InetAddress.getAllByName(actual_host);

								for (int idx = 0; idx < addr_list.length; idx++)
									{
										try
											{
												if(m_socketFactory==null)
													sock = new Socket(addr_list[idx], actual_port);
												else
													sock=m_socketFactory.connect(addr_list[idx], actual_port);
												break; // success
											}
										catch (SocketException se)
											{ // should be NoRouteToHostException
												if (idx == addr_list.length - 1 || close)
													{
														exception = se;
														break;
														//throw se; // we tried them all
													}
											}
									}
							}
					}
				catch (IOException ioe)
					{

						exception = ioe;
					}
				catch (Exception ioe)
					{

						exception = new IOException("UnknownIOExcpetion in EstablishConnection: " + ioe.getMessage());
					}

				if (close)
					{
						try
							{
								sock.close();
							}
						catch (Exception ioe)
							{
							}
						sock = null;
					}
			}

		IOException getException()
			{
				return exception;
			}

		Socket getSocket()
			{
				return sock;
			}

		void forget()
			{
				close = true;
			}
	}
