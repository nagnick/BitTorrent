package CNT5106;

public class TCPIn extends Thread{ // spining thread waiting for peer messages
    public TCPIn(){ // pass in peer info to form tcp connection

    }
    public void run(){
        try {
            // tcp network stuff
        }
        catch (Exception e) {
            System.out.println("Error running tcpIn thread");
        }
    }
}
