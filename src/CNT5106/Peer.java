package CNT5106;
import CNT5106.Message;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.ArrayList;

public class Peer {
    LinkedBlockingQueue<Message> inbox = new LinkedBlockingQueue<Message>(); // all recev tcp threads write to here
    ArrayList<TCPIn> peerInConnections = new ArrayList<TCPIn>(); // have these peers add messages to a thread safe queue
    ArrayList<TCPOut> peerOutConnections = new ArrayList<TCPOut>(); // peer connections to send messages
    public Peer(){
        // init stuff on creation
    }
    public boolean Connect(){
        // connect to other peers with help from main server
        try { // connect to server and find peers
            Socket serverSocket = new Socket("localhost", 8000);
            TCPOut serverOut = new TCPOut(serverSocket);
            TCPIn serverIn = new TCPIn(inbox, serverSocket);
            serverOut.send(new Message(1234)); // send my peer id??
            Message serverMessage = inbox.remove(); // get back a request message maybe??? with list of peerID's and IP's

        }
        catch (ConnectException e) {
                System.err.println("Connection refused. Server not found");
            }
            catch(UnknownHostException unknownHost){
                System.err.println("You are trying to connect to an unknown host!");
            }
            catch(IOException ioException){
                ioException.printStackTrace();
        }

        while(true) { // start connecting to peers change while peer list not empty
            // spin up several threads for each peer that connects
            try {
                Socket peerSocket = new Socket("localhost", 8000); // connect to a peer switch to peer ip as host
                TCPOut peerOut = new TCPOut(peerSocket); // add to list
                TCPIn peerIn = new TCPIn(inbox,peerSocket); // add to list
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
// use this lambda style if you need to spin up a random thread at any point
//        new Thread(() -> { // listen for other peers wishing to connect with me
        //ServerSocket listener = new ServerSocket(8000); // passive listener put this in a loop on own thread
        //Socket peerSocket = listener.accept();
        //TCPOut peerOut = new TCPOut(peerSocket); // add to list
        //TCPIn peerIn = new TCPIn(inbox,peerSocket); // add to list
//            }).start();

        //return false; // failed to connect to peer network
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
