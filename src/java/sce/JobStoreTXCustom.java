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

import java.io.IOException;
import org.quartz.impl.jdbcjobstore.JobStoreTX;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.JobPersistenceException;
import org.quartz.TriggerKey;
import org.quartz.spi.OperableTrigger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author Daniele Cenni, daniele.cenni@unifi.it
 */
public class JobStoreTXCustom extends JobStoreTX {

    private final String triggerRejectedMessage = "Trigger [{4}.{5}] of job [{1}.{2}] scheduled at: {6, date, dd-MM-yyyy HH:mm:ss.SSS} was rejected by IP {12}";
    private static String ip = null;

    @Override
    // FUTURE_TODO: this really ought to return something like a FiredTriggerBundle,
    // so that the fireInstanceId doesn't have to be on the trigger...
    protected List<OperableTrigger> acquireNextTrigger(Connection conn, long noLaterThan, int maxCount, long timeWindow)
            throws JobPersistenceException {
        if (timeWindow < 0) {
            throw new IllegalArgumentException();
        }

        List<OperableTrigger> acquiredTriggers = new ArrayList<>();
        Set<JobKey> acquiredJobKeysForNoConcurrentExec = new HashSet<>();
        final int MAX_DO_LOOP_RETRY = 3;
        int currentLoopCount = 0;
        long firstAcquiredTriggerFireTime = 0;

        do {
            currentLoopCount++;
            try {
                List<TriggerKey> keys = getDelegate().selectTriggerToAcquire(conn, noLaterThan + timeWindow, getMisfireTime(), maxCount);

                // No trigger is ready to fire yet.
                if (keys == null || keys.isEmpty()) {
                    return acquiredTriggers;
                }

                for (TriggerKey triggerKey : keys) {
                    // If our trigger is no longer available, try a new one.
                    OperableTrigger nextTrigger = retrieveTrigger(conn, triggerKey);
                    if (nextTrigger == null) {
                        continue; // next trigger
                    }

                    // If trigger's job is set as @DisallowConcurrentExecution, and it has already been added to result, then
                    // put it back into the timeTriggers set and continue to search for next trigger.
                    JobKey jobKey = nextTrigger.getJobKey();

                    JobDetail job = getDelegate().selectJobDetail(conn, jobKey, getClassLoadHelper());

                    //****************UPDATE****************
                    //if constraints are not satisfied, then skip to the next trigger
                    if (!checkConstraints(job.getJobDataMap())) {
                        //see row 2346 on JobStoreSupport.java for code misfire
                        //check if the trigger has misfired, in that case set the trigger as misfired and schedule it for next firing
                        /*TriggerStatus status = getDelegate().selectTriggerStatus(conn, triggerKey);
                         String newState = checkBlockedState(conn, status.getJobKey(), STATE_WAITING);
                         if (status.getNextFireTime().before(new Date())) {
                         updateMisfiredTrigger(conn, triggerKey, newState, true);
                         }*/
                        //set a temp fireinstanceid even if this trigger was not acquired
                        String tempFireInstanceId = getTempFireInstanceId(conn, nextTrigger);
                        if (tempFireInstanceId == null) {
                            tempFireInstanceId = getFiredTriggerRecordId();
                            setTempFireInstanceId(conn, tempFireInstanceId, nextTrigger);
                            //nextTrigger.setPriority(10); //set priority to 10 and update trigger with it
                            //this.getDelegate().updateTrigger(conn, nextTrigger, STATE_WAITING, null);
                        }

                        //log to database that this trigger is rejected because of physical constraints not satisfied
                        String ipAddress = getIpAddress();
                        Object[] args = {
                            //"ignored" + Long.toString(new java.util.Date().getTime()), //misfired trigger has not a fire instance id, timestamp is used
                            tempFireInstanceId,
                            nextTrigger.getJobKey().getName(),
                            nextTrigger.getJobKey().getGroup(),
                            jobDataMapToString(nextTrigger.getJobDataMap()),
                            new java.util.Date(),
                            nextTrigger.getKey().getName(),
                            nextTrigger.getKey().getGroup(),
                            nextTrigger.getPreviousFireTime() != null ? nextTrigger.getPreviousFireTime() : new Date(0),
                            nextTrigger.getNextFireTime() != null ? nextTrigger.getNextFireTime() : new Date(0),
                            "0",
                            "",
                            "",
                            "",
                            ipAddress != null ? ipAddress : "",
                            "TO_BE_EXECUTED", //this is not TO_BE_EXECUTED in the Quartz naming convention used for jobs, but it means that the trigger has been rejected by this scheduler because of job constraint(s) not satisfied, and has to be executed as soon as possible
                            "LoggingTriggerHistoryPluginCustom",
                            "INFO"
                        };
                        logToDatabase(conn, args, MessageFormat.format(getTriggerRejectedMessage(), args), true);
                        continue;
                    }
                    //**************************************

                    if (job.isConcurrentExectionDisallowed()) {
                        if (acquiredJobKeysForNoConcurrentExec.contains(jobKey)) {
                            continue; // next trigger
                        } else {
                            acquiredJobKeysForNoConcurrentExec.add(jobKey);
                        }
                    }

                    // We now have an acquired trigger, let's add to the return list.
                    // If our trigger was no longer in the expected state, try a new one.
                    int rowsUpdated = getDelegate().updateTriggerStateFromOtherState(conn, triggerKey, STATE_ACQUIRED, STATE_WAITING);
                    if (rowsUpdated <= 0) {
                        continue; // next trigger
                    }

                    //****************UPDATE****************
                    //if a tempFireInstanceId exists, set the fireInstanceId to it and delete the temp fire instance id from the table QRTZ_TEMP_FIRE_INSTANCE_ID, else set a new fireInstanceId
                    String tempFireInstanceId = getTempFireInstanceId(conn, nextTrigger);
                    if (tempFireInstanceId != null) {
                        nextTrigger.setFireInstanceId(tempFireInstanceId);
                        //****************ATTENTION****************/
                        //deleteTempFireInstanceId(conn, tempFireInstanceId, nextTrigger);
                        //deletion of tempFireInstanceId is not done here, but in the method triggerFired of LoggingTriggerHistoryPluginCustom
                    } else {
                        nextTrigger.setFireInstanceId(getFiredTriggerRecordId());
                    }
                    //**************************************
                    //nextTrigger.setFireInstanceId(getFiredTriggerRecordId());

                    getDelegate().insertFiredTrigger(conn, nextTrigger, STATE_ACQUIRED, null);

                    acquiredTriggers.add(nextTrigger); //TODO
                    if (firstAcquiredTriggerFireTime == 0) {
                        firstAcquiredTriggerFireTime = nextTrigger.getNextFireTime().getTime();
                    }
                }

                // if we didn't end up with any trigger to fire from that first
                // batch, try again for another batch. We allow with a max retry count.
                if (acquiredTriggers.isEmpty() && currentLoopCount < MAX_DO_LOOP_RETRY) {
                    continue;
                }

                // We are done with the while loop.
                break;
            } catch (SQLException | JobPersistenceException | ClassNotFoundException | IOException e) {
                throw new JobPersistenceException(
                        "Couldn't acquire next trigger: " + e.getMessage(), e);
            }
        } while (true);

        // Return the acquired trigger list
        return acquiredTriggers;
    }

    /*@Override
     protected synchronized String getFiredTriggerRecordId() {
     return getInstanceId() + java.util.UUID.randomUUID().toString();
     }*/
    //check if constrains (osArch, availableProcessors, freePhysicalMemorySize etc. as a json from JobDataMap "#jobConstraints") are satisfied
    public boolean checkConstraints(JobDataMap jobDataMap) {
        try {
            if (jobDataMap.containsKey("#jobConstraints")) {
                OperatingSystemMXBean omx = ManagementFactory.getOperatingSystemMXBean();
                com.sun.management.OperatingSystemMXBean osMxBean = null;
                if (omx instanceof com.sun.management.OperatingSystemMXBean) {
                    osMxBean = (com.sun.management.OperatingSystemMXBean) omx;
                }
                if (osMxBean != null) {
                    //read json from request
                    JSONParser parser = new JSONParser();
                    Object obj = parser.parse(jobDataMap.getString("#jobConstraints"));
                    JSONArray jsonArray = (JSONArray) obj;
                    String ipAddress = getIpAddress();
                    for (int i = 0; i < jsonArray.size(); i++) {
                        JSONObject jsonobject = (JSONObject) jsonArray.get(i);
                        String systemParameterName = (String) jsonobject.get("systemParameterName"); //system parameter name (osArch, availableProcessors, freePhysicalMemorySize etc.)
                        String operator = (String) jsonobject.get("operator"); // ==, !=, <, >, <=, >=
                        String value = (String) jsonobject.get("value"); //value of the system parameter that must be satisfied
                        String systemParameterValue; //system parameter actual value
                        boolean ipAddressConstraint = false; //set ip address constraint as not satisfied by default

                        switch (systemParameterName) {
                            case "osArch":
                                systemParameterValue = osMxBean.getArch();
                                break;
                            case "availableProcessors":
                                systemParameterValue = Integer.toString(osMxBean.getAvailableProcessors());
                                break;
                            case "osName":
                                systemParameterValue = osMxBean.getArch();
                                break;
                            case "systemLoadAverage":
                                systemParameterValue = Double.toString(osMxBean.getSystemLoadAverage());
                                break;
                            case "osVersion":
                                systemParameterValue = osMxBean.getArch();
                                break;
                            case "committedVirtualMemorySize":
                                systemParameterValue = Long.toString(osMxBean.getCommittedVirtualMemorySize());
                                break;
                            case "freePhysicalMemorySize":
                                systemParameterValue = Long.toString(osMxBean.getFreePhysicalMemorySize());
                                break;
                            case "freeSwapSpaceSize":
                                systemParameterValue = Long.toString(osMxBean.getFreeSwapSpaceSize());
                                break;
                            case "processCpuLoad":
                                systemParameterValue = Double.toString(osMxBean.getProcessCpuLoad());
                                break;
                            case "processCpuTime":
                                systemParameterValue = Long.toString(osMxBean.getProcessCpuTime());
                                break;
                            case "systemCpuLoad":
                                systemParameterValue = Double.toString(osMxBean.getSystemCpuLoad());
                                break;
                            case "totalPhysicalMemorySize":
                                systemParameterValue = Long.toString(osMxBean.getTotalPhysicalMemorySize());
                                break;
                            case "totalSwapSpaceSize":
                                systemParameterValue = Long.toString(osMxBean.getTotalSwapSpaceSize());
                                break;
                            default:
                                systemParameterValue = null;
                        }

                        //if systemParameterName is ipAddress, and there is one ip address that is equal/not equal (depending on the operator) to the scheduler's ip address, then set the ipAddressConstraint to true
                        if (systemParameterName.equals("ipAddress")) {
                            String[] ipAddressesArray = value.split(";");
                            for (String tmp : ipAddressesArray) {
                                if ((operator.equals("==") && ipAddress.equals(tmp)) || (operator.equals("!=") && !ipAddress.equals(tmp))) {
                                    ipAddressConstraint = true;
                                    break;
                                }
                            }
                            //if the ipAddressConstraint is false, the return false
                            if (!ipAddressConstraint) {
                                return false;
                            }
                        }

                        //if the constraint is not satisfied, then return false
                        //if systemParameterName = ipAddress, then systemParameterValue is null (default switch case) and this if condition is not satisfied
                        if (systemParameterValue != null
                                && ((operator.equals("==") && !isNumeric(systemParameterValue) && !isNumeric(value) && !value.equalsIgnoreCase(systemParameterValue))
                                || (operator.equals("!=") && !isNumeric(systemParameterValue) && !isNumeric(value) && value.equalsIgnoreCase(systemParameterValue))
                                || (operator.equals("==") && isNumeric(systemParameterValue) && isNumeric(value) && Double.parseDouble(systemParameterValue) != Double.parseDouble(value))
                                || (operator.equals("!=") && isNumeric(systemParameterValue) && isNumeric(value) && Double.parseDouble(systemParameterValue) == Double.parseDouble(value))
                                || (operator.equals("<") && isNumeric(systemParameterValue) && isNumeric(value) && Double.parseDouble(systemParameterValue) >= Double.parseDouble(value))
                                || (operator.equals(">") && isNumeric(systemParameterValue) && isNumeric(value) && Double.parseDouble(systemParameterValue) <= Double.parseDouble(value))
                                || (operator.equals("<=") && isNumeric(systemParameterValue) && isNumeric(value) && Double.parseDouble(systemParameterValue) > Double.parseDouble(value))
                                || (operator.equals(">=") && isNumeric(systemParameterValue) && isNumeric(value) && Double.parseDouble(systemParameterValue) < Double.parseDouble(value)))) {
                            return false;
                        }
                    }
                }
            }
            return true;
        } catch (ParseException e) {
            e.printStackTrace();
            return true;
        }
    }

    //get the system status as a Map
    public Map<String, String> getSystemStatus() {
        OperatingSystemMXBean omx = ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean osMxBean = null;
        if (omx instanceof com.sun.management.OperatingSystemMXBean) {
            osMxBean = (com.sun.management.OperatingSystemMXBean) omx;
        }
        Map<String, String> statusMap = new HashMap<>();
        if (osMxBean != null) {
            statusMap.put("osArch", osMxBean.getArch());
            statusMap.put("availableProcessors", Integer.toString(osMxBean.getAvailableProcessors()));
            statusMap.put("osName", osMxBean.getName());
            statusMap.put("systemLoadAverage", Double.toString(osMxBean.getSystemLoadAverage()));
            statusMap.put("osVersion", osMxBean.getVersion());
            statusMap.put("committedVirtualMemorySize", Long.toString(osMxBean.getCommittedVirtualMemorySize()));
            statusMap.put("freePhysicalMemorySize", Long.toString(osMxBean.getFreePhysicalMemorySize()));
            statusMap.put("freeSwapSpaceSize", Long.toString(osMxBean.getFreeSwapSpaceSize()));
            statusMap.put("processCpuLoad", Double.toString(osMxBean.getProcessCpuLoad()));
            statusMap.put("processCpuTime", Long.toString(osMxBean.getProcessCpuTime()));
            statusMap.put("systemCpuLoad", Double.toString(osMxBean.getSystemCpuLoad()));
            statusMap.put("totalPhysicalMemorySize", Long.toString(osMxBean.getTotalPhysicalMemorySize()));
            statusMap.put("totalSwapSpaceSize", Long.toString(osMxBean.getTotalSwapSpaceSize()));
        }
        return statusMap;
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

    public void logToDatabase(Connection conn, Object args[], String message, boolean updateStatus) throws SQLException {
        PreparedStatement preparedStatement = null;
        PreparedStatement preparedStatementStatusUpdate = null;
        PreparedStatement preparedStatementStatusInsert = null;
        Statement stmt = null; //statement for start, commit or rollback transaction
        try {
            stmt = conn.createStatement();
            stmt.executeQuery("START TRANSACTION"); //START TRANSACTION
            preparedStatement = conn.prepareStatement("INSERT INTO quartz.QRTZ_LOGS (FIRE_INSTANCE_ID, JOB_NAME, JOB_GROUP, JOB_DATA, DATE, TRIGGER_NAME, TRIGGER_GROUP, PREV_FIRE_TIME, NEXT_FIRE_TIME, REFIRE_COUNT, RESULT, SCHEDULER_INSTANCE_ID, SCHEDULER_NAME, IP_ADDRESS, STATUS, LOGGER, LEVEL, MESSAGE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            preparedStatementStatusUpdate = conn.prepareStatement("UPDATE quartz.QRTZ_STATUS SET JOB_NAME = ?, JOB_GROUP = ?, JOB_DATA = ?, DATE = ?, TRIGGER_NAME = ?, TRIGGER_GROUP = ?, PREV_FIRE_TIME = ?, NEXT_FIRE_TIME = ?, REFIRE_COUNT = ?, RESULT = ?, SCHEDULER_INSTANCE_ID = ?, SCHEDULER_NAME = ?, IP_ADDRESS = ?, STATUS = ?, LOGGER = ?, LEVEL = ?, MESSAGE = ? WHERE FIRE_INSTANCE_ID = ?");
            preparedStatementStatusInsert = conn.prepareStatement("INSERT INTO quartz.QRTZ_STATUS (FIRE_INSTANCE_ID, JOB_NAME, JOB_GROUP, JOB_DATA, DATE, TRIGGER_NAME, TRIGGER_GROUP, PREV_FIRE_TIME, NEXT_FIRE_TIME, REFIRE_COUNT, RESULT, SCHEDULER_INSTANCE_ID, SCHEDULER_NAME, IP_ADDRESS, STATUS, LOGGER, LEVEL, MESSAGE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE DATE = values(DATE), JOB_NAME = values(JOB_NAME), JOB_GROUP = values(JOB_GROUP), JOB_DATA = values(JOB_DATA), TRIGGER_NAME = values(TRIGGER_NAME), TRIGGER_GROUP = values(TRIGGER_GROUP), PREV_FIRE_TIME = values(PREV_FIRE_TIME), NEXT_FIRE_TIME = values(NEXT_FIRE_TIME), REFIRE_COUNT = values(REFIRE_COUNT), RESULT = values(RESULT), SCHEDULER_INSTANCE_ID = values(SCHEDULER_INSTANCE_ID), SCHEDULER_NAME = values(SCHEDULER_NAME), IP_ADDRESS = values(IP_ADDRESS), STATUS = values(STATUS), LOGGER = values(LOGGER), LEVEL = values(LEVEL), MESSAGE = values(MESSAGE)");
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
            if (conn != null) {
                conn.close();
            }
        }
    }

    //insert a rejected trigger TEMP_FIRE_INSTANCE_ID to the database table QRTZ.REJECTED_TRIGGERS
    public void setTempFireInstanceId(Connection conn, String temp_fire_instance_id, OperableTrigger trigger) throws SQLException {
        try (PreparedStatement preparedStatement = conn.prepareStatement("INSERT IGNORE INTO quartz.QRTZ_REJECTED_TRIGGERS (TEMP_FIRE_INSTANCE_ID, TRIGGER_NAME, TRIGGER_GROUP, JOB_NAME, JOB_GROUP) VALUES (?,?,?,?,?)")) {
            preparedStatement.setString(1, temp_fire_instance_id);
            preparedStatement.setString(2, trigger.getKey().getName());
            preparedStatement.setString(3, trigger.getKey().getGroup());
            preparedStatement.setString(4, trigger.getJobKey().getName());
            preparedStatement.setString(5, trigger.getJobKey().getGroup());
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new SQLException(e);
        }
    }

    //get a rejected trigger TEMP_FIRE_INSTANCE_ID to the database table QRTZ.REJECTED_TRIGGERS
    public String getTempFireInstanceId(Connection conn, OperableTrigger trigger) throws SQLException {
        PreparedStatement preparedStatement = null;
        String result = null;
        try {
            preparedStatement = conn.prepareStatement("SELECT TEMP_FIRE_INSTANCE_ID FROM quartz.QRTZ_REJECTED_TRIGGERS WHERE TRIGGER_NAME = ? AND TRIGGER_GROUP = ?");
            preparedStatement.setString(1, trigger.getKey().getName());
            preparedStatement.setString(2, trigger.getKey().getGroup());
            ResultSet rs = preparedStatement.executeQuery();
            while (rs.next()) {
                result = rs.getString("TEMP_FIRE_INSTANCE_ID");
            }
            return result;
        } catch (SQLException e) {
            throw new SQLException(e);
        } finally {
            if (preparedStatement != null) {
                preparedStatement.close();
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
        }
    }

    public String getTriggerRejectedMessage() {
        return triggerRejectedMessage;
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

    //convert a job data map to string
    public String jobDataMapToString(JobDataMap jobDataMap) {
        String result = "";
        for (Map.Entry<String, Object> entry : jobDataMap.entrySet()) {
            result += entry.getKey() + "=" + entry.getValue() + ";\n";
        }
        return result;
    }
}
