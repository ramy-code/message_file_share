import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Server extends AbstractHost{
	private LinkedList<ClientLog> clientsList;
	private ServerSocket serverSocket;
	public String localIP;
	public int port;
	private volatile Semaphore semWaitApproval = new Semaphore(0, true);
	private volatile Semaphore semApproved = new Semaphore(0, true);
	private volatile DatagramSocket udpSocket = null;
	private LinkedList<Socket> connectionSockets = null;

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
		connectionSockets = new LinkedList<Socket>();
		this.port = serverSocket.getLocalPort();
	}
	
	public void waitForConnections()
	{
		//Déclaration du Thread attendant la connexion
		Thread listenThread = new Thread(new Runnable() {
			public void run()
			{
				udpSocket = runDiscoveryListener();
				while(true) {
					if(serverSocket == null || serverSocket.isClosed())
						return;
					System.out.println("En attente de connexion sur l'addresse : " + serverSocket.getLocalSocketAddress() +  "...");
					try
					{
						Socket connectionSocket = serverSocket.accept();
						System.out.println("Hôte " + connectionSocket.getInetAddress().getHostName() + " / " + 
								connectionSocket.getInetAddress().getHostAddress() +
								":" + connectionSocket.getPort() + " connecté.");	//connection acceptée
						
						//attendre la reception du username
						String username = fetchUsername(connectionSocket.getInputStream());
						System.out.println("Username du client reçu: " + username);
						
						//une fois reçu ajouter le client à la liste
						ClientLog client = new ClientLog(username, connectionSocket);
						synchronized(clientsList) {
							if(!clientsList.contains(client)) {
								clientsList.add(client);
								//Lancer Thread d'écoute
								runListenThread(client);
								broadcastClientList();
								broadcastMessage(null, formatMessage(FLAG_MESSAGE, client.username + " has joined the chat."));
								synchronized (connectionSockets) {
									connectionSockets.add(connectionSocket);
								}
							}else {
								client.close();
								System.out.println("Client déjà connecté !");
							}
						}
						System.out.println("liste des clients: ");
						for(ClientLog c : clientsList)
							System.out.println(c.username);
					}
					catch(IOException e)
					{
						/*
						 * détecte si connexion impossible ou bien arrêtée par le thread principal 
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
		/*
		 * Lit le username du client qui vient de se connecter (c'est le premier message que le client envoie)
		 */
		
		//Lecture de la taille du username
		int remainingBytes = is.read();
		
		byte buffer[] = new byte[30];
		int size = 0;
		int index = 0;
		while(remainingBytes > 0)	{
			size = is.read(buffer, index, remainingBytes);
			if(size < 0)
				break;
			
			index += size;
			remainingBytes -= size;
		}
		
		if(remainingBytes == -1 || size == -1)	//Si OutputStream du client est fermé alors fermer ce socket
			throw new ClosedConnectionException("Connexion fermée par le client");
		
		return new String(buffer).trim();
	}
	
	private void runListenThread(ClientLog client)
	{
		Thread listenThread = new Thread(new Runnable() {
			public void run()
			{
				//Boucle d'écoute des messages reçus
				InputStream is = client.is;
				while(client.connected.get())
				{
					//System.err.println("Entered while loop");
					try { 
						messageProcessor(client, readStream(is));
					}catch(IOException e) {
						if(client.connected.get()) {
							try {
								client.close();
							} catch (IOException e1) {
								e1.printStackTrace();
							}
						}
					}
				}
				
				//System.err.println("Exited while loop");
				synchronized(clientsList) {
					clientsList.remove(client);
				}
				
				broadcastClientList();
				broadcastMessage(null, client.username + " has disconnected.");
			}
		});
		
		listenThread.start();
	}
	
	private ClientLog getClientByName(String username) {
		synchronized (clientsList) {
			for(ClientLog c : clientsList) {
				if(c.username.equals(username))
					return c;
			}
		}
		
		return null;
	}
	
	private byte messageProcessor(ClientLog sender, byte[] buffer)
	{
		if(buffer == null || buffer.length < 1)
			return -1;
		//String out = "";
		/*out += connectionSocket.getInetAddress().getHostName() + ":" + 
				connectionSocket.getPort() + " :> ";*/
		
		String message;
		switch(buffer[0])
		{
			case FLAG_MESSAGE:
				message = sender.username + ": ";
				message += new String(buffer, 1, buffer.length - 1);
				broadcastMessage(sender, message);
				break;
				
			case FLAG_PRIVATE_MESSAGE:
				String data = new String(buffer, 1, buffer.length - 1);
				
				int separator = data.indexOf(';');
				if(separator > 0) {
					String username = data.substring(0, separator);
					ClientLog receiver = getClientByName(username);
					if(receiver != null) {
						message = sender.username + " (private): ";
						message += data.substring(separator+1, data.length());
						synchronized(receiver.os) {
							sendMessage(receiver.os, formatMessage(FLAG_PRIVATE_MESSAGE, message));
						}
					}
				}
				break;
				
			case FLAG_CLIENTS_LIST:
				String list = "";
				for(ClientLog client : clientsList) {
					list += client.username + ";";
				}
				synchronized(sender.os) {
					sendMessage(sender.os, formatMessage(FLAG_CLIENTS_LIST, list));
				}
				break;
				
			case FLAG_FILE:
				StringTokenizer st = new StringTokenizer(new String(buffer, 1, buffer.length - 1), "|");
				try {
					String fileName = st.nextToken();
					int length = Integer.valueOf(st.nextToken());
					byte[] fileData = receiveFile(sender.is, length);
					broadcastFile(sender, fileName, length, fileData);
				}catch(Exception e) {
					e.printStackTrace();
				}
				break;
			
			case FLAG_FILE_SEND_ACCEPT:
				semApproved.release();
				semWaitApproval.release();
				break;
				
			case FLAG_FILE_SEND_DENY:
				semWaitApproval.release();
				break;
		}
		
		return 0;
	}
	
	private void broadcastClientList() {
		String list = "";
		for(ClientLog client : clientsList) {
			list += client.username + ";";
		}
		broadcastMessage(null, FLAG_CLIENTS_LIST, list);
	}
	
	private void broadcastFile(ClientLog sender, String fileName, int length, byte[] fileData) {
		synchronized(clientsList) {
			for(ClientLog client : clientsList) {
				if(!client.equals(sender)) {
					sendFile(client.os, fileName, length, fileData);
				}
			}
		}
	}
	
	private void sendFile(OutputStream os, String fileName, int length, byte[] fileData) {
		//Envoie de la demande de transfert de fichier
		synchronized(os) {
			sendMessage(os, formatMessage(FLAG_FILE_SEND_RQST, fileName + " (" + length + " octets)"));
		}
		
		//Lancement du thread qui attend la réponse et envoie le fichier
		Thread fileTransfertThread = new Thread(new Runnable() {
			public void run()
			{
				try {
					if(semWaitApproval.tryAcquire(60, TimeUnit.SECONDS)) {
						if(semApproved.tryAcquire(0, TimeUnit.SECONDS)) {
							//Transfert de fichier accepté par le client: commencer à envoyer
							System.out.println("trasfert de fichier accepté");
							synchronized(os) {
								sendMessage(os, formatMessage(FLAG_FILE, fileName + "|" + length));
								sendMessage(os, fileData);
							}
						}
						else System.out.println("transfert de fichier refusé");
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		});
		
		fileTransfertThread.start();
	}
	
	private void broadcastMessage(ClientLog sender, byte[] message) {
		synchronized(clientsList) {
			for(ClientLog client : clientsList) {
				if(!client.equals(sender)) {
					synchronized(client.os) {
						sendMessage(client.os, message);
					}
				}
			}
		}
	}
	
	private void broadcastMessage(ClientLog sender, byte command, String message) {
		byte[] formattedMessage = formatMessage(command, message);
		broadcastMessage(sender, formattedMessage);
	}
	
	private void broadcastMessage(ClientLog sender, String message) {
		broadcastMessage(sender, FLAG_MESSAGE, message);
	}
	
	private DatagramSocket runDiscoveryListener() {
		//Lance un thread qui reste en écoute aux messages de découverte
		boolean socketOpened = false;
		for(int i = 0; i < 10 && !socketOpened; i++) {
			try {
				udpSocket = new DatagramSocket(UDPPort + i, InetAddress.getByName(getLocalAddress()));
				socketOpened = true;
			} catch (SocketException | UnknownHostException e) {
				e.printStackTrace();
			}
		}
		
		if(!socketOpened) {
			System.err.println("Could not open discovery listener");
			return null;
		}
		
		Thread discoveryListenThread = new Thread(new Runnable() {
			public void run() {
				while(true) { //reste en écoute tant que udpSocket reste ouvert
					byte[] dpBuffer = new byte[5];
					DatagramPacket packet = new DatagramPacket(dpBuffer, dpBuffer.length);
					int receivedPort = -1;
					
					try {
						udpSocket.receive(packet); //en écoute des messages de découverte (se débloque si udpSocket est fermé de l'extérieur)
						if(dpBuffer[0] == FLAG_SERVER_DISCOVERY) {
							receivedPort = ByteBuffer.wrap(packet.getData(), 1, 4).getInt();
							ByteBuffer bbuf = ByteBuffer.allocate(5);
							bbuf.put(FLAG_SERVER_AD);
							bbuf.putInt(port);
							DatagramPacket responsePacket = new DatagramPacket(bbuf.array(), 5, packet.getAddress(), receivedPort);
							udpSocket.send(responsePacket);
						}
					} catch (IOException e) {
							return;
					}
				}
			}
		});
		
		discoveryListenThread.start();
		
		return udpSocket;
	}
	
	public void close() {
		try {
			serverSocket.close();
			if(udpSocket != null)
				udpSocket.close();
			if(connectionSockets != null) {
				for(Socket s : connectionSockets) {
					if(!s.isClosed()) {
						s.close();
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException {
		Server serveur = new Server();
		serveur.waitForConnections();
	}
}
