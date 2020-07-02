import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractHost {
	
	protected volatile AtomicBoolean USER_INTERRUPTED = new AtomicBoolean(false);
	
	public class ClosedConnectionException extends IOException {

		public ClosedConnectionException(String string) {
			super(string);
		}
	}
	
	public static String getLocalAddress() throws SocketException, UnknownHostException
	{
		/*
		 * Retourne l'adresse IP de la machine locale
		 */
		try(final DatagramSocket socket = new DatagramSocket())
		{
			socket.connect(InetAddress.getByName("192.168.1.1"), 80);
			return socket.getLocalAddress().getHostAddress();
		}
	}
	
	public void close() {
		throw new UnsupportedOperationException("Close method Not yet implemented");
	}
}
