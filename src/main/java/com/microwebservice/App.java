package com.microwebservice;
import static spark.Spark.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.servlet.MultipartConfigElement;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import com.google.gson.Gson;
import com.microwebservice.model.MyResponse;

import io.github.cdimascio.dotenv.Dotenv;

import java.awt.Graphics2D;

import java.awt.image.BufferedImage;

/**
 * Hello world!
 */
public final class App {
    private App() {
    }

    /**
     * Says hello to the world.
     * 
     * @param args The arguments of the program.
     */
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        try {
            FileInputStream serviceAccount = new FileInputStream(dotenv.get("SERVICE_ACCOUNT_PATH"));

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setStorageBucket(dotenv.get("BUCKET_NAME"))
                    .build();

            FirebaseApp.initializeApp(options);
        } catch (Exception e) {
            System.out.println(e.toString());
        }
        // App app = new App();
        get("/hello", (req, res) -> "Hello World 2");
        post("/upload", "multipart/form-data", (req, res) -> {
            try {

                // Initialize firebase storage
                Bucket bucket = StorageClient.getInstance(FirebaseApp.getInstance()).bucket();

                // Initialize gson
                Gson gson = new Gson();

                // This code to support file format uploaded from postman
                MultipartConfigElement multipartConfigElement = new MultipartConfigElement("public/images");
                req.raw().setAttribute("org.eclipse.jetty.multipartConfig",
                        multipartConfigElement);

                // Intial Content of the file
                InputStream content = req.raw().getPart("file").getInputStream();
                String initialUploadedContentType = req.raw().getPart("file").getContentType();

                // Variable to store the content of the file
                List<InputStream> inputStreams = new ArrayList<>();
                List<String> fileNames = new ArrayList<>();
                List<String> fileContentTypes = new ArrayList<>();

                // Response object
                MyResponse response;

                if (initialUploadedContentType.equals("application/zip")) {
                    // process zip file
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    // Read the input stream into the byte array
                    int data;
                    while ((data = content.read()) != -1) {
                        byteArrayOutputStream.write(data);
                    }
                    // Convert the byte array to a zip file
                    byte[] zipBytes = byteArrayOutputStream.toByteArray();
                    ByteArrayInputStream zipStream = new ByteArrayInputStream(zipBytes);

                    ZipInputStream zipInputStream = new ZipInputStream(zipStream);

                    // Read the contents of the zip file
                    ZipEntry zipEntry;
                    while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                        String entryName = zipEntry.getName();
                        fileNames.add(entryName);
                        // Do something with the contents of the zip file
                        if (entryName.endsWith(".png")) {
                            ByteArrayInputStream imageStream = extractedZipEntry(zipInputStream);
                            inputStreams.add(imageStream);
                            fileContentTypes.add("image/png");
                        } else if (entryName.endsWith(".jpg")) {
                            ByteArrayInputStream imageStream = extractedZipEntry(zipInputStream);
                            inputStreams.add(imageStream);
                            fileContentTypes.add("image/jpg");
                        } else if (entryName.endsWith(".jpeg")) {
                            ByteArrayInputStream imageStream = extractedZipEntry(zipInputStream);
                            inputStreams.add(imageStream);
                            fileContentTypes.add("image/jpeg");
                        } else {
                            // do nothing, don't read file that is not image
                        }
                        zipInputStream.closeEntry();
                    }
                } else if (initialUploadedContentType.equals("image/png")
                        || initialUploadedContentType.equals("image/jpg")
                        || initialUploadedContentType.equals("image/jpeg")) {
                    System.out.println("This is executed");
                    inputStreams.add(content);
                    fileNames.add(req.raw().getPart("file").getSubmittedFileName());
                    fileContentTypes.add(initialUploadedContentType);
                } else {
                    response = new MyResponse("400",
                            "The uploaded file is not zip file or an image");
                    return gson.toJson(response);
                }

                // Later can change to unit test because this is not client fault, it was
                // programmer fault
                // Must assume everything is correct, if not, return error
                // check if inputStream list and file name list should be same length
                if (inputStreams.size() != fileNames.size() || inputStreams.size() != fileContentTypes.size()) {
                    // return error
                    System.out.println("Something error happen, the length is not consistent throughout the list");
                    response = new MyResponse("400",
                            "Something error happen, the length is not consistent throughout the list");
                    return gson.toJson(response);
                }

                System.out.println(inputStreams.size());

                // Upload a file to the storage bucket
                List<HashMap<String, String>> hmDataList = new ArrayList<>();
                String folderName = UUID.randomUUID().toString();
                int MAX_SIZE = 128;
                int THUMBNAIL_SIZE = 64;
                int THUMBNAIL_SIZE2 = 32;
                for (int i = 0; i < inputStreams.size(); i++) {
                    HashMap<String, String> data = new HashMap<>();
                    System.out.println(
                            "file name : " + fileNames.get(i) + " file content type : " + fileContentTypes.get(i));
                    BufferedImage image = ImageIO.read(inputStreams.get(i));
                    BufferedImage imageThumbnail64;
                    BufferedImage imageThumbnail32;
                    if (image.getWidth() > MAX_SIZE || image.getHeight() > MAX_SIZE) {
                        // calculate the target height and width
                        double aspectRatio = (double) image.getWidth() / (double) image.getHeight();
                        int targetWidth = 0;
                        int targetHeight = 0;
                        if (image.getWidth() > image.getHeight()) {
                            targetWidth = THUMBNAIL_SIZE;
                            targetHeight = (int) (targetWidth / aspectRatio);
                            imageThumbnail64 = resizeImage(image, targetWidth, targetHeight);
                            targetWidth = THUMBNAIL_SIZE2;
                            targetHeight = (int) (targetWidth / aspectRatio);
                            imageThumbnail32 = resizeImage(image, targetWidth, targetHeight);
                        } else {
                            targetHeight = THUMBNAIL_SIZE;
                            targetWidth = (int) (targetHeight * aspectRatio);
                            imageThumbnail64 = resizeImage(image, targetWidth, targetHeight);
                            targetHeight = THUMBNAIL_SIZE2;
                            targetWidth = (int) (targetHeight * aspectRatio);
                            imageThumbnail32 = resizeImage(image, targetWidth, targetHeight);
                        }

                        // Handle thumbnail 64
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        ImageIO.write(imageThumbnail64, getFileExtension(fileContentTypes.get(i)), output);
                        // Get the input stream from the byte array output stream
                        InputStream resizedInput = new ByteArrayInputStream(output.toByteArray());
                        Blob blob = bucket.create(folderName + "/" + fileNames.get(i) + "64", resizedInput,
                                fileContentTypes.get(i));
                        String url = blob.getMediaLink();
                        data.put("imageName", fileNames.get(i) + "64");
                        data.put("URL64", url);

                        // Handle thumbnail 32
                        output = new ByteArrayOutputStream();
                        ImageIO.write(imageThumbnail32, getFileExtension(fileContentTypes.get(i)), output);
                        // Get the input stream from the byte array output stream
                        resizedInput = new ByteArrayInputStream(output.toByteArray());
                        Blob blob2 = bucket.create(folderName + "/" + fileNames.get(i) + "32", resizedInput,
                                fileContentTypes.get(i));
                        url = blob2.getMediaLink();
                        data.put("imageName", fileNames.get(i) + "32");
                        data.put("URL32", url);
                        // add hashmap into the list
                        hmDataList.add(data);
                    } else {
                        Blob blob3 = bucket.create(folderName + "/" + fileNames.get(i), inputStreams.get(i),
                                fileContentTypes.get(i));
                        // Get a URL for the uploaded file
                        String url = blob3.getMediaLink();
                        data.put("imageName", fileNames.get(i));
                        data.put("URL", url);
                        hmDataList.add(data);
                        System.out.println("File uploaded successfully: " + url);
                    }
                }

                // Convert to JSON
                response = new MyResponse("200", "success", hmDataList);
                return gson.toJsonTree(response);
            } catch (Exception e) {
                System.out.println(e.toString());
                MyResponse response = new MyResponse("200", "e.toString()");
                return response;
            }
        });
    }

    private static ByteArrayInputStream extractedZipEntry(ZipInputStream zipInputStream) throws IOException {
        int data;
        ByteArrayOutputStream byteArrayOutputStream2 = new ByteArrayOutputStream();
        while ((data = zipInputStream.read()) != -1) {
            byteArrayOutputStream2.write(data);
        }
        byte[] imageBytes = byteArrayOutputStream2.toByteArray();
        ByteArrayInputStream imageStream = new ByteArrayInputStream(imageBytes);
        return imageStream;
    }

    private static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight)
            throws IOException {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        graphics2D.dispose();
        return resizedImage;
    }

    private static String getFileExtension(String contentType) {
        String extension = "";
        if (contentType.equals("image/png")) {
            extension = "png";
        } else if (contentType.equals("image/jpg")) {
            extension = "jpg";
        } else {
            extension = "jpeg";
        }
        return extension;
    }
}
