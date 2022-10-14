package CNT5106;
import CNT5106.Message;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class Peer {
    Integer myID;
    Thread serverThread;
    LinkedBlockingQueue<Message> inbox = new LinkedBlockingQueue<Message>(); // all recev tcp threads write to here
    ConcurrentHashMap<Integer,TCPIn> peerInConnections = new ConcurrentHashMap<Integer, TCPIn>(); // have these peers add messages to a thread safe queue
    ConcurrentHashMap<Integer,TCPOut> peerOutConnections = new ConcurrentHashMap<Integer, TCPOut>(); // peer connections to send messages
    public Peer(){
        // init stuff on creation
        myID = -1;
    }
    public void Connect(){ // finish this once server is build
        // connect to other peers with help from manifest file
        // read file connect to those peers probably need to try multiple times as other peers may not be up yet
        int i = 0;
        while(i++ < 5) { // start connecting to peers change while peer list not empty from manifest file
            // spin up several threads for each peer that connects
            try {
                Socket peerSocket = new Socket("localhost", 8000); // connect to a peer switch to peer ip as host
                TCPOut peerOut = new TCPOut(peerSocket); // add to list
                TCPIn peerIn = new TCPIn(inbox,peerSocket); // add to list
                peerOut.send(new Message(myID));// send handshake
                Message peerHandshake = peerIn.getHandShake();
                peerIn.start(); // start that peers thread
                peerInConnections.put(peerHandshake.peerID,peerIn);
                peerOutConnections.put(peerHandshake.peerID,peerOut);
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
// use this lambda style if you need to spin up a random thread at any point just dont capture it
        serverThread = new Thread(() -> { // listen for other peers wishing to connect with me on seperate thread
            try {
                ServerSocket listener = new ServerSocket(8000); // passive listener on own thread
                while(true) {
                    Socket peerSocket = listener.accept(); // this blocks waiting for new connections
                    TCPOut peerOut = new TCPOut(peerSocket); // add to list
                    TCPIn peerIn = new TCPIn(inbox, peerSocket); // add to list
                    peerOut.send(new Message(myID));// send handshake
                    Message peerHandshake = peerIn.getHandShake();
                    peerIn.start(); // start that peers thread
                    peerInConnections.put(peerHandshake.peerID, peerIn);
                    peerOutConnections.put(peerHandshake.peerID, peerOut);
                }
            }
            catch (Exception e){
                System.out.println("Error running server sockets");
            }
        });
        serverThread.start();

    }
    public boolean getFile(){
        // start main process of asking peers for bytes of file
        while(!inbox.isEmpty()){ // add && file is incomplete
            //process messages and respond appropriately
            System.out.println(inbox.peek().type.toString());
            inbox.remove();
        }
        return false; // failed to get all bytes of file from network
    }
    public static void main(String args[])
    {
        Peer me = new Peer();
        //me.Connect();
        //me.getFile(); work in progress
        Message myMessage = new Message(5, Message.MessageTypes.unchoke,"Hello");
        byte[] temp = myMessage.toBytes();
        System.out.println(Arrays.toString(temp));
        System.out.println(Arrays.toString((myMessage = new Message(temp,false)).toBytes()));
        System.out.println(myMessage.toString());
        System.out.println(Arrays.toString(temp = new Message(128).toBytes()));
        System.out.println(Arrays.toString((myMessage = new Message(temp,true)).toBytes()));
        System.out.println(myMessage.toString());
    }
}
