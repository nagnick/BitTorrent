package CNT5106;

public class TCPOut{ // connections ready to send messages
    public TCPOut(){ // pass in peer info to connect to
        // initialize tcp connection to peer
    }
    public boolean Send(char[] message){
        System.out.println("Sending message: " + String.valueOf(message));
        return false;
    }
}
