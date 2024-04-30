package ae.gcg.plugins.doxis.fileupload;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListColumnFormatDefault;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.StringUtil;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONObject;

public class DoxisFileFormatter extends DataListColumnFormatDefault {

    private final static String MESSAGE_PATH = "messages/DoxisFileFormatter";

    @Override
    public String getName() {
        return "Doxis DMS File Formatter";
    }

    @Override
    public String getVersion() {
        return "8.0.0";
    }

    @Override
    public String getDescription() {
        return "Format filename and download file from Doxis DMS inside the datalist";
    }

    @Override
    public String getLabel() {
        return "Doxis DMS File Formatter";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/DoxisFileDownloadFormatter.json", null, true, MESSAGE_PATH);
    }

    @Override
    public String format(DataList dataList, DataListColumn column, Object row, Object value) {
        StringBuilder result = new StringBuilder();
        if (value != null) {
            String[] values = value.toString().split(";");
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            String appId = "";
            String appVersion = "";
            if (appDef != null) {
                appId = appDef.getId();
                appVersion = appDef.getVersion().toString();
            }

            String enableDownload = getPropertyString("enableDownload");
            String serverUrl = getPropertyString("serverUrl");
            String username = getPropertyString("username");
            String password = getPropertyString("password");
            String customerName = getPropertyString("customerName");
            String repositoryName = getPropertyString("repositoryName");

            JSONObject jsonParams = new JSONObject();
            jsonParams.put("serverUrl", serverUrl);
            jsonParams.put("username", username);
            jsonParams.put("password", password);
            jsonParams.put("customerName", customerName);
            jsonParams.put("repositoryName", repositoryName);

            for (String v : values) {
                if (v != null && !v.isEmpty() && v.indexOf('|') != -1) {
                    String[] verticalBarSplit = v.split("\\|");
                    if (verticalBarSplit.length > 0) {
                        String filename = verticalBarSplit[0];
                        String documentId = verticalBarSplit[1];
                        String params = StringUtil.escapeString(SecurityUtil.encrypt(jsonParams.toString()), StringUtil.TYPE_URL, null);

                        if ("true".equalsIgnoreCase(enableDownload)) {
                            String filePath = WorkflowUtil.getHttpServletRequest().getContextPath() + "/web/json/app/" + appId + "/" + appVersion + "/plugin/ae.gcg.plugins.doxis.fileupload.DoxisFileUpload/service?dID=" + documentId + "&action=download&params=" + params;
                            String downloadUrl = "<a href=\"" + filePath + "\" target=\"_blank\">" + filename + "</a>";
                            result.append(downloadUrl);
                        } else {
                            result.append(filename);
                        }
                        result.append(";");
                    }

                } else {
                    result.append(v);
                }

            }
            if (result.length() > 0) {
                result.deleteCharAt(result.length() - 1);
            }
        }
        return result.toString();
    }


}
