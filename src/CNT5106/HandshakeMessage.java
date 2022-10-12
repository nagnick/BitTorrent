package CNT5106;

public class HandshakeMessage {
    String header = "P2PFILESHARINGPROJ";
    String middle = "0000000000";
    int peerID;
    HandshakeMessage(int peerID){
        this.peerID = peerID;
    }
    char[] toBytes(){
        return (header+ middle + peerID).toCharArray(); // return char [] aka bytes
    }
}
