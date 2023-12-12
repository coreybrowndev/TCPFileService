package tcp;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;
import java.util.concurrent.*;

public class FileServiceClient {
    private static String fileName;
    private static ByteBuffer request;
    private static int bytesToRead = 0;
    private static ByteBuffer statusCode;
    private static byte[] a;
    private static Scanner keyboard = new Scanner(System.in);
    private static String command;

    private static SocketChannel channel;

    private static ExecutorService executor = Executors.newFixedThreadPool(2);



    public static void main(String[] args) throws Exception {
        if(args.length != 2) {
            System.out.println("Syntax: FileClient <ServerIP> <ServerPort>");
            return;
        }

        //Convert port into an Integer
        int serverPort = Integer.parseInt(args[1]);
//        String command;

        do {
//            Scanner keyboard = new Scanner(System.in);
            System.out.println("\nPlease type a command: ");
            command = keyboard.nextLine();
            //Sending request data to server
            ByteBuffer buffer = ByteBuffer.wrap(command.getBytes());
            channel = SocketChannel.open();
            channel.connect(new InetSocketAddress(args[0], serverPort));
            channel.write(buffer);


            //CREATE A NEW channel for every command. Close half the connection after we get the data from the user.

            switch(command) {
                case "G":
                    executor.submit(() -> {
                        try{
                            downloadFile();
                        }catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    break;
                case "U":
                    uploadProcess();
                    break;
                case "D":
                    System.out.println("Enter a file name to be deleted: ");
                    fileName = keyboard.nextLine();
                    request = ByteBuffer.wrap((fileName).getBytes());
                    channel.write(request);
                    //Tell server that it has received all data and it can process the request.
                    // So that server doesn't sit waiting
                    channel.shutdownOutput();
                    bytesToRead = 1;
                    statusCode = ByteBuffer.allocate(bytesToRead);


                    while((bytesToRead -= channel.read(statusCode)) > 0);
                    statusCode.flip();
                    a = new byte[1];
                    statusCode.get(a);
                    System.out.println(new String(a));
                    break;

                case "L":
                    channel.shutdownOutput();
                    bytesToRead = 1;
                    statusCode = ByteBuffer.allocate(bytesToRead);
                    while((bytesToRead -= channel.read(statusCode)) > 0);
                    statusCode.flip();
                    a = new byte[1];
                    statusCode.get(a);
                    System.out.println(new String(a));
                    break;
                case "R":
                    System.out.println("Enter the old file name to rename: ");
                    String oldFileName = keyboard.nextLine();
                    System.out.println("Enter the new file name: ");
                    String newFileName = keyboard.nextLine();

                    // Send old and new file names to the server for renaming
                    String renameRequest = "R" + oldFileName + "|" + newFileName;
                    //pipe character as a delimiter
                    ByteBuffer renameBuffer = ByteBuffer.wrap(renameRequest.getBytes());
                    channel.write(renameBuffer);

                    channel.shutdownOutput();
                    bytesToRead = 1;
                    statusCode = ByteBuffer.allocate(bytesToRead);

                    while ((bytesToRead -= channel.read(statusCode)) > 0) ;
                    statusCode.flip();
                    a = new byte[1];
                    statusCode.get(a);
                    System.out.println(new String(a));
                    break;
                default:
                    if(!command.equals("0")) {
                        System.out.println("Unknown command");
                    }

            }

            //Getting server response back to client
            ByteBuffer responseBuffer = ByteBuffer.allocate(1024);
            int bytesRead = channel.read(responseBuffer);
            if (bytesRead > 0) {
                responseBuffer.flip();
                byte[] responseData = new byte[bytesRead];
                responseBuffer.get(responseData, 0, responseData.length);
                String response = new String(responseData);
                switch (command) {
                    case "G":
                        System.out.printf("Here is your downloaded file: %s", response);
                        break;
                    case "D":
                        System.out.printf("The file was deleted: %s", response);
                        break;
                    case "U":
                        System.out.printf("File was uploaded successfully: %s", response);
                        break;
                    case "L":
                        System.out.printf("Files: %s\n", response);
                        break;
                    case "R":
                        System.out.printf("Renamed file: %s", response);
                    default:
                        System.out.println(response);
                        break;
                }
            }
        }while(!command.equals("0"));
    }

    public static void downloadFile() throws IOException {
        System.out.println("Enter a file name to download");
        fileName = keyboard.nextLine();
        request = ByteBuffer.wrap((fileName).getBytes());

        channel.write(request);
        //Implement file download functionality

        channel.shutdownOutput();
        bytesToRead = 1;
        statusCode = ByteBuffer.allocate(bytesToRead);
        channel.read(statusCode);

        while((bytesToRead -= channel.read(statusCode)) > 0);
        statusCode.flip();
        a = new byte[1];
        statusCode.get(a);
        System.out.println(new String(a));
    }

    public static void uploadProcess() {
        System.out.println("Enter a file name to be uploaded");
        fileName = keyboard.nextLine();
        File fileToUpload = new File(fileName);
        if (!fileToUpload.exists() || !fileToUpload.isFile()) {
            System.out.println("Invalid file path.");
            return;
        }

        executor.submit(() -> {
            try {
                uploadFile(fileToUpload);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public static void uploadFile(File fileToUpload) throws IOException {
        // Send the filename first with a separator
        ByteBuffer fileNameBuffer = ByteBuffer.wrap((fileToUpload.getName() + "%").getBytes());
        channel.write(fileNameBuffer);

        // Read and send file in chunks
        byte[] bufferArray = new byte[1000];
        FileInputStream fis = new FileInputStream(fileToUpload);
        int bytesRead;
        while ((bytesRead = fis.read(bufferArray)) != -1) {
            ByteBuffer fileChunkBuffer = ByteBuffer.wrap(bufferArray, 0, bytesRead);
            channel.write(fileChunkBuffer);
        }

        channel.shutdownOutput();
        bytesToRead = 1;
        statusCode = ByteBuffer.allocate(bytesToRead);

        while((bytesToRead -= channel.read(statusCode)) > 0);
        statusCode.flip();
        a = new byte[1];
        statusCode.get(a);
        System.out.println(new String(a));

        fis.close();
        //Implement functionality for file upload procedure
    }
}
