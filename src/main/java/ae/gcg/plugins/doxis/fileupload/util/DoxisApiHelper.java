package ae.gcg.plugins.doxis.fileupload.util;

import okhttp3.*;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;

public class DoxisApiHelper {
    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    private static final String MULTIPART_FORM_DATA = "multipart/form-data";
    private final String LOGIN_URL = "/restws/publicws/rest/api/v1/login";
    private final String CREATE_DOCUMENT_API = "/restws/publicws/rest/api/v1/documents";
    private final OkHttpClient CLIENT = HttpClientFactory.getClient();
    private final String CLASS_NAME = this.getClass().getSimpleName();
    private final String FILE_NAME_ATTRIBUTE_ID = "339a143f-94bd-4b30-a8c4-d2dcae1940b0";
    //private final String FILE_NAME_ATTRIBUTE_ID = "a4832f35-d61f-440e-8354-f5581744c16a";


    public String createDocument(String serverURL, String username, String password, String customerName, File file, String documentType, String repositoryName, String meetingNumber, String committeeName, String committeeDecisionNumber) throws IOException {
        LogUtil.info(CLASS_NAME, "CREATE DOCUMENT PARAMETERS" + meetingNumber + committeeName + committeeDecisionNumber + documentType + repositoryName);

        MediaType jsonMediaType = MediaType.parse(APPLICATION_JSON);
        String fileType = file.getName().split("\\.")[1];
        LogUtil.info("FileType 0------->>>> ", fileType);
        JSONObject documentParamsObject = createDocumentParams(file, documentType);
        if (fileType.equals("pdf")){
            documentParamsObject.put("mimeTypeName", "application/pdf");
        }else if (fileType.equals("doc")){
            documentParamsObject.put("mimeTypeName", "application/msword");
        }else if(fileType.equals("docx")){
            documentParamsObject.put("mimeTypeName", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }else if(fileType.equals("ptx") || fileType.equals("pptx")){
            documentParamsObject.put("mimeTypeName", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        }else if (fileType.equals("xlsx") || fileType.equals("xls")){
            documentParamsObject.put("mimeTypeName", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
        else{
            documentParamsObject.put("mimeTypeName", "application/pdf");
        }
        JSONArray attributesArray = new JSONArray();
        JSONObject attributesObject = new JSONObject();
        attributesObject.put("attributeDefinitionUUID", FILE_NAME_ATTRIBUTE_ID);
        attributesObject.put("values", new JSONArray().put(file.getName()));
        attributesObject.put("attributeDataType", "STRING");
        attributesArray.put(attributesObject);

        if(repositoryName.equals("Meetings_Docs")){
            //MEETING Number
            JSONObject meetingObject = new JSONObject();
            meetingObject.put("attributeDefinitionUUID", "21ce7cc4-a13d-4ea7-8543-bbd521a41e88");
            meetingObject.put("attributeDataType", "STRING");
            meetingObject.put("values", new JSONArray().put(meetingNumber));
            attributesArray.put(meetingObject);

        }else{
            //committee Name
            JSONObject committeeNameObject = new JSONObject();
            committeeNameObject.put("attributeDefinitionUUID", "cf0371e7-ff9e-4c87-a7cd-884d420f4f91");
            committeeNameObject.put("attributeDataType", "STRING");
            committeeNameObject.put("values", new JSONArray().put(committeeName));
            attributesArray.put(committeeNameObject);

            // committee decision number
            JSONObject committeeNumberObject = new JSONObject();
            committeeNumberObject.put("attributeDefinitionUUID", "30a67a6b-3cd4-4c5f-8819-d079676b7799");
            committeeNumberObject.put("attributeDataType", "STRING");
            committeeNumberObject.put("values", new JSONArray().put(committeeDecisionNumber));
            attributesArray.put(committeeNumberObject);
        }

        documentParamsObject.put("attributes", attributesArray);
        LogUtil.info("Attributes JSON", attributesArray.toString());
        String documentParamsRequestBody = documentParamsObject.toString();
        LogUtil.info("Request Body" , documentParamsRequestBody);
        RequestBody jsonBody = RequestBody.create(jsonMediaType, documentParamsRequestBody);


        MediaType fileMediaType = MediaType.parse(APPLICATION_OCTET_STREAM);
        byte[] fileContent = Files.readAllBytes(file.toPath());
        RequestBody fileBody = RequestBody.create(fileMediaType, fileContent);

        RequestBody multipartBody = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("documentParams", null, jsonBody).addFormDataPart("inputStream", file.getName(), fileBody).build();

        Request request = new Request.Builder().url(serverURL + CREATE_DOCUMENT_API).addHeader("Authorization", getJWT(serverURL, username, password, customerName)).addHeader("Content-Type", MULTIPART_FORM_DATA).post(multipartBody).build();

        String documentUUID = null;
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LogUtil.info("Meeting ID", meetingNumber);
                LogUtil.info(CLASS_NAME, "Failed to create document. HTTP Status: " + response.code() + response.body().string());
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


    public Response getDocument(String serverURL, String username, String password, String customerName, String documentId, String repositoryName) throws IOException {
        String getURL = serverURL + "/restws/publicws/rest/api/v1/dmsRepositories/" + repositoryName + "/documents/" + documentId + "/versions/current/representations/default/contentObjects/0";

        Request request = new Request.Builder().url(getURL).addHeader("Authorization", getJWT(serverURL, username, password, customerName)).get().build();

        Response response = CLIENT.newCall(request).execute();
        if (!response.isSuccessful()) {
            LogUtil.info(CLASS_NAME, "Failed to fetch document. HTTP Status: " + response.code());
            throw new IOException("Failed to fetch document with ID: " + documentId + ". HTTP Status: " + response.code());
        }

        Headers headers = response.headers();
        LogUtil.info(CLASS_NAME, "Content-Type: " + headers.get("Content-Type"));
        LogUtil.info(CLASS_NAME, "Content-Length: " + headers.get("Content-Length"));
        LogUtil.info(CLASS_NAME, "Content-Disposition: " + headers.get("Content-Disposition"));

        return response;
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
