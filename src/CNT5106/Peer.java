package CNT5106;

import java.io.*;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Timer;

public class Peer{
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
    boolean havePieces[]; //track what pieces I have by index value
	Logger logger;
    Thread serverThread;
	Timer timer;
	UnchokeTimer optimisticTimer;
	UnchokeTimer regularTimer;
	ArrayList<Integer> preferredPeers; // will hold numPreferredPeers
	int optimisticPeer; // will hold random peer note this peer may also be a preferred peer after a regular time period
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
		Arrays.fill(havePieces, haveFile); //set the initial values of the pieces array based on whether we've got the entire file.
    }
	public void timerUp(boolean optimistic){
		Message unchoke = new Message(0, Message.MessageTypes.unchoke,"");
		Message choke = new Message(0, Message.MessageTypes.choke,"");
		Object[] keys = peerTCPConnections.keySet().toArray(); // get keys in map currently
		//FIX DON'T CHOKE/UNCHOKE PEERS ALREADY IN THAT STATE
		//FIX ERROR IN DUPLICATE TIME ADDS IF PEER IS IN PERFERED PEERS AND OPTIMISTIC PEER
		if (optimistic){
			int i = rand.nextInt(keys.length); // random index [0 - keys.length-1]
			peerTCPConnections.get(optimisticPeer).send(choke); // choke peer before setting new one
			optimisticPeer = (Integer)keys[i];
			peerTCPConnections.get(optimisticPeer).send(unchoke); // unchoke new peer
		}else{
			// set regular unchoke peers
			//numPreferredPeers
			//preferredPeers.add(0,);
			// choose based on download rate???? only regular timer computes download rate(key to accurate download rate computation)
			// peerId key map value holds the amount of messages received paired with the total # of time intervals run
			// each data message increments that peers # of messages
			// each regular timer up recalculates all peer download rates
			// top numPreferredPeers become new preferred peers
			// FIX MUST CHOOSE BASED ON DOWNLOAD RATE = (totalInMessages/totalTimeUnchoked) update time before calculation
			//FIX DON'T CHOKE/UNCHOKE PEERS ALREADY IN THAT STATE
			for(int i = 0; i < preferredPeers.size(); i++){ // remove current prefered peers
				PeerTCPConnection current = peerTCPConnections.get(preferredPeers.get(i));
				current.incrementTotalTimeUnChoked(5);
				current.send(choke);
			}
			preferredPeers = new ArrayList<Integer>(); // set up new array list for next set of prefered peers
			for(int i = 0; i < numPreferredPeers && i < keys.length; i++) {
				preferredPeers.add(0, (Integer)(keys[i])); // fill with peers with best download rate
				peerTCPConnections.get((Integer)keys[i]).send(unchoke); // unchoke since it is a prefered peer now
			}
			if(numPreferredPeers + 1 < keys.length ){ // if enough to have all peers be unique
				optimisticPeer = (Integer)keys[numPreferredPeers+1];
				peerTCPConnections.get((Integer)keys[numPreferredPeers+1]).send(unchoke); // unchoke since it is a prefered peer
			}else if(keys.length > 1){ // repeat first peer if there aren't enough for optimistic unchoke peer so not empty
				optimisticPeer = (Integer)keys[0];
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
		                ioException.printStackTrace();
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

		setAndRunTimer(true); // start timers
		setAndRunTimer(false);
		// init perferred peer array with peers we have connected to...
		Object[] keys = peerTCPConnections.keySet().toArray(); // get keys in map currently
		Message unchoke = new Message(0, Message.MessageTypes.unchoke,"");
		for(int i = 0; i < numPreferredPeers && i < keys.length; i++) {
			preferredPeers.add(0, (Integer)(keys[i])); // fill with first keys in map "Random selection"
			peerTCPConnections.get((Integer)keys[i]).send(unchoke); // unchoke since it is a prefered peer
		}
		if(numPreferredPeers + 1 < keys.length ){ // if enough to have all peers be unique
			optimisticPeer = (Integer)keys[numPreferredPeers+1];
			peerTCPConnections.get((Integer)keys[numPreferredPeers+1]).send(unchoke); // unchoke since it is a prefered peer
		}else if(keys.length > 1){ // repeat first peer if there aren't enough for optimistic unchoke peer so not empty
			optimisticPeer = (Integer)keys[0];
		}
    }
	private void processMessage(Message message){
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
	private void processChokeMessage(Message message){ // Work in progress -Nick

		logger.logChoking(message.peerID);

	}
	private void processUnchokeMessage(Message message){ // Work in progress -Nick

		logger.logUnchoking(message.peerID);

	}
	private void processInterestedMessage(Message message){
		logger.logRecvIntMessage(message.peerID);
		Boolean peerPieces[] = peerPieceMap.get(message.peerID); //retrieve the current mapping of what pieces we think peer has
		Boolean newPeerPieceMap[] = new Boolean[numPieces];
		for(int i=0; i<numPieces; i++) //figure out what pieces from me peer already has
		{
			newPeerPieceMap[i] = peerPieces[i] & havePieces[i];
		}
		for(int i=0; i<numPieces; i++) //figure out what pieces peer has that aren't from me
		{
			newPeerPieceMap[i] = newPeerPieceMap[i] | peerPieces[i];
		}
		peerPieceMap.put(message.peerID, newPeerPieceMap); //update the peer's piece mapping
	}
	private void processNotInterestedMessage(Message message){
		logger.logRecvNotIntMessage(message.peerID);
		Boolean peerPieces[] = peerPieceMap.get(message.peerID); //retrieve the current mapping of what pieces we think peer has
		Boolean newPeerPieceMap[] = new Boolean[numPieces];
		for(int i=0; i<numPieces; i++) //peer has all of my pieces, so OR what I think it has with what I have.
		{
			newPeerPieceMap[i] = peerPieces[i] | havePieces[i];
		}
		peerPieceMap.put(message.peerID, newPeerPieceMap);  //update the peer's piece mapping
	}
	private void processHaveMessage(Message message){
		logger.logRecvHaveMessage(message.peerID,Integer.parseInt(message.payload)); // probably have to fix payload always int?
		int pieceIndex = Integer.parseInt(message.payload);
		Boolean peerPieces[] = peerPieceMap.get(message.peerID);
		Boolean newPeerPieceMap[] = Arrays.copyOf(peerPieces, numPieces); //create copy of the peer's piece map so we don't modify existing one
		newPeerPieceMap[pieceIndex] = true; //set the piece the peer says it has to true
		peerPieceMap.put(message.peerID, newPeerPieceMap);
	}
	private void processBitfieldMessage(Message message){
		//logger.lo no logger method for bitfield
		boolean peerBitfield[] = new boolean[message.payload.length()];
		for(int i =  0; i< message.payload.length();i++)
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
	}
	private void processRequestMessage(Message message){
		//Payload consists of 4 byte piece index filed
		int reqPiece = Integer.parseInt(message.payload);
		//Check if peer is choked or unchoked
		
		//If valid peer send them piece
		
		
	}
	private void processPieceMessage(Message message){
		//Retrieve 4 byte piece index value
		int recvPiece = Integer.parseInt(message.payload);
		//Update Current peers bitfield to have that piece
		this.havePieces[recvPiece] = true;
		//Log download completetion of this piece
		logger.logDownloadingPiece(message.peerID, -1,-1); // fix to parse message payload into required fields
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
			if(haveFile){
				logger.logDownloadCompletion();
				break; // exit job complete? ro do i keep running to help other peers
			}
        }
    }
    public static void main(String args[])
    {
    	final int peerID = Integer.parseInt(args[0]);  //peerID is specified at time of execution, pull it from args
        final String logFileName = "log_peer_" + args[0] + ".log";
        final String commonConfigFile = "Common.cfg";
        final String peerInfoConfigFile = "PeerInfo.cfg";
        
    	Peer me = new Peer(peerID, logFileName, commonConfigFile, peerInfoConfigFile);
        me.Connect();
        //me.run(); work in progress

		// message class testing
        Message myMessage = new Message(5, Message.MessageTypes.unchoke,"Hello");
        byte[] temp = myMessage.toBytes();
        System.out.println(Arrays.toString(temp));
        System.out.println(Arrays.toString((myMessage = new Message(temp,false,100)).toBytes()));
        System.out.println(myMessage.toString());
        System.out.println(Arrays.toString(temp = new Message(-10000).toBytes()));
        System.out.println(Arrays.toString((myMessage = new Message(temp,true,101)).toBytes()));
        System.out.println(myMessage.toString());
        
        //Logger testing
        Logger myLog = new Logger("myLog2.txt", 7);
		int[] prefNeighbors = new int[] {3,7,9,1};
		myLog.logTCPConnection(1003);
		myLog.logChangePrefNeighbors(prefNeighbors);
		myLog.logChangeOptUnchokedNeighbor(14);
		myLog.logUnchoking(15);
		myLog.logChoking(16);
		myLog.logRecvHaveMessage(17, 1200);
		myLog.logRecvIntMessage(18);
		myLog.logRecvNotIntMessage(19);
		myLog.logDownloadingPiece(20, 1507, 1659);
		myLog.logDownloadCompletion();
    }
}
