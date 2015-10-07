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

import java.text.MessageFormat;
import org.quartz.JobExecutionContext;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.Trigger.CompletedExecutionInstruction;
import org.quartz.plugins.history.LoggingTriggerHistoryPlugin;
import java.util.Enumeration;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.SocketException;
import java.util.Properties;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.Map;
import javax.sql.DataSource;
import org.quartz.JobDataMap;
import org.quartz.spi.OperableTrigger;

public class LoggingTriggerHistoryPluginCustom extends LoggingTriggerHistoryPlugin {

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Data members.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */
    private String triggerFiredMessageNew = "Trigger [{5}.{6}] fired job [{1}.{2}] scheduled at: {7, date, dd-MM-yyyy HH:mm:ss.SSS}, next scheduled at: {8, date, dd-MM-yyyy HH:mm:ss.SSS}";

    private String triggerMisfiredMessageNew = "Trigger [{5}.{6}] misfired job [{1}.{2}]. Should have fired at: {8, date, dd-MM-yyyy HH:mm:ss.SSS}";

    private String triggerCompleteMessageNew = "Trigger [{5}.{6}] completed firing job [{1}.{2}] with resulting trigger instruction code: {10}. Next scheduled at: {8, date, dd-MM-yyyy HH:mm:ss.SSS}";

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
    public LoggingTriggerHistoryPluginCustom() {
        try {
            prop = new Properties();
            //prop.load(this.getClass().getClassLoader().getResourceAsStream("quartz.properties"));
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
     * Get the message that is printed upon the completion of a trigger's
     * firing.
     *
     * @return String
     */
    @Override
    public String getTriggerCompleteMessage() {
        return triggerCompleteMessageNew;
    }

    /**
     * Get the message that is printed upon a trigger's firing.
     *
     * @return String
     */
    @Override
    public String getTriggerFiredMessage() {
        return triggerFiredMessageNew;
    }

    /**
     * Get the message that is printed upon a trigger's mis-firing.
     *
     * @return String
     */
    @Override
    public String getTriggerMisfiredMessage() {
        return triggerMisfiredMessageNew;
    }

    /**
     * Set the message that is printed upon the completion of a trigger's
     * firing.
     *
     * @param triggerCompleteMessage String in java.text.MessageFormat syntax.
     */
    @Override
    public void setTriggerCompleteMessage(String triggerCompleteMessage) {
        this.triggerCompleteMessageNew = triggerCompleteMessage;
    }

    /**
     * Set the message that is printed upon a trigger's firing.
     *
     * @param triggerFiredMessage String in java.text.MessageFormat syntax.
     */
    @Override
    public void setTriggerFiredMessage(String triggerFiredMessage) {
        this.triggerFiredMessageNew = triggerFiredMessage;
    }

    /**
     * Set the message that is printed upon a trigger's firing.
     *
     * @param triggerMisfiredMessage String in java.text.MessageFormat syntax.
     */
    @Override
    public void setTriggerMisfiredMessage(String triggerMisfiredMessage) {
        this.triggerMisfiredMessageNew = triggerMisfiredMessage;
    }

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * TriggerListener Interface.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */
    // Called by the Scheduler when a Trigger has fired, and it's associated JobDetail is about to be executed
    @Override
    public void triggerFired(Trigger trigger, JobExecutionContext context) {
        try {
            /*if (!getLog().isInfoEnabled()) {
             return;
             }*/
            String ipAddress = getIpAddress();
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
                "FIRED",
                "LoggingTriggerHistoryPluginCustom",
                "INFO"
            };
            //getLog().info(MessageFormat.format(getTriggerFiredMessage(), args));
            logToDatabase(args, MessageFormat.format(getTriggerFiredMessage(), args), true);
            deleteTempFireInstanceId(dataSource.getConnection(), context.getFireInstanceId(), (OperableTrigger) trigger);
        } catch (SQLException | SchedulerException e) {
            e.printStackTrace();
        }
    }

    // Called by the Scheduler when a Trigger has misfired
    // A misfire occurs if a persistent trigger "misses" its 
    // firing time because of the scheduler being shutdown, or
    // because there are no available threads in Quartz's thread
    // pool for executing the job. The different trigger types have
    // different misfire instructions available to them. By default
    // they use a 'smart policy' instruction - which has dynamic
    // behavior based on trigger type and configuration. When the
    // scheduler starts, it searches for any persistent triggers
    // that have misfired, and it then updates each of them based
    // on their individually configured misfire instructions. 
    // When you start using Quartz in your own projects, you should
    // make yourself familiar with the misfire instructions that are
    // defined on the given trigger types, and explained in their JavaDoc.
    // More specific information about misfire instructions will be given
    // within the tutorial lessons specific to each trigger type.
    @Override
    public void triggerMisfired(Trigger trigger) {
        try {
            /*if (!getLog().isInfoEnabled()) {
             return;
             }*/
            Object[] args = {
                "misfired" + Long.toString(new java.util.Date().getTime()), //misfired trigger has not a fire instance id, timestamp is used
                trigger.getJobKey().getName(),
                trigger.getJobKey().getGroup(),
                jobDataMapToString(trigger.getJobDataMap()),
                new java.util.Date(),
                trigger.getKey().getName(),
                trigger.getKey().getGroup(),
                trigger.getPreviousFireTime() != null ? trigger.getPreviousFireTime() : new Date(0),
                trigger.getNextFireTime() != null ? trigger.getNextFireTime() : new Date(0),
                "0",
                "",
                "",
                "",
                "",
                "MISFIRED",
                "LoggingTriggerHistoryPluginCustom",
                "INFO"
            };
            //getLog().info(MessageFormat.format(getTriggerMisfiredMessage(), args));
            logToDatabase(args, MessageFormat.format(getTriggerMisfiredMessage(), args), true);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Called by the Scheduler when a Trigger has fired, it's associated JobDetail has been executed, and it's triggered method has been called
    @Override
    public void triggerComplete(Trigger trigger, JobExecutionContext context,
            CompletedExecutionInstruction triggerInstructionCode) {
        try {
            /*if (!getLog().isInfoEnabled()) {
             return;
             }*/
            String ipAddress = getIpAddress();
            String instrCode = "UNKNOWN";
            if (triggerInstructionCode == CompletedExecutionInstruction.DELETE_TRIGGER) {
                instrCode = "DELETE TRIGGER";
            } else if (triggerInstructionCode == CompletedExecutionInstruction.NOOP) {
                instrCode = "DO NOTHING";
            } else if (triggerInstructionCode == CompletedExecutionInstruction.RE_EXECUTE_JOB) {
                instrCode = "RE-EXECUTE JOB";
            } else if (triggerInstructionCode == CompletedExecutionInstruction.SET_ALL_JOB_TRIGGERS_COMPLETE) {
                instrCode = "SET ALL OF JOB'S TRIGGERS COMPLETE";
            } else if (triggerInstructionCode == CompletedExecutionInstruction.SET_TRIGGER_COMPLETE) {
                instrCode = "SET THIS TRIGGER COMPLETE";
            }

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
                triggerInstructionCode.toString(),
                context.getScheduler().getSchedulerInstanceId(),
                context.getScheduler().getSchedulerName(),
                ipAddress != null ? ipAddress : "",
                "COMPLETE",
                "LoggingTriggerHistoryPluginCustom",
                "INFO"
            };
            //getLog().info(MessageFormat.format(getTriggerCompleteMessage(), args));
            //log but do not update QRTZ.STATUS table with the COMPLETE status (updateStatus = false)
            logToDatabase(args, MessageFormat.format(getTriggerCompleteMessage(), args), false);
        } catch (Exception e) {
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

    public void logToDatabase(Object args[], String message, boolean updateStatus) throws SQLException {
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        PreparedStatement preparedStatementStatusUpdate = null;
        PreparedStatement preparedStatementStatusInsert = null;
        Statement stmt = null; //statement for opening TRANSACTION
        try {
            conn = Main.getConnection();
            stmt = conn.createStatement();
            stmt.executeQuery("START TRANSACTION"); //START TRANSACTION
            // conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            preparedStatement = conn.prepareStatement("INSERT INTO quartz.QRTZ_LOGS (FIRE_INSTANCE_ID, JOB_NAME, JOB_GROUP, JOB_DATA, DATE, TRIGGER_NAME, TRIGGER_GROUP, PREV_FIRE_TIME, NEXT_FIRE_TIME, REFIRE_COUNT, RESULT, SCHEDULER_INSTANCE_ID, SCHEDULER_NAME, IP_ADDRESS, STATUS, LOGGER, LEVEL, MESSAGE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            preparedStatementStatusUpdate = conn.prepareStatement("UPDATE quartz.QRTZ_STATUS SET JOB_NAME = ?, JOB_GROUP = ?, JOB_DATA = ?, DATE = ?, TRIGGER_NAME = ?, TRIGGER_GROUP = ?, PREV_FIRE_TIME = ?, NEXT_FIRE_TIME = ?, REFIRE_COUNT = ?, RESULT = ?, SCHEDULER_INSTANCE_ID = ?, SCHEDULER_NAME = ?, IP_ADDRESS = ?, STATUS = ?, LOGGER = ?, LEVEL = ?, MESSAGE = ? WHERE FIRE_INSTANCE_ID = ?");
            preparedStatementStatusInsert = conn.prepareStatement("INSERT INTO quartz.QRTZ_STATUS (FIRE_INSTANCE_ID, JOB_NAME, JOB_GROUP, JOB_DATA, DATE, TRIGGER_NAME, TRIGGER_GROUP, PREV_FIRE_TIME, NEXT_FIRE_TIME, REFIRE_COUNT, RESULT, SCHEDULER_INSTANCE_ID, SCHEDULER_NAME, IP_ADDRESS, STATUS, LOGGER, LEVEL, MESSAGE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE DATE = values(DATE), JOB_NAME = values(JOB_NAME), JOB_GROUP = values(JOB_GROUP), TRIGGER_NAME = values(TRIGGER_NAME), TRIGGER_GROUP = values(TRIGGER_GROUP), PREV_FIRE_TIME = values(PREV_FIRE_TIME), NEXT_FIRE_TIME = values(NEXT_FIRE_TIME), REFIRE_COUNT = values(REFIRE_COUNT), RESULT = values(RESULT), SCHEDULER_INSTANCE_ID = values(SCHEDULER_INSTANCE_ID), SCHEDULER_NAME = values(SCHEDULER_NAME), IP_ADDRESS = values(IP_ADDRESS), STATUS = values(STATUS), LOGGER = values(LOGGER), LEVEL = values(LEVEL), MESSAGE = values(MESSAGE)");
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof String) {
                    preparedStatement.setString(i + 1, (String) args[i]);
                    preparedStatementStatusInsert.setString(i + 1, (String) args[i]);
                } else if (args[i] instanceof Date) {
                    preparedStatement.setTimestamp(i + 1, new java.sql.Timestamp(((Date) args[i]).getTime()));
                    preparedStatementStatusInsert.setTimestamp(i + 1, new java.sql.Timestamp(((Date) args[i]).getTime()));
                }
                //shift the first args element (FIRE_INSTANCE_ID) for the preparedStatementStatusUpdate, to be set below
                if (i + 1 < args.length && args[i + 1] instanceof String) {
                    preparedStatementStatusUpdate.setString(i + 1, (String) args[i + 1]);
                } else if (i + 1 < args.length && args[i + 1] instanceof Date) {
                    preparedStatementStatusUpdate.setTimestamp(i + 1, new java.sql.Timestamp(((Date) args[i + 1]).getTime()));
                }
            }
            preparedStatementStatusUpdate.setString(args.length, message);
            preparedStatementStatusUpdate.setString(args.length + 1, (String) args[0]); //SET "WHERE FIRE_INSTANCE_ID = ?" IN THE preparedStatementStatusUpdate
            preparedStatement.setString(args.length + 1, message);
            preparedStatementStatusInsert.setString(args.length + 1, message);
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

    //delete a rejected trigger TEMP_FIRE_INSTANCE_ID from the database table QRTZ.REJECTED_TRIGGERS
    public void deleteTempFireInstanceId(Connection conn, String temp_fire_instance_id, OperableTrigger trigger) throws SQLException {
        try (PreparedStatement preparedStatement = conn.prepareStatement("DELETE FROM quartz.QRTZ_REJECTED_TRIGGERS WHERE TEMP_FIRE_INSTANCE_ID = ? AND TRIGGER_NAME = ? AND TRIGGER_GROUP = ?")) {
            preparedStatement.setString(1, temp_fire_instance_id);
            preparedStatement.setString(2, trigger.getKey().getName());
            preparedStatement.setString(3, trigger.getKey().getGroup());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLException(e);
        } finally {
            conn.close();
        }
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
