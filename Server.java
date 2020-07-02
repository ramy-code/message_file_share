import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.LinkedList;

import AbstractHost.ClosedConnectionException;

public class Server extends AbstractHost{
	private LinkedList<ClientLog> clientsList;
	private ServerSocket serverSocket;
	private String localIP;

	public Server() throws UnknownHostException, IOException
	{
		this(getLocalAddress(), 0);
	}
	
	public Server(int port) throws UnknownHostException, IOException
	{
		this(getLocalAddress(), port);
	}
	
	public Server(String hostIP) throws UnknownHostException, IOException
	{
		this(hostIP, 0);
	}
	
	public Server(String hostingIP, int port) throws UnknownHostException, IOException
	{
		localIP = hostingIP;
		serverSocket = new ServerSocket(port, 10, InetAddress.getByName(localIP));
		clientsList = new LinkedList<ClientLog>();
	}
	
	public void waitForConnections()
	{
		//D�claration du Thread attendant la connexion
		Thread listenThread = new Thread(new Runnable() {
			public void run()
			{
				while(true) {
					System.out.println("En attente de connexion sur l'addresse : " + serverSocket.getLocalSocketAddress() +  "...");
					try
					{
						Socket connectionSocket = serverSocket.accept();
						System.out.println("H�te " + connectionSocket.getInetAddress().getHostName() + " / " + 
								connectionSocket.getInetAddress().getHostAddress() +
								":" + connectionSocket.getPort() + " connect�.");	//connection accept�e
						
						//attendre la reception du username
						String username = fetchUsername(connectionSocket.getInputStream());
						
						//une fois re�u ajouter le client � la liste
						ClientLog client = new ClientLog(username, connectionSocket);
						clientsList.add(client);
						runListenThread(client);
						
						//Lancer Thread d'�coute
						runMessageListener();
						openDataConnection();
					}
					catch(IOException e)
					{
						/*
						 * d�tecte si connexion impossible ou bien arr�t�e par le thread principal 
						*/
						if(!USER_INTERRUPTED.get()) {
							System.err.println("Impossible de se connecter au client.");
							e.printStackTrace();
						}
						else break;
					}
				}
			}
		});
		
		listenThread.start();
	}
	
	private String fetchUsername(InputStream is) throws IOException {
		int index = 0;
		//Lecture de la taille du username
		int remainingBytes = is.read();
		
		byte buffer[] = new byte[30];
		int size = 0;
		while(remainingBytes > 0)	{
			size = is.read(buffer, index, remainingBytes);
			if(size < 0)
				break;
			
			index += size;
			remainingBytes -= size;
		}
		
		if(remainingBytes == -1 || size == -1)	//Si OutputStream du client est ferm� alors fermer ce socket
			throw new ClosedConnectionException("Connexion ferm�e par le client");
		
		return new String(buffer).trim();
	}
	
	private void runListenThread(ClientLog client)
	{
		Thread listenThread = new Thread(new Runnable() {
			public void run()
			{
				//Boucle d'�coute des messages re�us
				while(true)
				{
					try { 
						messageProcessor(readStream());
					}catch(ClosedConnectionException cce) {
						if(client.connected.get()) {
							client.close();
						}
					}catch (IOException e) {
							e.printStackTrace();
					}
				}
			}
		});
		
		listenThread.start();
	}
}
