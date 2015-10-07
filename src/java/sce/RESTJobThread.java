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
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.quartz.InterruptableJob;
import org.quartz.SchedulerMetaData;
import org.quartz.UnableToInterruptJobException;

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
//THIS CLASS IS EQUAL TO RESTJob, but implements the InterruptableJob as a Thread
public class RESTJobThread implements InterruptableJob {

    //http://forums.terracotta.org/forums/posts/list/4073.page
    private volatile boolean interrupted = false;
    private volatile Thread thread;
    private String result;
    private JobExecutionContext context;
    private URLConnection urlConnection;

    public RESTJobThread() {
    }

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        setContext(context); //set context

        //JobKey key = context.getJobDetail().getKey();
        //JobDataMap jobDataMap = getContext().getJobDetail().getJobDataMap();
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    URL url = new URL(getContext().getJobDetail().getJobDataMap().getString("#url"));
                    setURLConnection(url.openConnection());

                    setResult(getUrlContents(getURLConnection()));

                    //set the result to the job execution context, to be able to retrieve it later (e.g., with a job listener)
                    getContext().setResult(result);

                    //if notificationEmail is defined in the job data map, then send a notification email to it
                    if (getContext().getJobDetail().getJobDataMap().containsKey("#notificationEmail")) {
                        sendEmail(getContext(), getContext().getJobDetail().getJobDataMap().getString("#notificationEmail"));
                    }

                    //trigger the linked jobs of the finished job, depending on the job result [true, false]
                    jobChain(getContext());

                    //Mail.sendMail("prova", "disdsit@gmail.com", "prova di email", "", "");
                    System.out.println("Instance " + getContext().getJobDetail().getKey() + " of REST Job returns: " + truncateResult(result));
                } catch (MalformedURLException e) {
                } catch (IOException e) {
                }
            }
        };
        this.thread = t; //set thread
        t.start();

        while (!this.interrupted && t.isAlive()) {
            try {
                t.join();
            } catch (InterruptedException e) {
                break;
            }
        }

        if (t.isAlive()) {
            // might want to log something here since the call is still
            // running but the quartz job is finishing
        }
    }

    //http://thushw.blogspot.it/2010/10/java-urlconnection-provides-no-fail.html
    //http://alvinalexander.com/blog/post/java/java-how-read-from-url-string-text
    @Override
    public void interrupt() throws UnableToInterruptJobException {
        this.interrupted = true;
        ((HttpURLConnection) this.urlConnection).disconnect();
        this.thread.interrupt();
    }

    public String readURL(String url) {
        try {
            String res = "";
            URL oracle = new URL(url);
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(oracle.openStream()))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    res += inputLine;
                }
            }
            return res;
        } catch (IOException e) {
            return "";
        }
    }

    private String getUrlContents(URLConnection urlConnection) {
        StringBuilder content = new StringBuilder();
        try {
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
                String line;
                // read from the urlconnection via the bufferedreader
                while ((line = bufferedReader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
        } catch (IOException e) {
        }
        return content.toString();
    }

    //send a notification email upon job completion
    public void sendEmail(JobExecutionContext context, String email) {
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
                //message += "Scheduler IP: " + e.getMessage() + "\n";
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
        }
    }

    //trigger the linked jobs of the finished job, depending on the job result [true, false]
    public void jobChain(JobExecutionContext context) {
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
        }
    }

    public void setResult(String result) {
        this.result = result;
    }

    public void setContext(JobExecutionContext context) {
        this.context = context;
    }

    public JobExecutionContext getContext() {
        return this.context;
    }

    public void setURLConnection(URLConnection urlConnection) {
        this.urlConnection = urlConnection;
    }

    public URLConnection getURLConnection() {
        return this.urlConnection;
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
}
