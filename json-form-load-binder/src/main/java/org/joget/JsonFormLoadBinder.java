package org.joget;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadBinder;
import org.joget.apps.form.model.FormLoadElementBinder;
import org.joget.apps.form.model.FormLoadMultiRowElementBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.property.service.PropertyUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONException;
import org.json.JSONObject;

public class JsonFormLoadBinder extends FormBinder implements FormLoadBinder, FormLoadElementBinder, FormLoadMultiRowElementBinder {

    public String getName() {
        return "Json Form Load Binder";
    }

    public String getVersion() {
        return "7.0.0";
    }

    public String getDescription() {
        return "Reads a JSON feed URL to get form data";
    }

    public String getLabel() {
        return getName();
    }

    public String getClassName() {
        return getClass().getName();
    }

    public String getPropertyOptions() {
        String json = AppUtil.readPluginResource(getClass().getName(), "/properties/JsonFormLoadBinder.json", null, true, "messages/JsonFormLoadBinder");
        return json;
    }

    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        FormRowSet results = new FormRowSet();
        results.setMultiRow(true);
        String baseObjName = getPropertyString("baseObjName");
        Map jsonObject = new HashMap();

        try {
            jsonObject = getJsonData();

            Object[] mappings = null;
            if (getProperty("mappings") instanceof Object[]) {
                mappings = (Object[]) getProperty("mappings");
            }

            if (jsonObject != null) {
                if ((baseObjName != null) && (!baseObjName.isEmpty())) {
                    if ((getObjectFromMap(baseObjName, jsonObject) instanceof HashMap)) {
                        FormRow row = new FormRow();

                        if (mappings != null && mappings.length > 0) {
                            for (Object o : mappings) {
                                Map mapping = (HashMap) o;
                                String attribute = mapping.get("attribute").toString();
                                String fieldId = mapping.get("fieldId").toString();
                                String value = (String) getObjectFromMap(attribute.replace(baseObjName, baseObjName + "[" + 0 + "]"), jsonObject);
                                row.setProperty(fieldId, value);

                            }
                        }
                        results.add(row);
                    } else {
                        Object[] baseObjectArray = (Object[]) getObjectFromMap(baseObjName, jsonObject);
                        if (baseObjectArray != null && baseObjectArray.length > 0) {

                            if (baseObjectArray.length > 1) {
                                results.setMultiRow(true);
                            }

                            for (int i = 0; i < baseObjectArray.length; i++) {
                                FormRow row = new FormRow();

                                if (mappings != null && mappings.length > 0) {
                                    for (Object o : mappings) {
                                        Map mapping = (HashMap) o;
                                        String attribute = mapping.get("attribute").toString();
                                        String fieldId = mapping.get("fieldId").toString();
                                        String value = (String) getObjectFromMap(attribute.replace(baseObjName, baseObjName + "[" + i + "]"), jsonObject);
                                        row.setProperty(fieldId, value);
                                    }
                                }
                                results.add(row);
                            }
                        }
                    }
                } else {
//                    LogUtil.info(getClass().getName(), "Invalid Base Object Name.");

                    //Support single object responses
                    FormRow row = new FormRow();

                    if (mappings != null && mappings.length > 0) {
                        for (Object o : mappings) {
                            Map mapping = (HashMap) o;
                            String attribute = mapping.get("attribute").toString();
                            String fieldId = mapping.get("fieldId").toString();
                            String value = (String) jsonObject.get(attribute);
                            row.setProperty(fieldId, value);
                        }
                    }
                    
                    results.add(row);
                }
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "");
            return new FormRowSet();
        }

        return results;
    }

    public Map getJsonData() {
        WorkflowAssignment wfAssignment = (WorkflowAssignment) getProperty("workflowAssignment");

        String jsonUrl = getPropertyString("jsonUrl");
        CloseableHttpClient client = null;
        HttpRequestBase request = null;
        //jsonMap = null;
        try {
            client = org.apache.http.impl.client.HttpClients.createDefault();
            jsonUrl = WorkflowUtil.processVariable(jsonUrl, "", wfAssignment);
            jsonUrl = org.joget.commons.util.StringUtil.encodeUrlParam(jsonUrl);

            if ("true".equalsIgnoreCase(getPropertyString("debugMode"))) {
                LogUtil.info(getClass().getName(), ("post".equalsIgnoreCase(getPropertyString("requestType")) ? "POST" : "GET") + " : " + jsonUrl);
            }
            Object requestEntity;
            Object[] paramsValues;
            if ("post".equalsIgnoreCase(getPropertyString("requestType"))) {
                request = new HttpPost(jsonUrl);

                if ("jsonPayload".equals(getPropertyString("postMethod"))) {
                    JSONObject obj = new JSONObject();
                    paramsValues = (Object[]) getProperty("params");
                    for (Object o : paramsValues) {
                        Map mapping = (HashMap) o;
                        String name = mapping.get("name").toString();
                        String value = mapping.get("value").toString();
                        obj.accumulate(name, WorkflowUtil.processVariable(value, "", wfAssignment));
                    }

                    requestEntity = new StringEntity(obj.toString(4));
                    ((HttpPost) request).setEntity((HttpEntity) requestEntity);
                    request.setHeader("Content-type", "application/json");
                    if ("true".equalsIgnoreCase(getPropertyString("debugMode"))) {
                        LogUtil.info(getClass().getName(), "JSON Payload : " + obj.toString(4));
                    }
                } else {
                    List<NameValuePair> urlParameters = new ArrayList();
                    paramsValues = (Object[]) getProperty("params");
                    for (Object o : paramsValues) {
                        Map mapping = (HashMap) o;
                        String name = mapping.get("name").toString();
                        String value = mapping.get("value").toString();
                        urlParameters.add(new BasicNameValuePair(name, WorkflowUtil.processVariable(value, "", wfAssignment)));
                        if ("true".equalsIgnoreCase(getPropertyString("debugMode"))) {
                            LogUtil.info(getClass().getName(), "Adding param " + name + " : " + value);
                        }
                    }
                    ((HttpPost) request).setEntity(new org.apache.http.client.entity.UrlEncodedFormEntity(urlParameters));
                }
            } else {
                request = new HttpGet(jsonUrl);
            }

            paramsValues = (Object[]) getProperty("headers");
            for (Object o : paramsValues) {
                Map mapping = (HashMap) o;
                String name = mapping.get("name").toString();
                String value = mapping.get("value").toString();
                if ((name != null) && (!name.isEmpty()) && (value != null) && (!value.isEmpty())) {
                    request.setHeader(name, value);
                    if ("true".equalsIgnoreCase(getPropertyString("debugMode"))) {
                        LogUtil.info(getClass().getName(), "Adding request header " + name + " : " + value);
                    }
                }
            }

            HttpResponse response = client.execute(request);

            if ("true".equalsIgnoreCase(getPropertyString("debugMode"))) {
                LogUtil.info(getClass().getName(), jsonUrl + " returned with status : " + response.getStatusLine().getStatusCode());
            }

            String jsonResponse = EntityUtils.toString(response.getEntity(), "UTF-8");

            if ((jsonResponse != null) && (!jsonResponse.isEmpty())) {
                if ((jsonResponse.startsWith("[")) && (jsonResponse.endsWith("]"))) {
                    jsonResponse = "{ \"response\" : " + jsonResponse + " }";
                }
                if ("true".equalsIgnoreCase(getPropertyString("debugMode"))) {
                    LogUtil.info(getClass().getName(), jsonResponse);
                }
            }
            return PropertyUtil.getProperties(new JSONObject(jsonResponse));
        } catch (IOException ex) {
            LogUtil.error(getClass().getName(), ex, "Unable to execute RESTful call request. Check your connection or URL.");
        } catch (ParseException ex) {
            LogUtil.error(getClass().getName(), ex, "Unable to parse JSON to String for further processing.");
        } catch (JSONException ex) {
            LogUtil.error(getClass().getName(), ex, "Invalid JSON. Check JSON URL.");
        } finally {
            try {
                if (request != null) {
                    request.releaseConnection();
                }
                if (client != null) {
                    client.close();
                }
            } catch (IOException ex) {
                LogUtil.error(getClass().getName(), ex, "Unable to terminate RESTful call connection or connection does not exist.");
            }
        }
        return null;
    }

    protected Object getObjectFromMap(String key, Map object) {
        try {
            if (key.contains(".")) {
                String subKey = key.substring(key.indexOf(".") + 1);
                key = key.substring(0, key.indexOf("."));

                Map tempObject = (Map) getObjectFromMap(key, object);

                if (tempObject != null) {
                    return getObjectFromMap(subKey, tempObject);
                }
            } else if ((key.contains("[")) && (key.contains("]"))) {
                String tempKey = key.substring(0, key.indexOf("["));
                int number = Integer.parseInt(key.substring(key.indexOf("[") + 1, key.indexOf("]")));
                if ((object.get(tempKey) instanceof HashMap)) {
                    Map tempObjectArray = (Map) object.get(tempKey);
                    if (tempObjectArray != null) {
                        return tempObjectArray;
                    }
                } else {
                    Object[] tempObjectArray = (Object[]) object.get(tempKey);
                    if ((tempObjectArray != null) && (tempObjectArray.length > number)) {
                        return tempObjectArray[number];
                    }
                }
            } else {
                return object.get(key);
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "");
        }
        return null;
    }
}
