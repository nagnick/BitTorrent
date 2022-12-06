package CNT5106;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
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

public class Peer implements Runnable{
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
	int serverListenPort;
    int numPreferredPeers; //number of preferred peers this peer should have
    int unchokingInterval; // in seconds
    int optimisticUnchokingInterval;
    String desiredFileName; //the file we are trying to get
    int desiredFileSize; //the size of the file we want
    int pieceSize; //the size of the pieces of the file we want
    int numPieces; //the total number of pieces of the file, used as part of bitfield logic.
	int numPiecesIHave = 0; // used to track download progress
    boolean haveFile; //indicate if I have entire file or not
	boolean allPeersHaveFile = false; // used to decide when to exit. if all peers have it and so do I then exit.
	boolean terminate = false; // controls all the threads.
    boolean[] havePieces; //track what pieces I have by index value
	boolean[] requestedPieces; // track pieces I have requested from peers
	boolean startedWithFile = false; // used to decide when to output file
	byte [] file; // actual file loaded in on init if peer has the file.
	Logger logger;
    Thread serverThread;
	ServerSocket listener;
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
    	Pattern prefNeighborsRegex = Pattern.compile("^(NumberOfPreferredNeighbors)\\s(\\d{1,})", Pattern.CASE_INSENSITIVE); //regex pattern for number of preferred neighbors config directive
    	Pattern unchokingIntervalRegex = Pattern.compile("^(UnchokingInterval)\\s(\\d{1,})", Pattern.CASE_INSENSITIVE); //regex pattern for unchoking interval config directive
    	Pattern optUnchokingIntervalRegex = Pattern.compile("^(OptimisticUnchokingInterval)\\s(\\d{1,})", Pattern.CASE_INSENSITIVE); //regex pattern for optimistic unchoking interval config directive
    	Pattern fileNameRegex = Pattern.compile("^(FileName)\\s(.{1,})", Pattern.CASE_INSENSITIVE); //regex pattern for file name config directive
    	Pattern	fileSizeRegex = Pattern.compile("^(FileSize)\\s(\\d{1,})", Pattern.CASE_INSENSITIVE); //regex pattern for file size config directive
    	Pattern pieceSizeRegex = Pattern.compile("^(PieceSize)\\s(\\d{1,})", Pattern.CASE_INSENSITIVE); //regex pattern for piece size config directive
		//create dedicated matcher objects since java keeps state internally with matcher objects
		Matcher prefNeighborsMatcher = prefNeighborsRegex.matcher("");
		Matcher unchokingIntervalMatcher = unchokingIntervalRegex.matcher("");
		Matcher optUnchokingIntervalMatcher = optUnchokingIntervalRegex.matcher("");
		Matcher fileNameMatcher = fileNameRegex.matcher("");
		Matcher fileSizeMatcher = fileSizeRegex.matcher("");
		Matcher pieceSizeMatcher = pieceSizeRegex.matcher("");
    	try {
			Scanner configFile = new Scanner(new File(commonConfigFileName));
			while (configFile.hasNextLine()) //keep looping while we have more lines to read
			{
				String configLine = configFile.nextLine(); //pull in the config line
				prefNeighborsMatcher.reset(configLine); //explicitly reset each matcher's state so it parses each line fresh
				unchokingIntervalMatcher.reset(configLine);
				optUnchokingIntervalMatcher.reset(configLine);
				fileNameMatcher.reset(configLine);
				fileSizeMatcher.reset(configLine);
				pieceSizeMatcher.reset(configLine);
				if (prefNeighborsMatcher.find()) //config line is for number of preferred neighbors
				{
					this.numPreferredPeers = Integer.parseInt(prefNeighborsMatcher.group(2)); //extract the value for config directive from regex, cast to int, and store it.
				} else if (unchokingIntervalMatcher.find()) {
					this.unchokingInterval = Integer.parseInt(unchokingIntervalMatcher.group(2)); //extract the value for config directive from regex, cast to int, and store it.
				} else if (optUnchokingIntervalMatcher.find()) {
					this.optimisticUnchokingInterval = Integer.parseInt(optUnchokingIntervalMatcher.group(2)); //extract the value for config directive from regex, cast to int, and store it.
				} else if (fileNameMatcher.find()) {
					this.desiredFileName = fileNameMatcher.group(2);
				} else if (fileSizeMatcher.find()) {
					this.desiredFileSize = Integer.parseInt(fileSizeMatcher.group(2));
				} else if (pieceSizeMatcher.find()) {
					this.pieceSize = Integer.parseInt(pieceSizeMatcher.group(2));
				}
			}
			configFile.close(); //we're done with the common config file, close it out.
			//Log the common config info to show it is read correctly
			logger.logCommonConfig(this.numPreferredPeers, this.unchokingInterval, this.optimisticUnchokingInterval, this.desiredFileName, this.desiredFileSize, this.pieceSize);
			numPieces = (int) (Math.ceil((double) (desiredFileSize) / (double) (pieceSize)));
			this.havePieces = new boolean[numPieces]; //init the pieces array to track what pieces we have
			this.requestedPieces = new boolean[numPieces];
		}
		catch(Exception e){
			System.err.println(e.getMessage());
		}
    }
	public void timerUp(boolean optimistic){
		Message unchoke = new Message(0, Message.MessageTypes.unchoke,null);
		Message choke = new Message(0, Message.MessageTypes.choke,null);
		Object[] keys = peerTCPConnections.keySet().toArray(); // get keys in map currently
		if(keys.length != 0) { // no peers yet
			if (optimistic) { // DONE
				if (optimisticPeer != -1) { // if an optimistic peer has been set do calculations for download rate (Assumes -1 not valid peerID aka not set)
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
				while (!peerTCPConnections.get((Integer) keys[i]).choked) { // keep looking until find a peer that is choked
					i = rand.nextInt(keys.length); // random index [0 - keys.length-1]
				}
				optimisticPeer = (Integer) keys[i]; // set new optimistic peer
				logger.logChangeOptUnchokedNeighbor(optimisticPeer);
				peerTCPConnections.get(optimisticPeer).send(unchoke); // unchoke new peer
				peerTCPConnections.get(optimisticPeer).choked = false; // used for tracking
			} else {
				// set regular unchoke peers
				//NOTE: each data message increments that peers # of messages this is done in the PeerTCPConnection thread
				if (preferredPeers.isEmpty()) { // has not been intialized so do it now // DONE
					preferredPeers = new ArrayList<Integer>(); // set up new array list for next set of prefered peers
					for (int i = 0; i < numPreferredPeers && i < keys.length; i++) {
						preferredPeers.add(0, (Integer) (keys[i])); // fill with peers
						peerTCPConnections.get((Integer) keys[i]).send(unchoke); // unchoke since it is a prefered peer now
					}
					logger.logChangePrefNeighbors(preferredPeers);
				} else if (!haveFile) { // has run before so select preferred peers based on download rate because I don't have file //Done
					// top numPreferredPeers become new preferred peers
					for (int i = 0; i < preferredPeers.size(); i++) { // remove current prefered peers
						PeerTCPConnection current = peerTCPConnections.get(preferredPeers.get(i));
						if (preferredPeers.get(i) != optimisticPeer) { // don't add to optimisticPeers runtime let optimistic timer do it(don't double count)
							current.totalPreferredPeriods += 1;
							current.send(choke); // don't choke optimistic peer
							current.choked = true;
						}
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
						PeerTCPConnection best = bestPeers.poll();
						preferredPeers.add(0, best.peerID); // fill with peers with best download rate
						best.send(unchoke); // unchoke since it is a prefered peer now
						best.choked = false;
					}
					logger.logChangePrefNeighbors(preferredPeers);
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
					logger.logChangePrefNeighbors(preferredPeers);
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
	private Message makeMyBitFieldMessage(){ // done
		byte[] payload = new byte[(int)Math.ceil(((double)numPieces)/8)];
		int toSend = 0;
		for(int i = 0; i < havePieces.length; i++){
			if(i != 0 && i%8 == 0){
				payload[(i-1)/8] = (byte)toSend;
				toSend =0;
			}
			toSend = toSend << 1;
			if(havePieces[i]) {
				toSend = toSend | 1;
			}
		}
		// add final zeros is numofPieces is not perfectly divisible by byte
		if(havePieces.length%8 != 0) {
			for(int i = 0; i < 8 - (havePieces.length%8); i++){ // shift over last few places to leave trailing zeros...
				toSend = toSend << 1;
			}
			payload[payload.length-1] = (byte)toSend;
		}
		return new Message(payload.length,MessageTypes.bitfield,payload);
	}
    
    public void Connect(){ // parse manifest file and connect to peers
		try {
			Scanner peerInfoFile = new Scanner(new File(peerInfoFileName));
			Pattern peerInfoRegex = Pattern.compile("^(\\d{1,})\\s([a-zA-Z\\d-\\.]{1,})\\s(\\d{1,})\\s(0|1)", Pattern.CASE_INSENSITIVE);
			// connect to other peers with help from manifest file
			// read file connect to those peers probably need to try multiple times as other peers may not be up yet
			int currentLineNumber = 0; //keep track of what line number we're on.as it determines what we should do when we hit our own entry
			boolean isFirstPeer = false; //are we the first peer listed in the file?
			serverListenPort = 0; //what port we should be listening on
			Matcher peerInfoMatcher = peerInfoRegex.matcher(""); //init peer info matcher with blank string so we can reset it for each line
			while (peerInfoFile.hasNextLine()) { // start connecting to peers change while peer list not empty from manifest file
				String peerInfoLine = peerInfoFile.nextLine(); //pull the current line into a string
				peerInfoMatcher.reset(peerInfoLine); //match the line against the peer info regex so we can extract the attributes from subgroups.
				if (peerInfoMatcher.find()) //only continue if the line is in expected format, otherwise silently ignore the line
				{
					int currentPeerID = Integer.parseInt(peerInfoMatcher.group(1));
					String peerHostName = peerInfoMatcher.group(2);
					int peerListenPort = Integer.parseInt(peerInfoMatcher.group(3));
					boolean peerHasFile = (Objects.equals(peerInfoMatcher.group(4), "1"));
					logger.logPeerConfig(currentPeerID, peerHostName, peerListenPort, peerHasFile);
					if (currentPeerID == myID) {
						serverListenPort = peerListenPort;
						haveFile = peerHasFile;
						startedWithFile = haveFile;
						Arrays.fill(havePieces, haveFile); //set the initial values of the pieces array based on whether we've got the entire file.
						Arrays.fill(requestedPieces, haveFile); // don't request pieces I have so add to requested list
						if (haveFile) { // if I have the file read it into memory
							numPiecesIHave = numPieces;
							try {
								desiredFileName = myID +"/"+ desiredFileName;
								desiredFileName = System.getProperty("user.dir") + "/"+ desiredFileName;
								Path path = Paths.get(desiredFileName);
								file = Files.readAllBytes(path); // bring file into memory
							} catch (Exception e) {
								System.err.println("Error reading file to distribute");
							}
						} else {
							file = new byte[desiredFileSize]; // init space to save the file.
						}
						if (currentLineNumber == 0) //we're the first peer
						{
							isFirstPeer = true;
						}
					} else {
						// spin up several threads for each peer that connects
						PeerTCPConnection peerConnection = null;
						try {
							if (!isFirstPeer) //only try to connect when we're not the first peer
							{
								Socket peerSocket = new Socket(InetAddress.getByName(peerHostName), peerListenPort); // connect to a peer
								peerConnection = new PeerTCPConnection(inbox, peerSocket); // new connection
								peerConnection.send(new Message(myID));// send handshake always first step
								Message peerHandshake = peerConnection.getHandShake(); // receive response handshake always second step
								peerConnection.setPeerId(peerHandshake.peerID); // set peerID for tracking of message origin in message queue
								// important later when messages are mixed in queue to track their origin
								if (peerTCPConnections.get(peerHandshake.peerID) == null) { // if not in map put in
									peerConnection.start(); // start that peers reading thread
									//peerConnection.send(makeMyBitFieldMessage()); // sends out bit field of pieces I have upon connection
									peerTCPConnections.put(peerHandshake.peerID, peerConnection);
								}
								else{
									peerConnection.close();
								}
							}
							peerFileMap.put(currentPeerID, peerHasFile); //still build the map of which peers have what files.
						} catch (Exception e) {
							System.err.println("Connection refused." + e);
							if(peerConnection != null)
								peerConnection.close();
						}
					}
				}
				currentLineNumber++;
			}
			peerTCPConnections.forEach((peerID, peerConnection) -> {
				peerConnection.send(makeMyBitFieldMessage());
			});
// use this lambda style if you need to spin up a random thread at any point just dont capture it
			setAndRunTimer(true); // start timers will set up preferred peer array and optimistic peer once they run
			setAndRunTimer(false); // this gives time for peers to connect before initializing unchoked peers
		}
		catch(Exception e){
			System.err.println(e.getMessage());
		}
    }
	private void processMessage(Message message){ // done
		// process each message depending on their type
		switch (message.type.ordinal()){
			case(0): {
				processChokeMessage(message);
				break;
			}
			case(1): {
				processUnchokeMessage(message);
				break;
			}
			case(2): {
				processInterestedMessage(message);
				break;
			}
			case(3): {
				processNotInterestedMessage(message);
				break;
			}
			case(4):{
				processHaveMessage(message);
				break;
			}
			case(5): {
				processBitfieldMessage(message);
				break;
			}
			case(6):{
				processRequestMessage(message);
				break;
			}
			case(7): {
				processPieceMessage(message);
				break;
			}
			default:{
				throw new RuntimeException("Unexpected message type in processMessage\n");
			}
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
		// peerPeice map has peices that that peers has
		//peer id and boolean[] index is for that piece id and true if that peer has that file so if i don't have it so request it...
		if(!haveFile) { // if i don't have file make a request else do nothing
			Boolean[] peersPieces = peerPieceMap.get(message.peerID);
			for (int i = 0; i < peersPieces.length && i < requestedPieces.length; i++) { // add check that it has not been requested??
				if (peersPieces[i] && !requestedPieces[i]) { // request a piece that they have and I have not requested already
					ByteBuffer mybuff = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
					mybuff.putInt(i);
					peerTCPConnections.get(message.peerID).send(new Message(4, MessageTypes.request, mybuff.array()));
					break;
				}
			}
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
	private void processHaveMessage(Message message){ //done
		ByteBuffer buff = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
		int pieceIndex = buff.put(message.payload).getInt(0);
		logger.logRecvHaveMessage(message.peerID,pieceIndex); // treat chars as bytes...
		Boolean[] peerPieces = peerPieceMap.get(message.peerID);
		Boolean[] newPeerPieceMap = Arrays.copyOf(peerPieces, numPieces); //create copy of the peer's piece map so we don't modify existing one
		newPeerPieceMap[pieceIndex] = true; //set the piece the peer says it has to true
		boolean peerHaveFile = true;
		for(int k = 0;k < newPeerPieceMap.length;k++)//Check to see if peer has complete file
		{
			if(!newPeerPieceMap[k])
			{
				peerHaveFile = false; //variable to check if peer has whole file
				break; // save some time
			}
		}
		peerTCPConnections.get(message.peerID).haveFile = peerHaveFile;
		peerPieceMap.put(message.peerID, newPeerPieceMap);

		Boolean[] peerAndMissingPieces = new Boolean[numPieces]; //the pieces I'm missing ANDed with the pieces the peer has
		for(int i=0; i<numPieces; i++)
		{
			peerAndMissingPieces[i] = !havePieces[i] & newPeerPieceMap[i]; //invert what I have to mark if missing, AND it with what peer has to check if it has it
		}

		if(Arrays.stream(peerAndMissingPieces).anyMatch(value -> value == Boolean.TRUE)) //the peer has a piece that I am missing
		{
			Message interestedNotification = new Message(0, MessageTypes.interested, null);
			peerTCPConnections.get(message.peerID).send(interestedNotification);
		}

		else //the peer has nothing i need
		{
			Message notInterestedNotification = new Message(0, MessageTypes.notInterested, null);
			peerTCPConnections.get(message.peerID).send(notInterestedNotification);
		}
		allPeersHaveFile = true;
		peerTCPConnections.forEach((peerID, peerConnection) -> {
			if(!peerConnection.haveFile){
				allPeersHaveFile = false;
			}
		});
	}
	private void processBitfieldMessage(Message message){ // done // fix goes beyond bounds pieceIndex exceeeds array length
		Boolean[] currentPeerPieceMapping = new Boolean[numPieces];
		PeerTCPConnection currentPeer = peerTCPConnections.get(message.peerID); //pull the current peer from map
		currentPeer.haveFile = true; // set up to check if peer has file
		int segmentIndex = 0; //segment index value used to map the bits in each char to their piece index val
		for(byte msgByte : message.payload) //iterate over each byte of the payload
		{
			String bitString = String.format("%8s", Integer.toBinaryString(msgByte & 0xFF)).replace(' ', '0');
			for(int i=0; i< bitString.length(); i++) //iterate over the entire char's binary string value
			{
				int pieceIndex = i + 8*segmentIndex; //calculate current piece index based on what what bit we are on and what byte we're looking at
				if(pieceIndex >= numPieces) //if we overshoot how many pieces we have, end the loop
				{
					break;
				}
				if(bitString.charAt(i) == '1')
				{
					currentPeerPieceMapping[pieceIndex] = Boolean.TRUE;
				}
				else
				{
					currentPeerPieceMapping[pieceIndex] = Boolean.FALSE;
					currentPeer.haveFile = false; // missing a piece does not have file
				}
			}
			segmentIndex++; //increment the segment index to get ready for the next char
		}
		peerPieceMap.put(message.peerID, currentPeerPieceMapping); //update the peerPieceMap value for the current peer
		allPeersHaveFile = true;
		peerTCPConnections.forEach((peerID, peerConnection) -> {
			if(!peerConnection.haveFile){
				allPeersHaveFile = false;
			}
		});
	}
	private void processRequestMessage(Message message){ // done
		//Payload consists of 4 byte piece index filed and
		ByteBuffer buff =  ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
		byte[] received = message.payload;
		//Retrieve 4 byte piece index value
		int reqPiece = buff.put(received).getInt(0); // the piece requested
		//Update Current peers bitfield to have that piece
		//Check if peer is choked or unchoked
		PeerTCPConnection requestee = peerTCPConnections.get(message.peerID);
		if(!requestee.choked){ //If peer is not choked send them piece
			int startingIndex = reqPiece*pieceSize;
			//Include piece index in beignning of message payload
			int filePieceSize = pieceSize;
			if(reqPiece >= (desiredFileSize/pieceSize)){
				filePieceSize = desiredFileSize%pieceSize;
			}
			ByteBuffer mybuff = ByteBuffer.allocate(filePieceSize + 4).order(ByteOrder.BIG_ENDIAN); // add 4 bytes for piece index
			mybuff.putInt(reqPiece);
			//piece of file is from reqpiece*pieceSize to (reqpiece * pieceSize) + pieceSize. not inclusive
			mybuff.put(file,startingIndex,filePieceSize);
			requestee.send(new Message(mybuff.array().length, MessageTypes.piece, mybuff.array()));
		}
	}
	private void processPieceMessage(Message message){ // done
		ByteBuffer buff = ByteBuffer.allocate(message.length).order(ByteOrder.BIG_ENDIAN).put(message.payload);
		//Retrieve 4 byte piece index value
		int recvPiece = buff.getInt(0); // the piece i get is the piece i requested
		//Update Current peers bitfield to have that piece
		if(!havePieces[recvPiece]){ // if not a duplicate piece increment my download count
			numPiecesIHave++;
			logger.logDownloadingPiece(message.peerID, recvPiece,numPiecesIHave); // log new piece added
		}
		this.havePieces[recvPiece] = true;
		requestedPieces[recvPiece] = true; // just got it so update to not request anymore
		//Log download completetion of this piece
		int startingIndex = recvPiece*pieceSize;
		buff.position(4);
		buff.get(file,startingIndex, message.length-4);
		//Check havePieces to see if completed file
		for(int i = 0; i < this.havePieces.length;i++ )
		{
			if(this.havePieces[i])
			{
				this.haveFile = true;
			}else
			{
				this.haveFile = false;
				break;
			}
		}
		//send out new have messages to all the peers we're connected to
		Message haveNotification = new Message(4, MessageTypes.have, Arrays.copyOfRange(message.payload,0,4));
		peerTCPConnections.forEach((peerID, peerConnection) -> {
			peerConnection.send(haveNotification); // update peers on what I have
		});
		PeerTCPConnection sender = peerTCPConnections.get(message.peerID);
		if(!sender.iamChoked){ // if sender has not choked me request another piece keep ping pong going
			// do not send if it has choked me because it won't send and will break the accuracy of the requestedPieces array
			ByteBuffer mybuff = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
			Boolean[] peersPieces = peerPieceMap.get(message.peerID);
			for (int i = 0; i < peersPieces.length && i < requestedPieces.length; i++){ // add check that it has not been requested??
				if(peersPieces[i] && !requestedPieces[i]){ // request a piece that I don't have yet and I have not requested already
					mybuff.putInt(i);
					//requestedPieces[i] = true; // just requested it so update
					sender.send(new Message(4,MessageTypes.request,mybuff.array()));
					break;
				}
			}
		}
	}
	public void run(){ // server thread will run this
		// listen for other peers wishing to connect with me on seperate thread
			try { // fix this and connection phase to avoid duplicate connections
				final int serverPort = serverListenPort;
				listener = new ServerSocket(serverPort); // passive listener on own thread
				while (!Thread.currentThread().isInterrupted()) { // need to add map duplicate insert checks as some peers may try to connect after we have already connected
					Socket peerSocket = listener.accept(); // this blocks waiting for new connections so must be on own thread
					PeerTCPConnection peerConnection = new PeerTCPConnection(inbox, peerSocket); // new connection
					peerConnection.send(new Message(myID));// send handshake always first step
					Message peerHandshake = peerConnection.getHandShake(); // receive response handshake always second step

					peerConnection.setPeerId(peerHandshake.peerID); // set peerID for tracking of message origin in message queue
					// important later when messages are mixed in queue to track their origin
					if (peerTCPConnections.get(peerHandshake.peerID) == null) { // if not in map put in
						peerConnection.start(); // start that peers reading thread
						peerConnection.send(makeMyBitFieldMessage()); // sends out bit field of pieces I have upon connection
						peerTCPConnections.put(peerHandshake.peerID, peerConnection);
						logger.logTCPConnection(peerHandshake.peerID); // new connection log it
					} else { // if in map don't need two connections to peer so close it
						peerConnection.close();
					}
				}
			} catch (Exception e) {
				System.err.println("Error running server sockets" + e);
			}
	}
    public void get(){ // file retrieval and peer file distribution done here
        // start main process of asking peers for bytes of file
		serverThread = new Thread(this);
		serverThread.start(); // runs this run method which is the socket server
        while(true){ // add && file is incomplete
            //process messages and respond appropriately
			if(!inbox.isEmpty()) {
				processMessage(inbox.peek());
				inbox.remove();
			}
			if(haveFile && !startedWithFile){
				logger.logDownloadCompletion();
				try {
					desiredFileName = myID +"/"+ desiredFileName;
					desiredFileName = System.getProperty("user.dir") + "/"+ desiredFileName;
					Path path = Paths.get(desiredFileName); // broken need to fix
					Files.write(path,file);
				}
				catch (Exception e){
					System.err.println("Error writing file out." + e);
				}
				startedWithFile = true;
			}
			if(haveFile && allPeersHaveFile){ // fix allPeersHaveFile is never changed to true when all peers have the file.
				//kill everything
				regularTimer.cancel();
				optimisticTimer.cancel();
				timer.cancel();
				timer.purge();
				terminate = true;
				serverThread.interrupt();
				try {
					listener.close();
					serverThread.join();
				}catch(Exception e){
					System.err.println("ServerThread error" + e);
				}
				peerTCPConnections.forEach((peerID, peerConnection) -> {
					peerConnection.close();
				});
				break; // exit job complete
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
        me.get();
    }
}
