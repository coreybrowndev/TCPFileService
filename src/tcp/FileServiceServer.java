package tcp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

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
            }while(request.position() < request.capacity() && numBytes >= 0);

            request.flip();
            char command = (char) request.get();
            //Message from the client, displaying it on the server side
            byte[] a;
            String fileName;
            File file;
            boolean success = false;
            ByteBuffer fileBytes = null;
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
                        serveChannel.write(ByteBuffer.wrap(fileName.getBytes()));
                        File created = new File(fileName);
                        Files.write(created.toPath(), fileContent);

                    }else {
                        ByteBuffer code = ByteBuffer.wrap("Process Failed".getBytes());
                        serveChannel.write(code);
                    }
                    break;
                case 'U':
                    a = new byte[request.remaining()];

                    request.get(a);
                    String totalData = new String(a);

                    int separatorIndex = totalData.indexOf('%');

                    if(separatorIndex == -1) {
                        System.out.println("Invalid file format");
                        ByteBuffer code = ByteBuffer.wrap("Process Failed".getBytes());
                        serveChannel.write(code);
                        break;
                    }

                    fileName = totalData.substring(0, separatorIndex);
                    Path outputPath = Paths.get("ServerFiles", fileName);
                    FileOutputStream fos = new FileOutputStream(outputPath.toFile(), true);  // append mode

                    byte[] remainingBytes = totalData.substring(separatorIndex + 1).getBytes();
                    fos.write(remainingBytes);

                    ByteBuffer fileChunkBuffer = ByteBuffer.allocate(1000);
                    int bytesRead;

                    while ((bytesRead = serveChannel.read(fileChunkBuffer)) > 0) {
                        fos.write(fileChunkBuffer.array(), 0, bytesRead);
                        fileChunkBuffer.clear();
                    }
                    fos.close();

                    ByteBuffer sCode = ByteBuffer.wrap("S".getBytes());
                    serveChannel.write(sCode);
                    serveChannel.write(ByteBuffer.wrap(fileName.getBytes()));
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
                        serveChannel.write(ByteBuffer.wrap(fileName.getBytes()));
                    }else {
                        ByteBuffer code = ByteBuffer.wrap("F".getBytes());
                        serveChannel.write(code);
                    }
                    break;
                case 'L':
                    final File folder = new File("ServerFiles");
                    ArrayList<String> filesArr = new ArrayList<>();

                    for(File aFile : folder.listFiles()) {
                        filesArr.add(String.valueOf(aFile));
                    }

                    for(String someFile : filesArr) {
                        System.out.printf("File: %s\n", someFile);
                        fileBytes = ByteBuffer.wrap(someFile.getBytes());
                        serveChannel.write(fileBytes);
                    }

                    break;
                case 'R':
                    a = new byte[request.remaining()];
                    request.get(a);
                    String renameRequest = new String(a);
                    String[] renameParts = renameRequest.substring(1).split("\\|");
                    //After creating this substring, the code uses the split method to split it into an array of strings using the pipe character.
                    //(|) as the delimiter.

                    if (renameParts.length == 2) {
                        String oldFileName = renameParts[0];
                        String newFileName = renameParts[1];
                        File oldFile = new File("ServerFiles/" + oldFileName);
                        File newFile = new File("ServerFiles/" + newFileName);

                        if (oldFile.exists() && oldFile.isFile()) {
                            success = oldFile.renameTo(newFile);
                        }
                    }

                    if (success) {
                        ByteBuffer code = ByteBuffer.wrap("S".getBytes());
                        serveChannel.write(code);
                    } else {
                        ByteBuffer code = ByteBuffer.wrap("F".getBytes());
                        serveChannel.write(code);
                    }
                    break;
                default:
                    System.out.println("Bye Bye!!");
                    break;
            }

            //Send reply back to client
            //Rewinds the position without touching the limit
            request.rewind();
            //Read from the buffer and write into the TCP channel
//            serveChannel.write(request);
            serveChannel.close();

            //

        }

    }
}
