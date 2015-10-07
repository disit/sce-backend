/* 
 * Copyright 2001-2009 Terracotta, Inc. 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not 
 * use this file except in compliance with the License. You may obtain a copy 
 * of the License at 
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0 
 *   
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations 
 * under the License.
 * 
 */
/**
 *
 * @author Daniele Cenni, daniele.cenni@unifi.it
 */
package sce;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.plugins.history.LoggingJobHistoryPlugin;
import java.util.Enumeration;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.SocketException;
import java.text.MessageFormat;
import java.util.Properties;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.quartz.JobDataMap;

public class LoggingJobHistoryPluginCustom extends LoggingJobHistoryPlugin {

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Data members.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */
    private String jobToBeFiredMessageNew = "Job {1}.{2} fired (by trigger {5}.{6}) at: {4, date, dd-MM-yyyy HH:mm:ss.SSS}, re-fire: {9}";

    private String jobSuccessMessageNew = "Job {1}.{2} execution complete at {4, date, dd-MM-yyyy HH:mm:ss.SSS} and reports: {10}";

    private String jobFailedMessageNew = "Job {1}.{2} execution failed at {4, date, dd-MM-yyyy HH:mm:ss.SSS} and reports: {10}";

    private String jobWasVetoedMessageNew = "Job {1}.{2} was vetoed.  It was to be fired (by trigger {5}.{6}) at: {4, date, dd-MM-yyyy HH:mm:ss.SSS}";

    private static String ip = null;

    private static Properties prop = null;

    private static ConnectionPool connectionPool;

    private static DataSource dataSource;

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Constructors.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */
    public LoggingJobHistoryPluginCustom() {
        try {
            prop = new Properties();
            prop.load(this.getClass().getResourceAsStream("quartz.properties"));
            connectionPool = new ConnectionPool(prop.getProperty("org.quartz.dataSource.quartzDataSource.URL"), prop.getProperty("org.quartz.dataSource.quartzDataSource.user"), prop.getProperty("org.quartz.dataSource.quartzDataSource.password"));
            dataSource = connectionPool.setUp();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start() {
        // do nothing...
    }

    @Override
    public void shutdown() {
        try {
            connectionPool.getConnectionPool().close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the message that is logged when a Job successfully completes its
     * execution.
     *
     * @return
     */
    @Override
    public String getJobSuccessMessage() {
        return jobSuccessMessageNew;
    }

    /**
     * Get the message that is logged when a Job fails its execution.
     *
     * @return
     */
    @Override
    public String getJobFailedMessage() {
        return jobFailedMessageNew;
    }

    /**
     * Get the message that is logged when a Job is about to execute.
     *
     * @return
     */
    @Override
    public String getJobToBeFiredMessage() {
        return jobToBeFiredMessageNew;
    }

    /**
     * Set the message that is logged when a Job successfully completes its
     * execution.
     *
     * @param jobSuccessMessage String in java.text.MessageFormat syntax.
     */
    @Override
    public void setJobSuccessMessage(String jobSuccessMessage) {
        this.jobSuccessMessageNew = jobSuccessMessage;
    }

    /**
     * Set the message that is logged when a Job fails its execution.
     *
     * @param jobFailedMessage String in java.text.MessageFormat syntax.
     */
    @Override
    public void setJobFailedMessage(String jobFailedMessage) {
        this.jobFailedMessageNew = jobFailedMessage;
    }

    /**
     * Set the message that is logged when a Job is about to execute.
     *
     * @param jobToBeFiredMessage String in java.text.MessageFormat syntax.
     */
    @Override
    public void setJobToBeFiredMessage(String jobToBeFiredMessage) {
        this.jobToBeFiredMessageNew = jobToBeFiredMessage;
    }

    /**
     * Get the message that is logged when a Job execution is vetoed by a
     * trigger listener.
     *
     * @return
     */
    @Override
    public String getJobWasVetoedMessage() {
        return jobWasVetoedMessageNew;
    }

    /**
     * Set the message that is logged when a Job execution is vetoed by a
     * trigger listener.
     *
     * @param jobWasVetoedMessage String in java.text.MessageFormat syntax.
     */
    @Override
    public void setJobWasVetoedMessage(String jobWasVetoedMessage) {
        this.jobWasVetoedMessageNew = jobWasVetoedMessage;
    }

    /**
     * @param context
     * @see org.quartz.JobListener#jobToBeExecuted(JobExecutionContext) Called
     * by the Scheduler when a JobDetail is about to be executed (an associated
     * Trigger has occurred)
     */
    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        try {
            /*if (!getLog().isInfoEnabled()) {
             return;
             }*/
            String ipAddress = getIpAddress();
            Trigger trigger = context.getTrigger();

            Object[] args = {
                context.getFireInstanceId(),
                context.getJobDetail().getKey().getName(),
                context.getJobDetail().getKey().getGroup(),
                jobDataMapToString(context.getJobDetail().getJobDataMap()),
                new java.util.Date(),
                trigger.getKey().getName(),
                trigger.getKey().getGroup(),
                trigger.getPreviousFireTime() != null ? trigger.getPreviousFireTime() : new Date(0),
                trigger.getNextFireTime() != null ? trigger.getNextFireTime() : new Date(0),
                Integer.toString(context.getRefireCount()),
                "",
                context.getScheduler().getSchedulerInstanceId(),
                context.getScheduler().getSchedulerName(),
                ipAddress != null ? ipAddress : "",
                "RUNNING",
                "LoggingJobHistoryPluginCustom",
                "INFO"
            };

            // update the job data map of the job to be executed with the fire instance id (to be used to retrieve the running job for setting/getting the job progress %)
            // the job data map is not updated on the scheduler, but only on the running instance of the job, because the id is related to it
            /*JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
            JSONArray jsonarray = null;
            JSONParser parser = new JSONParser();
            if (jobDataMap.containsKey("#processParameters")) {
                jsonarray = (JSONArray) parser.parse(jobDataMap.getString("#processParameters"));
            } else {
                jsonarray = new JSONArray();
            }
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("fireInstanceId", context.getFireInstanceId());
            jsonarray.add(jsonObject);
            context.getJobDetail().getJobDataMap().put("#processParameters", jsonarray.toString());*/

            //getLog().info(MessageFormat.format(getJobToBeFiredMessage(), args));
            //log data and set progress value as null because it has not to be updated in the database row
            logToDatabase(args, MessageFormat.format(getJobToBeFiredMessage(), args), true, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @param context
     * @param jobException
     * @see org.quartz.JobListener#jobWasExecuted(JobExecutionContext,
     * JobExecutionException) Called by the Scheduler after a JobDetail has been
     * executed, and be for the associated Trigger's triggered method has been
     * called
     */
    @Override
    public void jobWasExecuted(JobExecutionContext context,
            JobExecutionException jobException) {
        try {
            String ipAddress = getIpAddress();
            Trigger trigger = context.getTrigger();

            Object[] args;

            if (jobException != null) {
                /*if (!getLog().isWarnEnabled()) {
                 return;
                 }*/

                String errMsg = jobException.getMessage();
                args = new Object[]{
                    context.getFireInstanceId(),
                    context.getJobDetail().getKey().getName(),
                    context.getJobDetail().getKey().getGroup(),
                    jobDataMapToString(context.getJobDetail().getJobDataMap()),
                    new java.util.Date(),
                    trigger.getKey().getName(),
                    trigger.getKey().getGroup(),
                    trigger.getPreviousFireTime() != null ? trigger.getPreviousFireTime() : new Date(0),
                    trigger.getNextFireTime() != null ? trigger.getNextFireTime() : new Date(0),
                    Integer.toString(context.getRefireCount()),
                    errMsg,
                    context.getScheduler().getSchedulerInstanceId(),
                    context.getScheduler().getSchedulerName(),
                    ipAddress != null ? ipAddress : "",
                    "FAILED",
                    "LoggingJobHistoryPluginCustom",
                    "INFO"
                };
                //getLog().warn(MessageFormat.format(getJobFailedMessage(), args), jobException);
                //log data and set progress value as null because it has not to be updated in the database row
                logToDatabase(args, MessageFormat.format(getJobFailedMessage(), args), true, null);
            } else {
                /*if (!getLog().isInfoEnabled()) {
                 return;
                 }*/

                String result = String.valueOf(context.getResult());
                args = new Object[]{
                    context.getFireInstanceId(),
                    context.getJobDetail().getKey().getName(),
                    context.getJobDetail().getKey().getGroup(),
                    jobDataMapToString(context.getJobDetail().getJobDataMap()),
                    new java.util.Date(),
                    trigger.getKey().getName(),
                    trigger.getKey().getGroup(),
                    trigger.getPreviousFireTime() != null ? trigger.getPreviousFireTime() : new Date(0),
                    trigger.getNextFireTime() != null ? trigger.getNextFireTime() : new Date(0),
                    Integer.toString(context.getRefireCount()),
                    result, // truncateResult(result) truncate result if too long to insert in the database
                    context.getScheduler().getSchedulerInstanceId(),
                    context.getScheduler().getSchedulerName(),
                    ipAddress != null ? ipAddress : "",
                    "SUCCESS",
                    "LoggingJobHistoryPluginCustom",
                    "INFO"
                };
                //getLog().info(MessageFormat.format(getJobSuccessMessage(), args));
                //log data and set progress value as 100 because the job completed successfully
                logToDatabase(args, MessageFormat.format(getJobSuccessMessage(), args), true, "100");
            }
        } catch (SQLException | SchedulerException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param context
     * @see
     * org.quartz.JobListener#jobExecutionVetoed(org.quartz.JobExecutionContext)
     * Called by the Scheduler when a JobDetail was about to be executed (an
     * associated Trigger has occurred), but a TriggerListener vetoed it's
     * execution
     */
    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        try {
            /*if (!getLog().isInfoEnabled()) {
             return;
             }*/
            String ipAddress = getIpAddress();
            Trigger trigger = context.getTrigger();

            Object[] args = {
                context.getFireInstanceId(),
                context.getJobDetail().getKey().getName(),
                context.getJobDetail().getKey().getGroup(),
                jobDataMapToString(context.getJobDetail().getJobDataMap()),
                new java.util.Date(),
                trigger.getKey().getName(),
                trigger.getKey().getGroup(),
                trigger.getPreviousFireTime() != null ? trigger.getPreviousFireTime() : new Date(0),
                trigger.getNextFireTime() != null ? trigger.getNextFireTime() : new Date(0),
                Integer.toString(context.getRefireCount()),
                "",
                context.getScheduler().getSchedulerInstanceId(),
                context.getScheduler().getSchedulerName(),
                ipAddress != null ? ipAddress : "",
                "VETOED",
                "LoggingJobHistoryPluginCustom",
                "INFO"
            };
            //getLog().info(MessageFormat.format(getJobWasVetoedMessage(), args));
            //log data and set progress value as null because it has not to be updated in the database row
            logToDatabase(args, MessageFormat.format(getJobWasVetoedMessage(), args), true, null);
        } catch (SchedulerException | SQLException e) {
            e.printStackTrace();
        }
    }

    //get the ipv4 addresses, semicolon separated, of the current network interface (excluding 127.0.0.1)
    public static String getIpAddress() {
        if (ip == null) {
            try {
                for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                    NetworkInterface intf = (NetworkInterface) en.nextElement();
                    for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                        InetAddress inetAddress = (InetAddress) enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                            String ipAddress = inetAddress.getHostAddress().toString();
                            ip = (ip == null ? "" : ip + ";") + ipAddress;
                        }
                    }
                }
            } catch (SocketException e) {
                return "";
            }
        }
        return ip;
    }

    public void logToDatabase(Object args[], String message, boolean updateStatus, String progress) throws SQLException {
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        PreparedStatement preparedStatementStatusUpdate = null;
        PreparedStatement preparedStatementStatusInsert = null;
        Statement stmt = null; //statement for opening TRANSACTION
        try {
            conn = dataSource.getConnection();
            stmt = conn.createStatement();
            stmt.executeQuery("START TRANSACTION"); //START TRANSACTION
            // conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);

            //if progress value is not null, then include it in the insert/update query
            if (progress != null) {
                preparedStatement = conn.prepareStatement("INSERT INTO quartz.QRTZ_LOGS (FIRE_INSTANCE_ID, JOB_NAME, JOB_GROUP, JOB_DATA, DATE, TRIGGER_NAME, TRIGGER_GROUP, PREV_FIRE_TIME, NEXT_FIRE_TIME, REFIRE_COUNT, RESULT, SCHEDULER_INSTANCE_ID, SCHEDULER_NAME, IP_ADDRESS, STATUS, LOGGER, LEVEL, MESSAGE, PROGRESS) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                preparedStatementStatusUpdate = conn.prepareStatement("UPDATE quartz.QRTZ_STATUS SET JOB_NAME = ?, JOB_GROUP = ?, JOB_DATA = ?, DATE = ?, TRIGGER_NAME = ?, TRIGGER_GROUP = ?, PREV_FIRE_TIME = ?, NEXT_FIRE_TIME = ?, REFIRE_COUNT = ?, RESULT = ?, SCHEDULER_INSTANCE_ID = ?, SCHEDULER_NAME = ?, IP_ADDRESS = ?, STATUS = ?, LOGGER = ?, LEVEL = ?, MESSAGE = ?, PROGRESS = ? WHERE FIRE_INSTANCE_ID = ?");
                preparedStatementStatusInsert = conn.prepareStatement("INSERT INTO quartz.QRTZ_STATUS (FIRE_INSTANCE_ID, JOB_NAME, JOB_GROUP, JOB_DATA, DATE, TRIGGER_NAME, TRIGGER_GROUP, PREV_FIRE_TIME, NEXT_FIRE_TIME, REFIRE_COUNT, RESULT, SCHEDULER_INSTANCE_ID, SCHEDULER_NAME, IP_ADDRESS, STATUS, LOGGER, LEVEL, MESSAGE, PROGRESS) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE DATE = values(DATE), JOB_NAME = values(JOB_NAME), JOB_GROUP = values(JOB_GROUP), JOB_DATA = values(JOB_DATA), TRIGGER_NAME = values(TRIGGER_NAME), TRIGGER_GROUP = values(TRIGGER_GROUP), PREV_FIRE_TIME = values(PREV_FIRE_TIME), NEXT_FIRE_TIME = values(NEXT_FIRE_TIME), REFIRE_COUNT = values(REFIRE_COUNT), RESULT = values(RESULT), SCHEDULER_INSTANCE_ID = values(SCHEDULER_INSTANCE_ID), SCHEDULER_NAME = values(SCHEDULER_NAME), IP_ADDRESS = values(IP_ADDRESS), STATUS = values(STATUS), LOGGER = values(LOGGER), LEVEL = values(LEVEL), MESSAGE = values(MESSAGE), PROGRESS = values(PROGRESS)");
            } else {
                preparedStatement = conn.prepareStatement("INSERT INTO quartz.QRTZ_LOGS (FIRE_INSTANCE_ID, JOB_NAME, JOB_GROUP, JOB_DATA, DATE, TRIGGER_NAME, TRIGGER_GROUP, PREV_FIRE_TIME, NEXT_FIRE_TIME, REFIRE_COUNT, RESULT, SCHEDULER_INSTANCE_ID, SCHEDULER_NAME, IP_ADDRESS, STATUS, LOGGER, LEVEL, MESSAGE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                preparedStatementStatusUpdate = conn.prepareStatement("UPDATE quartz.QRTZ_STATUS SET JOB_NAME = ?, JOB_GROUP = ?, JOB_DATA = ?, DATE = ?, TRIGGER_NAME = ?, TRIGGER_GROUP = ?, PREV_FIRE_TIME = ?, NEXT_FIRE_TIME = ?, REFIRE_COUNT = ?, RESULT = ?, SCHEDULER_INSTANCE_ID = ?, SCHEDULER_NAME = ?, IP_ADDRESS = ?, STATUS = ?, LOGGER = ?, LEVEL = ?, MESSAGE = ? WHERE FIRE_INSTANCE_ID = ?");
                preparedStatementStatusInsert = conn.prepareStatement("INSERT INTO quartz.QRTZ_STATUS (FIRE_INSTANCE_ID, JOB_NAME, JOB_GROUP, JOB_DATA, DATE, TRIGGER_NAME, TRIGGER_GROUP, PREV_FIRE_TIME, NEXT_FIRE_TIME, REFIRE_COUNT, RESULT, SCHEDULER_INSTANCE_ID, SCHEDULER_NAME, IP_ADDRESS, STATUS, LOGGER, LEVEL, MESSAGE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE DATE = values(DATE), JOB_NAME = values(JOB_NAME), JOB_GROUP = values(JOB_GROUP), JOB_DATA = values(JOB_DATA), TRIGGER_NAME = values(TRIGGER_NAME), TRIGGER_GROUP = values(TRIGGER_GROUP), PREV_FIRE_TIME = values(PREV_FIRE_TIME), NEXT_FIRE_TIME = values(NEXT_FIRE_TIME), REFIRE_COUNT = values(REFIRE_COUNT), RESULT = values(RESULT), SCHEDULER_INSTANCE_ID = values(SCHEDULER_INSTANCE_ID), SCHEDULER_NAME = values(SCHEDULER_NAME), IP_ADDRESS = values(IP_ADDRESS), STATUS = values(STATUS), LOGGER = values(LOGGER), LEVEL = values(LEVEL), MESSAGE = values(MESSAGE)");
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof String) {
                    preparedStatement.setString(i + 1, (String) args[i]);
                    preparedStatementStatusInsert.setString(i + 1, (String) args[i]);
                } else if (args[i] instanceof Date) {
                    preparedStatement.setTimestamp(i + 1, new java.sql.Timestamp(((Date) args[i]).getTime()));
                    preparedStatementStatusInsert.setTimestamp(i + 1, new java.sql.Timestamp(((Date) args[i]).getTime()));
                }
                //skip the first args element (FIRE_INSTANCE_ID) for the preparedStatementStatusUpdate, to be set below
                if (i + 1 < args.length && args[i + 1] instanceof String) {
                    preparedStatementStatusUpdate.setString(i + 1, (String) args[i + 1]);
                } else if (i + 1 < args.length && args[i + 1] instanceof Date) {
                    preparedStatementStatusUpdate.setTimestamp(i + 1, new java.sql.Timestamp(((Date) args[i + 1]).getTime()));
                }
            }
            preparedStatementStatusUpdate.setString(args.length, message);
            if (progress != null) {
                preparedStatementStatusUpdate.setString(args.length + 1, progress);
                preparedStatementStatusUpdate.setString(args.length + 2, (String) args[0]); //SET "WHERE FIRE_INSTANCE_ID = ?" IN THE preparedStatementStatusUpdate
                
                preparedStatement.setString(args.length + 1, message);
                preparedStatement.setString(args.length + 2, progress);

                preparedStatementStatusInsert.setString(args.length + 1, message);
                preparedStatementStatusInsert.setString(args.length + 2, progress);
            } else {
                preparedStatementStatusUpdate.setString(args.length + 1, (String) args[0]); //SET "WHERE FIRE_INSTANCE_ID = ?" IN THE preparedStatementStatusUpdate
                preparedStatement.setString(args.length + 1, message);
                preparedStatementStatusInsert.setString(args.length + 1, message);
            }

            preparedStatement.executeUpdate();
            if (updateStatus) {
                int rows = preparedStatementStatusUpdate.executeUpdate(); //UPDATE
                //IF UPDATE ON QRTZ_STATUS RETURNS 0 ROWS THEN INSERT
                if (rows == 0) {
                    preparedStatementStatusInsert.executeUpdate(); //INSERT
                }
            }
            stmt.executeQuery("COMMIT"); //COMMIT TRANSACTION
        } catch (SQLException e) {
            if (stmt != null) {
                stmt.executeQuery("ROLLBACK"); //ROLLBACK TRANSACTION
            }
            e.printStackTrace();
            throw new SQLException(e);
        } finally {
            if (preparedStatement != null) {
                preparedStatement.close();
            }
            if (preparedStatementStatusUpdate != null) {
                preparedStatementStatusUpdate.close();
            }
            if (preparedStatementStatusInsert != null) {
                preparedStatementStatusInsert.close();
            }
            if (!conn.isClosed()) {
                conn.close();
            }
        }
    }

    //return a truncated result if too long for displaying
    public String truncateResult(String result) {
        if (result != null) {
            return result.length() > 30 ? result.substring(0, 29) + "..." : result;
        }
        return result;
    }

    //convert a job data map to string
    public String jobDataMapToString(JobDataMap jobDataMap) {
        String result = "";
        for (Map.Entry<String, Object> entry : jobDataMap.entrySet()) {
            result += entry.getKey() + "=" + entry.getValue() + ";\n";
        }
        return result;
    }
}

// EOF
