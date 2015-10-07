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
import static org.quartz.JobBuilder.*;
import static org.quartz.TriggerBuilder.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.Enumeration;
import java.util.UUID;
import java.util.Map;
import java.util.Date;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
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
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import static sce.RESTXMLJobStateful.convertToURLEscapingIllegalCharacters;
import static sce.RESTXMLJobStateful.parseXMLResponse;

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
public class RESTXMLJob implements InterruptableJob {

    private URLConnection urlConnection;

    public RESTXMLJob() {
    }

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        try {
            //JobKey key = context.getJobDetail().getKey();

            JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();

            String url = jobDataMap.getString("#url");
            String binding = jobDataMap.getString("#binding");
            if (url == null | binding == null) {
                throw new JobExecutionException("#url and #binding parameters must be not null");
            }

            URL u = new URL(url);

            //get user credentials from URL, if present
            final String usernamePassword = u.getUserInfo();

            //set the basic authentication credentials for the connection
            if (usernamePassword != null) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(usernamePassword.split(":")[0], usernamePassword.split(":")[1].toCharArray());
                    }
                });
            }

            //set the url connection, to disconnect if interrupt() is requested
            this.urlConnection = u.openConnection();

            //set the "Accept" header
            this.urlConnection.setRequestProperty("Accept", "application/sparql-results+xml");

            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            //detect and exclude BOM
            //BOMInputStream bomIn = new BOMInputStream(this.urlConnection.getInputStream(), false);
            Document document = docBuilder.parse(this.urlConnection.getInputStream());

            String result = parseXMLResponse(document.getDocumentElement(), binding, context);

            //set the result to the job execution context, to be able to retrieve it later (e.g., with a job listener)
            context.setResult(result);

            //if notificationEmail is defined in the job data map, then send a notification email to it
            if (jobDataMap.containsKey("#notificationEmail")) {
                sendEmail(context, jobDataMap.getString("#notificationEmail"));
            }

            //trigger the linked jobs of the finished job, depending on the job result [true, false]
            jobChain(context);
            //System.out.println("Instance " + key + " of REST Job returns: " + truncateResult(result));
        } catch (IOException | ParserConfigurationException | SAXException e) {
            e.printStackTrace(System.out);
            throw new JobExecutionException(e);
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
            message += "Result: " + (context.getResult() != null ? truncateResult((String) context.getResult()) : "") + "\n";
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

    //parse XML response and eventually, set the result in the context and invoke a new job with the result as the #url parameter
    public static String parseXMLResponse(Node node, String binding, JobExecutionContext context) throws JobExecutionException {
        //if node name is "uri", and the parent node name is "binding", and the node "binding" has the specified attribute name (parameter String binding), and the parent node name of the node "binding" is "result"
        /*<result>
         <binding name='s'>
         <uri>http://www.cloudicaro.it/cloud_ontology/core#IcaroService</uri>
         </binding>
         </result>
         */
        try {
            String parentNodeName = node.getParentNode().getNodeName();
            Node parentNodeAttribute = node.getParentNode().getAttributes() != null ? node.getParentNode().getAttributes().getNamedItem("name") : null;
            String bindingValue = parentNodeAttribute != null ? parentNodeAttribute.getTextContent() : "";
            String parentParentNodeName = node.getParentNode().getParentNode() != null ? node.getParentNode().getParentNode().getNodeName() : "";
            if (node.getNodeName().equals("url") && parentNodeName.equals("binding") && parentNodeAttribute != null && bindingValue.equalsIgnoreCase(binding) && parentParentNodeName.equals("result")) {
                //System.out.println(node.getNodeName() + ": " + node.getTextContent() + " " + node.getParentNode().getAttributes().getNamedItem("name").getTextContent());
                createAndExecuteJob(node.getTextContent(), context);
            }

            NodeList nodeList = node.getChildNodes();
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node currentNode = nodeList.item(i);
                if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                    //calls this method for all the children which is Element
                    parseXMLResponse(currentNode, binding, context);
                }
            }
            return "done";
        } catch (DOMException | JobExecutionException e) {
            e.printStackTrace(System.out);
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
            e.printStackTrace(System.out);
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
            e.printStackTrace(System.out);
            return null;
        }
    }

    //not used
    public static byte[] readURL(String url) {
        try {
            URL u = new URL(url);
            URLConnection conn = u.openConnection();
            // Look like faking the request coming from Web browser solve 403 error
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-GB; rv:1.9.2.13) Gecko/20101203 Firefox/3.6.13 (.NET CLR 3.5.30729)");
            byte[] bytes;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String json = in.readLine();
                bytes = json.getBytes("UTF-8");
            }
            return bytes;
        } catch (IOException e) {
            e.printStackTrace(System.out);
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
                tmpOut = new ByteArrayOutputStream(16384); // Pick some appropriate size
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
}
