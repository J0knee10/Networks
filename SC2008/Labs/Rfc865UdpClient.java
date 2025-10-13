import java.net.*;
import java.io.*;

public class Rfc865UdpClient {
    public static void main(String[] argv) {
        DatagramSocket socket = null;

        try {
            // 1. Open UDP socket
            socket = new DatagramSocket(); // This client socket will automatically use a random port
            InetAddress serverAddress = InetAddress.getByName("10.96.189.98"); // The server's IP address (can use "localhost" for testing)

            // 2. Send UDP request to server
            // Get the client's local IP address
            InetAddress clientAddress = InetAddress.getLocalHost();
            System.out.println("Client IP address: " + clientAddress.getHostAddress());
            String requestMessage = "JONATHAN LOW SENG KIAN, SCSJ, " + clientAddress.getHostAddress(); // Concatenate the IP address
            System.out.println("Request message: " + requestMessage);
            byte[] requestData = requestMessage.getBytes();  // Convert request message to byte array
            DatagramPacket requestPacket = new DatagramPacket(requestData, requestData.length, serverAddress, 17); // Port 17 (QOTD)

            socket.send(requestPacket);  // Send the request to the server
            System.out.println("Sent request to server: " + serverAddress + ":17");

            // 3. Receive UDP reply from server
            byte[] receiveData = new byte[1024];  // Buffer to hold the incoming response (quote)
            DatagramPacket replyPacket = new DatagramPacket(receiveData, receiveData.length);

            // Receive the reply from the server (this will block until the server sends data)
            socket.receive(replyPacket);

            // Convert the received byte array into a string (quote)
            String quote = new String(replyPacket.getData(), 0, replyPacket.getLength());
            System.out.println("Received quote: " + quote);

        } catch (SocketException e) {
            System.err.println("Error opening socket: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("IO Error: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();  // Always close the socket when done
                System.out.println("Socket closed.");
            }
        }
    }
}
