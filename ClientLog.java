import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientLog {
	public String username;
	public InetAddress ipAddress;
	public Socket connectionSocket;
	public InputStream is;
	public OutputStream os;
	public volatile AtomicBoolean connected = new AtomicBoolean(false);
	
	public ClientLog(String username, Socket connectionSocket) throws IOException {
		this.username = username;
		this.connectionSocket = connectionSocket;
		this.is = connectionSocket.getInputStream();
		this.os = connectionSocket.getOutputStream();
		this.connected.set(true);
		this.ipAddress = connectionSocket.getInetAddress();
	}
	
	public void close() throws IOException {
		connected.set(false);
		is.close();
		os.close();
		connectionSocket.close();
	}
	
	@Override
	public boolean equals(Object o) {
		if(o == null)
			return false;
		if(this == o)
			return true;
		ClientLog other = (ClientLog) o;
		return this.username.equals(other.username); //|| this.ipAddress.equals(other.ipAddress);
	}
}
