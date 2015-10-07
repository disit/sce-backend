/* Smart Cloud/City Engine backend (SCE).
   Copyright (C) 2015 DISIT Lab http://www.disit.org - University of Florence

   This program is free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation; either version 2
   of the License, or (at your option) any later version.
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA. */

package sce;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.Enumeration;
import java.util.UUID;
import java.util.Map;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.client.methods.HttpPost;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionException;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;
import org.quartz.JobKey;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.SchedulerMetaData;
import org.quartz.SimpleScheduleBuilder;
import org.w3c.dom.DOMException;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.codehaus.jackson.map.ObjectMapper;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/*@DisallowConcurrentExecution
 * An annotation that marks a Job
 * class as one that must not have
 * multiple instances executed concurrently
 * (where instance is based-upon a JobDetail 
 * definition - or in other words based upon a JobKey). 
 */

/*@PersistJobDataAfterExecution
 * An annotation that marks a Job class as one that makes
 * updates to its JobDataMap during execution, and wishes the
 * scheduler to re-store the JobDataMap when execution completes.
 * Jobs that are marked with this annotation should also seriously
 * consider using the DisallowConcurrentExecution annotation, to avoid
 * data storage race conditions with concurrently executing job instances.
 * If you use the @PersistJobDataAfterExecution annotation, you should strongly
 * consider also using the @DisallowConcurrentExecution annotation, in order to
 * avoid possible confusion (race conditions) of what data was left stored when two
 * instances of the same job (JobDetail) executed concurrently.
 */
public class RESTAppMetricJob implements InterruptableJob {

    private URLConnection urlConnection;
    private final Properties prop = new Properties();

    public RESTAppMetricJob() {
        try {
            //LOAD QUARTZ PROPERTIES
            prop.load(this.getClass().getResourceAsStream("quartz.properties"));
        } catch (IOException ex) {
            Logger.getLogger(RESTAppMetricJob.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        Connection conn = null;
        try {
            String url1 = prop.getProperty("kb_url");
            //required parameters #url
            JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
            String callUrl = jobDataMap.getString("#url"); // url to call when passing result data from SPARQL query
            if (callUrl == null) {
                throw new JobExecutionException("#url parameter must be not null");
            }
            String timeout = jobDataMap.getString("#timeout"); // timeout in ms to use when calling the #url
            if (timeout == null) {
                timeout = "5000";
            }

            //first SPARQL query to retrieve application related metrics and business configurations
            String url = url1 + "?query=" + URLEncoder.encode(getSPARQLQuery(), "UTF-8");
            URL u = new URL(url);
            final String usernamePassword = u.getUserInfo();
            if (usernamePassword != null) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(usernamePassword.split(":")[0], usernamePassword.split(":")[1].toCharArray());
                    }
                });
            }
            this.urlConnection = u.openConnection();
            this.urlConnection.setRequestProperty("Accept", "application/sparql-results+json");
            HashMap<String, Object> res = new ObjectMapper().readValue(urlConnection.getInputStream(), HashMap.class);
            HashMap<String, Object> r = (HashMap<String, Object>) res.get("results");
            ArrayList<Object> list = (ArrayList<Object>) r.get("bindings");
            ArrayList<String[]> lst = new ArrayList<>();
            for (Object obj : list) {
                HashMap<String, Object> o = (HashMap<String, Object>) obj;
                String mn = (String) ((HashMap<String, Object>) o.get("mn")).get("value");
                String bc = (String) ((HashMap<String, Object>) o.get("bc")).get("value");
                lst.add(new String[]{mn, bc});
            }

            //second SPARQL query to retrieve alerts for SLA
            url = url1 + "?query=" + URLEncoder.encode(getValuesForMetrics(lst), "UTF-8");
            u = new URL(url);
            //java.io.FileWriter fstream = new java.io.FileWriter("/var/www/html/sce/log.txt", false);
            //java.io.BufferedWriter out = new java.io.BufferedWriter(fstream);
            //out.write(getAlertsForSLA(lst, slaTimestamp));
            //out.close();
            this.urlConnection = u.openConnection();
            this.urlConnection.setRequestProperty("Accept", "application/sparql-results+json");

            //format the result
            HashMap<String, Object> alerts = new ObjectMapper().readValue(urlConnection.getInputStream(), HashMap.class);
            HashMap<String, Object> r1 = (HashMap<String, Object>) alerts.get("results");
            ArrayList<Object> list1 = (ArrayList<Object>) r1.get("bindings");
            String result = "";

            //MYSQL CONNECTION
            conn = Main.getConnection();
            // conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED); // use for transactions and at the end call conn.commit() conn.close()
            int counter = 0;

            //SET timestamp FOR MYSQL ROW
            Date dt = new java.util.Date();
            SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = sdf.format(dt);

            // JSON to be sent to the SM
            JSONArray jsonArray = new JSONArray();

            for (Object obj : list1) {
                //JSON to insert into database
                //JSONArray jsonArray = new JSONArray();
                HashMap<String, Object> o = (HashMap<String, Object>) obj;
                String sm = (String) ((HashMap<String, Object>) o.get("sm")).get("value"); //metric
                String mn = (String) ((HashMap<String, Object>) o.get("mn")).get("value"); //metric_name
                String mu = (String) ((HashMap<String, Object>) o.get("mu")).get("value"); //metric_unit
                String v = (String) ((HashMap<String, Object>) o.get("v")).get("value"); //value
                String mt = (String) ((HashMap<String, Object>) o.get("mt")).get("value"); //metric_timestamp
                String bc = (String) ((HashMap<String, Object>) o.get("bc")).get("value"); //business_configuration

                // add these metric value to the json array
                JSONObject object = new JSONObject();
                object.put("business_configuration", bc);
                object.put("metric", sm);
                object.put("metric_name", mn);
                object.put("metric_unit", mu);
                object.put("value", v);
                object.put("metric_timestamp", mt);
                jsonArray.add(object);

                //INSERT THE DATA INTO DATABASE
                PreparedStatement preparedStatement = conn.prepareStatement("INSERT INTO quartz.QRTZ_APP_METRICS (timestamp, metric, metric_name, metric_unit, value, metric_timestamp, business_configuration) VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE timestamp=?");
                preparedStatement.setString(1, timestamp); // date
                preparedStatement.setString(2, sm); // metric
                preparedStatement.setString(3, mn); // metric_name
                preparedStatement.setString(4, mu); // metric_unit
                preparedStatement.setString(5, v); // value
                preparedStatement.setString(6, mt); // metric_timestamp (e.g., 2014-12-01T16:14:00)
                preparedStatement.setString(7, bc); // business_configuration 
                preparedStatement.setString(8, timestamp); // date
                preparedStatement.executeUpdate();
                preparedStatement.close();

                result += "\nService Metric: " + sm + "\n";
                result += "\nMetric Name: " + mn + "\n";
                result += "\nMetric Unit: " + mu + "\n";
                result += "Timestamp: " + mt + "\n";
                result += "Business Configuration: " + bc + "\n";
                result += "Value" + (counter + 1) + ": " + v + "\n";
                result += "Call Url: " + callUrl + "\n";

                counter++;
            }

            // send the JSON to the CM
            URL tmp_u = new URL(callUrl);
            final String usr_pwd = tmp_u.getUserInfo();
            String usr = null;
            String pwd = null;
            if (usr_pwd != null) {
                usr = usr_pwd.split(":")[0];
                pwd = usr_pwd.split(":")[1];
            }
            sendPostRequest(jsonArray.toJSONString(), callUrl, usr, pwd, Integer.parseInt(timeout));

            //set the result to the job execution context, to be able to retrieve it later (e.g., with a job listener)
            context.setResult(result);

            if (jobDataMap.containsKey("#notificationEmail")) {
                sendEmail(context, jobDataMap.getString("#notificationEmail"));
            }
            jobChain(context);
        } catch (MalformedURLException ex) {
            Logger.getLogger(RESTCheckSLAJob.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace();
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(RESTCheckSLAJob.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace();
        } catch (IOException | SQLException ex) {
            Logger.getLogger(RESTCheckSLAJob.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException ex) {
                Logger.getLogger(RESTCheckSLAJob.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void interrupt() throws UnableToInterruptJobException {
        ((HttpURLConnection) this.urlConnection).disconnect();
    }

    //send a notification email upon job completion
    public void sendEmail(JobExecutionContext context, String email) throws JobExecutionException {
        try {
            Date d = new Date();
            String message = "Job was executed at: " + d.toString() + "\n";
            SchedulerMetaData schedulerMetadata = context.getScheduler().getMetaData();
            //Get the scheduler instance id
            message += "Scheduler Instance Id: " + schedulerMetadata.getSchedulerInstanceId() + "\n";
            //Get the scheduler name
            message += "Scheduler Name: " + schedulerMetadata.getSchedulerName() + "\n";
            try {
                //Get the scheduler ip
                Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces();
                message += "Scheduler IP: ";
                for (; n.hasMoreElements();) {
                    NetworkInterface e = n.nextElement();
                    //System.out.println("Interface: " + e.getName());
                    Enumeration<InetAddress> a = e.getInetAddresses();
                    for (; a.hasMoreElements();) {
                        InetAddress addr = a.nextElement();
                        message += !addr.getHostAddress().equals("127.0.0.1") ? addr.getHostAddress() + " " : "";
                    }
                }
                message += "\n";
            } catch (SocketException e) {
                throw new JobExecutionException(e);
            }
            //Returns the result (if any) that the Job set before its execution completed (the type of object set as the result is entirely up to the particular job).
            //The result itself is meaningless to Quartz, but may be informative to JobListeners or TriggerListeners that are watching the job's execution. 
            message += "Result: " + (context.getResult() != null ? context.getResult() : "") + "\n";
            //Get the unique Id that identifies this particular firing instance of the trigger that triggered this job execution. It is unique to this JobExecutionContext instance as well. 
            message += "Fire Instance Id: " + (context.getFireInstanceId() != null ? context.getFireInstanceId() : "") + "\n";
            //Get a handle to the Calendar referenced by the Trigger instance that fired the Job. 
            message += "Calendar: " + (context.getCalendar() != null ? context.getCalendar().getDescription() : "") + "\n";
            //The actual time the trigger fired. For instance the scheduled time may have been 10:00:00 but the actual fire time may have been 10:00:03 if the scheduler was too busy. 
            message += "Fire Time: " + (context.getFireTime() != null ? context.getFireTime() : "") + "\n";
            //the job name
            message += "Job Name: " + (context.getJobDetail().getKey() != null ? context.getJobDetail().getKey().getName() : "") + "\n";
            //the job group
            message += "Job Group: " + (context.getJobDetail().getKey() != null ? context.getJobDetail().getKey().getGroup() : "") + "\n";
            //The amount of time the job ran for (in milliseconds). The returned value will be -1 until the job has actually completed (or thrown an exception), and is therefore generally only useful to JobListeners and TriggerListeners.
            //message += "RunTime: " + context.getJobRunTime() + "\n";
            //the next fire time
            message += "Next Fire Time: " + (context.getNextFireTime() != null && context.getNextFireTime().toString() != null ? context.getNextFireTime().toString() : "") + "\n";
            //the previous fire time
            message += "Previous Fire Time: " + (context.getPreviousFireTime() != null && context.getPreviousFireTime().toString() != null ? context.getPreviousFireTime().toString() : "") + "\n";
            //refire count
            message += "Refire Count: " + context.getRefireCount() + "\n";

            //job data map
            message += "\nJob data map: \n";
            JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
            for (Map.Entry<String, Object> entry : jobDataMap.entrySet()) {
                message += entry.getKey() + " = " + entry.getValue() + "\n";
            }
            Mail.sendMail("SCE notification", message, email, null, null);
        } catch (SchedulerException e) {
            throw new JobExecutionException(e);
        }
    }

    //trigger the linked jobs of the finished job, depending on the job result [true, false]
    public void jobChain(JobExecutionContext context) throws JobExecutionException {
        try {
            //get the job data map
            JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
            //get the finished job result (true/false)
            String resultjob = context.getResult() != null ? (String) context.getResult() : null;

            if (jobDataMap.containsKey("#nextJobs") && resultjob != null) {
                //read json from request
                JSONParser parser = new JSONParser();
                Object obj = parser.parse(jobDataMap.getString("#nextJobs"));
                JSONArray jsonArray = (JSONArray) obj;
                for (int i = 0; i < jsonArray.size(); i++) {
                    JSONObject jsonobject = (JSONObject) jsonArray.get(i);
                    String operator = (String) jsonobject.get("operator");
                    String res = (String) jsonobject.get("result");
                    String nextJobName = (String) jsonobject.get("jobName");
                    String nextJobGroup = (String) jsonobject.get("jobGroup");

                    //if condition is satisfied, trigger the new job it does exist in the scheduler
                    if ((operator.equals("==") && !isNumeric(resultjob) && !isNumeric(res) && res.equalsIgnoreCase(resultjob))
                            || (operator.equals("==") && isNumeric(resultjob) && isNumeric(res) && Double.parseDouble(resultjob) == Double.parseDouble(res))
                            || (operator.equals("!=") && !isNumeric(resultjob) && !isNumeric(res) && !res.equalsIgnoreCase(resultjob))
                            || (operator.equals("!=") && isNumeric(resultjob) && isNumeric(res) && Double.parseDouble(resultjob) != Double.parseDouble(res))
                            || (operator.equals("<") && isNumeric(resultjob) && isNumeric(res) && Double.parseDouble(resultjob) < Double.parseDouble(res))
                            || (operator.equals(">") && isNumeric(resultjob) && isNumeric(res) && Double.parseDouble(resultjob) > Double.parseDouble(res))
                            || (operator.equals("<=") && isNumeric(resultjob) && isNumeric(res) && Double.parseDouble(resultjob) <= Double.parseDouble(res))
                            || (operator.equals(">=") && isNumeric(resultjob) && isNumeric(res) && Double.parseDouble(resultjob) >= Double.parseDouble(res))) {
                        //if nextJobName contains email(s), then send email(s) with obtained result, instead of triggering jobs
                        if (nextJobName.contains("@") && nextJobGroup.equals(" ")) {
                            sendEmail(context, nextJobName);
                        } //else trigger next job
                        else if (context.getScheduler().checkExists(JobKey.jobKey(nextJobName, nextJobGroup))) {
                            context.getScheduler().triggerJob(JobKey.jobKey(nextJobName, nextJobGroup));
                        }
                    }
                }
            }
        } catch (SchedulerException | ParseException e) {
            throw new JobExecutionException(e);
        }
    }

    //test if a string is numeric
    public boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    //return a truncated result if too long for displaying
    public String truncateResult(String result) {
        if (result != null) {
            return result.length() > 30 ? result.substring(0, 29) + "..." : result;
        }
        return result;
    }

    //parse XML response and eventually set the result in the context
    public static String parseKBResponse(Node node, JobExecutionContext context) throws JobExecutionException {
        String result = "";
        try {
            String parentNodeName = node.getParentNode().getNodeName();
            Node parentNodeAttribute = node.getParentNode().getAttributes() != null ? node.getParentNode().getAttributes().getNamedItem("name") : null;
            String parentParentNodeName = node.getParentNode().getParentNode() != null ? node.getParentNode().getParentNode().getNodeName() : "";
            if (/*node.getNodeName().equals("literal") &&*/parentNodeName.equals("binding") && parentNodeAttribute != null && parentParentNodeName.equals("result")) {
                result += "\n" + node.getNodeName() + ": " + node.getTextContent() + " " + node.getParentNode().getAttributes().getNamedItem("name").getTextContent();
                context.setResult(context.getResult() != null ? context.getResult() + result : result);
                //createAndExecuteJob(node.getTextContent());
            }

            NodeList nodeList = node.getChildNodes();
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node currentNode = nodeList.item(i);
                if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                    //calls this method for all the children which is Element
                    parseKBResponse(currentNode, context);
                }
            }
            return result;
        } catch (DOMException | JobExecutionException e) {
            e.printStackTrace();
            throw new JobExecutionException(e);
        }
    }

    //create a non durable REST job at runtime and immediately execute it one time
    public static void createAndExecuteJob(String callerResult, JobExecutionContext context) {
        try {
            JobDetail job = newJob(RESTJob.class)
                    .withIdentity(UUID.randomUUID().toString(), UUID.randomUUID().toString())
                    //.usingJobData("#callerJobName", context.getJobDetail().getKey().getName())
                    //.usingJobData("#callerJobGroup", context.getJobDetail().getKey().getGroup())
                    .usingJobData("#url", callerResult)
                    .storeDurably(false)
                    .build();

            Trigger trigger = newTrigger()
                    .withIdentity(TriggerKey.triggerKey(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0))
                    .startNow()
                    .build();

            context.getScheduler().scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            e.printStackTrace();
            //don't throw a JobExecutionException if a job execution, invoked by this job, fails
        }
    }

    //not used
    public static URL convertToURLEscapingIllegalCharacters(String string) {
        try {
            String decodedURL = URLDecoder.decode(string, "UTF-8");
            URL url = new URL(decodedURL);
            URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
            return uri.toURL();
        } catch (MalformedURLException | URISyntaxException | UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    //not used
    public static byte[] readURL(String url) {
        try {
            URL u = new URL(url);
            URLConnection conn = u.openConnection();
            // look like simulating the request coming from Web browser solve 403 error
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-GB; rv:1.9.2.13) Gecko/20101203 Firefox/3.6.13 (.NET CLR 3.5.30729)");
            byte[] bytes;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String json = in.readLine();
                bytes = json.getBytes("UTF-8");
            }
            return bytes;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    //not used
    public static ByteBuffer getAsByteArray(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        ByteArrayOutputStream tmpOut;
        try (InputStream in = connection.getInputStream()) {
            int contentLength = connection.getContentLength();
            if (contentLength != -1) {
                tmpOut = new ByteArrayOutputStream(contentLength);
            } else {
                tmpOut = new ByteArrayOutputStream(16384); // pick some appropriate size
            }
            byte[] buf = new byte[512];
            while (true) {
                int len = in.read(buf);
                if (len == -1) {
                    break;
                }
                tmpOut.write(buf, 0, len);
            }
        }
        tmpOut.close();
        byte[] array = tmpOut.toByteArray();
        return ByteBuffer.wrap(array);
    }

    //get SPARQL query
    //select all application related metrics
    public String getSPARQLQuery() {
        return "# SCE-3 APPLICATION METRICS\n"
                + "PREFIX icr:<http://www.cloudicaro.it/cloud_ontology/core#>\n"
                + "select ?mn ?bc where {\n"
                + "   ?obj a icr:ServiceLevelObjective.\n"
                + "   ?obj icr:hasSLMetric ?metric.\n"
                + "   ?metric icr:dependsOn ?simplemetric.\n"
                + "   ?simplemetric icr:hasMetricName ?mn.\n"
                + "   ?simplemetric icr:dependsOn ?bc.\n"
                + "   ?bc a icr:BusinessConfiguration.\n"
                + "}";
    }

    //return a SPARQL query to find application related metrics values
    //service[0] = metric name [mn] (e.g., Joomla LAST Registered Users)
    //service[1] = business configuration (e.g, urn:cloudicaro:BusinessConfiguration:31000)
    public static String getValuesForMetrics(ArrayList<String[]> services) {
        String result = "# SCE-4 " + services.size() + "\n"
                + "PREFIX icr:<http://www.cloudicaro.it/cloud_ontology/core#>\n"
                + " select * where {\n";
        int counter = 1;
        for (String[] service : services) {
            String union = (counter != 1 ? " union" : "");
            result += union + " {\n"
                    + "select ?sm ?v ?mu ?mt (\"" + service[0] + "\" AS ?mn) (\"" + service[1] + "\" AS ?bc) where {\n"
                    + "?sm a icr:ServiceMetric;\n"
                    + " icr:hasMetricName \"" + service[0] + "\";\n"
                    + " icr:dependsOn <" + service[1] + ">;\n"
                    + " icr:hasMetricValue ?v;\n"
                    + " icr:hasMetricUnit ?mu;\n"
                    + " icr:atTime ?mt.\n"
                    + "} order by desc(?mt) limit 1"
                    + "}\n";
            counter++;
        }
        result += "}";

        return result;
    }

    //get the opposite property (<=, >=, =) for a metric
    public static String getPropertyComplement(String property) {
        if (property.contains("hasMetricValueLessThan")) {
            return ">";
        } else if (property.contains("hasMetricValueGreaterThan")) {
            return "<";
        } else {
            return "=";
        }
    }

    //get the property (<=, >=, =) for a metric
    public static String getProperty(String property) {
        if (property.contains("hasMetricValueLessThan")) {
            return "<";
        } else if (property.contains("hasMetricValueGreaterThan")) {
            return ">";
        } else {
            return "=";
        }
    }

    //check sla
    public boolean checkSLA(double value, double threshold, String property) {
        if (property.contains("hasMetricValueLessThan")) {
            if (value >= threshold) {
                return true;
            }
        } else if (property.contains("hasMetricValueGreaterThan")) {
            if (value <= threshold) {
                return true;
            }
        } else if (property.contains("hasMetricValue")) {
            if (value != threshold) {
                return true;
            }
        }
        return false;
    }

    public static String printMap(Map mp) {
        String result = "";
        Iterator it = mp.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry) it.next();
            //System.out.println(pairs.getKey() + " = " + pairs.getValue());
            result += pairs.getKey() + " = " + pairs.getValue() + "\n";
            it.remove(); // avoids a ConcurrentModificationException
        }
        return result;
    }

    private String getUrlContents(URLConnection urlConnection) throws JobExecutionException {
        StringBuilder content = new StringBuilder();
        try {
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    content.append(line);
                }
            }
        } catch (IOException e) {
            throw new JobExecutionException(e);
        }
        return content.toString();
    }

    // in case of alarm, send a POST request to the SM
    public void sendPostRequest(String json, String url, String username, String password, int timeout) {
        HTTPPostReq httpPostReq = new HTTPPostReq();
        HttpPost httpPost = httpPostReq.createConnectivity(url, username, password, timeout);
        httpPostReq.executeReq(json, httpPost);
    }
}
