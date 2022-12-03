package CNT5106;

import java.io.*;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Timer;

import static CNT5106.Message.MessageTypes;

public class Peer{
	public static class TCPConnectionDownloadRateComparator implements Comparator<PeerTCPConnection> { // used by MAX priority queue in timerup function
		@Override
		public int compare(PeerTCPConnection x, PeerTCPConnection y) {
			//normal set up x < y return -1(move x up) x > y return 1(move y up) this is min heap so swap
			if(x.interested && y.interested) {
				if (x.downloadRate < y.downloadRate) {
					return 1; // move y up better download rate
				}
				if (x.downloadRate > y.downloadRate) {
					return -1; // move x up better download rate
				}
				return 0; // equal case
			}
			if(x.interested){ // x should move up
				return -1;
			}
			if(y.interested) { // y should move up
				return 1;
			}
			return 0; // both not interested
		}
	}
    Integer myID; //ID of this peer
    String logFileName; //the name of the file this peer should be logging to
    String commonConfigFileName; //the common config file name
    String peerInfoFileName; //the peer info file name
    int numPreferredPeers; //number of preferred peers this peer should have
    int unchokingInterval; // in seconds
    int optimisticUnchokingInterval;
    String desiredFileName; //the file we are trying to get
    int desiredFileSize; //the size of the file we want
    int pieceSize; //the size of the pieces of the file we want
    int numPieces; //the total number of pieces of the file, used as part of bitfield logic.
    boolean haveFile; //indicate if I have entire file or not
	boolean allPeersHaveFile = false; // used to decide when to exit. if all peers have it and so do I then exit.
    boolean[] havePieces; //track what pieces I have by index value
	boolean[] requestedPieces; // track pieces I have requested from peers
	byte [] file; // actual file loaded in on init if peer has the file.
	Logger logger;
    Thread serverThread;
	Timer timer;
	UnchokeTimer optimisticTimer;
	UnchokeTimer regularTimer;
	ArrayList<Integer> preferredPeers = new ArrayList<>(); // will hold numPreferredPeers
	int optimisticPeer = -1; // will hold random peer note this peer may also be a preferred peer after a regular time period
    LinkedBlockingQueue<Message> inbox = new LinkedBlockingQueue<Message>(); // all recev tcp threads write to here
    HashMap<Integer,PeerTCPConnection> peerTCPConnections = new HashMap<Integer, PeerTCPConnection>(); // holds all peer tcp connections
    ConcurrentHashMap<Integer,Boolean> peerFileMap = new ConcurrentHashMap<Integer, Boolean>(); // map peer IDs to status of having file or not
    ConcurrentHashMap<Integer,Boolean[]> peerPieceMap = new ConcurrentHashMap<Integer, Boolean[]>(); //map peer IDs to boolean arrays indicating if it has piece
	Random rand;
    public Peer(int peerID, String logFileName, String commonConfigFileName, String peerInfoFileName) {
        myID = peerID;
        this.logFileName = logFileName;
        this.commonConfigFileName = commonConfigFileName;
        this.peerInfoFileName = peerInfoFileName;
        logger = new Logger(logFileName,myID); // initialize logger
		timer = new Timer(); // init new timer
		rand = new Random(); // init random num generator
		optimisticTimer = new UnchokeTimer(this,true); // timer tasks that call timerUp method of peer class
		regularTimer = new UnchokeTimer(this,false);


        //read in the common config file and set the other attributes for the peer.
    	Pattern prefNeighborsRegex = Pattern.compile("^(NUmberOfPreferredNeighbors)\\s(\\d{1,})$", Pattern.CASE_INSENSITIVE); //regex pattern for number of preferred neighbors config directive
    	Pattern unchokingIntervalRegex = Pattern.compile("^(UnchokingInterval)\\s(\\d{1,})$", Pattern.CASE_INSENSITIVE); //regex pattern for unchoking interval config directive
    	Pattern optUnchokingIntervalRegex = Pattern.compile("^(OptimisticUnchokingInterval)\\s(\\d{1,})$", Pattern.CASE_INSENSITIVE); //regex pattern for optimistic unchoking interval config directive
    	Pattern fileNameRegex = Pattern.compile("^(FileName)\\s(.{1,})$", Pattern.CASE_INSENSITIVE); //regex pattern for file name config directive
    	Pattern	fileSizeRegex = Pattern.compile("^(FileSize)\\s(\\d{1,})$", Pattern.CASE_INSENSITIVE); //regex pattern for file size config directive
    	Pattern pieceSizeRegex = Pattern.compile("^(PieceSize)\\s(\\d{1,})$", Pattern.CASE_INSENSITIVE); //regex pattern for piece size config directive
    	
    	Scanner configFile = new Scanner(commonConfigFileName);
    	while(configFile.hasNextLine()) //keep looping while we have more lines to read
    	{
    		String configLine = configFile.nextLine(); //pull in the config line
    		if(prefNeighborsRegex.matcher(configLine).find()) //config line is for number of preferred neighbors
    		{
    			this.numPreferredPeers = Integer.parseInt(prefNeighborsRegex.matcher(configLine).group(1)); //extract the value for config directive from regex, cast to int, and store it.
    		}
    		else if(unchokingIntervalRegex.matcher(configLine).find())
    		{
    			this.unchokingInterval = Integer.parseInt(unchokingIntervalRegex.matcher(configLine).group(1)); //extract the value for config directive from regex, cast to int, and store it.
    		}
    		else if(optUnchokingIntervalRegex.matcher(configLine).find())
    		{
    			this.optimisticUnchokingInterval = Integer.parseInt(optUnchokingIntervalRegex.matcher(configLine).group(1)); //extract the value for config directive from regex, cast to int, and store it.
    		}
    		else if(fileNameRegex.matcher(configLine).find())
    		{
    			this.desiredFileName = fileNameRegex.matcher(configLine).group(1);
    		}
    		else if(fileSizeRegex.matcher(configLine).find())
    		{
    			this.desiredFileSize = Integer.parseInt(fileSizeRegex.matcher(configLine).group(1));
    		}
    		else if(pieceSizeRegex.matcher(configLine).find())
    		{
    			this.pieceSize = Integer.parseInt(pieceSizeRegex.matcher(configLine).group(1));
    		}
    	}
    	configFile.close(); //we're done with the common config file, close it out.
    	numPieces = (int)(Math.ceil((double)(desiredFileSize)/(double)(pieceSize)));
		this.havePieces = new boolean[numPieces]; //init the pieces array to track what pieces we have
		this.requestedPieces = new boolean[numPieces];
		Arrays.fill(havePieces, haveFile); //set the initial values of the pieces array based on whether we've got the entire file.
		Arrays.fill(requestedPieces,haveFile); // don't request pieces I have so add to requested list
		if(haveFile){ // if I have the file read it into memory
			try {
				Path path = Paths.get(desiredFileName);
				file =  Files.readAllBytes(path); // bring file into memory
			}
			catch (Exception e){
				System.err.println("Error reading file to distribute");
			}
		}
		else{
			file = new byte[desiredFileSize]; // init space to save the file.
		}
    }
	public void timerUp(boolean optimistic){
		Message unchoke = new Message(0, Message.MessageTypes.unchoke,"");
		Message choke = new Message(0, Message.MessageTypes.choke,"");
		Object[] keys = peerTCPConnections.keySet().toArray(); // get keys in map currently
		if (optimistic){ // DONE
			if(optimisticPeer != -1) { // if an optimistic peer has been set do calculations for download rate (Assumes -1 not valid peerID aka not set)
				PeerTCPConnection opt = peerTCPConnections.get(optimisticPeer);
				opt.send(choke); // choke peer before setting new one
				opt.totalOptimisticPeriods += 1;
				// set new download rate
				opt.downloadRate = (double) opt.totalInMessages / ((opt.totalPreferredPeriods * unchokingInterval)
						+ (opt.totalOptimisticPeriods * optimisticUnchokingInterval));
				peerTCPConnections.get(optimisticPeer).send(choke); // choke old peer
				peerTCPConnections.get(optimisticPeer).choked = true; // used for tracking
			}
			int i = rand.nextInt(keys.length); // random index [0 - keys.length-1]
			while(!peerTCPConnections.get((Integer)keys[i]).choked) { // keep looking until find a peer that is choked
				i = rand.nextInt(keys.length); // random index [0 - keys.length-1]
			}
			optimisticPeer = (Integer) keys[i]; // set new optimistic peer
			peerTCPConnections.get(optimisticPeer).send(unchoke); // unchoke new peer
			peerTCPConnections.get(optimisticPeer).choked = false; // used for tracking
		}else {
			// set regular unchoke peers
			//NOTE: each data message increments that peers # of messages this is done in the PeerTCPConnection thread
			if (preferredPeers.isEmpty()) { // has not been intialized so do it now // DONE
				preferredPeers = new ArrayList<Integer>(); // set up new array list for next set of prefered peers
				for (int i = 0; i < numPreferredPeers && i < keys.length; i++) {
					preferredPeers.add(0, (Integer) (keys[i])); // fill with peers
					peerTCPConnections.get((Integer) keys[i]).send(unchoke); // unchoke since it is a prefered peer now
				}
			} else if (!haveFile) { // has run before so select preferred peers based on download rate because I don't have file //Done
				// top numPreferredPeers become new preferred peers
				for (int i = 0; i < preferredPeers.size(); i++) { // remove current prefered peers
					PeerTCPConnection current = peerTCPConnections.get(preferredPeers.get(i));
					if (preferredPeers.get(i) != optimisticPeer) { // don't add to optimisticPeers runtime let optimistic timer do it(don't double count)
						current.totalPreferredPeriods += 1;
					}
					current.send(choke);
					current.choked = true;
					// set new download rate
					current.downloadRate = (double) current.totalInMessages / ((current.totalPreferredPeriods * unchokingInterval)
							+ (current.totalOptimisticPeriods * optimisticUnchokingInterval));
				}
				Comparator<PeerTCPConnection> comp = new TCPConnectionDownloadRateComparator();
				PriorityQueue<PeerTCPConnection> bestPeers = new PriorityQueue<PeerTCPConnection>(peerTCPConnections.size(), comp);
				// fill max priority queue based on download rate
				for (int i = 0; i < keys.length; i++) { // add all peers to a max priority queue
					int current = (int) keys[i];
					bestPeers.add(peerTCPConnections.get(current));
				}
				preferredPeers = new ArrayList<Integer>(); // set up new array list for next set of prefered peers
				for (int i = 0; i < numPreferredPeers && !bestPeers.isEmpty(); i++) {
					PeerTCPConnection best = bestPeers.peek();
					preferredPeers.add(0, best.peerID); // fill with peers with best download rate
					best.send(unchoke); // unchoke since it is a prefered peer now
					best.choked = false;
				}
			} else { // has run before but I have file so don't use download rates anymore // DONE
				for (int i = 0; i < preferredPeers.size(); i++) { // choke old preferred
					int current = preferredPeers.get(i);
					peerTCPConnections.get(current).send(choke);
					peerTCPConnections.get(current).choked = true;
				}
				preferredPeers = new ArrayList<>();
				for (int i = 0; i < keys.length && preferredPeers.size() < numPreferredPeers; i++) {
					PeerTCPConnection current = peerTCPConnections.get((int) keys[i]);
					if (current.choked && current.interested) {
						preferredPeers.add(0, (int) keys[i]);
						current.send(unchoke);
						current.choked = false;
					}
				}
			}
		}
	}
	public void setAndRunTimer(boolean optimistic){ // the scheduled task runs constantly after each period is up
		if (optimistic){
			// set optimistic unchoke timer
			timer.schedule(optimisticTimer,0,(long)(optimisticUnchokingInterval)*1000); // milli to seconds

		}else{
			// set regular unchoke timer
			timer.schedule(regularTimer,0,(long)(unchokingInterval)*1000); // milli to seconds
		}
	}
    
    public void Connect(){ // parse manifest file and connect to peers
    	Scanner peerInfoFile = new Scanner(peerInfoFileName);
    	Pattern peerInfoRegex = Pattern.compile("^(\\d{1,})\\s([a-zA-Z\\d-\\.]{1,})\\s(\\d{1,})\\s(0|1)$",Pattern.CASE_INSENSITIVE);
        // connect to other peers with help from manifest file
        // read file connect to those peers probably need to try multiple times as other peers may not be up yet
        int currentLineNumber = 0; //keep track of what line number we're on.as it determines what we should do when we hit our own entry
    	boolean isFirstPeer = false; //are we the first peer listed in the file?
    	int serverListenPort = 0; //what port we should be listening on
    	
        while(peerInfoFile.hasNextLine()) { // start connecting to peers change while peer list not empty from manifest file
        	String peerInfoLine = peerInfoFile.nextLine(); //pull the current line into a string
        	Matcher peerInfoMatcher = peerInfoRegex.matcher(peerInfoLine); //match the line against the peer info regex so we can extract the attributes from subgroups.
        	if(peerInfoMatcher.find()) //only continue if the line is in expected format, otherwise silently ignore the line
        	{
        		int currentPeerID = Integer.parseInt(peerInfoMatcher.group(0));
        		String peerHostName = peerInfoMatcher.group(1);
        		int peerListenPort = Integer.parseInt(peerInfoMatcher.group(2));
        		boolean peerHasFile = (Objects.equals(peerInfoMatcher.group(3), "1"));
        		
        		if(currentPeerID == myID)
        		{
        			serverListenPort = peerListenPort;
        			haveFile = peerHasFile;
        			if(currentLineNumber == 0) //we're the first peer
        			{
        				isFirstPeer = true;
        			}
        		}
        		else
        		{
		            // spin up several threads for each peer that connects
		            try {
		            	if(!isFirstPeer) //only try to connect when we're not the first peer
		            	{
			                Socket peerSocket = new Socket(peerHostName, peerListenPort); // connect to a peer
			                PeerTCPConnection peerConnection = new PeerTCPConnection(inbox,peerSocket); // new connection
			                peerConnection.send(new Message(myID));// send handshake always first step
			                Message peerHandshake = peerConnection.getHandShake(); // receive response handshake always second step
			
			                peerConnection.setPeerId(peerHandshake.peerID); // set peerID for tracking of message origin in message queue
			                 // important later when messages are mixed in queue to track their origin
			
			                peerConnection.start(); // start that peers reading thread
			                peerTCPConnections.put(peerHandshake.peerID,peerConnection);
		            	}
		            	peerFileMap.put(currentPeerID, peerHasFile); //still build the map of which peers have what files.
		            }
		            catch (ConnectException e) {
		                System.err.println("Connection refused. Peer not found");
		            }
		            catch(UnknownHostException unknownHost){
		                System.err.println("You are trying to connect to an unknown host!");
		            }
		            catch(IOException ioException){
						System.err.println("iOException in server connection loop");
		            }
        		}
        	}
		currentLineNumber++;
        }
// use this lambda style if you need to spin up a random thread at any point just dont capture it
        final int serverPort = serverListenPort;
        serverThread = new Thread(() -> { // listen for other peers wishing to connect with me on seperate thread
            try { // fix this and connection phase to avoid duplicate connections
                ServerSocket listener = new ServerSocket(serverPort); // passive listener on own thread
                while(true) { // need to add map duplicate insert checks as some peers may try to connect after we have already connected
                    Socket peerSocket = listener.accept(); // this blocks waiting for new connections so must be on own thread
					PeerTCPConnection peerConnection = new PeerTCPConnection(inbox,peerSocket); // new connection
					peerConnection.send(new Message(myID));// send handshake always first step
					Message peerHandshake = peerConnection.getHandShake(); // receive response handshake always second step

					peerConnection.setPeerId(peerHandshake.peerID); // set peerID for tracking of message origin in message queue
					// important later when messages are mixed in queue to track their origin

					peerConnection.start(); // start that peers reading thread
					peerTCPConnections.put(peerHandshake.peerID,peerConnection);
					//THIS LOGIC MUST BE TESTED HOW DOES OTHER PEER KNOW IT IS DUPLICATE CONNECTION????
					if(peerTCPConnections.get(peerHandshake.peerID) == null) { // if not in map put in
						peerTCPConnections.put(peerHandshake.peerID, peerConnection);
						logger.logTCPConnection(peerHandshake.peerID); // new connection log it
					}
					else{ // if in map don't need two connections to peer so close it
						peerConnection.close();
					}
                }
            }
            catch (Exception e){
                System.out.println("Error running server sockets");
            }
        });
        serverThread.start();

		setAndRunTimer(true); // start timers will set up preferred peer array and optimistic peer once they run
		setAndRunTimer(false); // this gives time for peers to connect before initializing unchoked peers
    }
	private void processMessage(Message message){ // done
		// process each message depending on their type
		switch (message.type.ordinal()){
			case(0)-> processChokeMessage(message);
			case(1)-> processUnchokeMessage(message);
			case(2)-> processInterestedMessage(message);
			case(3)-> processNotInterestedMessage(message);
			case(4)-> processHaveMessage(message);
			case(5)-> processBitfieldMessage(message);
			case(6)-> processRequestMessage(message);
			case(7)-> processPieceMessage(message);
			default -> throw new RuntimeException("Unexpected message type in processMessage\n");
		}
	}
	private void processChokeMessage(Message message){ //Done-Nick

		logger.logChoking(message.peerID);
		// it choked me so do nothing. I will no longer receive file pieces
		// it may lose preferred peer status later on when download rate drops
		//track peers view of me to maintain ping pong of pieces because
		peerTCPConnections.get(message.peerID).iamChoked = true;
	}
	private void processUnchokeMessage(Message message){ //Done -Nick
		peerTCPConnections.get(message.peerID).iamChoked = false;
		logger.logUnchoking(message.peerID);
		// it unchoked me so send it what I want if download rate is good I may make it a preferred peer
		// it unchoked me so i will get pieces send which ones I want....
		StringBuilder payload = new StringBuilder();
		// peerPeice map has peices that that peers has
		//peer id and boolean[] index is for that piece id and true if that peer has that file so if i don't have it so request it...
		if(!haveFile) { // if i don't have file make a request else do nothing
			Boolean[] peersPieces = peerPieceMap.get(message.peerID);
			for (int i = 0; i < peersPieces.length && i < requestedPieces.length; i++) { // add check that it has not been requested??
				if (peersPieces[i] && !requestedPieces[i]) { // request a piece that they have and I have not requested already
					payload.append((char)i);
					requestedPieces[i] = true; // just requested it so update
					break;
				}
			}
			peerTCPConnections.get(message.peerID).send(new Message(4, MessageTypes.request, payload.toString()));
		}
	}
	private void processInterestedMessage(Message message){
		logger.logRecvIntMessage(message.peerID);
		Boolean[] peerPieces = peerPieceMap.get(message.peerID); //retrieve the current mapping of what pieces we think peer has
		Boolean[] newPeerPieceMap = new Boolean[numPieces];
		for(int i=0; i<numPieces; i++) //figure out what pieces from me peer already has
		{
			newPeerPieceMap[i] = peerPieces[i] & havePieces[i];
		}
		for(int i=0; i<numPieces; i++) //figure out what pieces peer has that aren't from me
		{
			newPeerPieceMap[i] = newPeerPieceMap[i] | peerPieces[i];
		}
		peerPieceMap.put(message.peerID, newPeerPieceMap); //update the peer's piece mapping
		peerTCPConnections.get(message.peerID).interested = true;
	}
	private void processNotInterestedMessage(Message message){
		logger.logRecvNotIntMessage(message.peerID);
		Boolean[] peerPieces = peerPieceMap.get(message.peerID); //retrieve the current mapping of what pieces we think peer has
		Boolean[] newPeerPieceMap = new Boolean[numPieces];
		for(int i=0; i<numPieces; i++) //peer has all of my pieces, so OR what I think it has with what I have.
		{
			newPeerPieceMap[i] = peerPieces[i] | havePieces[i];
		}
		peerPieceMap.put(message.peerID, newPeerPieceMap);  //update the peer's piece mapping
		peerTCPConnections.get(message.peerID).interested = false;
	}
	private void processHaveMessage(Message message){ //add check for peers having file
		logger.logRecvHaveMessage(message.peerID,Integer.parseInt(message.payload)); // probably have to fix payload always int?
		int pieceIndex = Integer.parseInt(message.payload);
		Boolean[] peerPieces = peerPieceMap.get(message.peerID);
		Boolean[] newPeerPieceMap = Arrays.copyOf(peerPieces, numPieces); //create copy of the peer's piece map so we don't modify existing one
		newPeerPieceMap[pieceIndex] = true; //set the piece the peer says it has to true
		peerPieceMap.put(message.peerID, newPeerPieceMap);

		Boolean[] peerAndMissingPieces = new Boolean[numPieces]; //the pieces I'm missing ANDed with the pieces the peer has
		for(int i=0; i<numPieces; i++)
		{
			peerAndMissingPieces[i] = !havePieces[i] & newPeerPieceMap[i]; //invert what I have to mark if missing, AND it with what peer has to check if it has it
		}

		if(Arrays.stream(peerAndMissingPieces).toList().contains(Boolean.TRUE)) //the peer has a piece that I am missing
		{
			Message interestedNotification = new Message(0, MessageTypes.interested, "");
			peerTCPConnections.get(message.peerID).send(interestedNotification);
		}

		else //the peer has nothing i need
		{
			Message notInterestedNotification = new Message(0, MessageTypes.notInterested, "");
			peerTCPConnections.get(message.peerID).send(notInterestedNotification);
		}
		// FIX SET peerConnection.iHaveFile to true if true somewhere above...
		allPeersHaveFile = true;
		peerTCPConnections.forEach((peerID, peerConnection) -> {
			if(!peerConnection.iHaveFile){
				allPeersHaveFile = false;
			}
		});
	}
	private void processBitfieldMessage(Message message){ // add check for peers having file
		//logger.lo no logger method for bitfield
		Boolean[] peerBitfield = new Boolean[message.payload.length()];
		for(int i =  0; i< message.payload.length();i++) // FIX THEY ARE BITS NOT CHARS
		{
			if(message.payload.charAt(i) =='1')
			{
				peerBitfield[i]= true;
			}else
			{
				peerBitfield[i] = false;
			}
		}
		peerPieceMap.put(message.peerID, peerBitfield);
		// FIX SET peerConnection.iHaveFile to true if true somewhere above...
		allPeersHaveFile = true;
		peerTCPConnections.forEach((peerID, peerConnection) -> {
			if(!peerConnection.iHaveFile){
				allPeersHaveFile = false;
			}
		});
	}
	private void processRequestMessage(Message message){ // done
		//Payload consists of 4 byte piece index filed and
		ByteBuffer lengthBuff = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
		byte[] received = message.payload.getBytes();
		//Retrieve 4 byte piece index value
		int reqPiece = lengthBuff.put(received).get(0); // the piece requested
		//Update Current peers bitfield to have that piece
		//Check if peer is choked or unchoked
		// Chris use bellow for checking if this peer is choked or not I just added this feature-Nic
		//Nic, thank you for this feature I will leave it using the feature and we can update if we need to during testing.-Christian
		PeerTCPConnection requestee = peerTCPConnections.get(message.peerID);
		if(!requestee.choked){ //If peer is not choked send them piece
			int startingIndex = reqPiece*pieceSize;
			StringBuilder payload = new StringBuilder();
			//Include piece index in beignning of message payload
			payload.append((char)reqPiece);
			//piece of file is from reqpiece*pieceSize to (reqpiece * pieceSize) + pieceSize. not inclusive
			for(int i = startingIndex; i < startingIndex + pieceSize && i < desiredFileSize; i++ ){ // add bytes received to my file
				payload.append((char) file[i]);
			}
				requestee.send(new Message(payload.length(), MessageTypes.piece, payload.toString()));
		}
	}
	private void processPieceMessage(Message message){ // done
		ByteBuffer lengthBuff = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
		byte[] PiecesReceived = message.payload.getBytes();
		byte[] indexOf = new byte[4];
		for(int i = 0; i < 4; i++){
			indexOf[i] = PiecesReceived[i];
		}
		//Retrieve 4 byte piece index value
		int recvPiece = lengthBuff.put(indexOf).get(0); // the piece i get is the piece i requested
		//Update Current peers bitfield to have that piece
		this.havePieces[recvPiece] = true;
		//Log download completetion of this piece
		logger.logDownloadingPiece(message.peerID, recvPiece,message.length);
		int startingIndex = recvPiece*pieceSize;
		for(int i = startingIndex + 4; i < message.length + startingIndex && i < desiredFileSize; i++ ){ // add bytes received to my file
			file[i] = PiecesReceived[i-startingIndex];
		}
		//Check havePieces to see if completed file
		for(int i = 0; i < this.havePieces.length;i++ )
		{
			if(this.havePieces[i])
			{
				this.haveFile = true;
				logger.logDownloadCompletion(); //Log completion of download
			}else
			{
				this.haveFile = false;
				break;
			}
		}
		//send out new have messages to all the peers we're connected to
		Message haveNotification = new Message(4, MessageTypes.have, Integer.toString(recvPiece));
		peerTCPConnections.forEach((peerID, peerConnection) -> {
			peerConnection.send(haveNotification); // update peers on what I have
		});
		PeerTCPConnection sender = peerTCPConnections.get(message.peerID);
		if(!sender.iamChoked){ // if sender has not choked me request another piece keep ping pong going
			// do not send if it has choked me because it won't send and will break the accuracy of the requestedPieces array
			int length = 4; // 4 byte piece index
			StringBuilder payload = new StringBuilder();
			Boolean[] peersPieces = peerPieceMap.get(message.peerID);
			for (int i = 0; i < peersPieces.length && i < requestedPieces.length; i++){ // add check that it has not been requested??
				if(peersPieces[i] && !requestedPieces[i]){ // request a piece that I don't have yet and I have not requested already
					payload.append((char)i);
					requestedPieces[i] = true; // just requested it so update
					break;
				}
			}
			sender.send(new Message(length,MessageTypes.request,payload.toString()));
		}
	}
    public void run(){ // file retrieval and peer file distribution done here
        // start main process of asking peers for bytes of file
        while(true){ // add && file is incomplete
            //process messages and respond appropriately
			if(!inbox.isEmpty()) {
				processMessage(inbox.peek());
				System.out.println(inbox.peek().type.toString());
				inbox.remove();
			}
			if(haveFile && allPeersHaveFile){ // fix allPeersHaveFile is never changed to true when all peers have the file.
				logger.logDownloadCompletion();
				try {
					FileOutputStream fos = new FileOutputStream(desiredFileName);
					fos.write(file, 0, file.length);
				}
				catch (Exception e){
					System.err.println("Error writing file out.");
				}
				break; // exit job complete? ro do i keep running to help other peers
			}
        }
    }
    public static void main(String[] args){
    	final int peerID = Integer.parseInt(args[0]);  //peerID is specified at time of execution, pull it from args
        final String logFileName = "log_peer_" + args[0] + ".log";
        final String commonConfigFile = "Common.cfg";
        final String peerInfoConfigFile = "PeerInfo.cfg";
        
    	Peer me = new Peer(peerID, logFileName, commonConfigFile, peerInfoConfigFile);
        me.Connect();
        me.run(); //work in progress
    }
	//TODO add tracking of peers so that we know when they all have the complete file and set allPeersHaveFile to true 2 spots to add check
	//TODO use BitField message currently unused and above will not work without using bitfield to keep peers file maps up to date
	// send after a peer handshake
}
