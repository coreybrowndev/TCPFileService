package tcp;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileServiceServer{
    public static void main(String[] args) throws Exception{


        //Process all channel request from the client
        ServerSocketChannel welcomeChannel = ServerSocketChannel.open();



        welcomeChannel.socket().bind(new InetSocketAddress(3000));
        //Blocking call -> Sitting waiting to accept connection request from client

        while(true) {
            //NOTE: Anytime there is a coonection request the 1st socket will create a new socket,
            //that will process the request, the first socket will sit waititng for the next request
            SocketChannel serveChannel = welcomeChannel.accept();
            //Empty container that will store bytes from the client
            ByteBuffer request = ByteBuffer.allocate(1024);
            int numBytes = 0;
            do {

                //read() will return -1 when the special signal is received.
                //The special signal is triggered by the shutdownOutput on the client side
                numBytes = serveChannel.read(request);
            }while(numBytes >= 0);

            request.flip();
            char command = (char) request.get();
            //Message from the client, displaying it on the server side
            byte[] a;
            String fileName;
            File file;
            boolean success = false;
            switch (command) {
                case 'G':
                    //File to download
                    a = new byte[request.remaining()];
                    request.get(a);
                    fileName = new String(a);
                    file = new File("ServerFiles/"+fileName);
                    if(file.exists() && file.isFile()) {
                        byte[] fileContent = Files.readAllBytes(file.toPath());
                        ByteBuffer fileBuffer = ByteBuffer.wrap(fileContent);
                        ByteBuffer code = ByteBuffer.wrap("Process succeeded".getBytes());
                        serveChannel.write(code);
                        serveChannel.write(fileBuffer);
                    }else {
                        ByteBuffer code = ByteBuffer.wrap("Process Failed".getBytes());
                        serveChannel.write(code);
                    }
                    break;
                case 'U':
                    a = new byte[request.remaining()];
                    request.get(a);
                    fileName = new String(a);
                    file = new File("ServerFiles/"+fileName);
                    boolean created = file.createNewFile();
                    if(created) {
                        file.createNewFile();
                        ByteBuffer code = ByteBuffer.wrap("S".getBytes());
                        serveChannel.write(code);
                    }else {
                        ByteBuffer code = ByteBuffer.wrap("F".getBytes());
                        serveChannel.write(code);
                    }
                    break;
                case 'D':
                    //Excludes the bytes that we got from the command
                     a = new byte[request.remaining()];
                    //Read file information
                    request.get(a);
                    fileName = new String(a);
                    file = new File("ServerFiles/"+fileName);

                    if(file.exists()) {
                         success = true;
                        file.delete();
                    }
                    if(success) {
                        ByteBuffer code = ByteBuffer.wrap("S".getBytes());
                        serveChannel.write(code);
                    }else {
                        ByteBuffer code = ByteBuffer.wrap("F".getBytes());
                        serveChannel.write(code);
                    }
                    break;
                case 'L':
                    break;
                case 'R':
                    break;
                default:
                    System.out.println("Bye Bye!!");
                    break;
            }

            //Send reply back to client
            //Rewinds the position without touching the limit
            request.rewind();
            //Read from the buffer and write into the TCP channel
            serveChannel.write(request);
            serveChannel.close();

            //

        }

    }
}
