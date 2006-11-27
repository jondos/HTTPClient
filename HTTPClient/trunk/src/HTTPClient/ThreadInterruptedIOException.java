package HTTPClient;

import java.io.InterruptedIOException;

/**
 * Is thrown if the current thread has been interrupted.
 * @author Rolf Wendolsky 06/06/17
 */
public class ThreadInterruptedIOException extends InterruptedIOException
{
	public ThreadInterruptedIOException(String a_message)
	{
		super(a_message);
	}
}
