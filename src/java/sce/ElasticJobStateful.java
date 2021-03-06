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

import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.PersistJobDataAfterExecution;
import org.quartz.SchedulerException;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.quartz.InterruptableJob;
import org.quartz.SchedulerMetaData;
import org.quartz.UnableToInterruptJobException;

@DisallowConcurrentExecution
/* An annotation that marks a Job
 * class as one that must not have
 * multiple instances executed concurrently
 * (where instance is based-upon a JobDetail 
 * definition - or in other words based upon a JobKey). 
 */
@PersistJobDataAfterExecution
/* An annotation that marks a Job class as one that makes
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

/*
 DisallowConcurrentExecution is an attribute that can be added to the Job class that tells
 Quartz not to execute multiple instances of a given job definition (that refers to the given job class) concurrently.
 Notice the wording there, as it was chosen very carefully. In the example from the previous section, if "SalesReportJob" has
 this attribute, than only one instance of "SalesReportForJoe" can execute at a given time, but it can execute concurrently with an
 instance of "SalesReportForMike". The constraint is based upon an instance definition (JobDetail), not on instances of the job class.
 However, it was decided (during the design of Quartz) to have the attribute carried on the class itself, because it does often make
 a difference to how the class is coded.

 PersistJobDataAfterExecution is an attribute that can be added to the Job class that tells Quartz to update the stored copy of the
 JobDetail's JobDataMap after the Execute() method completes successfully (without throwing an exception), such that the next execution
 of the same job (JobDetail) receives the updated values rather than the originally stored values. Like the DisallowConcurrentExecution
 attribute, this applies to a job definition instance, not a job class instance, though it was decided to have the job class carry the
 attribute because it does often make a difference to how the class is coded (e.g. the 'statefulness' will need to be explicitly 'understood'
 by the code within the execute method).

 If you use the PersistJobDataAfterExecution attribute, you should strongly consider also using the DisallowConcurrentExecution attribute,
 in order to avoid possible confusion (race conditions) of what data was left stored when two instances of the same job (JobDetail) executed concurrently.
 */
public class ElasticJobStateful implements InterruptableJob {

    private URLConnection urlConnection;

    public ElasticJobStateful() {
    }

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        try {
            // build the list of queries
            ArrayList<String> queries = new ArrayList<>();

            JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
            String url = jobDataMap.getString("#url");
            String elasticJob = jobDataMap.getString("#elasticJobConstraints");

            int counter = 0;
            String expression = "";
            String j_tmp = "";

            JSONParser parser = new JSONParser();
            JSONObject jsonobject = (JSONObject) parser.parse(elasticJob);
            Iterator<?> keys = jsonobject.keySet().iterator();
            while (keys.hasNext()) {
                String i = (String) keys.next();
                JSONObject jsonobject2 = (JSONObject) jsonobject.get(i);
                Iterator<?> keys2 = jsonobject2.keySet().iterator();
                while (keys2.hasNext()) {
                    String j = (String) keys2.next();
                    JSONObject jsonobject3 = (JSONObject) jsonobject2.get(j);
                    String configuration = "";
                    if (jsonobject3.get("slaconfiguration") != null) {
                        configuration = (String) jsonobject3.get("slaconfiguration");
                    } else if (jsonobject3.get("bcconfiguration") != null) {
                        configuration = (String) jsonobject3.get("bcconfiguration");
                    } else if (jsonobject3.get("vmconfiguration") != null) {
                        configuration = (String) jsonobject3.get("vmconfiguration");
                    } else if (jsonobject3.get("anyconfiguration") != null) {
                        configuration = (String) jsonobject3.get("anyconfiguration");
                    }
                    // add the query to the queries list
                    queries.add(getQuery((String) jsonobject3.get("metric"),
                            (String) jsonobject3.get("cfg"),
                            configuration,
                            (String) jsonobject3.get("relation"),
                            String.valueOf(jsonobject3.get("threshold")),
                            String.valueOf(jsonobject3.get("time")),
                            (String) jsonobject3.get("timeselect")));
                    String op = jsonobject3.get("match") != null ? (String) jsonobject3.get("match") : "";
                    switch (op) {
                        case "":
                            break;
                        case "any":
                            op = "OR(";
                            break;
                        case "all":
                            op = "AND(";
                            break;
                    }
                    String closed_parenthesis = " ";
                    int num_closed_parenthesis = !j_tmp.equals("") ? Integer.parseInt(j_tmp) - Integer.parseInt(j) : 0;
                    for (int parenthesis = 0; parenthesis < num_closed_parenthesis; parenthesis++) {
                        closed_parenthesis += " )";
                    }
                    expression += op + " " + counter + closed_parenthesis;
                    j_tmp = j;
                    counter++;
                }
            }
            ExpressionTree calc = new ExpressionTree(new Scanner(expression), queries);
            if (calc.evaluate()) {
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
                //call the callUrl
                URLConnection connection = u.openConnection();
                getUrlContents(connection);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new JobExecutionException(e);
        }
    }

    @Override
    public void interrupt() throws UnableToInterruptJobException {
        ((HttpURLConnection) this.urlConnection).disconnect();
    }

    public String readURL(String url) throws JobExecutionException {
        try {
            String result = "";
            URL oracle = new URL(url);
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(oracle.openStream()))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    result += inputLine;
                }
            }
            return result;
        } catch (IOException e) {
            throw new JobExecutionException(e);
        }
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

    // args[0] = metric
    // args[1] = cfg
    // args[2] = slaconfiguration, bcconfiguration, vmconfiguration, anyconfiguration
    public static String getQuery(String... args) {
        String metric_name = args[0];
        String cfg = args[1]; //sla, bc, vm, any
        String configuration = args[2];
        String relation = args[3];
        String threshold = args[4];
        String time = args[5];
        String timeselect = args[6]; //s, min, h, day, month
        String query = "";
        switch (relation) {
            case ("ABOVE"):
                relation = ">";
                break;
            case ("BELOW"):
                relation = "<";
                break;
        }
        switch (timeselect) {
            case ("s"):
                timeselect = "SECOND";
                break;
            case ("min"):
                timeselect = "MINUTE";
                break;
            case ("h"):
                timeselect = "HOUR";
                break;
            case ("day"):
                timeselect = "DAY";
                break;
            case ("week"):
                timeselect = "WEEK";
                break;
            case ("month"):
                timeselect = "MONTH";
                break;
        }
        switch (cfg) {
            case ("sla"):
                query = "SELECT IF(AVG(value) " + relation + " "
                        + (1 + (Double.parseDouble(threshold) / 100)) + " * threshold, true, false) AS result FROM quartz.QRTZ_SPARQL WHERE sla = '" + configuration
                        + "' AND metric_name = '" + metric_name + "' AND metric_timestamp >= NOW() - INTERVAL " + time + " " + timeselect;
                break;
            case ("bc"):
                query = "SELECT IF(AVG(value) " + relation + " "
                        + (1 + (Double.parseDouble(threshold) / 100)) + " * threshold, true, false) AS result FROM quartz.QRTZ_SPARQL WHERE business_configuration = '" + configuration
                        + "' AND metric_name = '" + metric_name + "' AND metric_timestamp >= NOW() - INTERVAL " + time + " " + timeselect;
                break;
            case ("vm"):
                query = "SELECT IF(AVG(value) " + relation + " "
                        + (1 + (Double.parseDouble(threshold) / 100)) + " * threshold, true, false) AS result FROM quartz.QRTZ_SPARQL WHERE virtual_machine_name = '" + configuration
                        + "' AND metric_name = '" + metric_name + "' AND metric_timestamp >= NOW() - INTERVAL " + time + " " + timeselect;
                break;
            case ("any"):
                query = "SELECT IF(AVG(value) " + relation + " "
                        + (1 + (Double.parseDouble(threshold) / 100)) + " * threshold, true, false) AS result FROM quartz.QRTZ_SPARQL WHERE metric_name = '" + metric_name
                        + "' AND metric_timestamp >= NOW() - INTERVAL " + time + " " + timeselect;
                break;
        }
        return query;
    }
}
