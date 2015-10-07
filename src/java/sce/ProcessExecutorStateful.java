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

import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.quartz.Job;
import org.quartz.InterruptableJob;
import org.quartz.UnableToInterruptJobException;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.PersistJobDataAfterExecution;
import org.quartz.SchedulerException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.quartz.SchedulerMetaData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
//import org.apache.catalina.tribes.util.Arrays;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.commons.exec.environment.EnvironmentUtils;

@DisallowConcurrentExecution
/*@DisallowConcurrentExecution
 * An annotation that marks a Job
 * class as one that must not have
 * multiple instances executed concurrently
 * (where instance is based-upon a JobDetail 
 * definition - or in other words based upon a JobKey). 
 */

@PersistJobDataAfterExecution
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
public class ProcessExecutorStateful implements Job, InterruptableJob {

    ExecuteWatchdog watchdog;
    long timeout;
    private String environment;
    private String result;

    public ProcessExecutorStateful() {
    }

    @Override
    public void execute(JobExecutionContext context)
            throws JobExecutionException {
        try {
            JobKey key = context.getJobDetail().getKey();

            JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();

            //set job execution timeout in seconds
            if (jobDataMap.containsKey("#jobTimeout")) {
                this.timeout = Long.parseLong(jobDataMap.getString("#jobTimeout"));
            } //default job execution timeout in seconds (0 means to wait forever)
            else {
                this.timeout = 0;
            }

            //set java environment variables
            if (jobDataMap.containsKey("#environment")) {
                this.environment = jobDataMap.getString("#environment");
            } else {
                this.environment = "";
            }

            //json has the form of an array, whose elements are associative arrays (i.e., 0->("processPath"->"/var/www/html/processBinary"), 1->("parameter1"->"value1")...)
            if (jobDataMap.containsKey("#processParameters")) {
                //read json from request
                JSONParser parser = new JSONParser();
                JSONArray jsonarray = (JSONArray) parser.parse(jobDataMap.getString("#processParameters"));
                int size = jsonarray.size();
                String[] processParameters = null;
                boolean script = false; //set to true if the process is a sh script

                for (int i = 0; i < size; i++) {
                    JSONObject jsonobject = (JSONObject) jsonarray.get(i);
                    Iterator<?> keys = jsonobject.keySet().iterator();
                    while (keys.hasNext()) {
                        String k = (String) keys.next();
                        //this condition is true only for the first element of the jsonobject (i.e., processParameters is istantiated once)
                        if (k.equals("processPath")) {
                            String processPath = (String) jsonobject.get("processPath");
                            if (processPath.endsWith(".sh")) {
                                script = true;
                                processParameters = new String[size + 1];
                                processParameters[i] = "/bin/sh";
                            } else {
                                processParameters = new String[size];
                            }
                        }
                        if (script) {
                            processParameters[i + 1] = (String) jsonobject.get(k);
                        } else {
                            processParameters[i] = (String) jsonobject.get(k);
                        }
                    }
                }

                String r = executeProcess(processParameters);

                //set the result to the job execution context, to be able to retrieve it later (e.g., with a job listener)
                context.setResult(r);

                //if notificationEmail is defined in the job data map, then send a notification email to it
                if (jobDataMap.containsKey("#notificationEmail")) {
                    sendEmail(context, jobDataMap.getString("#notificationEmail"));
                }

                //trigger the linked jobs of the finished job, depending on the job result [true, false]
                jobChain(context);

                //System.out.println("Instance " + key + " of REST Job returns: " + truncateResult(r));
            } else {
                //System.out.println("Instance " + key + " of ProcessExecutor Job returns: process not found");
            }
        } catch (NumberFormatException | ParseException | JobExecutionException e) {
            throw new JobExecutionException(e.getMessage(), e);
        }
    }

    @Override
    public void interrupt() throws UnableToInterruptJobException {
        try {
            if (this.watchdog != null) {
                this.watchdog.destroyProcess();
            }
        } catch (IllegalThreadStateException e) {
            throw new UnableToInterruptJobException(e);
        }
    }

    // use apache commons 1.3
    // https://commons.apache.org/proper/commons-exec/tutorial.html
    public String executeProcess(String[] processParameters) throws JobExecutionException {
        try {
            //Command to be executed
            CommandLine command = new CommandLine(processParameters[0]);

            String[] params = new String[processParameters.length - 1];
            for (int i = 0; i < processParameters.length - 1; i++) {
                params[i] = processParameters[i + 1];
            }

            //Adding its arguments
            command.addArguments(params);

            //set timeout in seconds
            ExecuteWatchdog watchDog = new ExecuteWatchdog(this.timeout == 0 ? ExecuteWatchdog.INFINITE_TIMEOUT : this.timeout * 1000);
            this.watchdog = watchDog;

            //Result Handler for executing the process in a Asynch way
            DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
            //MyResultHandler resultHandler = new MyResultHandler();

            //Using Std out for the output/error stream
            //ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            //PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
            //This is used to end the process when the JVM exits
            ShutdownHookProcessDestroyer processDestroyer = new ShutdownHookProcessDestroyer();

            //Our main command executor
            DefaultExecutor executor = new DefaultExecutor();

            //Setting the properties
            executor.setStreamHandler(new PumpStreamHandler(null, null));
            executor.setWatchdog(watchDog);
            //executor.setExitValue(1); // this has to be set if the java code contains System.exit(1) to avoid a FAILED status

            //Setting the working directory
            //Use of recursion along with the ls makes this a long running process
            //executor.setWorkingDirectory(new File("/home"));
            executor.setProcessDestroyer(processDestroyer);

            //if set, use the java environment variables when running the command
            if (!this.environment.equals("")) {
                Map<String, String> procEnv = EnvironmentUtils.getProcEnvironment();
                EnvironmentUtils.addVariableToEnvironment(procEnv, this.environment);
                //Executing the command
                executor.execute(command, procEnv, resultHandler);
            } else {
                //Executing the command
                executor.execute(command, resultHandler);
            }

            //The below section depends on your need
            //Anything after this will be executed only when the command completes the execution
            resultHandler.waitFor();

            /*int exitValue = resultHandler.getExitValue();
             System.out.println(exitValue);
             if (executor.isFailure(exitValue)) {
             System.out.println("Execution failed");
             } else {
             System.out.println("Execution Successful");
             }
             System.out.println(outputStream.toString());*/
            //return outputStream.toString();
            if (watchdog.killedProcess()) {
                throw new JobExecutionException("Job Interrupted", new InterruptedException());
            }
            if (executor.isFailure(resultHandler.getExitValue())) {
                ExecuteException ex = resultHandler.getException();
                throw new JobExecutionException(ex.getMessage(), ex);
            }
            return "1";
        } catch (ExecuteException ex) {
            throw new JobExecutionException(ex.getMessage(), ex);
        } catch (IOException | InterruptedException | JobExecutionException ex) {
            throw new JobExecutionException(ex.getMessage(), ex);
        }
    }
    //this method does not implement a timeout for the process execution and does not require Java 8
    /*public String executeProcess(String[] processParameters) throws JobExecutionException {
     //String[] params = new String[3];
     //params[0] = "D:\\prog.exe";
     //params[1] = picA + ".jpg";
     //params[2] = picB + ".jpg";
     try {
     final Process p = new ProcessBuilder(processParameters).start();
     ThreadCustom thread = new ThreadCustom() {
     @Override
     public void run() {
     String line;
     String r = "";
     try {
     try (BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
     while ((line = input.readLine()) != null) {
     //System.out.println(line);
     r += line;
     }
     }
     setResult(r); //set the result
     } catch (IOException e) {
     setResult("Process not read: " + e);
     setException(e); //set the exception to be thrown below
     }
     }
     };
     //set the new process
     this.process = p;

     thread.start();
     int res = p.waitFor();
     thread.join();
     if (res != 0) {
     setResult("Process failed with status: " + res);

     int len;
     if ((len = p.getErrorStream().available()) > 0) {
     byte[] buf = new byte[len];
     p.getErrorStream().read(buf);
     System.err.println("Command error:\t\"" + new String(buf) + "\"");
     }
     }
     //return "Process started with status: " + result;
     if (thread.getException() != null) {
     throw new JobExecutionException(thread.getException());
     }
     return getResult();
     } catch (IOException | InterruptedException e) {
     //e.printStackTrace();
     //return e.getMessage();
     throw new JobExecutionException(e);
     }
     }*/
    //this method (with the class CallableProcess) implements a timeout for the process execution and does not require Java 8
    //http://bryox.blogspot.it/2011/12/execute-command-line-process-from-java.html
    /*public String executeProcess(String[] processParameters) throws JobExecutionException {
     ExecutorService service = Executors.newSingleThreadExecutor();
     ThreadCustom thread = null;
     try {
     final Process p = new ProcessBuilder(processParameters).start();
     thread = new ThreadCustom() {
     @Override
     public void run() {
     String line;
     String r = "";
     try {
     try (BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
     while ((line = input.readLine()) != null) {
     r += line;
     setResult(r);
     }
     }
     //setResult(r); //set the result
     } catch (IOException e) {
     setResult("Process not read: " + e);
     setException(e); //set the exception to be thrown below
     }
     }
     };
     //set the new process
     this.process = p;

     thread.start();
     //thread.wait();
     thread.join(this.timeout * 1000L); //a timeout of 0 means to wait forever 

     Callable<Integer> call = new CallableProcess(this.process);
     Future<Integer> future = service.submit(call);
     int exitValue = future.get(this.timeout, TimeUnit.SECONDS);
     if (exitValue != 0) {
     thread.interrupt();
     throw new JobExecutionException("Process did not exit correctly");
     }
     } catch (ExecutionException e) {
     if (thread != null && thread.isAlive()) {
     thread.interrupt();
     }
     throw new JobExecutionException("Process failed to execute", e);
     } catch (TimeoutException e) {
     if (this.process != null) {
     this.process.destroy();
     }
     if (thread != null && thread.isAlive()) {
     thread.interrupt();
     }
     throw new JobExecutionException("Process timed out", e);
     } catch (IOException | InterruptedException e) {
     if (thread != null && thread.isAlive()) {
     thread.interrupt();
     }
     throw new JobExecutionException("Process interrupted", e);
     } finally {
     service.shutdown();
     }
     if (thread != null && thread.isAlive()) {
     thread.interrupt();
     }
     //System.out.println("Thread status: " + (thread != null ? thread.getState().toString() : ""));
     return getResult();
     }

     private static class CallableProcess implements Callable {

     private final Process p;

     public CallableProcess(Process process) {
     p = process;
     }

     @Override
     public Integer call() throws Exception {
     return p.waitFor();
     }
     }*/

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

    public String getResult() {
        return this.result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    //return a truncated result if too long for displaying
    public String truncateResult(String result) {
        if (result != null) {
            return result.length() > 30 ? result.substring(0, 29) + "..." : result;
        }
        return result;
    }

    /**
     * File Permissions using File and PosixFilePermission
     *
     * @throws IOException
     */
    public void setFilePermissions() throws IOException {
        File file = new File("/Users/temp.txt");

        //set application user permissions to 455
        file.setExecutable(false);
        file.setReadable(false);
        file.setWritable(true);

        //change permission to 777 for all the users
        //no option for group and others
        file.setExecutable(true, false);
        file.setReadable(true, false);
        file.setWritable(true, false);

        //using PosixFilePermission to set file permissions 777
        Set<PosixFilePermission> perms = new HashSet<>();
        //add owners permission
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        //add group permissions
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.GROUP_WRITE);
        perms.add(PosixFilePermission.GROUP_EXECUTE);
        //add others permissions
        perms.add(PosixFilePermission.OTHERS_READ);
        perms.add(PosixFilePermission.OTHERS_WRITE);
        perms.add(PosixFilePermission.OTHERS_EXECUTE);

        Files.setPosixFilePermissions(Paths.get("/Users/pankaj/run.sh"), perms);
    }
}
