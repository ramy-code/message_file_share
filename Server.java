import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;

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
		//Déclaration du Thread attendant la connexion
		Thread listenThread = new Thread(new Runnable() {
			public void run()
			{
				while(true) {
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
						if(!clientsList.contains(client)) {
							clientsList.add(client);
							//Lancer Thread d'écoute
							runListenThread(client);
						}else {
							connectionSocket.close();
						}
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
					try { 
						messageProcessor(client, readStream(is));
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
	
	private byte[] readStream(InputStream is) throws IOException
	{	
		//Lecture de taille de message
		int messageSize = 0;

		//Lecture de la taille du message
		ByteBuffer sBuf = ByteBuffer.allocate(4); //size byte buffer
		for(int i = 0; i < 4; i++) {
			int sb = is.read();
			if(sb < 0) throw new ClosedConnectionException("Connexion fermée par le pair");
			sBuf.put((byte) sb);
		}
		messageSize = sBuf.getInt(0);
		final int messageSize_copy = messageSize;
		System.out.println("Size of received message : " + messageSize);

		byte[] buffer = new byte[4096];
		int index = 0;
		while(messageSize > 0)
		{
			int size = is.read(buffer, index, messageSize);
			if(size < 0) //Si outputStream de l'autre pair est fermé alors fermer ce socket
				throw new ClosedConnectionException("Connexion fermée par le pair");
			
			index += size;
			messageSize -= size;
		}
		
		return Arrays.copyOf(buffer, messageSize_copy);
	}
	
	private byte messageProcessor(ClientLog sender, byte[] buffer)
	{
		if(buffer == null || buffer.length < 1)
			return -1;
		//String out = "";
		/*out += connectionSocket.getInetAddress().getHostName() + ":" + 
				connectionSocket.getPort() + " :> ";*/
		
		switch(buffer[0])
		{
			case FLAG_MESSAGE:
				String message = sender.username + ": ";;
				message += new String(buffer, 1, buffer.length - 1);
				broadcastMessage(sender, message);
				break;
				
			/*case FLAG_DATA_STREAM_OFFER: 
				out+= "Open data socket offer received, port : " + 
						Integer.valueOf(new String(buffer, 1, buffer.length - 1));
				System.out.println(out);
				return FLAG_DATA_STREAM_OFFER;
				
			case FLAG_FILE_SEND_RQST:
				if(!fileTransfert.get()) { 
					StringTokenizer st = new StringTokenizer(new String(buffer, 1, buffer.length - 1));
					try {
						receiveFile(st.nextToken(), Long.valueOf(st.nextToken()));
					}catch(Exception e) {
						e.printStackTrace();
						fileTransfert.set(false);
					}
				}
				else denyFileShare();
				break;
				
			case FLAG_FILE_SEND_ACCEPT:
				approvedSend.set(true);
				semWaitApproval.release();
				break;
				
			case FLAG_FILE_SEND_DENY:
				approvedSend.set(false);
				semWaitApproval.release();
				break;
			*/
		}
		//System.out.println(out);
		
		return 0;
	}
	
	private synchronized void broadcastMessage(ClientLog sender, String message) {
		for(ClientLog client : clientsList) {
			if(!client.equals(sender)) {
				sendMessage(client, FLAG_MESSAGE, message);
			}
		}
	}
	
	private void sendMessage(ClientLog client, byte[] message) //Envoie un tableau de Byte brute
	{
		try {
			client.os.write(message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void sendMessage(ClientLog client, byte command, String data)
	{
		/*
		 * Envoie une commande de type 'command' contenant les données dans 'message'
		 */
		
		int messageSize = 0;
		byte[] byteArrayMessage = null;
		
		if(data != null) {
			byteArrayMessage = data.getBytes();
			messageSize = byteArrayMessage.length;
		}
		
		ByteBuffer messageBytes = ByteBuffer.allocate(messageSize + 5);
		
		//écriture de la taille de message
		/*for (byte b : ByteBuffer.allocate(4).putInt(messageSize + 1).array())
			messageBytes.put(b);
		*/
		messageBytes.putInt(messageSize + 1);
		messageBytes.put(command); // écriture de la commande
		if(data != null) // écriture du message
			messageBytes.put(byteArrayMessage);
		
		sendMessage(client, messageBytes.array());
	}
}
