package com.github.kvr000.zbyneklegal.format.storage.googledrive;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.apache.commons.lang3.SystemUtils;


public class GoogleDriveStorageRepository implements StorageRepository
{
    public static final Pattern URL_PATTERN = Pattern.compile("^https://drive.google.com/file/d/([^/]+)/view(?:\\?.*)?$");

    /**
     * Application name.
     */
    private static final String APPLICATION_NAME = "Google Drive API Java Quickstart";
    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    /**
     * Directory to store authorization tokens for this application.
     */
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES =
            Collections.singletonList(DriveScopes.DRIVE_READONLY);

    private static final String CREDENTIALS_FILE_PATH = SystemUtils.getUserHome().getPath() + "/.local/google.com/credentials/oauth-fileaccess.json";

    private final NetHttpTransport HTTP_TRANSPORT;
    private final Drive service;

    public GoogleDriveStorageRepository() throws IOException
    {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
        service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    @Override
    public InputStream downloadFile(String url) throws IOException
    {
        return service.files().get(getFileId(url)).executeMediaAsInputStream();
    }

    @Override
    public Map.Entry<String, String> checksum(String url) throws IOException
    {
        return new AbstractMap.SimpleImmutableEntry<>("MD5", service.files().get(getFileId(url))
                .setFields("md5Checksum")
                .execute()
                .getMd5Checksum()
        );
    }

    private String getFileId(String url) throws IOException
    {
        Matcher urlMatch = URL_PATTERN.matcher(url);
        if (!urlMatch.matches()) {
            throw new IOException("Unrecognized URL for GoogleDrive, expecting " + URL_PATTERN.pattern() + " got: " + url);
        }
        return urlMatch.group(1);
    }

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT)
            throws IOException
    {
        // Load client secrets.
        GoogleClientSecrets clientSecrets;
        try (InputStream in = Files.newInputStream(Paths.get(CREDENTIALS_FILE_PATH))) {
            clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        }

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        //returns an authorized Credential object.
        return credential;
    }
}