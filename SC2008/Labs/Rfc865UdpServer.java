import java.net.*;
import java.io.*;

public class Rfc865UdpServer {
    public static void main(String[] argv) {
        DatagramSocket socket = null;

        try {
            // 1. Open UDP socket at well-known port 17
            socket = new DatagramSocket(17); // Port 17 is the well-known port for QOTD
            System.out.println("Server is listening on port 17...");

            // 2. Listen for UDP request from client
            while (true) {
                byte[] receiveData = new byte[1024];  // buffer for incoming data
                DatagramPacket requestPacket = new DatagramPacket(receiveData, receiveData.length);

                // Receive the packet from the client (this will block until a packet is received)
                socket.receive(requestPacket);

                // For simplicity, we'll ignore the received data (following the RFC 865 design)
                System.out.println("Received request from " + requestPacket.getAddress() + ":" + requestPacket.getPort());

                // 3. Send UDP reply to client (Quote of the Day)
                String quote = "The early bird catches the worm.\n"; // You can customize this quote
                byte[] sendData = quote.getBytes();  // Convert quote string to byte array
                DatagramPacket replyPacket = new DatagramPacket(sendData, sendData.length, requestPacket.getAddress(), requestPacket.getPort());

                // Send the quote back to the client
                socket.send(replyPacket);
                System.out.println("Sent quote to " + requestPacket.getAddress() + ":" + requestPacket.getPort());

                // After handling the first request, send one last quote before closing the server
                String finalQuote = "A journey of a thousand miles begins with a single step.\n"; // Final quote before server shuts down
                byte[] finalQuoteData = finalQuote.getBytes();  // Convert final quote to byte array
                DatagramPacket finalReplyPacket = new DatagramPacket(finalQuoteData, finalQuoteData.length, requestPacket.getAddress(), requestPacket.getPort());

                // Send the final quote to the client
                socket.send(finalReplyPacket);
                System.out.println("Sent final quote to " + requestPacket.getAddress() + ":" + requestPacket.getPort());

                // Close the socket after sending the final quote and break out of the loop
                break;  // Exit after sending the final quote
            }

        } catch (SocketException e) {
            System.err.println("Error opening socket: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("IO Error: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("Socket closed.");
            }
        }
    }
}
