package ae.gcg.plugins.doxis.fileupload.util;

import okhttp3.*;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class DoxisApiHelper {
    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    private static final String MULTIPART_FORM_DATA = "multipart/form-data";
    private final String LOGIN_URL = "/restws/publicws/rest/api/v1/login";
    private final String CREATE_DOCUMENT_API = "/restws/publicws/rest/api/v1/documents";
    private final OkHttpClient CLIENT = HttpClientFactory.getClient();
    private final String CLASS_NAME = this.getClass().getSimpleName();


    public String createDocument(String serverURL, String username, String password, String customerName, File file, String documentType, String repositoryName) throws IOException {
        MediaType jsonMediaType = MediaType.parse(APPLICATION_JSON);
        JSONObject documentParamsObject = createDocumentParams(file, documentType);
        JSONArray attributesArray = new JSONArray();
        JSONObject attributesObject = new JSONObject();

        //Date Attribute
        attributesObject.put("attributeDefinitionUUID", "2b18b7c1-c85f-41ff-b18d-d22ce74038e0");
        attributesObject.put("attributeDataType", "STRING");
        String formattedDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        attributesObject.put("values", new JSONArray().put(formattedDate));
        attributesArray.put(attributesObject);

        //Name Attribute
        attributesObject = new JSONObject();
        attributesObject.put("attributeDefinitionUUID", "a4832f35-d61f-440e-8354-f5581744c16a");
        attributesObject.put("values", new JSONArray().put(file.getName()));
        attributesObject.put("attributeDataType", "STRING");
        attributesArray.put(attributesObject);

        documentParamsObject.put("attributes", attributesArray);
        String documentParamsRequestBody = documentParamsObject.toString();
        RequestBody jsonBody = RequestBody.create(jsonMediaType, documentParamsRequestBody);


        MediaType fileMediaType = MediaType.parse(APPLICATION_OCTET_STREAM);
        byte[] fileContent = Files.readAllBytes(file.toPath());
        RequestBody fileBody = RequestBody.create(fileMediaType, fileContent);

        RequestBody multipartBody = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("documentParams", null, jsonBody).addFormDataPart("inputStream", file.getName(), fileBody).build();

        Request request = new Request.Builder().url(serverURL + CREATE_DOCUMENT_API).addHeader("Authorization", getJWT(serverURL, username, password, customerName)).addHeader("Content-Type", MULTIPART_FORM_DATA).post(multipartBody).build();

        String documentUUID = null;
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LogUtil.info(CLASS_NAME, "Failed to create document. HTTP Status: " + response.code());
                return null;
            }
            if (response.body() == null) {
                LogUtil.info(CLASS_NAME, "Response body is null after document creation attempt.");
                return null;
            }
            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            documentUUID = jsonResponse.getJSONObject("documentWsTO").getString("uuid");
            LogUtil.info(CLASS_NAME, "Document creation successful. Document UUID: " + documentUUID);
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "Exception occurred during document creation: " + e.getMessage());
            throw e;
        }
        return documentUUID;
    }


    private String getJWT(String serverURL, String username, String password, String customerName) throws IOException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("userName", username);
        jsonObject.put("password", password);
        jsonObject.put("customerName", customerName);
        jsonObject.put("role", "admins");
        String jsonRequestBody = jsonObject.toString();

        MediaType mediaType = MediaType.parse(APPLICATION_JSON);
        RequestBody body = RequestBody.create(mediaType, jsonRequestBody);

        Request request = new Request.Builder().url(serverURL + LOGIN_URL).post(body).addHeader("Content-Type", APPLICATION_JSON).build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to retrieve JWT: HTTP error code " + response.code());
            }
            if (response.body() == null) {
                throw new IOException("Failed to retrieve JWT: Response body is null");
            }
            String responseBody = response.body().string();
            return stripQuotes(responseBody);
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "Error fetching JWT: " + e.getMessage());
            throw e;
        }
    }

    private String stripQuotes(String input) {
        if (input.startsWith("\"") && input.endsWith("\"")) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }


    public InputStream getDocument(String serverURL, String username, String password, String customerName, String documentId, String repositoryName) throws IOException {
        String getURL = serverURL + "/restws/publicws/rest/api/v1/dmsRepositories/" + repositoryName + "/documents/" + documentId + "/versions/current/representations/default/contentObjects/0";

        Request request = new Request.Builder().url(getURL).addHeader("Authorization", getJWT(serverURL, username, password, customerName)).get().build();

        Response response = CLIENT.newCall(request).execute();
        if (!response.isSuccessful()) {
            LogUtil.info(CLASS_NAME, "Failed to fetch document. HTTP Status: " + response.code());
            throw new IOException("Failed to fetch document with ID: " + documentId + ". HTTP Status: " + response.code());
        }
        return response.body().byteStream();
    }


    public boolean deleteDocument(String serverURL, String username, String password, String customerName, String documentId, String repositoryName) throws IOException {
        String downloadURL = serverURL + "/dmsRepositories/" + repositoryName + "/documents/" + documentId;
        LogUtil.info(CLASS_NAME, "Initiating deletion of document ID: " + documentId);

        Request request = new Request.Builder().url(downloadURL).addHeader("Authorization", getJWT(serverURL, username, password, customerName)).delete().build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (response.isSuccessful()) {
                LogUtil.info(CLASS_NAME, "Document deletion successful. Document ID: " + documentId);
                return true;
            } else {
                LogUtil.info(CLASS_NAME, "Failed to delete document. Server responded with status: " + response.code());
                return false;
            }
        } catch (Exception e) {
            LogUtil.info(CLASS_NAME, "Exception occurred while attempting to delete document ID: " + documentId + ". Error: " + e.getMessage());
            return false;
        }
    }


    private JSONObject createDocumentParams(File file, String documentType) {
        JSONObject documentParamsObject = new JSONObject();
        documentParamsObject.put("mimeTypeName", "application/pdf");
        documentParamsObject.put("documentTypeUUID", documentType);
        documentParamsObject.put("fullFileName", file.getName());
        documentParamsObject.put("defaultRepresentation", "true");
        documentParamsObject.put("minorVersion", "false");
        documentParamsObject.put("final", "false");
        return documentParamsObject;
    }


}
