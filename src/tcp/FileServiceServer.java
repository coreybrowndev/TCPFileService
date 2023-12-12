package tcp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static tcp.FileServiceServer.FileUpload.handleUpload;

public class FileServiceServer{
    private static byte[] a;
    private static String fileName;
    private static File file;
    private static boolean success = false;
    private static ByteBuffer fileBytes = null;
    private static ExecutorService executor = Executors.newFixedThreadPool(2);

    private static boolean uploader = false;
    private static int downloaders = 0;
    private static ReentrantLock lock = new ReentrantLock();
    private static Condition noUploader = lock.newCondition();
    private static Condition noDownloaderOrUploader = lock.newCondition();
    private static int shared = 0;

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

            switch (command) {
                case 'G':
                    //File to download
                    executor.submit(new FileDownload(serveChannel, fileName, request));
                    break;
                case 'U':
                    executor.submit(new FileUpload(serveChannel, request));
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
                case 'Q':
                    executor.shutdown();
                    System.exit(0);
                    break;
                default:
                    System.out.println("Bye Bye!!");
                    break;
            }

            //Send reply back to client
            //Rewinds the position without touching the limit
            request.rewind();
        }
    }

    public static class FileUpload implements Runnable {
        private final SocketChannel serveChannel;
        private final ByteBuffer request;

        public FileUpload(SocketChannel serveChannel, ByteBuffer request) {
            this.serveChannel = serveChannel;
            this.request = request;
        }


        public void run() {
            lock.lock();
            try {
                while(uploader || downloaders > 0) {
                    noDownloaderOrUploader.await();
                }
                uploader = true;
                shared++;
                handleUpload(serveChannel, request);
                uploader = false;
                noDownloaderOrUploader.signal();
                noUploader.signal();
            }catch(IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }finally {
                lock.unlock();
            }
        }

        public static void handleUpload(SocketChannel serveChannel, ByteBuffer request) throws IOException {
            a = new byte[request.remaining()];

            request.get(a);
            String totalData = new String(a);

            int separatorIndex = totalData.indexOf('%');

            if(separatorIndex == -1) {
                System.out.println("Invalid file format");
                ByteBuffer code = ByteBuffer.wrap("Process Failed".getBytes());
                serveChannel.write(code);
                return;
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

            serveChannel.close();
        }

    }


    public static class FileDownload implements Runnable {
        private final SocketChannel serveChannel;

        private final ByteBuffer request;

        public FileDownload(SocketChannel serveChannel, String fileName, ByteBuffer request) {
            this.serveChannel = serveChannel;
//            this.fileName = fileName;
            this.request = request;
        }

        @Override
        public void run() {
            lock.lock();
            try {
                while (uploader) {
                    noUploader.await();
                }
                downloaders++;
                handleDownload(serveChannel);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
            System.out.println(shared);
            lock.lock();
            try {
                downloaders--;
                if (downloaders == 0) {
                    noDownloaderOrUploader.signal();
                }
            } finally {
                lock.unlock();
            }
        }

        private void handleDownload(SocketChannel serveChannel) throws IOException {
            String fileName;
            File file;
            request.get();
            byte[] fileNameBytes = new byte[request.remaining()];
            request.get(fileNameBytes);
            fileName = new String(fileNameBytes);
            file = new File("ServerFiles/"+ fileName);
            if(file.exists() && file.isFile()) {
                byte[] fileContent = Files.readAllBytes(file.toPath());
                ByteBuffer fileBuffer = ByteBuffer.wrap(fileContent);
                ByteBuffer code = ByteBuffer.wrap("S".getBytes());
                serveChannel.write(code);
                serveChannel.write(fileBuffer);
                File created = new File(fileName);
                created.createNewFile();
                Files.write(created.toPath(), fileContent);
            }else {
                ByteBuffer code = ByteBuffer.wrap("F".getBytes());
                serveChannel.write(code);
            }
        }
    }
}


