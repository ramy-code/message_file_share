import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientLog {
	public String username;
	//private InetAddress ipAddress;
	public Socket connectionSocket;
	public InputStream is;
	public OutputStream os;
	public volatile AtomicBoolean connected = new AtomicBoolean(false);
	
	public ClientLog(String username, Socket connectionSocket) throws IOException {
		this.username = username;
		this.connectionSocket = connectionSocket;
		this.is = connectionSocket.getInputStream();
		this.os = connectionSocket.getOutputStream();
		//this.ipAddress = connectionSocket.getInetAddress();
	}
	
	public void close() {
		throw new UnsupportedOperationException("Close method Not yet implemented");
	}
}
